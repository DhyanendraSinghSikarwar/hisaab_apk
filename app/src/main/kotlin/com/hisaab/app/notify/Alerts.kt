package com.hisaab.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hisaab.app.MainActivity
import com.hisaab.app.R
import com.hisaab.app.i18n.t
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.widget.WidgetUpdater
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.BudgetDao
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.insight.Planning
import com.hisaab.shared.repo.TransactionsChangedNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Budget, bill and renewal alerts. Every check is local; each alert is sent once (its key is remembered),
 * so a rescan or a second check the same day never repeats it.
 */
@Singleton
class AlertsChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgets: BudgetDao,
    private val transactions: TransactionDao,
    private val accounts: AccountDao,
    private val settings: AppSettingsStore,
    private val manual: com.hisaab.shared.db.RecurringDao,
) {
    private val lock = Mutex()

    /** Category budgets: alert at the user's threshold (90% by default) and again when the limit is reached. */
    suspend fun checkBudgets() = lock.withLock {
        val limits = budgets.observeAll().first().takeIf { it.isNotEmpty() } ?: return@withLock
        val s = settings.settings.first()
        val month = YearMonth.now(Periods.zone)
        val range = Periods.range(month)
        val spent = transactions.observeCategoryTotals(range.first, range.last).first().associate { it.category to it.total }
        for (b in limits) {
            val used = spent[b.category] ?: 0
            for (level in listOf(s.budgetAlertPercent, 100).distinct().sortedDescending()) {
                if (used * 100 < b.monthlyLimitMinor * level) continue
                val key = "budget:${b.category.name}:$month:$level"
                if (key in s.alertedKeys) break
                val left = b.monthlyLimitMinor - used
                notify(
                    key, "${t(b.category.label)}: ${if (level >= 100) t("budget used up") else t("{level}% of budget used", "level" to level)}",
                    if (left > 0) t("{used} of {limit} spent. {left} left for {month}.", "used" to Money.format(used, showPaise = false), "limit" to Money.format(b.monthlyLimitMinor, showPaise = false), "left" to Money.format(left, showPaise = false), "month" to Periods.month(month))
                    else t("{used} spent against {limit}, {over} over.", "used" to Money.format(used, showPaise = false), "limit" to Money.format(b.monthlyLimitMinor, showPaise = false), "over" to Money.format(-left, showPaise = false)),
                    "hisaab://budgets",
                )
                settings.addAlerted(key)
                break // the highest level reached is enough
            }
        }
    }

    /** Monthly payments due within 3 days from an account that can't cover them, and insurance renewals within a week. */
    suspend fun checkUpcoming() = lock.withLock {
        val today = LocalDate.now(Periods.zone)
        val txs = transactions.since(today.minusDays(400).atStartOfDay(Periods.zone).toInstant().toEpochMilli())
        val s = settings.settings.first()
        val accs = accounts.observeWithActivity(Periods.startOfMonth(System.currentTimeMillis())).first().associateBy { it.id }
        for (r in Planning.withManual(Planning.recurring(txs, today, Periods.zone), manual.all(), today).filter { !it.income }) {
            val days = ChronoUnit.DAYS.between(today, r.nextDue)
            if (days !in 0..3) continue
            val account = r.accountId?.let(accs::get)?.let { a -> a.linkedAccountId?.let(accs::get) ?: a } ?: continue
            if (account.kind != com.hisaab.parser.model.AccountKind.ACCOUNT) continue
            val balance = account.currentBalanceMinor ?: continue
            if (balance >= r.amountMinor) continue
            val key = "bill:${r.name}:${r.nextDue}"
            if (key in s.alertedKeys) continue
            notify(
                key, when (days) {
                    0L -> t("{name} {amount} due today", "name" to r.name, "amount" to Money.format(r.amountMinor, showPaise = false))
                    1L -> t("{name} {amount} due tomorrow", "name" to r.name, "amount" to Money.format(r.amountMinor, showPaise = false))
                    else -> t("{name} {amount} due in {days} days", "name" to r.name, "amount" to Money.format(r.amountMinor, showPaise = false), "days" to days)
                },
                t("{account} ••{last} has {balance}.", "account" to (account.nickname ?: account.bankName), "last" to account.last4, "balance" to Money.format(balance, showPaise = false)) + " " +
                    t("Add {amount} so it doesn't bounce.", "amount" to Money.format(r.amountMinor - balance, showPaise = false)),
                "hisaab://bills",
            )
            settings.addAlerted(key)
        }
        for (p in Planning.policies(txs, today, Periods.zone).filter { !it.monthly }) {
            val days = ChronoUnit.DAYS.between(today, p.nextDue)
            if (days !in 0..7) continue
            val key = "renewal:${p.insurer}:${p.nextDue}"
            if (key in s.alertedKeys) continue
            notify(key, if (days == 0L) t("{kind} insurance renews today", "kind" to t(p.kind.label)) else t("{kind} insurance renews in {days} days", "kind" to t(p.kind.label), "days" to days),
                t("{insurer}: last premium {amount} on {date}. Renew in time to stay covered.", "insurer" to p.insurer, "amount" to Money.format(p.premiumMinor, showPaise = false), "date" to p.lastPaid),
                "hisaab://bills")
            settings.addAlerted(key)
        }
    }

    private fun notify(key: String, title: String, text: String, link: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, t("Budgets & bills"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = t("Budget limits, payments due from a low balance, and insurance renewals")
                })
            }
        }
        val open = PendingIntent.getActivity(
            context, key.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(link), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_statement)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true)
            .build()
        try { manager.notify(key.hashCode(), n) } catch (_: SecurityException) { }
    }

    private companion object {
        const val CHANNEL = "budgets_bills"
    }
}

/** After every change to transactions: refresh the widget and check budgets. */
@Singleton
class TransactionsChangedHub @Inject constructor(
    private val widget: WidgetUpdater,
    private val alerts: AlertsChecker,
) : TransactionsChangedNotifier {
    override suspend fun onTransactionsChanged() {
        widget.onTransactionsChanged()
        runCatching { alerts.checkBudgets() }
    }
}

/** Once a day: payments due from a low balance, insurance renewals, and budgets. */
@HiltWorker
class AlertsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val alerts: AlertsChecker,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { alerts.checkUpcoming() }
        runCatching { alerts.checkBudgets() }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily-alerts", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<AlertsWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
