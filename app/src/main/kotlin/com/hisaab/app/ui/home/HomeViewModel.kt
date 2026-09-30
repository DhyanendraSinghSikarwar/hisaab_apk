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
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class CategorySpend(val category: Category, val spent: Long, val budget: Long?)

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
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    transactions: TransactionDao,
    accounts: AccountDao,
    budgets: BudgetDao,
    private val settings: AppSettingsStore,
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
        ) { spent, income, accs -> Triple(spent, income, accs) }
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
        combine(scan, settings.settings) { s, a -> Triple(s, a.lastSmsResult, a.smsPromptDismissed) },
    ) { (m, t), cats, recent, review, (scanState, last, dismissed) ->
        val (spent, income, accs) = t
        val bank = accs.filter { it.kind == AccountKind.ACCOUNT && it.currentBalanceMinor != null }
        HomeState(
            month = m, isCurrentMonth = m >= YearMonth.now(Periods.zone),
            spent = spent, income = income,
            balance = bank.takeIf { it.isNotEmpty() }?.sumOf { it.currentBalanceMinor!! }, balanceAccounts = bank.size,
            accounts = accs, categories = cats, recent = recent, reviewCount = review, scan = scanState, lastScanResult = last,
            smsPromptDismissed = dismissed, loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { m -> m.plusMonths(1).takeIf { it <= YearMonth.now(Periods.zone) } ?: m }

    fun scanInbox(full: Boolean = false) = SmsScanScheduler.scan(context, full)
    fun dismissSmsPrompt() = viewModelScope.launch { settings.setSmsPromptDismissed(true) }

    private companion object {
        const val RECENT = 8
    }
}
