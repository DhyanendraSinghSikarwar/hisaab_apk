package com.hisaab.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.settings.TabLayout
import com.hisaab.app.settings.TabLayoutStore
import com.hisaab.app.settings.TabLayouts
import com.hisaab.app.sms.OptimizedSmsReaderWorker
import com.hisaab.app.sms.SmsScanScheduler
import com.hisaab.app.ui.category.CategoryLook
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.LedgerMath
import com.hisaab.app.ui.ledger.LedgerSlice
import com.hisaab.app.ui.ledger.LedgerSource
import com.hisaab.app.ui.ledger.NetWorth
import com.hisaab.app.ui.ledger.NetWorthSource
import com.hisaab.app.ui.ledger.PeriodKind
import com.hisaab.app.ui.ledger.ViewFilter
import com.hisaab.app.ui.plan.PlanSnapshot
import com.hisaab.app.ui.plan.PlanSource
import com.hisaab.app.ui.plan.Upcoming
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.BudgetDao
import com.hisaab.shared.db.BudgetEntity
import com.hisaab.shared.db.CustomCategoryEntity
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.StatementEntity
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.Insight
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ScanProgress(val running: Boolean, val scanned: Int, val found: Int)

/** What Home needs besides the widgets: prompts, notices and the user's name. */
data class HomeState(
    val reviewCount: Int = 0,
    val scan: ScanProgress = ScanProgress(false, 0, 0),
    val smsPromptDismissed: Boolean = false,
    /** Statements that arrived password protected: shown as a notice until unlocked. */
    val lockedStatements: List<StatementEntity> = emptyList(),
    val displayName: String? = null,
)

/** Income, spending and investing over the period; saved is what is left. */
data class CashFlow(val income: Long, val spent: Long, val invested: Long) {
    val saved: Long get() = income - spent - invested
    /** Saved as a share of income, in percent; null without income. */
    val rate: Int? get() = if (income > 0) (saved * 100 / income).toInt() else null
}

/**
 * Safe to spend: for the current month, what is left per day after spending and the bills still due; for any
 * other period, the average spent per day.
 */
data class SafeToSpend(
    val current: Boolean,
    val perDay: Long,
    val daysLeft: Int,
    /** Monthly budget, or income, the figure is worked from (0: neither known). */
    val base: Long,
    val spent: Long,
    val reserved: Long,
    val fromBudget: Boolean,
)

/** How an upcoming payment is coloured: card bills red, EMIs amber, SIPs and investments in the accent. */
enum class DueTone { CARD, EMI, INVEST, INCOME, OTHER }

data class DueItem(val upcoming: Upcoming, val tone: DueTone)

/** One category (built in or the user's own) and what was spent in it. */
data class CategoryAmount(val look: CategoryLook, val amount: Long)

/** A budget and what its category has used in the period. */
data class BudgetUse(val look: CategoryLook, val spent: Long, val limit: Long) {
    val fraction: Float get() = if (limit > 0) spent.toFloat() / limit else 0f
}

/** Everything the Home widgets draw, worked out off the main thread from the global Book + Period filter. */
data class HomeWidgets(
    val filter: ViewFilter = ViewFilter(),
    /** The month the period ends in, for drill-downs that take a month. */
    val month: YearMonth = YearMonth.now(Periods.zone),
    val cash: CashFlow = CashFlow(0, 0, 0),
    /** Set when the period's savings rate is the best of this many months. */
    val bestRateOf: Int? = null,
    val safe: SafeToSpend? = null,
    val categories: List<CategoryAmount> = emptyList(),
    val accounts: List<AccountWithActivity> = emptyList(),
    val recent: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetUse> = emptyList(),
    val hasBudgets: Boolean = false,
    val netWorth: NetWorth = NetWorth(),
    /** Net worth about 30 days ago, from the daily history; null without enough history. */
    val netWorthBefore: Long? = null,
    val upcoming: List<DueItem> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    transactions: TransactionDao,
    budgets: BudgetDao,
    private val settings: AppSettingsStore,
    categoryDao: com.hisaab.shared.db.CategoryDao,
    private val statementProcessor: com.hisaab.email.statement.StatementProcessor,
    private val layoutStore: TabLayoutStore,
    statements: StatementDao,
    plans: PlanSource,
    ledger: LedgerSource,
    worth: NetWorthSource,
    updater: com.hisaab.app.update.Updater,
    private val mailSettings: com.hisaab.email.sync.GmailSettingsStore,
    private val alerts: com.hisaab.app.notify.AlertsChecker,
) : ViewModel() {

    init {
        viewModelScope.launch { layoutStore.seedHomeOnce() }
    }

    private val scan = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SmsScanScheduler.WORK_NAME).map { infos ->
        val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        ScanProgress(
            running = running != null,
            scanned = running?.progress?.getInt(OptimizedSmsReaderWorker.KEY_SCANNED, 0) ?: 0,
            found = running?.progress?.getInt(OptimizedSmsReaderWorker.KEY_FOUND, 0) ?: 0,
        )
    }

    val state: StateFlow<HomeState> = combine(
        scan, settings.settings, statements.observeLocked(), transactions.observeReviewCount(),
    ) { s, a, locked, review ->
        HomeState(reviewCount = review, scan = s, smsPromptDismissed = a.smsPromptDismissed, lockedStatements = locked, displayName = a.displayName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Insights dismissed this session, by title. */
    private val dismissed = MutableStateFlow<Set<String>>(emptySet())

    val widgets: StateFlow<HomeWidgets> = combine(
        ledger.slice, worth.netWorth, plans.snapshot,
        combine(budgets.observeAll(), categoryDao.observeCustom(), dismissed) { b, c, d -> Extras(b, c, d) },
    ) { slice, nw, plan, extras -> build(slice, nw, plan, extras) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeWidgets())

    /** Home's layout: every widget in its saved order, and which are hidden. */
    val layout: StateFlow<TabLayout> = layoutStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, TabLayout())

    val update = updater.state

    /** The profile, and whether to ask for one: no name yet and the first-run prompt not skipped. */
    val profile: StateFlow<Pair<com.hisaab.app.settings.Profile, Boolean>?> =
        settings.settings.map { it.profile to (it.profile.name == null && !it.profilePromptDismissed) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** True while an SMS scan or email sync is running, for the refresh button. */
    val syncing: StateFlow<Boolean> = combine(
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SmsScanScheduler.WORK_NAME),
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(com.hisaab.email.sync.GmailScheduler.NOW),
    ) { a, b -> (a + b).any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The refresh button: new SMS, every connected inbox, and a fresh look at budgets and upcoming payments. */
    fun syncAll() = viewModelScope.launch {
        SmsScanScheduler.scanIfPermitted(context)
        val mail = mailSettings.read()
        if (mail.connected && mail.enabled) com.hisaab.email.sync.GmailScheduler.syncNow(context)
        runCatching { alerts.checkBudgets(); alerts.checkUpcoming() }
        // Statements that a newly saved password now opens.
        runCatching { statementProcessor.retryLocked() }
    }

    fun scanInbox(full: Boolean = false) = SmsScanScheduler.scan(context, full)
    fun dismissSmsPrompt() = viewModelScope.launch { settings.setSmsPromptDismissed(true) }
    fun toggleHideAmounts() = viewModelScope.launch { settings.setHideAmounts(!com.hisaab.app.ui.format.AmountPrivacy.hidden) }
    fun dismissInsight(i: Insight) = dismissed.update { it + i.title }

    /** Edit mode: move a widget one place up (-1) or down (+1). */
    fun move(key: String, by: Int) = viewModelScope.launch { layoutStore.move(TabLayouts.HOME, key, by) }
    fun setVisible(key: String, visible: Boolean) = viewModelScope.launch { layoutStore.setVisible(TabLayouts.HOME, key, visible) }
    fun applyPreset(shown: List<String>) = viewModelScope.launch { layoutStore.apply(TabLayouts.HOME, shown) }
    fun resetLayout() = viewModelScope.launch { layoutStore.reset(TabLayouts.HOME) }

    /** True once: Home then asks for the notification permission (Android 13+). */
    suspend fun shouldAskNotifications(): Boolean {
        val asked = settings.settings.first().notificationsAsked
        if (!asked) settings.setNotificationsAsked()
        return !asked
    }

    private data class Extras(val budgets: List<BudgetEntity>, val custom: List<CustomCategoryEntity>, val dismissed: Set<String>)

    private fun build(slice: LedgerSlice, nw: NetWorth, plan: PlanSnapshot, x: Extras): HomeWidgets {
        val f = slice.filter
        val today = LocalDate.now(Periods.zone)
        val month = if (f.isMonth) f.month else YearMonth.from(slice.to)
        val txs = slice.txs
        val cash = CashFlow(LedgerMath.income(txs), LedgerMath.spent(txs), LedgerMath.invested(txs))

        // Best savings rate of the twelve months ending with the period (calendar months only).
        val bestRateOf = cash.rate?.takeIf { f.isMonth }?.let { rate ->
            val others = LedgerMath.monthly(slice.year, slice.months).filter { it.month != month && it.income > 0 }
                .map { ((it.income - it.spent - it.invested) * 100 / it.income).toInt() }
            if (others.size >= 2 && others.all { rate > it }) others.size + 1 else null
        }

        // Spending by category; the user's own categories are taken out of the built-in ones they were filed under.
        val customById = x.custom.associateBy { it.id }
        val categories = txs.filter(LedgerMath::isSpend)
            .map { t -> (t.customCategoryId?.let { customById[it] }?.let { CategoryLook.of(it) } ?: CategoryLook.of(t.category)) to LedgerMath.rupees(t) }
            .groupBy { it.first.key }
            .map { (_, l) -> CategoryAmount(l.first().first, l.sumOf { it.second }) }
            .filter { it.amount > 0 }.sortedByDescending { it.amount }

        val spentByCat = txs.filter(LedgerMath::isSpend).groupBy { it.category }.mapValues { (_, l) -> l.sumOf(LedgerMath::rupees) }
        val budgetUses = x.budgets.filter { it.monthlyLimitMinor > 0 }
            .map { BudgetUse(CategoryLook.of(it.category), spentByCat[it.category] ?: 0L, it.monthlyLimitMinor) }
            .sortedByDescending { it.fraction }

        val currentMonth = f.kind == PeriodKind.MONTH && f.month == YearMonth.from(today)
        val safe = if (currentMonth) {
            val budgetTotal = x.budgets.sumOf { it.monthlyLimitMinor }
            val base = if (budgetTotal > 0) budgetTotal else cash.income
            val reserved = plan.upcoming.filter { it.kind != Upcoming.Kind.INCOME && YearMonth.from(it.due) == month }.sumOf { it.amountMinor }
            val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
            SafeToSpend(true, ((base - cash.spent - reserved).coerceAtLeast(0)) / daysLeft, daysLeft, base, cash.spent, reserved, budgetTotal > 0)
        } else {
            val end = minOf(slice.to, today)
            val days = (ChronoUnit.DAYS.between(slice.from, end) + 1).coerceAtLeast(1).toInt()
            SafeToSpend(false, cash.spent / days, days, 0, cash.spent, 0, false)
        }

        val recurringByName = plan.recurring.associateBy { it.name }
        val upcoming = plan.upcoming.filter { it.daysLeft in 0..7 }.map { u ->
            val cat = recurringByName[u.name]?.category
            val card = u.account?.let { it.kind == AccountKind.CARD && !it.isDebitCard } == true || u.name.contains("card", ignoreCase = true)
            DueItem(u, when {
                u.kind == Upcoming.Kind.INCOME -> DueTone.INCOME
                cat == Category.INVESTMENT -> DueTone.INVEST
                cat == Category.EMI_LOAN || u.kind == Upcoming.Kind.INSURANCE -> DueTone.EMI
                card -> DueTone.CARD
                else -> DueTone.OTHER
            })
        }

        val history = nw.history
        val before = history.lastOrNull { !it.day.isAfter(today.minusDays(30)) }
            ?: history.firstOrNull()?.takeIf { history.size >= 2 && it.day.isBefore(today) }

        return HomeWidgets(
            filter = f, month = month, cash = cash, bestRateOf = bestRateOf, safe = safe, categories = categories,
            accounts = slice.accounts, recent = txs.sortedByDescending { it.timestamp }.take(RECENT),
            budgets = budgetUses.take(3), hasBudgets = budgetUses.isNotEmpty(),
            netWorth = nw, netWorthBefore = before?.netMinor, upcoming = upcoming,
            insights = plan.insights.filter { it.title !in x.dismissed }, loaded = slice.loaded,
        )
    }

    private companion object {
        const val RECENT = 4
    }
}
