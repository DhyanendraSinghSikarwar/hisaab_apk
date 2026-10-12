package com.hisaab.app.ui.ledger

import com.hisaab.app.settings.LiabilityKind
import com.hisaab.app.settings.LocalListsStore
import com.hisaab.app.settings.NetWorthFilter
import com.hisaab.app.settings.NetWorthPrefs
import com.hisaab.app.settings.WorthPoint
import com.hisaab.app.ui.format.Periods
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.AccountUsage
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.CardFlow
import com.hisaab.shared.repo.CardDues
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

/**
 * A credit card's headroom, with what its latest statement billed and what has been spent on it since. Shown beside
 * net worth, never counted in it.
 */
data class CardLimit(
    val id: Long,
    val name: String,
    val last4: String,
    val availableMinor: Long?,
    val limitMinor: Long?,
    /** Total due on the latest statement; null without one. */
    val billedMinor: Long? = null,
    /** The latest statement's date. */
    val statementDay: LocalDate? = null,
    /** Spends on the card after [statementDay]; null without a statement date. */
    val unbilledMinor: Long? = null,
    /** Payment due date of the latest statement. */
    val dueDay: LocalDate? = null,
    val network: com.hisaab.shared.db.CardNetwork? = null,
    /** Minimum due on the latest statement; null once paid or without one. */
    val minDueMinor: Long? = null,
    /** What the card owes when no statement is known: limit minus available, else spends since the last bill payment. */
    val outstandingMinor: Long? = null,
    /** [billedMinor] and [dueDay] were worked out from the usual statement day. */
    val estimated: Boolean = false,
    /** [availableMinor] was moved on by transactions since the last stated limit. */
    val availableEstimated: Boolean = false,
) {
    /** The billed amount, else the outstanding figure; 0 for none. */
    val owedMinor: Long get() = billedMinor?.takeIf { it > 0 } ?: outstandingMinor?.takeIf { it > 0 } ?: 0L
}

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

private data class WorthInputs(val accounts: List<AccountWithActivity>, val holdings: List<HoldingEntity>, val statements: List<StatementEntity>, val filter: NetWorthFilter = NetWorthFilter())

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
    prefs: NetWorthPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val inputs = combine(accounts.observeWithActivity(0L), holdings.observeAll(), statements.observeAll(), prefs.filter) { a, h, s, f -> WorthInputs(a, h, s, f) }
        .shareIn(scope, SharingStarted.WhileSubscribed(10_000), replay = 1)

    /** Accounts whose balance counts toward net worth (bank balances and deposits), for the per-account switches. */
    val eligibleAccounts: kotlinx.coroutines.flow.Flow<List<AccountWithActivity>> = inputs.map { i ->
        i.accounts.filter { !it.hidden && isCounted(it) && (it.currentBalanceMinor ?: 0L) > 0 }
    }.distinctUntilChanged()

    private fun isCounted(a: AccountWithActivity) =
        (a.kind == AccountKind.ACCOUNT && a.accountType?.liquid != false) || a.accountType == AccountType.FD || a.accountType == AccountType.RD

    private val since: Long = YearMonth.now(Periods.zone).minusMonths(WorthHistory.MONTHS.toLong() + 1).atDay(1)
        .atStartOfDay(Periods.zone).toInstant().toEpochMilli()

    private val book = filters.filter.map { it.book }.distinctUntilChanged()

    /** Transactions of the last three years; a merged account's count on its target. */
    private val movements = combine(transactions.observeSince(since), accounts.observeMerged()) { txs, merged ->
        val parent = merged.mapNotNull { m -> m.mergedIntoId?.let { m.id to it } }.toMap()
        if (parent.isEmpty()) txs else txs.map { t -> parent[t.accountId]?.let { t.copy(accountId = it) } ?: t }
    }

    init {
        // Save today's all-books figure whenever it changes.
        combine(inputs, movements) { i, txs -> compute(i, Book.ALL, txs) }.map { it.assetsMinor to it.liabilitiesMinor }.distinctUntilChanged()
            .onEach { (a, l) -> if (a > 0 || l > 0) lists.recordWorth(a, l) }.launchIn(scope)
    }

    private val current = combine(inputs, book, movements) { i, b, txs -> Triple(i, compute(i, b, txs), txs) }

    val netWorth: StateFlow<NetWorth> = combine(current, lists.worth) { (i, n, txs), recorded ->
        withHistory(i, n, recorded, txs)
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(10_000), NetWorth())

    private fun inBook(usage: AccountUsage?, b: Book) = when (b) {
        Book.ALL -> true
        Book.PERSONAL -> usage != AccountUsage.BUSINESS
        Book.BUSINESS -> usage == AccountUsage.BUSINESS
    }

    private fun isCreditCard(a: AccountWithActivity) =
        // An untyped card is a credit card only with a limit in its alerts; debit cards carry balances, never limits.
        a.kind == AccountKind.CARD && !a.isDebitCard && (a.accountType == AccountType.CREDIT_CARD ||
            (a.accountType == null && a.availableLimitMinor != null))

    private fun compute(i: WorthInputs, b: Book, txs: List<TransactionEntity>): NetWorth {
        val visible = i.accounts.filter { !it.hidden && inBook(it.usage, b) }
        val hs = if (b == Book.BUSINESS) emptyList() else i.holdings
        val f = i.filter
        val banks = visible.filter { f.countsAccount(it.id) && it.kind == AccountKind.ACCOUNT && it.accountType?.liquid != false && (it.currentBalanceMinor ?: 0L) > 0 }
        val cash = banks.sumOf { it.currentBalanceMinor ?: 0L }
        val deposits = visible.filter { f.countsAccount(it.id) && (it.accountType == AccountType.FD || it.accountType == AccountType.RD) }.sumOf { it.currentBalanceMinor ?: 0L }
        val ppf = visible.filter { f.countsAccount(it.id) && it.accountType == AccountType.PPF }.sumOf { it.currentBalanceMinor ?: 0L }
        val loanAccs = visible.filter { it.accountType == AccountType.LOAN && f.countsLiability(LiabilityKind.LOANS) }
        val loans = loanAccs.sumOf { kotlin.math.abs(it.currentBalanceMinor ?: 0L) }
        // Credit cards: one position each (CardDues.position), the very figures Home and the card page show. The card dues
        // total is the sum of what each card owes, so the three can never differ.
        val refs = i.accounts.filter(::isCreditCard).map { CardDues.CardRef(it.bankName, it.last4) }
        val byCard = txs.filter { it.duplicateOfId == null && it.accountId != null }.groupBy { it.accountId }
        val cards = visible.filter(::isCreditCard).map { cardLimit(it, i.statements, refs, byCard[it.id].orEmpty()) }.sortedByDescending { it.availableMinor ?: -1L }
        val cardDue = if (f.countsLiability(LiabilityKind.CARD_DUES)) cards.sumOf { it.owedMinor } else 0L

        val holdingByClass = hs.filter { f.countsClass(AssetClass.of(it.kind).name) }.groupBy { AssetClass.of(it.kind) }.mapValues { (_, l) -> l.sumOf { it.valueMinor ?: 0L } }
        val byClass = holdingByClass.toMutableMap()
        byClass[AssetClass.CASH] = (byClass[AssetClass.CASH] ?: 0L) + cash
        byClass[AssetClass.DEBT] = (byClass[AssetClass.DEBT] ?: 0L) + deposits
        byClass[AssetClass.RETIREMENT] = (byClass[AssetClass.RETIREMENT] ?: 0L) + ppf

        byClass.keys.removeAll { !f.countsClass(it.name) }
        val assetParts = buildList {
            banks.forEach { add(WorthPart("bank-${it.id}", it.label, it.currentBalanceMinor ?: 0L, false, PartKind.BANK, AssetClass.CASH)) }
            if (deposits > 0) add(WorthPart("deposits", "Deposits", deposits, false, PartKind.DEPOSIT, AssetClass.DEBT))
            val classes = holdingByClass.toMutableMap()
            if (ppf > 0) classes[AssetClass.RETIREMENT] = (classes[AssetClass.RETIREMENT] ?: 0L) + ppf
            classes.filterValues { it > 0 }.forEach { (c, v) -> add(WorthPart("class-${c.name}", c.label, v, false, PartKind.HOLDING, c)) }
        }.filter { it.amountMinor > 0 && f.countsClass(it.assetClass?.name ?: "") }.sortedByDescending { it.amountMinor }
        val liabilityParts = buildList {
            loanAccs.forEach { a ->
                val v = kotlin.math.abs(a.currentBalanceMinor ?: 0L)
                if (v > 0) add(WorthPart("loan-${a.id}", a.label, v, true, PartKind.LOAN))
            }
            if (cardDue > 0) add(WorthPart("card-dues", "Card dues", cardDue, true, PartKind.CARD_DUE))
        }.sortedByDescending { it.amountMinor }

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
        val f = i.filter
        val assetIds = n.accounts.filter { f.countsAccount(it.id) && f.countsClass(AssetClass.CASH.name) }.filter { (it.kind == AccountKind.ACCOUNT && it.accountType?.liquid != false) || it.isDebitCard }.map { it.id }.toSet()
        val liabIds = if (f.countsLiability(LiabilityKind.CARD_DUES)) n.accounts.filter(::isCreditCard).map { it.id }.toSet() else emptySet()
        val estimated = WorthHistory.estimate(anchor, txs, assetIds, liabIds, n.holdings.filter { f.countsClass(AssetClass.of(it.kind).name) })
        val tail = if (real.isEmpty()) listOf(todayPoint) else real.dropLastWhile { it.day == today } + todayPoint
        return n.copy(history = estimated + tail, estimatedBefore = if (estimated.isEmpty()) null else anchor.day)
    }

    /** One card's position from its statement rows and spends: billed, unbilled, outstanding, minimum, due date, limit left. */
    private fun cardLimit(a: AccountWithActivity, statements: List<StatementEntity>, refs: List<CardDues.CardRef>, txs: List<TransactionEntity>): CardLimit {
        val today = LocalDate.now(Periods.zone)
        val from = today.minusDays(CardDues.WINDOW_DAYS).atStartOfDay(Periods.zone).toInstant().toEpochMilli()
        val rows = CardDues.rowsFor(statements, a.bankName, a.last4, refs)
        val oldest = rows.mapNotNull { it.statementEpochDay }.maxOrNull()?.let { LocalDate.ofEpochDay(it).atStartOfDay(Periods.zone).toInstant().toEpochMilli() }
        val since = minOf(from, oldest ?: from)
        val flow = txs.filter { it.timestamp >= since }.sortedBy { it.timestamp }.map { CardFlow(it.timestamp, it.type, LedgerMath.rupees(it)) }
        val p = CardDues.position(rows, a.currentBalanceMinor, flow, today, Periods.zone)
        return CardLimit(
            a.id, a.nickname?.takeIf { it.isNotBlank() } ?: a.bankName, a.last4, p.limitLeft(p.limitMinor, a.currentBalanceMinor), p.limitMinor,
            billedMinor = p.billedMinor, statementDay = p.statementDay, dueDay = p.dueDay, unbilledMinor = p.unbilledMinor, network = a.cardNetwork,
            minDueMinor = p.minDueMinor, outstandingMinor = p.outstandingMinor, estimated = p.estimated, availableEstimated = a.balanceIsEstimated,
        )
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
