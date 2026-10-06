package com.hisaab.app.ui.ledger

import com.hisaab.app.settings.LocalListsStore
import com.hisaab.app.settings.WorthPoint
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.HoldingKind
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.HoldingEntity
import com.hisaab.shared.db.StatementDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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

data class NetWorth(
    val assetsMinor: Long = 0,
    val liabilitiesMinor: Long = 0,
    val byClass: Map<AssetClass, Long> = emptyMap(),
    val holdings: List<HoldingEntity> = emptyList(),
    val accounts: List<AccountWithActivity> = emptyList(),
    /** One point a day, oldest first; today's is current. */
    val history: List<WorthPoint> = emptyList(),
    val investedMinor: Long = 0,
    val holdingsValueMinor: Long = 0,
    val loaded: Boolean = false,
) {
    val netMinor: Long get() = assetsMinor - liabilitiesMinor
}

/**
 * Net worth from what the app knows: bank balances, deposits, holdings, less loans and what is due on cards
 * (from each card's latest statement). Today's figure is saved, so the chart grows a point a day.
 */
@Singleton
class NetWorthSource @Inject constructor(
    accounts: AccountDao,
    holdings: HoldingDao,
    statements: StatementDao,
    private val lists: LocalListsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val current = combine(accounts.observeWithActivity(0L), holdings.observeAll(), statements.observeAll()) { accs, hs, sts ->
        val visible = accs.filter { !it.hidden }
        val cash = visible.filter { it.kind == AccountKind.ACCOUNT && it.accountType?.liquid != false }.sumOf { (it.currentBalanceMinor ?: 0L).coerceAtLeast(0) }
        val deposits = visible.filter { it.accountType == AccountType.FD || it.accountType == AccountType.RD }.sumOf { it.currentBalanceMinor ?: 0L }
        val ppf = visible.filter { it.accountType == AccountType.PPF }.sumOf { it.currentBalanceMinor ?: 0L }
        val loans = visible.filter { it.accountType == AccountType.LOAN }.sumOf { kotlin.math.abs(it.currentBalanceMinor ?: 0L) }
        val cardDue = sts.filter { it.totalDueMinor != null && it.last4 != null }
            .groupBy { it.bankName to it.last4 }.values.sumOf { l -> l.maxBy { it.statementEpochDay ?: 0 }.totalDueMinor ?: 0L }
        val byClass = hs.groupBy { AssetClass.of(it.kind) }.mapValues { (_, l) -> l.sumOf { it.valueMinor ?: 0L } }.toMutableMap()
        byClass[AssetClass.CASH] = (byClass[AssetClass.CASH] ?: 0L) + cash
        byClass[AssetClass.DEBT] = (byClass[AssetClass.DEBT] ?: 0L) + deposits
        byClass[AssetClass.RETIREMENT] = (byClass[AssetClass.RETIREMENT] ?: 0L) + ppf
        NetWorth(
            assetsMinor = byClass.values.sum(), liabilitiesMinor = loans + cardDue, byClass = byClass.filterValues { it > 0 },
            holdings = hs, accounts = visible, investedMinor = hs.sumOf { it.investedMinor ?: it.valueMinor ?: 0L },
            holdingsValueMinor = hs.sumOf { it.valueMinor ?: 0L }, loaded = true,
        )
    }

    init {
        // Save today's figure whenever it changes.
        current.map { it.assetsMinor to it.liabilitiesMinor }.distinctUntilChanged()
            .onEach { (a, l) -> if (a > 0 || l > 0) lists.recordWorth(a, l) }.launchIn(scope)
    }

    val netWorth: StateFlow<NetWorth> = combine(current, lists.worth) { n, h -> n.copy(history = h) }
        .stateIn(scope, SharingStarted.WhileSubscribed(10_000), NetWorth())
}
