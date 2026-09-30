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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

data class CategorySpend(val category: Category, val spent: Long, val budget: Long?)

data class ScanProgress(val running: Boolean, val scanned: Int, val found: Int)

data class HomeState(
    val month: YearMonth = YearMonth.now(),
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
    val loaded: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    transactions: TransactionDao,
    accounts: AccountDao,
    budgets: BudgetDao,
    settings: AppSettingsStore,
) : ViewModel() {
    private val monthStart = Periods.startOfMonth(System.currentTimeMillis())
    private val spendTypes = listOf("DEBIT", "INVESTMENT")

    private val totals = combine(
        transactions.observeTotal(spendTypes, monthStart, Long.MAX_VALUE),
        transactions.observeTotal(listOf("CREDIT"), monthStart, Long.MAX_VALUE),
        accounts.observeWithActivity(monthStart),
    ) { spent, income, accs -> Triple(spent, income, accs) }

    private val categories = combine(transactions.observeCategoryTotals(monthStart, Long.MAX_VALUE), budgets.observeAll()) { totals, b ->
        val limits = b.associate { it.category to it.monthlyLimitMinor }
        totals.map { CategorySpend(it.category, it.total, limits[it.category]) }
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
        totals, categories, transactions.observeRecent(8), transactions.observeReviewCount(),
        combine(scan, settings.settings) { s, a -> s to a.lastSmsResult },
    ) { (spent, income, accs), cats, recent, review, (scanState, last) ->
        val bank = accs.filter { it.kind == AccountKind.ACCOUNT && it.latestBalanceMinor != null }
        HomeState(
            spent = spent, income = income,
            balance = bank.takeIf { it.isNotEmpty() }?.sumOf { it.latestBalanceMinor!! }, balanceAccounts = bank.size,
            accounts = accs, categories = cats, recent = recent, reviewCount = review, scan = scanState, lastScanResult = last,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun scanInbox(full: Boolean = false) = SmsScanScheduler.scan(context, full)
}
