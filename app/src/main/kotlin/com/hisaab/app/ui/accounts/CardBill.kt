package com.hisaab.app.ui.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.Hx
import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** A credit card's latest statement: what it billed, when it is due, and what has been spent since. */
data class CardBill(val billedMinor: Long?, val dueDay: LocalDate?, val unbilledMinor: Long?) {
    val known get() = billedMinor != null || unbilledMinor != null
}

/** Billing figures for every credit card, by account id. */
@HiltViewModel
class CardBillViewModel @Inject constructor(accounts: AccountDao, statements: StatementDao, txs: TransactionDao) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val bills = combine(accounts.observeWithActivity(Periods.startOfMonth(System.currentTimeMillis())), statements.observeAll()) { accs, all ->
        accs.filter { it.isCreditCardRow } to all
    }.flatMapLatest { (cards, all) ->
        if (cards.isEmpty()) return@flatMapLatest flowOf(emptyMap<Long, CardBill>())
        combine(cards.map { a ->
            val mine = all.filter { it.last4 == a.last4 && (it.bankName == null || it.bankName.equals(a.bankName, ignoreCase = true)) }
            val bill = mine.filter { it.totalDueMinor != null }.maxByOrNull { it.statementEpochDay ?: 0 }
            val due = bill?.dueEpochDay?.let(LocalDate::ofEpochDay)
            val day = bill?.statementEpochDay?.let(LocalDate::ofEpochDay)
            if (day == null) flowOf(a.id to CardBill(bill?.totalDueMinor, due, null))
            else {
                val from = day.plusDays(1).atStartOfDay(Periods.zone).toInstant().toEpochMilli()
                txs.monthlyForAccount(a.id, from, Long.MAX_VALUE / 2, Periods.offsetMillis(from)).map { rows ->
                    a.id to CardBill(bill.totalDueMinor, due, rows.sumOf { it.spent })
                }
            }
        }) { it.toMap() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

/** A credit card, as opposed to a bank account, debit card or deposit. */
internal val AccountWithActivity.isCreditCardRow: Boolean
    get() = kind == AccountKind.CARD && !isDebitCard && (accountType == AccountType.CREDIT_CARD || (accountType == null && latestBalanceMinor == null))

/** The billing figures for card [id], or null for none. */
@Composable
internal fun cardBill(id: Long, vm: CardBillViewModel = hiltViewModel()): CardBill? {
    val all by vm.bills.collectAsStateWithLifecycle()
    return all[id]
}

private val DUE_FORMAT = DateTimeFormatter.ofPattern("d MMM")

/** "Due 18 Oct" with its colour: red once overdue, amber within five days, muted otherwise. */
@Composable
internal fun dueLabel(due: LocalDate): Pair<String, Color> {
    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(Periods.zone), due)
    val colour = when { days < 0 -> Hx.neg; days <= 5 -> Hx.warn; else -> Hx.text2 }
    return t("Due {date}", "date" to due.format(DUE_FORMAT)) to colour
}
