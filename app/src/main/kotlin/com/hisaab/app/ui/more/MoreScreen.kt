package com.hisaab.app.ui.more

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.notify.PaymentNotificationListener
import com.hisaab.app.settings.AppSettings
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.SmsPermissionState
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.LedgerMath
import com.hisaab.app.ui.plan.PlanSnapshot
import com.hisaab.app.ui.plan.PlanSource
import com.hisaab.app.ui.profile.ProfileAvatar
import com.hisaab.app.ui.theme.Hx
import com.hisaab.app.ui.theme.clearTopBar
import com.hisaab.email.imap.MailAccountStore
import com.hisaab.email.sync.GmailSettings
import com.hisaab.email.sync.GmailSettingsStore
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountUsage
import com.hisaab.shared.db.BudgetDao
import com.hisaab.shared.db.MerchantRuleDao
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.StatementEntity
import com.hisaab.shared.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

// ---------------------------------------------------------------------------------------------
// What the More list shows under each entry, from live data.
// ---------------------------------------------------------------------------------------------

data class MoreState(
    val budgetsActive: Int = 0,
    val budgetsNear: Int = 0,
    val plan: PlanSnapshot = PlanSnapshot(),
    val tax: TaxState = TaxState(),
    val hasBusiness: Boolean = false,
    val businessRevenue: Long = 0,
    val accounts: Int = 0,
    val rules: Int = 0,
    val statements: Int = 0,
    val lockedStatements: Int = 0,
    val app: AppSettings? = null,
    val mail: GmailSettings? = null,
    val imapEmails: List<String> = emptyList(),
)

private data class MoneyBits(val budgetsActive: Int, val budgetsNear: Int, val hasBusiness: Boolean, val revenue: Long, val accounts: Int)
private data class Housekeeping(val rules: Int, val statements: Int, val locked: Int)

@HiltViewModel
class MoreViewModel @Inject constructor(
    budgets: BudgetDao,
    transactions: TransactionDao,
    accounts: AccountDao,
    rules: MerchantRuleDao,
    statements: StatementDao,
    plan: PlanSource,
    tax: TaxSource,
    app: AppSettingsStore,
    gmail: GmailSettingsStore,
    mailAccounts: MailAccountStore,
) : ViewModel() {
    private val month = Periods.range(YearMonth.now(Periods.zone))

    private val money = combine(
        budgets.observeAll(),
        transactions.observeCategoryTotals(month.first, month.last),
        app.settings.map { it.budgetAlertPercent },
        accounts.observeWithActivity(month.first),
        transactions.observeBetween(month.first, month.last),
    ) { b, spent, alertAt, accs, txs ->
        val byCat = spent.associate { it.category to it.total }
        val near = b.count { (byCat[it.category] ?: 0L) * 100 >= it.monthlyLimitMinor * alertAt }
        val bizIds = accs.filter { it.usage == AccountUsage.BUSINESS }.map { it.id }.toSet()
        val revenue = LedgerMath.income(txs.filter { it.accountId != null && it.accountId in bizIds && !it.needsReview })
        MoneyBits(b.size, near, bizIds.isNotEmpty(), revenue, accs.count { !it.hidden })
    }

    private val house = combine(rules.observeAll(), statements.observeAll()) { r, st ->
        Housekeeping(r.size, st.size, st.count { it.status == StatementEntity.LOCKED })
    }

    private val mail = combine(gmail.settings, mailAccounts.emails) { g, e -> g to e }

    val state: StateFlow<MoreState> = combine(money, plan.snapshot, tax.state, house, combine(app.settings, mail) { a, m -> a to m }) { m, p, t, h, am ->
        MoreState(
            budgetsActive = m.budgetsActive, budgetsNear = m.budgetsNear, plan = p, tax = t,
            hasBusiness = m.hasBusiness, businessRevenue = m.revenue, accounts = m.accounts,
            rules = h.rules, statements = h.statements, lockedStatements = h.locked,
            app = am.first, mail = am.second.first, imapEmails = am.second.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreState())
}

// ---------------------------------------------------------------------------------------------
// The list.
// ---------------------------------------------------------------------------------------------

private data class Entry(val route: String, val title: String, val subtitle: String, val icon: ImageVector, val color: Color, val alert: Boolean = false)

private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreRoute(contentPadding: PaddingValues, onOpen: (String) -> Unit, vm: MoreViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var smsOk by remember { mutableStateOf(SmsPermissionState.check(context)) }
    var notifAccess by remember { mutableStateOf(PaymentNotificationListener.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        smsOk = SmsPermissionState.check(context)
        notifAccess = PaymentNotificationListener.hasAccess(context)
        onPauseOrDispose { }
    }
    val app = s.app
    val p = Hx.palette

    val sections: List<Pair<String, List<Entry>>> = listOf(
        "Plan" to listOf(
            Entry(
                "budgets", "Budgets",
                when {
                    s.budgetsActive == 0 -> "Set monthly limits by category"
                    s.budgetsNear > 0 -> "${s.budgetsActive} active · ${s.budgetsNear} near limit"
                    else -> "${s.budgetsActive} active · all on track"
                },
                Icons.Filled.DonutLarge, p[0], alert = s.budgetsNear > 0,
            ),
            run {
                val bills = s.plan.recurring.filter { !it.income }
                val perMonth = bills.sumOf { if (it.yearly) it.amountMinor / 12 else it.amountMinor }
                Entry(
                    "bills", "Bills & subscriptions",
                    when {
                        !s.plan.loaded -> "Looking for repeat payments…"
                        bills.isEmpty() -> "No repeat payments found yet"
                        else -> "${plural(bills.size, "recurring", "recurring")} · ${Money.format(perMonth, showPaise = false)}/mo"
                    },
                    Icons.AutoMirrored.Filled.ReceiptLong, p[1],
                )
            },
            Entry(
                "tax", "Tax centre",
                if (s.tax.loaded && s.tax.hasIncome) {
                    val t = s.tax
                    if (t.saving > 0) "${t.fyLabel} · ${if (t.newIsBetter) "New" else "Old"} regime saves ${Money.format(t.saving, showPaise = false)}"
                    else "${t.fyLabel} · estimated ${Money.format(t.newRegime.total, showPaise = false)}"
                } else "Estimate your tax for ${s.tax.fyLabel}",
                Icons.Filled.AccountBalance, p[6],
            ),
            Entry(
                "business", "Business book",
                if (s.hasBusiness) "Revenue ${Money.format(s.businessRevenue, showPaise = false)} this month" else "Mark accounts as Business",
                Icons.Filled.BusinessCenter, p[5],
            ),
        ),
        "Automation" to listOf(
            Entry("rules", "Rules", if (s.rules == 0) "Learned as you categorise" else plural(s.rules, "rule"), Icons.Filled.AutoAwesome, p[4]),
            Entry(
                "sources", "Data sources",
                run {
                    fun mark(ok: Boolean) = if (ok) "✓" else "✗"
                    val smsOn = smsOk && (app?.smsEnabled ?: true)
                    val mailOn = mailboxLabel(s.mail, s.imapEmails) != null && s.mail?.needsReauth != true
                    val notifOn = (app?.appNotificationsEnabled ?: false) && notifAccess
                    "SMS ${mark(smsOn)} · Email ${mark(mailOn)} · Notifications ${mark(notifOn)}"
                },
                Icons.Filled.Hub, p[2], alert = !smsOk,
            ),
        ),
        "Accounts" to listOf(
            Entry("accounts", "Accounts & cards", if (s.accounts == 0) "Found from your messages" else plural(s.accounts, "account") + " and cards", Icons.Filled.CreditCard, p[0]),
            Entry(
                "statements", "Statements",
                when {
                    s.statements == 0 -> "Card, bank and investment PDFs"
                    s.lockedStatements > 0 -> "${plural(s.statements, "statement")} · ${s.lockedStatements} need a password"
                    else -> plural(s.statements, "statement")
                },
                Icons.Filled.Description, p[3], alert = s.lockedStatements > 0,
            ),
        ),
        "App" to listOf(
            Entry(
                "customize", "Customise",
                "${(app?.theme ?: com.hisaab.app.settings.ThemeMode.SYSTEM).name.lowercase().replaceFirstChar { it.uppercase() }} theme · tab sections",
                Icons.Filled.Tune, p[7],
            ),
            Entry(
                "alerts", "Notifications & alerts",
                if (app == null) "Transaction and budget alerts"
                else "Transactions ${if (app.transactionNotifications) "on" else "off"} · budgets at ${app.budgetAlertPercent}%",
                Icons.Filled.Notifications, p[1],
            ),
            Entry(
                "security", "Security & backup",
                if (app == null) "App lock and backup" else "App lock ${if (app.appLock) "on" else "off"} · amounts ${if (app.hideAmounts) "hidden" else "shown"}",
                Icons.Filled.Lock, p[3],
            ),
            Entry("forex", "Forex rates", "Rates used for foreign spends", Icons.Filled.CurrencyExchange, p[6]),
            Entry("settings", "About & updates", "Version ${com.hisaab.app.BuildConfig.VERSION_NAME}", Icons.Filled.Info, p[2]),
        ),
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBar(),
                title = { Text("More", fontWeight = FontWeight.SemiBold) },
                actions = {
                    val name = app?.profile?.name ?: app?.displayName ?: "You"
                    Box(Modifier.padding(end = 12.dp).clip(CircleShape).clickable { onOpen("profile") }) {
                        ProfileAvatar(name, app?.profile?.photoPath, 36.dp)
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var index = 0
            sections.forEach { (header, entries) ->
                item("h:$header") {
                    Text(
                        header.uppercase(), color = Hx.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp,
                        modifier = Modifier.padding(start = 4.dp, top = if (header == sections.first().first) 0.dp else CardGap),
                    )
                }
                entries.forEach { e ->
                    val i = index++
                    item("e:${e.title}") { EntryCard(e, i) { onOpen(e.route) } }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(e: Entry, index: Int, onClick: () -> Unit) {
    // Cards rise into place one after another the first time the list is shown.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(index * 25L); appear.animateTo(1f, tween(260)) }
    HCard(
        modifier = Modifier.graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 16.dp.toPx() },
        onClick = onClick, padding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(e.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(e.icon, null, tint = e.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    e.subtitle, fontSize = 12.sp, color = if (e.alert) Hx.warn else Hx.text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Hx.text2)
        }
    }
}
