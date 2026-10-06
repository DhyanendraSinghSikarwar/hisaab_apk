package com.hisaab.app.ui.ledger

import com.hisaab.app.settings.LocalListsStore
import com.hisaab.app.settings.WorthPoint
import com.hisaab.app.ui.format.Periods
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.AccountUsage
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.HoldingEntity
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.StatementEntity
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** The prototype's asset classes, for the allocation donut and the net-worth layers. */
enum class AssetClass(val label: String) {
    EQUITY("Equity"), RETIREMENT("Retirement"), DEBT("Debt & FD"), GOLD("Gold"), CASH("Cash"), OTHER("Others");

    companion object {
        fun of(k: HoldingKind): AssetClass = when (k) {
            HoldingKind.MUTUAL_FUND, HoldingKind.STOCK, HoldingKind.ETF -> EQUITY
            HoldingKind.EPF, HoldingKind.NPS, HoldingKind.PPF -> RETIREMENT
            HoldingKind.FD, HoldingKind.BOND -> DEBT
            HoldingKind.GOLD -> GOLD
            HoldingKind.OTHER -> OTHER
        }
    }
}

/** What a slice of the net-worth bar stands for. */
enum class PartKind { BANK, DEPOSIT, HOLDING, LOAN, CARD_DUE, OTHERS }

/** One segment of the net-worth bar: a bank account, deposits, a holdings class, a loan or card dues. */
data class WorthPart(
    val key: String,
    val label: String,
    val amountMinor: Long,
    val liability: Boolean,
    val kind: PartKind,
    val assetClass: AssetClass? = null,
)

/** A credit card's headroom. Shown beside net worth, never counted in it. */
data class CardLimit(val id: Long, val name: String, val last4: String, val availableMinor: Long?, val limitMinor: Long?)

data class NetWorth(
    val assetsMinor: Long = 0,
    val liabilitiesMinor: Long = 0,
    val byClass: Map<AssetClass, Long> = emptyMap(),
    val holdings: List<HoldingEntity> = emptyList(),
    val accounts: List<AccountWithActivity> = emptyList(),
    /** Oldest first; today's is current. Points before [estimatedBefore] are reconstructed from transactions. */
    val history: List<WorthPoint> = emptyList(),
    val investedMinor: Long = 0,
    val holdingsValueMinor: Long = 0,
    /** Asset segments (largest first), then liability segments. */
    val parts: List<WorthPart> = emptyList(),
    val cards: List<CardLimit> = emptyList(),
    val book: Book = Book.ALL,
    /** History points dated before this day are estimates; null when none are. */
    val estimatedBefore: LocalDate? = null,
    val loaded: Boolean = false,
) {
    val netMinor: Long get() = assetsMinor - liabilitiesMinor

    /** Assets for a legend: the [top] largest, the rest folded into "Others". */
    fun assetLegend(top: Int = 6): List<WorthPart> = fold(parts.filter { !it.liability }, top, liability = false)
    fun liabilityLegend(top: Int = 3): List<WorthPart> = fold(parts.filter { it.liability }, top, liability = true)

    private fun fold(l: List<WorthPart>, top: Int, liability: Boolean): List<WorthPart> {
        val sorted = l.sortedByDescending { it.amountMinor }
        if (sorted.size <= top + 1) return sorted
        val rest = sorted.drop(top)
        return sorted.take(top) + WorthPart("others-$liability", "Others", rest.sumOf { it.amountMinor }, liability, PartKind.OTHERS)
    }
}

private data class WorthInputs(val accounts: List<AccountWithActivity>, val holdings: List<HoldingEntity>, val statements: List<StatementEntity>)

/**
 * Net worth from what the app knows: bank balances, deposits, holdings, less loans and what is due on cards
 * (from each card's latest statement), for the book picked in the global filter. Today's all-books figure is
 * saved, so the chart grows a point a day; months before that are estimated from transactions (see [WorthHistory]).
 */
@Singleton
class NetWorthSource @Inject constructor(
    accounts: AccountDao,
    holdings: HoldingDao,
    statements: StatementDao,
    transactions: TransactionDao,
    filters: ViewFilterStore,
    private val lists: LocalListsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val inputs = combine(accounts.observeWithActivity(0L), holdings.observeAll(), statements.observeAll()) { a, h, s -> WorthInputs(a, h, s) }
        .shareIn(scope, SharingStarted.WhileSubscribed(10_000), replay = 1)

    init {
        // Save today's all-books figure whenever it changes.
        inputs.map { compute(it, Book.ALL) }.map { it.assetsMinor to it.liabilitiesMinor }.distinctUntilChanged()
            .onEach { (a, l) -> if (a > 0 || l > 0) lists.recordWorth(a, l) }.launchIn(scope)
    }

    private val since: Long = YearMonth.now(Periods.zone).minusMonths(WorthHistory.MONTHS.toLong() + 1).atDay(1)
        .atStartOfDay(Periods.zone).toInstant().toEpochMilli()

    private val book = filters.filter.map { it.book }.distinctUntilChanged()

    private val current = combine(inputs, book) { i, b -> i to compute(i, b) }

    val netWorth: StateFlow<NetWorth> = combine(current, lists.worth, transactions.observeSince(since)) { (i, n), recorded, txs ->
        withHistory(i, n, recorded, txs)
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(10_000), NetWorth())

    private fun inBook(usage: AccountUsage?, b: Book) = when (b) {
        Book.ALL -> true
        Book.PERSONAL -> usage != AccountUsage.BUSINESS
        Book.BUSINESS -> usage == AccountUsage.BUSINESS
    }

    private fun isCreditCard(a: AccountWithActivity) =
        a.kind == AccountKind.CARD && (a.accountType == AccountType.CREDIT_CARD || a.accountType == null)

    private fun cardFor(st: StatementEntity, accs: List<AccountWithActivity>): AccountWithActivity? =
        accs.firstOrNull { it.kind == AccountKind.CARD && it.last4 == st.last4 && it.bankName.equals(st.bankName, ignoreCase = true) }
            ?: accs.firstOrNull { it.kind == AccountKind.CARD && it.last4 == st.last4 }

    private fun compute(i: WorthInputs, b: Book): NetWorth {
        val visible = i.accounts.filter { !it.hidden && inBook(it.usage, b) }
        val hs = if (b == Book.BUSINESS) emptyList() else i.holdings
        val banks = visible.filter { it.kind == AccountKind.ACCOUNT && it.accountType?.liquid != false && (it.currentBalanceMinor ?: 0L) > 0 }
        val cash = banks.sumOf { it.currentBalanceMinor ?: 0L }
        val deposits = visible.filter { it.accountType == AccountType.FD || it.accountType == AccountType.RD }.sumOf { it.currentBalanceMinor ?: 0L }
        val ppf = visible.filter { it.accountType == AccountType.PPF }.sumOf { it.currentBalanceMinor ?: 0L }
        val loanAccs = visible.filter { it.accountType == AccountType.LOAN }
        val loans = loanAccs.sumOf { kotlin.math.abs(it.currentBalanceMinor ?: 0L) }
        // Latest statement per card, kept when the card (or, for an unknown card, the personal book) is in view.
        val latest = i.statements.filter { it.last4 != null }.groupBy { it.bankName to it.last4 }.values
            .map { l -> l.maxBy { it.statementEpochDay ?: 0 } }
        val dues = latest.filter { it.totalDueMinor != null }.filter { st ->
            val card = cardFor(st, i.accounts)
            (card == null || !card.hidden) && inBook(card?.usage ?: AccountUsage.PERSONAL, b)
        }
        val cardDue = dues.sumOf { it.totalDueMinor ?: 0L }

        val holdingByClass = hs.groupBy { AssetClass.of(it.kind) }.mapValues { (_, l) -> l.sumOf { it.valueMinor ?: 0L } }
        val byClass = holdingByClass.toMutableMap()
        byClass[AssetClass.CASH] = (byClass[AssetClass.CASH] ?: 0L) + cash
        byClass[AssetClass.DEBT] = (byClass[AssetClass.DEBT] ?: 0L) + deposits
        byClass[AssetClass.RETIREMENT] = (byClass[AssetClass.RETIREMENT] ?: 0L) + ppf

        val assetParts = buildList {
            banks.forEach { add(WorthPart("bank-${it.id}", it.label, it.currentBalanceMinor ?: 0L, false, PartKind.BANK, AssetClass.CASH)) }
            if (deposits > 0) add(WorthPart("deposits", "Deposits", deposits, false, PartKind.DEPOSIT, AssetClass.DEBT))
            val classes = holdingByClass.toMutableMap()
            if (ppf > 0) classes[AssetClass.RETIREMENT] = (classes[AssetClass.RETIREMENT] ?: 0L) + ppf
            classes.filterValues { it > 0 }.forEach { (c, v) -> add(WorthPart("class-${c.name}", c.label, v, false, PartKind.HOLDING, c)) }
        }.filter { it.amountMinor > 0 }.sortedByDescending { it.amountMinor }
        val liabilityParts = buildList {
            loanAccs.forEach { a ->
                val v = kotlin.math.abs(a.currentBalanceMinor ?: 0L)
                if (v > 0) add(WorthPart("loan-${a.id}", a.label, v, true, PartKind.LOAN))
            }
            if (cardDue > 0) add(WorthPart("card-dues", "Card dues", cardDue, true, PartKind.CARD_DUE))
        }.sortedByDescending { it.amountMinor }

        val cards = visible.filter(::isCreditCard).map { a ->
            val st = latest.filter { it.creditLimitMinor != null && it.last4 == a.last4 && (it.bankName == null || it.bankName.equals(a.bankName, ignoreCase = true)) }
                .maxByOrNull { it.statementEpochDay ?: 0 }
            CardLimit(a.id, a.nickname?.takeIf { it.isNotBlank() } ?: a.bankName, a.last4, a.currentBalanceMinor, st?.creditLimitMinor)
        }.sortedByDescending { it.availableMinor ?: -1L }

        return NetWorth(
            assetsMinor = byClass.values.sum(), liabilitiesMinor = loans + cardDue, byClass = byClass.filterValues { it > 0 },
            holdings = hs, accounts = visible, investedMinor = hs.sumOf { it.investedMinor ?: it.valueMinor ?: 0L },
            holdingsValueMinor = hs.sumOf { it.valueMinor ?: 0L }, parts = assetParts + liabilityParts, cards = cards, book = b, loaded = true,
        )
    }

    /** Recorded days win; before the first of them (or for a single book, before today) the history is estimated. */
    private fun withHistory(i: WorthInputs, n: NetWorth, recorded: List<WorthPoint>, txs: List<TransactionEntity>): NetWorth {
        val today = LocalDate.now(Periods.zone)
        val todayPoint = WorthPoint(today, n.assetsMinor, n.liabilitiesMinor)
        // Recorded figures are for all books; a single book is estimated from its own accounts only.
        val real = if (n.book == Book.ALL) recorded.filter { !it.day.isAfter(today) } else emptyList()
        val anchor = real.firstOrNull() ?: todayPoint
        val assetIds = n.accounts.filter { (it.kind == AccountKind.ACCOUNT && it.accountType?.liquid != false) || it.isDebitCard }.map { it.id }.toSet()
        val liabIds = n.accounts.filter(::isCreditCard).map { it.id }.toSet()
        val estimated = WorthHistory.estimate(anchor, txs, assetIds, liabIds, n.holdings)
        val tail = if (real.isEmpty()) listOf(todayPoint) else real.dropLastWhile { it.day == today } + todayPoint
        return n.copy(history = estimated + tail, estimatedBefore = if (estimated.isEmpty()) null else anchor.day)
    }

    private val AccountWithActivity.label: String
        get() = nickname?.takeIf { it.isNotBlank() } ?: listOf(bankName, last4.takeIf { it.isNotBlank() }?.let { "••$it" }).filterNotNull().joinToString(" ")
}

/**
 * Month-end (and, for the last six months, weekly) net worth before the first recorded day, walked back from
 * that day's figure:
 *  - bank accounts and debit cards: assets then = assets at the anchor − (credits − debits in between);
 *  - credit cards: dues then = dues at the anchor + (payments − spends in between), never below zero;
 *  - investments move money from the bank into holdings, so they leave net worth unchanged;
 *  - holdings keep their value, except that growth between a holding's previous and latest figure (EPF, NPS…)
 *    is spread evenly over that interval; deposits and loans are held constant.
 * Points are only produced from the first transaction on, since nothing before it is known.
 */
internal object WorthHistory {
    const val MONTHS = 36

    fun estimate(
        anchor: WorthPoint, txs: List<TransactionEntity>, assetIds: Set<Long>, liabIds: Set<Long>, holdings: List<HoldingEntity>,
    ): List<WorthPoint> {
        val tracked = txs.filter { t ->
            t.duplicateOfId == null && t.accountId != null && (t.accountId in assetIds || t.accountId in liabIds) &&
                (t.type == TransactionType.CREDIT || t.type == TransactionType.DEBIT)
        }
        val gains = holdings.filter { it.previousValueMinor != null && it.previousAsOf != null && it.asOf != null && it.valueMinor != null && it.asOf!! > it.previousAsOf!! }
        if (tracked.isEmpty() && gains.isEmpty()) return emptyList()
        val firstDay = Periods.localDate(tracked.minOfOrNull { it.timestamp } ?: gains.minOf { it.previousAsOf!! })

        val anchorMonth = YearMonth.from(anchor.day)
        val days = sortedSetOf<LocalDate>()
        for (k in 1..MONTHS) days += anchorMonth.minusMonths(k.toLong()).atEndOfMonth()
        for (w in 1..26) days += anchor.day.minusWeeks(w.toLong())
        val wanted = days.filter { it.isBefore(anchor.day) && !it.isBefore(firstDay.minusDays(1)) }
        if (wanted.isEmpty()) return emptyList()

        fun endOf(d: LocalDate) = d.plusDays(1).atStartOfDay(Periods.zone).toInstant().toEpochMilli() - 1
        val anchorEnd = endOf(anchor.day)
        fun signed(t: TransactionEntity) = LedgerMath.rupees(t).let { if (t.type == TransactionType.CREDIT) it else -it }
        fun gainAt(h: HoldingEntity, at: Long): Double {
            val from = h.previousAsOf!!; val to = h.asOf!!
            val f = ((at - from).toDouble() / (to - from)).coerceIn(0.0, 1.0)
            return (h.valueMinor!! - h.previousValueMinor!!) * f
        }
        val sorted = tracked.filter { it.timestamp <= anchorEnd }.sortedBy { it.timestamp }
        return wanted.map { d ->
            val end = endOf(d)
            var assetFlow = 0L; var liabFlow = 0L
            for (t in sorted) {
                if (t.timestamp <= end) continue
                if (t.accountId in liabIds) liabFlow += signed(t) else assetFlow += signed(t)
            }
            val growth = gains.sumOf { gainAt(it, anchorEnd) - gainAt(it, end) }.toLong()
            WorthPoint(d, (anchor.assetsMinor - assetFlow - growth).coerceAtLeast(0), (anchor.liabilitiesMinor + liabFlow).coerceAtLeast(0))
        }
    }
}
