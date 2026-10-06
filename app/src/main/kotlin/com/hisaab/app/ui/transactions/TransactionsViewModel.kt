package com.hisaab.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.LedgerMath
import com.hisaab.app.ui.ledger.LedgerSource
import com.hisaab.app.ui.ledger.ViewFilterStore
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountEntity
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.db.TransactionSourceDao
import com.hisaab.shared.repo.TransactionRepository
import com.hisaab.shared.repo.TransactionsChangedNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** The quick type chips under the search box. */
enum class TxKind(val label: String) {
    ALL("All"), EXPENSE("Expense"), INCOME("Income"), TRANSFER("Transfer"), INVESTMENT("Investment"), REVIEW("Needs review");

    fun matches(t: TransactionEntity): Boolean = when (this) {
        ALL -> true
        EXPENSE -> LedgerMath.isSpend(t)
        INCOME -> LedgerMath.isIncome(t)
        TRANSFER -> isTransfer(t)
        INVESTMENT -> LedgerMath.isInvest(t)
        REVIEW -> t.needsReview
    }
}

fun isTransfer(t: TransactionEntity) = t.type == TransactionType.TRANSFER || t.category == Category.TRANSFER

/** Money in minus money out for one transaction, in rupee paise; transfers count as zero. */
fun netOf(t: TransactionEntity): Long = when {
    isTransfer(t) -> 0L
    t.type == TransactionType.CREDIT -> LedgerMath.rupees(t)
    else -> -LedgerMath.rupees(t)
}

data class DayGroup(val date: LocalDate, val txs: List<TransactionEntity>, val net: Long, val out: Long)

data class MerchantGroup(val name: String, val txs: List<TransactionEntity>, val gross: Long, val net: Long)

data class TxUi(
    val loaded: Boolean = false,
    val from: LocalDate = LocalDate.now(Periods.zone),
    val to: LocalDate = LocalDate.now(Periods.zone),
    val periodLabel: String = "",
    /** In the period and book, after the account/category filter: what the KPIs and counts describe. */
    val base: List<TransactionEntity> = emptyList(),
    /** [base] after the type chip and search. */
    val shown: List<TransactionEntity> = emptyList(),
    val inMinor: Long = 0,
    val outMinor: Long = 0,
    val reviewCount: Int = 0,
    val days: List<DayGroup> = emptyList(),
    val merchants: List<MerchantGroup> = emptyList(),
)

/** Narrowing that came from elsewhere in the app (an account or a category screen). */
data class Scope(val accountId: Long? = null, val category: Category? = null)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val notifier: TransactionsChangedNotifier,
    private val sourcesDao: TransactionSourceDao,
    accounts: AccountDao,
    ledger: LedgerSource,
    filters: ViewFilterStore,
    handle: SavedStateHandle,
) : ViewModel() {
    val query = MutableStateFlow("")
    val kind = MutableStateFlow(TxKind.ALL)
    val scope = MutableStateFlow(
        Scope(
            accountId = handle.get<Long>("accountId")?.takeIf { it > 0 },
            category = handle.get<String>("category")?.let { runCatching { Category.valueOf(it) }.getOrNull() },
        ),
    )

    init {
        // A link that names a month ("See all" on Home) moves the global period to it, once per visit.
        handle.get<String>("month")?.let { runCatching { YearMonth.parse(it) }.getOrNull() }?.let(filters::setMonth)
    }

    val accounts: StateFlow<List<AccountEntity>> = accounts.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(FlowPreview::class)
    val ui: StateFlow<TxUi> = combine(
        ledger.slice, scope, kind, query.debounce(120).map { it.trim() }.distinctUntilChanged().onStart { emit("") },
    ) { slice, sc, k, q ->
        val base = slice.txs.filter { t ->
            (sc.accountId == null || t.accountId == sc.accountId) && (sc.category == null || t.category == sc.category)
        }
        val shown = base.filter { k.matches(it) && matchesSearch(it, q) }
        TxUi(
            loaded = slice.loaded, from = slice.from, to = slice.to, periodLabel = slice.filter.label,
            base = base, shown = shown,
            inMinor = LedgerMath.income(base), outMinor = LedgerMath.spent(base) + LedgerMath.invested(base),
            reviewCount = base.count { it.needsReview },
            days = shown.groupBy { Periods.localDate(it.timestamp) }.map { (d, l) ->
                DayGroup(d, l, l.sumOf(::netOf), l.filter { LedgerMath.isSpend(it) || LedgerMath.isInvest(it) }.sumOf(LedgerMath::rupees))
            },
            merchants = shown.groupBy { it.merchant ?: it.bankName }.map { (n, l) ->
                MerchantGroup(n, l, l.sumOf(LedgerMath::rupees), l.sumOf(::netOf))
            }.sortedByDescending { it.gross },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxUi())

    /** Where each shown transaction came from ("SMS", "EMAIL"…), looked up in batches. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sources: StateFlow<Map<Long, List<String>>> = ui.map { u -> u.shown.map { it.id } }.distinctUntilChanged().mapLatest { ids ->
        ids.chunked(500).flatMap { sourcesDao.sourcesOf(it) }.groupBy({ it.transactionId }, { it.source })
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setQuery(q: String) { query.value = q }
    fun setKind(k: TxKind) { kind.value = k }
    fun clearAccount() = scope.update { it.copy(accountId = null) }
    fun clearCategory() = scope.update { it.copy(category = null) }

    fun setCategory(ids: Set<Long>, category: Category) = viewModelScope.launch {
        repository.setCategory(ids.toList(), category)
        notifier.onTransactionsChanged()
    }

    fun delete(ids: Set<Long>) = viewModelScope.launch {
        repository.deleteTransactions(ids.toList())
        notifier.onTransactionsChanged()
    }

    private fun matchesSearch(t: TransactionEntity, q: String): Boolean {
        if (q.isEmpty()) return true
        fun has(s: String?) = s != null && s.contains(q, ignoreCase = true)
        val digits = q.replace(",", "").replace("₹", "").trim().substringBefore('.')
        return has(t.merchant) || has(t.bankName) || has(t.note) || has(t.referenceNumber) || has(t.upiId) || has(t.category.label) ||
            (digits.isNotEmpty() && digits.all { it.isDigit() } && (t.amountMinor / 100).toString().startsWith(digits))
    }
}
