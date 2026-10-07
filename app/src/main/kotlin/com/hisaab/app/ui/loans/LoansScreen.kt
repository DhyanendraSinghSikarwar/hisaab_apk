package com.hisaab.app.ui.loans

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.charts.StackedMonthBars
import com.hisaab.app.ui.components.AccountAvatar
import com.hisaab.app.ui.components.BrandMark
import com.hisaab.app.ui.components.Brands
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.CollapsibleCard
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.components.HeroCard
import com.hisaab.app.ui.components.KpiRow
import com.hisaab.app.ui.components.LegendDot
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.components.SplitBar
import com.hisaab.app.ui.components.Tag
import com.hisaab.app.ui.components.enterOnce
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.Hx
import com.hisaab.app.ui.theme.clearTopBar
import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountEntity
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.insight.Amortization
import com.hisaab.shared.insight.Loan
import com.hisaab.shared.insight.LoanTerms
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")
private val SHORT = DateTimeFormatter.ofPattern("d MMM")
private val MONTH = DateTimeFormatter.ofPattern("MMM yy")

private fun rupees(minor: Long?) = minor?.let { Money.format(it, showPaise = false) } ?: "—"

private fun dueText(d: LocalDate?): String? = d?.let {
    val days = ChronoUnit.DAYS.between(LocalDate.now(Periods.zone), it)
    when (days) { 0L -> t("today"); 1L -> t("tomorrow"); else -> t("in {n} days", "n" to days) }
}

/** The lender's logo, with the "Loan" badge for a loan account. */
@Composable
private fun LoanAvatar(loan: Loan, size: androidx.compose.ui.unit.Dp = 40.dp) {
    if (loan.detected) BrandMark(Brands.forBank(loan.bankName), size = size)
    else AccountAvatar(loan.bankName, AccountKind.ACCOUNT, AccountType.LOAN, size = size)
}

// ---------------------------------------------------------------------------------------------
// The list.
// ---------------------------------------------------------------------------------------------

@HiltViewModel
class LoansViewModel @Inject constructor(source: LoanSource) : ViewModel() {
    val snapshot: StateFlow<LoansSnapshot> = source.snapshot
}

/** Every loan: the total still owed, then one card per loan with its EMI, next date and how much is repaid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansRoute(onBack: () -> Unit, onOpenLoan: (String) -> Unit, vm: LoansViewModel = hiltViewModel()) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(colors = clearTopBar(), title = { Text(t("Loans")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back")) } })
    }) { inner ->
        val accounts = s.loans.filter { !it.detected }
        val detected = s.loans.filter { it.detected }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp,
                bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            if (s.loaded && s.loans.isEmpty()) {
                item {
                    EmptyState(Icons.Filled.Payments, t("No loans yet"),
                        t("Loans appear from loan account messages and EMI debits. Or set an account's type to Loan."))
                }
                return@LazyColumn
            }
            if (s.loans.isNotEmpty()) item(key = "hero") { LoansHero(s) }
            items(accounts, key = { it.key }) { l -> LoanCard(l, Modifier.animateItem().enterOnce(0)) { onOpenLoan(l.key) } }
            if (detected.isNotEmpty()) {
                item(key = "detected-h") {
                    Text(t("Detected EMIs"), style = MaterialTheme.typography.titleSmall, color = Hx.text2,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
                items(detected, key = { it.key }) { l -> LoanCard(l, Modifier.animateItem()) { onOpenLoan(l.key) } }
            }
        }
    }
}

@Composable
private fun LoansHero(s: LoansSnapshot) {
    val next = s.active.filter { it.nextDue != null }.minByOrNull { it.nextDue!! }
    HeroCard {
        Text(t("Outstanding"), fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
        Text(rupees(s.outstandingMinor), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            HeroFigure(t("EMIs a month"), rupees(s.monthlyEmiMinor), Modifier.weight(1f))
            HeroFigure(t("Loans"), "${s.active.size}", Modifier.weight(1f))
            HeroFigure(t("Next EMI"), next?.nextDue?.format(SHORT) ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun LoanCard(l: Loan, modifier: Modifier = Modifier, onClick: () -> Unit) {
    HCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoanAvatar(l)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (l.detected) { Spacer(Modifier.width(6.dp)); Tag(t("Detected"), Hx.warn) }
                    if (l.closed) { Spacer(Modifier.width(6.dp)); Tag(t("Closed"), Hx.pos) }
                }
                Text(
                    listOfNotNull(l.emiMinor?.let { t("EMI {amount}", "amount" to rupees(it)) }, l.nextDue?.let { t("next {date}", "date" to it.format(SHORT)) }).joinToString(" · ").ifEmpty { t("No EMI found yet") },
                    fontSize = 12.sp, color = Hx.text2, maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(rupees(l.outstandingMinor), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(t("outstanding"), fontSize = 11.sp, color = Hx.text2)
            }
        }
        l.progress?.let { p ->
            Spacer(Modifier.height(12.dp))
            SplitBar(listOf(p to Hx.pos), height = 6.dp)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(t("{pct}% repaid", "pct" to (p * 100).toInt()), fontSize = 11.sp, color = Hx.text2, modifier = Modifier.weight(1f))
                Text(
                    l.totalEmis?.let { t("{paid} of {total} EMIs", "paid" to l.paidCount, "total" to it) } ?: l.remainingEmis?.let { t("about {n} EMIs left", "n" to it) }.orEmpty(),
                    fontSize = 11.sp, color = Hx.text2,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// One loan.
// ---------------------------------------------------------------------------------------------

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    source: LoanSource,
    private val dao: AccountDao,
) : ViewModel() {
    private val key: String = handle.get<Long>("accountId")?.takeIf { it > 0 }?.let { "a$it" } ?: "e${handle.get<String>("emi").orEmpty()}"

    val snapshot: StateFlow<LoansSnapshot> = source.snapshot
    val loan: StateFlow<Loan?> = source.snapshot.map { s -> s.loans.firstOrNull { it.key == key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), source.snapshot.value.loans.firstOrNull { it.key == key })

    fun saveTerms(accountId: Long, terms: LoanTerms?) = viewModelScope.launch {
        dao.setLoanTerms(accountId, terms?.principalMinor, terms?.rateBps, terms?.tenureMonths, terms?.startDay?.toEpochDay())
    }

    /** Makes a detected EMI a loan account; its EMIs then match by the lender's name. Calls [done] with the new id. */
    fun track(loan: Loan, done: (Long) -> Unit) = viewModelScope.launch {
        val existing = dao.find(loan.name, "")
        val id = existing?.id ?: dao.insert(
            AccountEntity(bankName = loan.name, last4 = "", kind = AccountKind.ACCOUNT, createdAt = System.currentTimeMillis(), accountType = AccountType.LOAN),
        )
        if (existing != null && existing.accountType != AccountType.LOAN) dao.setType(id, AccountType.LOAN, null)
        if (id > 0) done(id)
    }

    /** Links a detected EMI to a loan account by naming the account after the EMI's payee. */
    fun link(loan: Loan, account: AccountWithActivity, done: (Long) -> Unit) = viewModelScope.launch {
        dao.rename(account.id, loan.name, account.colorArgb)
        done(account.id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailRoute(
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenLoan: (Long) -> Unit,
    vm: LoanDetailViewModel = hiltViewModel(),
) {
    val loan by vm.loan.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    val l = loan
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(
            colors = clearTopBar(),
            title = { Text(l?.name ?: t("Loan"), maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back")) } },
            actions = { if (l?.accountId != null) IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, t("Edit loan details")) } },
        )
    }) { inner ->
        if (l == null) {
            if (snap.loaded) EmptyState(Icons.Filled.Payments, t("Loan not found"), t("It may have been removed or renamed."), Modifier.padding(inner))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp,
                bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            item(key = "hero") { LoanHero(l) }
            item(key = "repay") { RepaymentCard(l) }
            if (l.detected) {
                item(key = "detected") {
                    DetectedCard(
                        hasLoanAccounts = snap.loans.any { !it.detected },
                        onTrack = { vm.track(l, onOpenLoan) },
                        onLink = { linking = true },
                    )
                }
            } else if (l.terms == null) {
                item(key = "prompt") {
                    HCard(onClick = { editing = true }) {
                        HRow(
                            t("Add loan details for payoff date"), t("Amount, interest rate, tenure and first EMI"),
                            leading = { Icon(Icons.Filled.PostAdd, null, tint = Hx.accent) },
                        ) { Pill(t("Add"), on = true) { editing = true } }
                    }
                }
            }
            if (l.upcoming.isNotEmpty()) item(key = "schedule") { ScheduleCard(l) }
            item(key = "payments") { PaymentsCard(l, snap.accounts, onOpenTransaction) }
        }
    }
    val accountId = l?.accountId
    if (editing && l != null && accountId != null) {
        TermsSheet(l, onDismiss = { editing = false }, onSave = { vm.saveTerms(accountId, it); editing = false })
    }
    if (linking && l != null) {
        LinkSheet(snap.loans.filter { !it.detected }.mapNotNull { x -> x.accountId?.let(snap.accounts::get) }, onDismiss = { linking = false }) { a ->
            linking = false
            vm.link(l, a, onOpenLoan)
        }
    }
}

@Composable
private fun LoanHero(l: Loan) {
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoanAvatar(l, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (l.closed) t("Repaid") else t("Outstanding"), fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                Text(rupees(l.outstandingMinor), fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Text(
            when {
                l.outstandingMinor == null -> t("Not stated by the bank yet")
                l.outstandingFromBank -> t("As stated by the bank")
                else -> t("By the repayment schedule")
            },
            fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            HeroFigure(t("EMI"), rupees(l.emiMinor), Modifier.weight(1f))
            HeroFigure(t("Next due"), l.nextDue?.format(SHORT) ?: "—", Modifier.weight(1f))
            HeroFigure(t("Due"), dueText(l.nextDue) ?: "—", Modifier.weight(1f))
        }
        l.progress?.let { p ->
            Spacer(Modifier.height(14.dp))
            SplitBar(listOf(p to Color.White), height = 6.dp)
            Text(t("{pct}% repaid", "pct" to (p * 100).toInt()), fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RepaymentCard(l: Loan) {
    HCard(title = t("Repayment")) {
        val total = l.totalEmis ?: l.remainingEmis?.let { l.paidCount + it }
        if (total != null && total > 0) {
            val paid = l.paidCount.coerceAtMost(total)
            SplitBar(listOf(paid.toFloat() / total to Hx.pos), height = 10.dp)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(Hx.pos); Text("  " + t("{n} paid", "n" to paid), fontSize = 12.sp, color = Hx.text2, modifier = Modifier.weight(1f))
                LegendDot(Hx.surface2); Text("  " + t("{n} left", "n" to total - paid), fontSize = 12.sp, color = Hx.text2)
            }
        }
        KpiRow(
            Triple(t("EMIs paid"), l.totalEmis?.let { t("{paid} of {total}", "paid" to l.paidCount, "total" to it) } ?: "${l.paidCount}", null),
            Triple(t("Total paid"), rupees(l.totalPaidMinor), null),
            Triple(t("Remaining"), l.remainingEmis?.let { t("{n} EMIs", "n" to it) } ?: "—", null),
        )
        Spacer(Modifier.height(12.dp))
        val terms = l.terms
        if (terms != null) {
            KpiRow(
                Triple(t("Principal paid"), rupees(l.principalPaidMinor), Hx.pos),
                Triple(t("Interest paid"), rupees(l.interestPaidMinor), Hx.neg),
                Triple(t("Total interest"), rupees(l.totalInterestMinor), null),
            )
            Spacer(Modifier.height(12.dp))
            KpiRow(
                Triple(t("Borrowed"), rupees(terms.principalMinor), null),
                Triple(t("Rate"), "%.2f%%".format(terms.rateBps / 100.0), null),
                Triple(t("Payoff"), l.payoffDate?.format(MONTH) ?: "—", null),
            )
        } else {
            KpiRow(
                Triple(t("First EMI seen"), l.firstPaid?.format(SHORT) ?: "—", null),
                Triple(t("Last paid"), l.lastPaid?.format(SHORT) ?: "—", null),
                Triple(t("Payoff"), l.payoffDate?.let { "~" + it.format(MONTH) } ?: "—", null),
            )
        }
        l.emiDay?.let {
            Text(t("EMI on the {day} of every month · in Bills & subscriptions", "day" to ordinal(it)), fontSize = 11.sp, color = Hx.text2,
                modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun DetectedCard(hasLoanAccounts: Boolean, onTrack: () -> Unit, onLink: () -> Unit) {
    HCard(title = t("Detected from EMI payments")) {
        Text(t("Track it as a loan to add its amount, rate and tenure."), fontSize = 13.sp, color = Hx.text2)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(t("Track as loan"), on = true, leading = Icons.Filled.PostAdd, onClick = onTrack)
            if (hasLoanAccounts) Pill(t("Link to a loan"), leading = Icons.Filled.Link, onClick = onLink)
        }
    }
}

@Composable
private fun ScheduleCard(l: Loan) {
    val rows = l.upcoming
    val principal = Hx.accent
    val interest = Hx.warn
    HCard(title = t("Next {n} EMIs", "n" to rows.size)) {
        StackedMonthBars(
            labels = rows.map { r -> r.due?.format(DateTimeFormatter.ofPattern("MMM")) ?: "#${r.number}" },
            stacks = rows.map { listOf(it.principalMinor, it.interestMinor) },
            colors = listOf(principal, interest),
            averageLabel = null, average = null, onTap = {},
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) { LegendDot(principal); Text("  " + t("Principal"), fontSize = 12.sp, color = Hx.text2) }
            Row(verticalAlignment = Alignment.CenterVertically) { LegendDot(interest); Text("  " + t("Interest"), fontSize = 12.sp, color = Hx.text2) }
        }
    }
    Spacer(Modifier.height(CardGap))
    CollapsibleCard(t("Schedule"), trailing = t("{n} EMIs", "n" to rows.size), initiallyExpanded = false) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            listOf("Date", "Principal", "Interest", "Balance").forEachIndexed { i, h ->
                Text(t(h), fontSize = 11.sp, color = Hx.text2, fontWeight = FontWeight.Medium, modifier = Modifier.weight(if (i == 0) 0.8f else 1f))
            }
        }
        rows.forEach { r ->
            HorizontalDivider(color = Hx.border.copy(alpha = 0.6f))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(r.due?.format(MONTH) ?: "#${r.number}", fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                Text(rupees(r.principalMinor), fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(rupees(r.interestMinor), fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(Money.compact(r.balanceAfterMinor), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PaymentsCard(l: Loan, accounts: Map<Long, AccountWithActivity>, onOpen: (Long) -> Unit) {
    HCard(title = t("Payments · {n}", "n" to l.payments.size)) {
        if (l.payments.isEmpty()) {
            Text(t("No EMI debits found yet."), fontSize = 13.sp, color = Hx.text2)
            return@HCard
        }
        l.payments.take(60).forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = Hx.border.copy(alpha = 0.6f))
            val from = t.accountId?.let(accounts::get)?.let { a -> "${a.nickname ?: a.bankName}" + (a.last4.takeIf { it.isNotBlank() }?.let { " ••$it" } ?: "") }
            HRow(
                title = Periods.localDate(t.timestamp).format(DAY),
                subtitle = listOfNotNull(t.merchant, from).joinToString(" · ").ifEmpty { null },
                onClick = { onOpen(t.id) },
            ) {
                val emi = l.emiMinor
                val extra = emi != null && t.amountMinor > emi + emi / 5
                Column(horizontalAlignment = Alignment.End) {
                    Text(rupees(t.amountMinor), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (extra) Text(com.hisaab.app.i18n.t("prepayment"), fontSize = 11.sp, color = Hx.pos)
                }
            }
        }
    }
}

private fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"
    else -> "th"
}

// ---------------------------------------------------------------------------------------------
// Sheets.
// ---------------------------------------------------------------------------------------------

/** A loan's terms: amount borrowed, rate, tenure and first EMI. Clearing them all removes the schedule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermsSheet(l: Loan, onDismiss: () -> Unit, onSave: (LoanTerms?) -> Unit) {
    val t = l.terms
    var principal by remember { mutableStateOf(t?.principalMinor?.let { (it / 100).toString() }.orEmpty()) }
    var rate by remember { mutableStateOf(t?.rateBps?.let { "%.2f".format(it / 100.0).trimEnd('0').trimEnd('.') }.orEmpty()) }
    var tenure by remember { mutableStateOf(t?.tenureMonths?.toString().orEmpty()) }
    var start by remember { mutableStateOf(t?.startDay ?: l.firstPaid) }
    val p = Money.parseInput(principal)?.takeIf { it > 0 }
    val r = rate.trim().toDoubleOrNull()?.takeIf { it in 0.0..60.0 }
    val n = tenure.trim().toIntOrNull()?.takeIf { it in 1..600 }
    val badP = principal.isNotBlank() && p == null
    val badR = rate.isNotBlank() && r == null
    val badN = tenure.isNotBlank() && n == null
    val complete = p != null && r != null && n != null
    val emi = if (complete) Amortization.emi(p!!, Math.round(r!! * 100).toInt(), n!!) else null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoanAvatar(l, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(com.hisaab.app.i18n.t("Loan details"), style = MaterialTheme.typography.titleLarge)
                    Text(l.name, style = MaterialTheme.typography.bodyMedium, color = Hx.text2)
                }
            }
            OutlinedTextField(
                principal, { principal = it }, Modifier.fillMaxWidth(), label = { Text(com.hisaab.app.i18n.t("Loan amount")) }, prefix = { Text("₹") },
                singleLine = true, isError = badP, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.weight(1f), label = { Text(com.hisaab.app.i18n.t("Interest rate")) },
                    suffix = { Text(com.hisaab.app.i18n.t("% p.a.")) }, singleLine = true, isError = badR, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    tenure, { tenure = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text(com.hisaab.app.i18n.t("Tenure")) },
                    suffix = { Text(com.hisaab.app.i18n.t("months")) }, singleLine = true, isError = badN, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            DateField(com.hisaab.app.i18n.t("First EMI"), start) { start = it }
            if (emi != null) {
                HCard(container = Hx.surface2, padding = 12.dp) {
                    KpiRow(
                        Triple(com.hisaab.app.i18n.t("EMI by these terms"), rupees(emi), null),
                        Triple(com.hisaab.app.i18n.t("EMI debited"), rupees(l.emiMinor), null),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (t != null) TextButton(onClick = { onSave(null) }) { Text(com.hisaab.app.i18n.t("Clear"), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(com.hisaab.app.i18n.t("Cancel")) }
                Button(
                    onClick = { onSave(LoanTerms(p!!, Math.round(r!! * 100).toInt(), n!!, start)) },
                    enabled = complete,
                ) { Text(com.hisaab.app.i18n.t("Save")) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, day: LocalDate?, onDay: (LocalDate?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    LaunchedEffect(pressed) { if (pressed) picking = true }
    OutlinedTextField(
        day?.format(DAY).orEmpty(), {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Event, null) }, singleLine = true, interactionSource = press,
    )
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (day ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    onDay(state.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                    picking = false
                }) { Text(t("Done")) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(t("Cancel")) } },
        ) { DatePicker(state, Modifier.padding(top = 8.dp)) }
    }
}

/** Picks the loan account a detected EMI belongs to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkSheet(accounts: List<AccountWithActivity>, onDismiss: () -> Unit, onPick: (AccountWithActivity) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Text(t("Link to a loan"), style = MaterialTheme.typography.titleLarge)
            Text(t("The loan is renamed after this EMI's payee so its payments match."), fontSize = 12.sp, color = Hx.text2,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            accounts.forEach { a ->
                HRow(
                    a.nickname ?: a.bankName, a.last4.takeIf { it.isNotBlank() }?.let { t("Loan") + " ••$it" } ?: t("Loan"),
                    leading = { AccountAvatar(a.bankName, a.kind, a.accountType, size = 36.dp) },
                    onClick = { onPick(a) },
                )
            }
        }
    }
}
