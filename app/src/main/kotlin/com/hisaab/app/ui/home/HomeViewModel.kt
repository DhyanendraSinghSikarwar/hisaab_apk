package com.hisaab.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.sms.OptimizedSmsReaderWorker
import com.hisaab.app.sms.SmsScanScheduler
import com.hisaab.app.ui.format.Periods
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.BudgetDao
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.StatementEntity
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class CategorySpend(val category: Category, val spent: Long, val budget: Long?)

/** This month's budgets at a glance: total limit, what was spent in budgeted categories, and how many are near or over. */
data class BudgetSummary(val limit: Long, val spent: Long, val count: Int, val nearOrOver: Int)

data class ScanProgress(val running: Boolean, val scanned: Int, val found: Int)

data class HomeState(
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val spent: Long = 0,
    val income: Long = 0,
    val balance: Long? = null,
    val balanceAccounts: Int = 0,
    val accounts: List<AccountWithActivity> = emptyList(),
    val categories: List<CategorySpend> = emptyList(),
    val recent: List<TransactionEntity> = emptyList(),
    val reviewCount: Int = 0,
    val scan: ScanProgress = ScanProgress(false, 0, 0),
    val lastScanResult: String? = null,
    val smsPromptDismissed: Boolean = false,
    val investments: Long = 0,
    val holdingCount: Int = 0,
    /** Statements that arrived password protected: shown as a banner until unlocked. */
    val lockedStatements: List<StatementEntity> = emptyList(),
    /** Spent in the month before [month], for the "vs last month" chip. */
    val prevSpent: Long = 0,
    val displayName: String? = null,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionDao,
    accounts: AccountDao,
    private val budgets: BudgetDao,
    private val settings: AppSettingsStore,
    private val categoryDao: com.hisaab.shared.db.CategoryDao,
    holdings: HoldingDao,
    statements: StatementDao,
    plans: com.hisaab.app.ui.plan.PlanSource,
    updater: com.hisaab.app.update.Updater,
    private val mailSettings: com.hisaab.email.sync.GmailSettingsStore,
    private val alerts: com.hisaab.app.notify.AlertsChecker,
) : ViewModel() {
    private val spendTypes = listOf("DEBIT", "INVESTMENT")

    /** The month Home shows. Spent, income, categories, account spend and the list all cover just this month. */
    private val month = MutableStateFlow(YearMonth.now(Periods.zone))

    private val totals = month.flatMapLatest { m ->
        val r = Periods.range(m)
        combine(
            transactions.observeTotal(spendTypes, r.first, r.last),
            transactions.observeTotal(listOf("CREDIT"), r.first, r.last),
            accounts.observeWithActivity(r.first, r.last + 1),
            Periods.range(m.minusMonths(1)).let { p -> transactions.observeTotal(spendTypes, p.first, p.last) },
        ) { spent, income, accs, prev -> Totals(spent, income, accs, prev) }
    }

    private val categories = month.flatMapLatest { m ->
        val r = Periods.range(m)
        combine(transactions.observeCategoryTotals(r.first, r.last), budgets.observeAll()) { totals, b ->
            val limits = b.associate { it.category to it.monthlyLimitMinor }
            totals.map { CategorySpend(it.category, it.total, limits[it.category]) }
        }
    }

    private val recent = month.flatMapLatest { m ->
        val r = Periods.range(m)
        transactions.observe(null, null, null, null, null, r.first, r.last, RECENT)
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
        combine(month, totals) { m, t -> m to t }, categories, recent, transactions.observeReviewCount(),
        combine(scan, settings.settings, holdings.observeAll(), statements.observeLocked()) { s, a, h, locked ->
            Triple(s, a.lastSmsResult, a.smsPromptDismissed) to Triple(h, locked, a.displayName)
        },
    ) { (m, t), cats, recent, review, (scanInfo, extra) ->
        val (scanState, last, dismissed) = scanInfo
        val (held, locked, name) = extra
        val (spent, income, accs, prev) = t
        val bank = accs.filter { it.isLiquid && it.currentBalanceMinor != null }
        HomeState(
            month = m, isCurrentMonth = m >= YearMonth.now(Periods.zone),
            spent = spent, income = income,
            balance = bank.takeIf { it.isNotEmpty() }?.sumOf { it.currentBalanceMinor!! }, balanceAccounts = bank.size,
            accounts = accs.filter { !it.hidden }, categories = cats, recent = recent, reviewCount = review, scan = scanState, lastScanResult = last,
            smsPromptDismissed = dismissed, investments = held.sumOf { it.valueMinor ?: 0 }, holdingCount = held.size, lockedStatements = locked,
            prevSpent = prev, displayName = name, loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    val budgetSummary: StateFlow<BudgetSummary?> = combine(
        budgets.observeAll(),
        month.flatMapLatest { m -> val r = Periods.range(m); transactions.observeCategoryTotals(r.first, r.last) },
        settings.settings,
    ) { b, totals, s ->
        if (b.isEmpty()) return@combine null
        val spent = totals.associate { it.category to it.total }
        BudgetSummary(
            limit = b.sumOf { it.monthlyLimitMinor }, spent = b.sumOf { spent[it.category] ?: 0 }, count = b.size,
            nearOrOver = b.count { (spent[it.category] ?: 0) * 100 >= it.monthlyLimitMinor * s.budgetAlertPercent },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val update = updater.state

    /** Daily spending over the last ~18 weeks, for the Activity grid. */
    /** Spent this month in each category of the user's own (filed under Other in the built-in totals). */
    val customSpend: StateFlow<List<Pair<com.hisaab.shared.db.CustomCategoryEntity, Long>>> = month.flatMapLatest { m ->
        val r = Periods.range(m)
        combine(categoryDao.observeCustomTotals(r.first, r.last), categoryDao.observeCustom()) { totals, cats ->
            totals.mapNotNull { t -> cats.firstOrNull { it.id == t.customCategoryId }?.let { it to t.total } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The profile, and whether to ask for one: no name yet and the first-run prompt not skipped. */
    val profile: StateFlow<Pair<com.hisaab.app.settings.Profile, Boolean>?> =
        settings.settings.map { it.profile to (it.profile.name == null && !it.profilePromptDismissed) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activity: StateFlow<Map<java.time.LocalDate, Long>> = run {
        val from = java.time.LocalDate.now(Periods.zone).minusWeeks(19).atStartOfDay(Periods.zone).toInstant().toEpochMilli()
        val offset = Periods.offsetMillis()
        transactions.observeDailySpend(from, Long.MAX_VALUE, offset).map { days ->
            days.associate { java.time.LocalDate.ofEpochDay(it.day) to it.total }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    }

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
    }

    /** Upcoming payments, savings and insights, shared with Bills and Analytics. */
    val plan = plans.snapshot

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { m -> m.plusMonths(1).takeIf { it <= YearMonth.now(Periods.zone) } ?: m }

    fun scanInbox(full: Boolean = false) = SmsScanScheduler.scan(context, full)
    fun dismissSmsPrompt() = viewModelScope.launch { settings.setSmsPromptDismissed(true) }
    fun toggleHideAmounts() = viewModelScope.launch { settings.setHideAmounts(!com.hisaab.app.ui.format.AmountPrivacy.hidden) }

    /** True once: Home then asks for the notification permission (Android 13+). */
    suspend fun shouldAskNotifications(): Boolean {
        val asked = settings.settings.first().notificationsAsked
        if (!asked) settings.setNotificationsAsked()
        return !asked
    }

    private data class Totals(val spent: Long, val income: Long, val accounts: List<AccountWithActivity>, val prevSpent: Long)

    private companion object {
        const val RECENT = 5
    }
}
