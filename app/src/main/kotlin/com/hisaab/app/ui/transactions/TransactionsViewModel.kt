package com.hisaab.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.components.Brands
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.LedgerMath
import com.hisaab.app.ui.ledger.LedgerSlice
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
import kotlinx.coroutines.flow.filterNotNull
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

/** The type chips under the search box. [REVIEW] has no chip: the review banner selects it. */
enum class TxKind(val label: String) {
    ALL("All"), INCOME("Income"), EXPENSE("Spends"), INVESTMENT("Investments"), TRANSFER("Transfers"), REVIEW("Needs review");

    fun matches(t: TransactionEntity): Boolean = when (this) {
        ALL -> true
        EXPENSE -> LedgerMath.isSpend(t)
        INCOME -> LedgerMath.isIncome(t)
        TRANSFER -> isTransfer(t)
        INVESTMENT -> LedgerMath.isInvest(t)
        REVIEW -> t.needsReview
    }

    companion object {
        /** The kinds shown as chips. */
        val CHIPS = listOf(ALL, INCOME, EXPENSE, INVESTMENT, TRANSFER)
    }
}

/** Where a transaction came from, as the source chips group it. [MANUAL] is anything else, or no message at all. */
enum class TxSource(val label: String, private val codes: Set<String>) {
    SMS("SMS", setOf("SMS")), EMAIL("Email", setOf("EMAIL")), STATEMENT("Statement", setOf("STATEMENT")),
    APP("App", setOf("APP", "NOTIFICATION")), MANUAL("Manual", emptySet());

    fun matches(sources: List<String>?): Boolean {
        val found = sources.orEmpty().map { it.uppercase() }
        return if (this == MANUAL) found.none { c -> entries.any { c in it.codes } } else found.any { it in codes }
    }
}

/** One bank or card issuer in the period: [key] is the brand name, [bankName] a raw name to draw its logo from. */
data class BankOption(val key: String, val short: String, val bankName: String, val count: Int)

/** The brand a bank name belongs to, so "HDFC" and "HDFC Bank" share one chip. */
fun bankKey(bankName: String): String = Brands.forBank(bankName).name

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
    /** [base] after the type, bank and source chips and search: what the list and the KPIs show. */
    val shown: List<TransactionEntity> = emptyList(),
    /** Banks and card issuers in [base], most used first. */
    val banks: List<BankOption> = emptyList(),
    /** Source chips worth showing: the four main ones, plus Manual when [base] has any. */
    val sourceChips: List<TxSource> = emptyList(),
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
    /** The chosen bank chip, by [bankKey]; null for all banks. */
    val bank = MutableStateFlow<String?>(null)
    val source = MutableStateFlow<TxSource?>(null)
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

    /** The period's transactions after any scope from a link. */
    private val base: StateFlow<Pair<LedgerSlice, List<TransactionEntity>>?> = combine(ledger.slice, scope) { slice, sc ->
        slice to slice.txs.filter { t ->
            (sc.accountId == null || t.accountId == sc.accountId) && (sc.category == null || t.category == sc.category)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Where each transaction in the period came from ("SMS", "EMAIL"…), looked up in batches. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sources: StateFlow<Map<Long, List<String>>> = base.map { b -> b?.second.orEmpty().map { it.id } }.distinctUntilChanged().mapLatest { ids ->
        ids.chunked(500).flatMap { sourcesDao.sourcesOf(it) }.groupBy({ it.transactionId }, { it.source })
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    @OptIn(FlowPreview::class)
    private val search = query.debounce(120).map { it.trim() }.distinctUntilChanged().onStart { emit("") }

    val ui: StateFlow<TxUi> = combine(
        base.filterNotNull(), sources, combine(kind, bank, source, ::Triple), search,
    ) { (slice, all), src, (k, b, s), q ->
        val shown = all.filter { t ->
            k.matches(t) && (b == null || bankKey(t.bankName) == b) && (s == null || s.matches(src[t.id])) && matchesSearch(t, q)
        }
        TxUi(
            loaded = slice.loaded, from = slice.from, to = slice.to, periodLabel = slice.filter.label,
            base = all, shown = shown,
            banks = all.groupBy { bankKey(it.bankName) }.map { (key, l) ->
                BankOption(key, key.removeSuffix(" Bank").trim().ifEmpty { key }, l.first().bankName, l.size)
            }.sortedByDescending { it.count },
            sourceChips = TxSource.entries.filter { it != TxSource.MANUAL || all.any { t -> TxSource.MANUAL.matches(src[t.id]) } },
            inMinor = LedgerMath.income(shown), outMinor = LedgerMath.spent(shown) + LedgerMath.invested(shown),
            reviewCount = all.count { it.needsReview },
            days = shown.groupBy { Periods.localDate(it.timestamp) }.map { (d, l) ->
                DayGroup(d, l, l.sumOf(::netOf), l.filter { LedgerMath.isSpend(it) || LedgerMath.isInvest(it) }.sumOf(LedgerMath::rupees))
            },
            merchants = shown.groupBy { it.merchant ?: it.bankName }.map { (n, l) ->
                MerchantGroup(n, l, l.sumOf(LedgerMath::rupees), l.sumOf(::netOf))
            }.sortedByDescending { it.gross },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxUi())

    /** True when any type, bank or source chip narrows the list. */
    val filtered: StateFlow<Boolean> = combine(kind, bank, source) { k, b, s -> k != TxKind.ALL || b != null || s != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setQuery(q: String) { query.value = q }
    fun setKind(k: TxKind) { kind.value = k }
    fun setBank(key: String?) { bank.value = key }
    fun setSource(s: TxSource?) { source.value = s }
    fun clearFilters() { kind.value = TxKind.ALL; bank.value = null; source.value = null }
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
