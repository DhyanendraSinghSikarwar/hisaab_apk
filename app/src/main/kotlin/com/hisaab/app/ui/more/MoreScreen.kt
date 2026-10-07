package com.hisaab.app.ui.more

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Translate
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
import com.hisaab.app.i18n.t
import com.hisaab.app.notify.PaymentNotificationListener
import com.hisaab.app.settings.AppSettings
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.enterOnce
import com.hisaab.app.ui.components.SmsPermissionState
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.Book
import com.hisaab.app.ui.ledger.ViewFilterStore
import com.hisaab.app.ui.plan.PlanSnapshot
import com.hisaab.app.ui.plan.PlanSource
import com.hisaab.app.ui.profile.ProfileAvatar
import com.hisaab.app.ui.theme.Hx
import com.hisaab.app.ui.theme.clearTopBar
import com.hisaab.email.imap.MailAccountStore
import com.hisaab.email.sync.GmailSettings
import com.hisaab.email.sync.GmailSettingsStore
import com.hisaab.shared.db.AccountDao
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
    val accounts: Int = 0,
    val rules: Int = 0,
    val statements: Int = 0,
    val lockedStatements: Int = 0,
    val app: AppSettings? = null,
    val mail: GmailSettings? = null,
    val imapEmails: List<String> = emptyList(),
    val loans: com.hisaab.app.ui.loans.LoansSnapshot = com.hisaab.app.ui.loans.LoansSnapshot(),
)

private data class MoneyBits(val budgetsActive: Int, val budgetsNear: Int, val accounts: Int)
private data class Housekeeping(val rules: Int, val statements: Int, val locked: Int)

@HiltViewModel
class MoreViewModel @Inject constructor(
    budgets: BudgetDao,
    transactions: TransactionDao,
    accounts: AccountDao,
    rules: MerchantRuleDao,
    statements: StatementDao,
    plan: PlanSource,
    loans: com.hisaab.app.ui.loans.LoanSource,
    tax: TaxSource,
    app: AppSettingsStore,
    gmail: GmailSettingsStore,
    mailAccounts: MailAccountStore,
    private val filters: ViewFilterStore,
) : ViewModel() {
    private val month = Periods.range(YearMonth.now(Periods.zone))

    private val money = combine(
        budgets.observeAll(),
        transactions.observeCategoryTotals(month.first, month.last),
        app.settings.map { it.budgetAlertPercent },
        accounts.observeWithActivity(month.first),
        filters.filter.map { it.book },
    ) { b, spent, alertAt, accs, book ->
        val byCat = spent.associate { it.category to it.total }
        val near = b.count { (byCat[it.category] ?: 0L) * 100 >= it.monthlyLimitMinor * alertAt }
        MoneyBits(b.size, near, accs.count { !it.hidden && (book == Book.ALL || it.usage.name == book.name) })
    }

    private val house = combine(rules.observeAll(), statements.observeAll()) { r, st ->
        Housekeeping(r.size, st.size, st.count { it.status == StatementEntity.LOCKED })
    }

    private val mail = combine(gmail.settings, mailAccounts.emails, loans.snapshot) { g, e, l -> Triple(g, e, l) }

    val state: StateFlow<MoreState> = combine(money, plan.snapshot, tax.state, house, combine(app.settings, mail) { a, m -> a to m }) { m, p, t, h, am ->
        MoreState(
            budgetsActive = m.budgetsActive, budgetsNear = m.budgetsNear, plan = p, tax = t,
            accounts = m.accounts,
            rules = h.rules, statements = h.statements, lockedStatements = h.locked,
            app = am.first, mail = am.second.first, imapEmails = am.second.second, loans = am.second.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreState())

    /** The book the whole app shows (Personal, Business or All), kept across launches. */
    val book: StateFlow<Book> = filters.filter.map { it.book }.stateIn(viewModelScope, SharingStarted.Eagerly, filters.filter.value.book)

    fun setBook(b: Book) = filters.setBook(b)
}

// ---------------------------------------------------------------------------------------------
// The list.
// ---------------------------------------------------------------------------------------------

private data class Entry(val route: String, val title: String, val subtitle: String, val icon: ImageVector, val color: Color, val alert: Boolean = false)

/** [one] or [many] (each already translated, with an `{n}` placeholder) filled with [n]. */
private fun plural(n: Int, one: String, many: String) = (if (n == 1) one else many).replace("{n}", n.toString())

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
        t("Plan") to listOf(
            Entry(
                "budgets", t("Budgets"),
                when {
                    s.budgetsActive == 0 -> t("Set monthly limits by category")
                    s.budgetsNear > 0 -> t("{active} active · {near} near limit", "active" to s.budgetsActive, "near" to s.budgetsNear)
                    else -> t("{active} active · all on track", "active" to s.budgetsActive)
                },
                Icons.Filled.DonutLarge, p[0], alert = s.budgetsNear > 0,
            ),
            run {
                val bills = s.plan.recurring.filter { !it.income }
                val perMonth = bills.sumOf { if (it.yearly) it.amountMinor / 12 else it.amountMinor }
                Entry(
                    "bills", t("Bills & subscriptions"),
                    when {
                        !s.plan.loaded -> t("Looking for repeat payments…")
                        bills.isEmpty() -> t("No repeat payments found yet")
                        else -> t("{n} recurring · {amount}/mo", "n" to bills.size, "amount" to Money.format(perMonth, showPaise = false))
                    },
                    Icons.AutoMirrored.Filled.ReceiptLong, p[1],
                )
            },
            run {
                val l = s.loans
                Entry(
                    "loans", t("Loans"),
                    when {
                        !l.loaded -> t("Looking for loans…")
                        l.active.isEmpty() -> t("From loan messages and EMI debits")
                        else -> "${plural(l.active.size, t("{n} loan"), t("{n} loans"))} · ${t("{amount} EMI/month", "amount" to Money.format(l.monthlyEmiMinor, showPaise = false))}"
                    },
                    Icons.Filled.Payments, p[5],
                )
            },
            Entry(
                "tax", t("Tax centre"),
                if (s.tax.loaded && s.tax.hasIncome) {
                    val tx = s.tax
                    val saving = Money.format(tx.saving, showPaise = false)
                    if (tx.saving > 0) "${tx.fyLabel} · " + (if (tx.newIsBetter) t("New regime saves {amount}", "amount" to saving) else t("Old regime saves {amount}", "amount" to saving))
                    else "${tx.fyLabel} · " + t("estimated {amount}", "amount" to Money.format(tx.newRegime.total, showPaise = false))
                } else t("Estimate your tax for {fy}", "fy" to s.tax.fyLabel),
                Icons.Filled.AccountBalance, p[6],
            ),
        ),
        t("Automation") to listOf(
            Entry("rules", t("Rules"), if (s.rules == 0) t("Learned as you categorise") else plural(s.rules, t("{n} rule"), t("{n} rules")), Icons.Filled.AutoAwesome, p[4]),
            Entry(
                "sources", t("Data sources"),
                run {
                    fun mark(ok: Boolean) = if (ok) "✓" else "✗"
                    val smsOn = smsOk && (app?.smsEnabled ?: true)
                    val mailOn = mailboxLabel(s.mail, s.imapEmails) != null && s.mail?.needsReauth != true
                    val notifOn = (app?.appNotificationsEnabled ?: false) && notifAccess
                    t("SMS {sms} · Email {email} · Notifications {notif}", "sms" to mark(smsOn), "email" to mark(mailOn), "notif" to mark(notifOn))
                },
                Icons.Filled.Hub, p[2], alert = !smsOk,
            ),
            Entry("activity", t("Activity log"), t("Each sync, on this phone"), Icons.Filled.History, p[5]),
        ),
        t("Accounts") to listOf(
            Entry("accounts", t("Accounts & cards"), if (s.accounts == 0) t("Found from your messages") else plural(s.accounts, t("{n} account and cards"), t("{n} accounts and cards")), Icons.Filled.CreditCard, p[0]),
            Entry(
                "statements", t("Statements"),
                when {
                    s.statements == 0 -> t("Card, bank and investment PDFs")
                    s.lockedStatements > 0 -> "${plural(s.statements, t("{n} statement"), t("{n} statements"))} · ${t("{n} need a password", "n" to s.lockedStatements)}"
                    else -> plural(s.statements, t("{n} statement"), t("{n} statements"))
                },
                Icons.Filled.Description, p[3], alert = s.lockedStatements > 0,
            ),
        ),
        t("App") to listOf(
            Entry(
                "customize", t("Customise"),
                t("{theme} theme · tab sections", "theme" to t((app?.theme ?: com.hisaab.app.settings.ThemeMode.SYSTEM).name.lowercase().replaceFirstChar { it.uppercase() })),
                Icons.Filled.Tune, p[7],
            ),
            Entry(
                "alerts", t("Notifications & alerts"),
                if (app == null) t("Transaction and budget alerts")
                else t("Transactions {state} · budgets at {pct}%", "state" to if (app.transactionNotifications) t("on") else t("off"), "pct" to app.budgetAlertPercent),
                Icons.Filled.Notifications, p[1],
            ),
            Entry(
                "security", t("Security & backup"),
                if (app == null) t("App lock and backup") else t("App lock {lock} · amounts {amounts}", "lock" to if (app.appLock) t("on") else t("off"), "amounts" to if (app.hideAmounts) t("hidden") else t("shown")) +
                    if (app.textSize != com.hisaab.app.settings.TextSize.DEFAULT) " · " + t("text {size}", "size" to t(app.textSize.label).lowercase()) else "",
                Icons.Filled.Lock, p[3],
            ),
            Entry(
                "language", t("Language"),
                com.hisaab.app.i18n.I18n.language.let { if (it == com.hisaab.app.i18n.Language.ENGLISH) "English" else "${it.native} · ${t(it.english)}" },
                Icons.Filled.Translate, p[5],
            ),
            Entry("forex", t("Forex rates"), t("Rates used for foreign spends"), Icons.Filled.CurrencyExchange, p[6]),
            Entry("settings", t("About & updates"), t("Version {version}", "version" to com.hisaab.app.BuildConfig.VERSION_NAME), Icons.Filled.Info, p[2]),
        ),
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBar(),
                title = { Text(t("More"), fontWeight = FontWeight.SemiBold) },
                actions = {
                    val name = app?.profile?.name ?: app?.displayName ?: t("You")
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
                        modifier = Modifier.padding(start = 4.dp, top = CardGap),
                    )
                }
                entries.forEach { e ->
                    val i = index++
                    item("e:${e.title}") { EntryCard(e, i) { onOpen(e.route) } }
                }
            }
            item("footer") {
                Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("You're all caught up"), color = Hx.text2, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        t("DhanKosh {version}", "version" to "v" + com.hisaab.app.BuildConfig.VERSION_NAME),
                        color = Hx.text2.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** How many cards play the entrance animation on first show. */
private const val FIRST_SCREEN = 6

@Composable
private fun EntryCard(e: Entry, index: Int, onClick: () -> Unit) {
    // Only the first screenful rises into place; cards further down render at once, so scrolling never meets a blank card.
    HCard(
        modifier = if (index < FIRST_SCREEN) Modifier.enterOnce(index + 1) else Modifier,
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
