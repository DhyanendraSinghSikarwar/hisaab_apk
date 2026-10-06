package com.hisaab.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.charts.Sparkline
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.components.KpiRow
import com.hisaab.app.ui.components.LetterBadge
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.components.Tag
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.BookPeriodChips
import com.hisaab.app.ui.ledger.LedgerMath
import com.hisaab.app.ui.ledger.ViewFilterStore
import com.hisaab.app.ui.theme.Hx
import com.hisaab.app.ui.theme.color
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountUsage
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class BusinessState(
    val accounts: List<AccountWithActivity> = emptyList(),
    val periodLabel: String = "",
    val revenue: Long = 0,
    val expenses: Long = 0,
    /** Profit for each of the six months ending with the period's last month, oldest first. */
    val monthly: List<Pair<YearMonth, Long>> = emptyList(),
    /** The period's business transactions, newest first. */
    val txs: List<TransactionEntity> = emptyList(),
    val loaded: Boolean = false,
) {
    val profit: Long get() = revenue - expenses
}

@HiltViewModel
class BusinessViewModel @Inject constructor(
    filters: ViewFilterStore,
    accounts: AccountDao,
    transactions: TransactionDao,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<BusinessState> = filters.filter.flatMapLatest { f ->
        val (from, to) = f.range
        val months = (5 downTo 0).map { YearMonth.from(to).minusMonths(it.toLong()) }
        val start = minOf(from, months.first().atDay(1))
        combine(
            transactions.observeBetween(millis(start), millis(to.plusDays(1)) - 1),
            accounts.observeWithActivity(Periods.startOfMonth(System.currentTimeMillis())),
        ) { all, accs ->
            val biz = accs.filter { it.usage == AccountUsage.BUSINESS }
            val ids = biz.map { it.id }.toSet()
            val mine = all.filter { it.accountId != null && it.accountId in ids && !it.needsReview }
            fun inRange(t: TransactionEntity, a: LocalDate, b: LocalDate) = Periods.localDate(t.timestamp).let { d -> !d.isBefore(a) && !d.isAfter(b) }
            val period = mine.filter { inRange(it, from, to) }
            val monthly = LedgerMath.monthly(mine, months).map { it.month to (it.income - it.spent) }
            BusinessState(
                accounts = biz, periodLabel = f.label,
                revenue = LedgerMath.income(period), expenses = LedgerMath.spent(period),
                monthly = monthly, txs = period.sortedByDescending { it.timestamp }, loaded = true,
            )
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BusinessState())

    private fun millis(d: LocalDate) = d.atStartOfDay(Periods.zone).toInstant().toEpochMilli()
}

private const val PAGE = 60

@Composable
fun BusinessRoute(onBack: () -> Unit, onOpenTransaction: (Long) -> Unit, vm: BusinessViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var shown by remember { mutableIntStateOf(PAGE) }
    MoreScaffold("Business book", onBack) { inner ->
        if (!s.loaded) return@MoreScaffold
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            if (s.accounts.isEmpty()) {
                item("empty") { NoBusinessAccounts() }
                return@LazyColumn
            }
            item("chips") { BookPeriodChips(showBook = false) }
            item("kpis") {
                HCard {
                    KpiRow(
                        Triple("Revenue", Money.format(s.revenue, showPaise = false), Hx.pos),
                        Triple("Expenses", Money.format(s.expenses, showPaise = false), Hx.neg),
                        Triple("Profit", (if (s.profit < 0) "−" else "") + Money.format(kotlin.math.abs(s.profit), showPaise = false), if (s.profit >= 0) Hx.pos else Hx.neg),
                    )
                    if (s.revenue > 0) {
                        Spacer(Modifier.height(8.dp))
                        val margin = (s.profit * 100 / s.revenue).toInt()
                        Text("Margin $margin% · ${s.periodLabel}", fontSize = 12.sp, color = Hx.text2)
                    }
                }
            }
            if (s.monthly.any { it.second != 0L }) {
                item("trend") {
                    HCard(title = "Profit · last 6 months") {
                        Sparkline(s.monthly.map { it.second.toFloat() }, color = Hx.accent, height = 56.dp)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            s.monthly.forEach { (m, _) ->
                                Text(Periods.monthShort(m), fontSize = 11.sp, color = Hx.text2, modifier = Modifier.weight(1f))
                            }
                        }
                        val last = s.monthly.last()
                        Text(
                            "${Periods.month(last.first)}: ${if (last.second < 0) "loss" else "profit"} ${Money.format(kotlin.math.abs(last.second), showPaise = false)}",
                            fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item("accounts") {
                HCard(title = "Business accounts") {
                    s.accounts.forEach { a ->
                        HRow(
                            title = a.nickname ?: a.bankName,
                            subtitle = "••${a.last4} · ${a.transactionCount} transactions",
                            leading = { LetterBadge(a.nickname ?: a.bankName, a.colorArgb?.let { Color(it) } ?: Hx.palette[(a.id % Hx.palette.size).toInt()]) },
                        ) {
                            a.currentBalanceMinor?.let { Text(Money.format(it, showPaise = false), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
            item("txhead") {
                HCard(title = "Transactions · ${s.periodLabel}", padding = 12.dp) {
                    if (s.txs.isEmpty()) {
                        Text("No business transactions in this period.", fontSize = 13.sp, color = Hx.text2, modifier = Modifier.padding(4.dp))
                    } else {
                        Column {
                            s.txs.take(shown).forEachIndexed { i, t ->
                                if (i > 0) HorizontalDivider(color = Hx.border)
                                TxRow(t) { onOpenTransaction(t.id) }
                            }
                        }
                        if (s.txs.size > shown) {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                                Pill("Show more (${s.txs.size - shown} left)") { shown += PAGE }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TxRow(t: TransactionEntity, onClick: () -> Unit) {
    val credit = t.type == TransactionType.CREDIT
    val amount = Money.format(t.amountMinor, t.currency)
    HRow(
        title = t.merchant ?: t.bankName,
        subtitle = "${Periods.localDate(t.timestamp).format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))} · ${t.category.label}",
        leading = { LetterBadge(t.merchant ?: t.bankName, t.category.color, size = 32.dp) },
        onClick = onClick,
    ) {
        Text(
            if (credit) "+$amount" else amount, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = when (t.type) { TransactionType.CREDIT -> Hx.pos; TransactionType.DEBIT -> Hx.neg; else -> Hx.text2 },
        )
    }
}

@Composable
private fun NoBusinessAccounts() {
    HCard {
        Tag("No business accounts", Hx.accent)
        Spacer(Modifier.height(10.dp))
        Text("Keep your business money apart", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Mark the accounts and cards you use for work, and Hisaab will show their revenue, expenses and profit here, " +
                "separate from your personal spending.",
            fontSize = 13.sp, color = Hx.text2,
        )
        Spacer(Modifier.height(12.dp))
        listOf("Open More › Accounts & cards", "Tap the pencil next to the account", "Set Used for to Business").forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                LetterBadge("${i + 1}", Hx.accent, size = 24.dp)
                Text(step, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}
