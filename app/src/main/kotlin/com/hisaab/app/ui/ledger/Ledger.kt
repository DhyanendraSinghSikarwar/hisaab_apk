package com.hisaab.app.ui.ledger

import com.hisaab.app.ui.format.Periods
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountUsage
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/** Whose money a screen shows. Transactions with no account count as personal. */
enum class Book(val label: String) { ALL("All books"), PERSONAL("Personal"), BUSINESS("Business") }

enum class PeriodKind(val label: String) {
    THIS_WEEK("This week"), MONTH("Month"), LAST_MONTH("Last month"), FY("This FY"), LAST_FY("Last FY"), LAST_12("Last 12 months"), CUSTOM("Custom")
}

/** The global filter shown as two chips at the top of Home, Transactions and Analysis. */
data class ViewFilter(
    val book: Book = Book.ALL,
    val kind: PeriodKind = PeriodKind.MONTH,
    val month: YearMonth = YearMonth.now(Periods.zone),
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
) {
    /** The first and last day shown (inclusive). */
    val range: Pair<LocalDate, LocalDate>
        get() {
            val today = LocalDate.now(Periods.zone)
            return when (kind) {
                PeriodKind.THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to today
                PeriodKind.MONTH -> month.atDay(1) to month.atEndOfMonth()
                PeriodKind.LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
                PeriodKind.FY -> fyStart(today) to fyStart(today).plusYears(1).minusDays(1)
                PeriodKind.LAST_FY -> fyStart(today).minusYears(1) to fyStart(today).minusDays(1)
                PeriodKind.LAST_12 -> YearMonth.from(today).minusMonths(11).atDay(1) to today
                PeriodKind.CUSTOM -> (customFrom ?: today.withDayOfMonth(1)) to (customTo ?: today)
            }
        }

    val label: String
        get() = when (kind) {
            PeriodKind.MONTH -> month.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            PeriodKind.FY -> fyStart(LocalDate.now(Periods.zone)).let { "FY ${it.year}-${(it.year + 1) % 100}" }
            PeriodKind.LAST_FY -> fyStart(LocalDate.now(Periods.zone)).minusYears(1).let { "FY ${it.year}-${(it.year + 1) % 100}" }
            PeriodKind.CUSTOM -> range.let { (a, b) -> "${a.format(SHORT)} – ${b.format(SHORT)}" }
            else -> kind.label
        }

    /** A calendar month is shown; it can be stepped back and forward. */
    val isMonth: Boolean get() = kind == PeriodKind.MONTH

    /** The equal-length period just before, for comparisons. */
    val previous: Pair<LocalDate, LocalDate>
        get() {
            val (a, b) = range
            if (kind == PeriodKind.MONTH || kind == PeriodKind.LAST_MONTH) {
                val m = YearMonth.from(a).minusMonths(1)
                return m.atDay(1) to m.atEndOfMonth()
            }
            val days = ChronoUnit.DAYS.between(a, b) + 1
            return a.minusDays(days) to a.minusDays(1)
        }

    companion object {
        private val SHORT = DateTimeFormatter.ofPattern("d MMM")
        fun fyStart(d: LocalDate): LocalDate = LocalDate.of(if (d.monthValue >= 4) d.year else d.year - 1, 4, 1)
    }
}

/** Holds the global filter for the whole app session. */
@Singleton
class ViewFilterStore @Inject constructor() {
    private val _filter = MutableStateFlow(ViewFilter())
    val filter: StateFlow<ViewFilter> = _filter.asStateFlow()

    fun setBook(b: Book) = _filter.update { it.copy(book = b) }
    fun setKind(k: PeriodKind) = _filter.update { it.copy(kind = k, month = if (k == PeriodKind.MONTH) YearMonth.now(Periods.zone) else it.month) }
    fun setMonth(m: YearMonth) = _filter.update { it.copy(kind = PeriodKind.MONTH, month = m) }
    fun shiftMonth(by: Long) = _filter.update { it.copy(kind = PeriodKind.MONTH, month = it.month.plusMonths(by)) }
    fun setCustom(from: LocalDate, to: LocalDate) = _filter.update { it.copy(kind = PeriodKind.CUSTOM, customFrom = from, customTo = to) }
}

/** What every tab draws from: the period's transactions, the period before, the last 12 months, and accounts. */
data class LedgerSlice(
    val filter: ViewFilter = ViewFilter(),
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val txs: List<TransactionEntity> = emptyList(),
    val previous: List<TransactionEntity> = emptyList(),
    /** Twelve months ending with the period's last month, oldest first in [months]. */
    val year: List<TransactionEntity> = emptyList(),
    val months: List<YearMonth> = emptyList(),
    val accounts: List<AccountWithActivity> = emptyList(),
    val loaded: Boolean = false,
)

@Singleton
class LedgerSource @Inject constructor(
    private val transactions: TransactionDao,
    accounts: AccountDao,
    private val filters: ViewFilterStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    val slice: StateFlow<LedgerSlice> = filters.filter.flatMapLatest { f ->
        val (from, to) = f.range
        val (pFrom, _) = f.previous
        val lastMonth = YearMonth.from(to)
        val months = (11 downTo 0).map { lastMonth.minusMonths(it.toLong()) }
        val start = minOf(pFrom, months.first().atDay(1))
        combine(transactions.observeBetween(millis(start), endMillis(to)), accounts.observeWithActivity(millis(from.withDayOfMonth(1)))) { all, accs ->
            val usage = accs.associate { it.id to it.usage }
            val book = all.filter { inBook(it, f.book, usage) }
            fun between(a: LocalDate, b: LocalDate) = book.filter { Periods.localDate(it.timestamp).let { d -> !d.isBefore(a) && !d.isAfter(b) } }
            LedgerSlice(
                filter = f, from = from, to = to,
                txs = between(from, to), previous = between(pFrom, f.previous.second),
                year = between(months.first().atDay(1), lastMonth.atEndOfMonth()), months = months,
                accounts = accs.filter { !it.hidden && (f.book == Book.ALL || it.usage.name == f.book.name) }, loaded = true,
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(10_000), LedgerSlice())

    private fun inBook(t: TransactionEntity, b: Book, usage: Map<Long, AccountUsage>): Boolean = when (b) {
        Book.ALL -> true
        Book.PERSONAL -> t.accountId == null || usage[t.accountId] != AccountUsage.BUSINESS
        Book.BUSINESS -> t.accountId != null && usage[t.accountId] == AccountUsage.BUSINESS
    }

    private fun millis(d: LocalDate) = d.atStartOfDay(Periods.zone).toInstant().toEpochMilli()
    private fun endMillis(d: LocalDate) = millis(d.plusDays(1)) - 1
}

/** Essentials, lifestyle and the rest: how the prototype groups spending. */
enum class SpendGroup(val label: String) { ESSENTIALS("Essentials"), LIFESTYLE("Lifestyle"), OTHER("Other") }

data class MerchantSpend(val name: String, val total: Long, val count: Int, val category: Category)
data class MonthFlow(val month: YearMonth, val spent: Long, val income: Long, val invested: Long, val byGroup: Map<SpendGroup, Long>)

/** Sums and groupings over a list of transactions. Amounts are in paise; foreign spends use their rupee value. */
object LedgerMath {
    fun rupees(t: TransactionEntity): Long = t.inrMinor ?: if (t.currency == "INR") t.amountMinor else 0L

    fun isSpend(t: TransactionEntity) = t.type == TransactionType.DEBIT && t.category != Category.TRANSFER
    fun isIncome(t: TransactionEntity) = t.type == TransactionType.CREDIT && t.category != Category.TRANSFER
    fun isInvest(t: TransactionEntity) = t.type == TransactionType.INVESTMENT

    fun spent(txs: List<TransactionEntity>) = txs.filter(::isSpend).sumOf(::rupees)
    fun income(txs: List<TransactionEntity>) = txs.filter(::isIncome).sumOf(::rupees)
    fun invested(txs: List<TransactionEntity>) = txs.filter(::isInvest).sumOf(::rupees)

    fun group(c: Category): SpendGroup = when (c) {
        Category.RENT, Category.BILLS, Category.GROCERIES, Category.TRANSPORT, Category.FUEL, Category.HEALTH, Category.EDUCATION,
        Category.INSURANCE, Category.EMI_LOAN, Category.HOUSEHOLD, Category.TAXES, Category.FEES -> SpendGroup.ESSENTIALS
        Category.FOOD, Category.SHOPPING, Category.ENTERTAINMENT, Category.TRAVEL, Category.SUBSCRIPTIONS, Category.PERSONAL_CARE,
        Category.GIFTS, Category.DONATIONS -> SpendGroup.LIFESTYLE
        else -> SpendGroup.OTHER
    }

    fun byCategory(txs: List<TransactionEntity>): List<Pair<Category, Long>> =
        txs.filter(::isSpend).groupBy { it.category }.map { (c, l) -> c to l.sumOf(::rupees) }.filter { it.second > 0 }.sortedByDescending { it.second }

    fun byGroup(txs: List<TransactionEntity>): Map<SpendGroup, Long> =
        txs.filter(::isSpend).groupBy { group(it.category) }.mapValues { (_, l) -> l.sumOf(::rupees) }

    fun byMerchant(txs: List<TransactionEntity>): List<MerchantSpend> =
        txs.filter(::isSpend).groupBy { it.merchant ?: it.bankName }.map { (n, l) ->
            MerchantSpend(n, l.sumOf(::rupees), l.size, l.groupingBy { it.category }.eachCount().maxBy { it.value }.key)
        }.sortedByDescending { it.total }

    fun monthly(txs: List<TransactionEntity>, months: List<YearMonth>): List<MonthFlow> {
        val by = txs.groupBy { YearMonth.from(Periods.localDate(it.timestamp)) }
        return months.map { m -> by[m].orEmpty().let { l -> MonthFlow(m, spent(l), income(l), invested(l), byGroup(l)) } }
    }

    /** Spend by weekday (rows, Monday first) and three-hour slot (columns: 6a, 9a, 12p, 3p, 6p, 9p, 12a, 3a). */
    fun heat(txs: List<TransactionEntity>): Array<LongArray> {
        val grid = Array(7) { LongArray(8) }
        for (t in txs.filter(::isSpend)) {
            if (!t.hasExplicitTime) continue
            val z = java.time.Instant.ofEpochMilli(t.timestamp).atZone(Periods.zone)
            val slot = ((z.hour - 6 + 24) % 24) / 3
            grid[z.dayOfWeek.value - 1][slot] += rupees(t)
        }
        return grid
    }

    /** Spend so far, day by day, for [month] (cumulative, index 0 = day 1). */
    fun cumulative(txs: List<TransactionEntity>, month: YearMonth): List<Long> {
        val byDay = txs.filter(::isSpend).filter { YearMonth.from(Periods.localDate(it.timestamp)) == month }
            .groupBy { Periods.localDate(it.timestamp).dayOfMonth }.mapValues { (_, l) -> l.sumOf(::rupees) }
        var run = 0L
        return (1..month.lengthOfMonth()).map { d -> run += byDay[d] ?: 0L; run }
    }
}
