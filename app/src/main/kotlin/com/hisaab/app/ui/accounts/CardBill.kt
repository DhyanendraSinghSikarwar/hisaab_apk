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
import com.hisaab.shared.repo.CardDues
import com.hisaab.shared.repo.CardPosition
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

/** A credit card's billing figures, see [CardPosition]: billed and due date from a statement or due message, else outstanding. */
data class CardBill(
    val billedMinor: Long?,
    val dueDay: LocalDate?,
    val unbilledMinor: Long?,
    val minDueMinor: Long? = null,
    val outstandingMinor: Long? = null,
    /** Billed and due date were worked out from the usual statement day, no statement was seen. */
    val estimated: Boolean = false,
    /** Limit left, as [CardPosition.limitLeft] works it out (the same figure as the card page and Portfolio). */
    val limitLeftMinor: Long? = null,
) {
    val known get() = billedMinor != null || unbilledMinor != null || outstandingMinor != null
}

internal fun CardPosition.toBill(available: Long?) = CardBill(billedMinor, dueDay, unbilledMinor, minDueMinor, outstandingMinor, estimated, limitLeft(limitMinor, available))

/** Billing figures for every credit card, by account id. */
@HiltViewModel
class CardBillViewModel @Inject constructor(accounts: AccountDao, statements: StatementDao, txs: TransactionDao) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val bills = combine(accounts.observeWithActivity(Periods.startOfMonth(System.currentTimeMillis())), statements.observeAll()) { accs, all ->
        accs.filter { it.isCreditCardRow } to all
    }.flatMapLatest { (cards, all) ->
        if (cards.isEmpty()) return@flatMapLatest flowOf(emptyMap<Long, CardBill>())
        val today = LocalDate.now(Periods.zone)
        val from = today.minusDays(CardDues.WINDOW_DAYS).atStartOfDay(Periods.zone).toInstant().toEpochMilli()
        val refs = cards.map { CardDues.CardRef(it.bankName, it.last4) }
        combine(cards.map { a ->
            val mine = CardDues.rowsFor(all, a.bankName, a.last4, refs)
            // A bill older than the window still needs the spends since its statement date.
            val since = mine.mapNotNull { it.statementEpochDay }.maxOrNull()?.let { LocalDate.ofEpochDay(it).atStartOfDay(Periods.zone).toInstant().toEpochMilli() }
            txs.cardFlowSince(a.id, minOf(from, since ?: from)).map { flow ->
                a.id to CardDues.position(mine, a.currentBalanceMinor, flow, today, Periods.zone).toBill(a.currentBalanceMinor)
            }
        }) { it.toMap() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

/** A credit card, as opposed to a bank account, debit card or deposit. */
internal val AccountWithActivity.isCreditCardRow: Boolean
    get() = kind == AccountKind.CARD && !isDebitCard && (accountType == AccountType.CREDIT_CARD || (accountType == null && availableLimitMinor != null))

/** The billing figures for card [id], or null for none. */
@Composable
internal fun cardBill(id: Long, vm: CardBillViewModel = hiltViewModel()): CardBill? {
    val all by vm.bills.collectAsStateWithLifecycle()
    return all[id]
}

private val DUE_FORMAT = DateTimeFormatter.ofPattern("d MMM")

/** "From statement · 2 Oct", "From SMS · 2 Oct", "From email · 2 Oct" or "Edited · 10 Oct"; null when the figures were worked out. */
internal fun cardSourceLine(p: CardPosition): String? {
    val at = p.sourceAtMs?.let { java.time.Instant.ofEpochMilli(it).atZone(Periods.zone).toLocalDate() }
    val edited = p.source == "MANUAL"
    val day = (if (edited) at else p.statementDay ?: at)?.format(DUE_FORMAT) ?: return null
    return when (p.source) {
        "MANUAL" -> t("Edited · {date}", "date" to day)
        "SMS" -> t("From SMS · {date}", "date" to day)
        "EMAIL" -> t("From email · {date}", "date" to day)
        "STATEMENT" -> t("From statement · {date}", "date" to day)
        else -> null
    }
}

/** "Pay by 18 Oct · 5 days left" with its colour: red once overdue, amber within five days, muted otherwise; "est." when worked out. */
@Composable
internal fun dueLabel(due: LocalDate, estimated: Boolean = false): Pair<String, Color> {
    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(Periods.zone), due)
    val colour = when { days < 0 -> Hx.neg; days <= 5 -> Hx.warn; else -> Hx.text2 }
    val left = if (days < 0) t("Overdue") else t("{n} days left", "n" to days.toString())
    val label = t("Pay by {date}", "date" to due.format(DUE_FORMAT)) + " · " + left + if (estimated) " · " + t("est.") else ""
    return label to colour
}
