package com.hisaab.email.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hisaab.email.api.AuthRequiredException
import com.hisaab.email.imap.ImapSyncEngine
import com.hisaab.email.imap.MailAuthException
import com.hisaab.shared.repo.TransactionsChangedNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.concurrent.TimeUnit

@HiltWorker
class GmailSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: GmailSyncEngine,
    private val imap: ImapSyncEngine,
    private val settings: GmailSettingsStore,
    private val notifier: TransactionsChangedNotifier,
    private val activity: SyncActivity,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val start = System.currentTimeMillis()
        val connection = settings.read().connection
        return try {
            val progress: suspend (Int) -> Unit = { n -> setProgress(workDataOf(KEY_EMAILS_READ to n)) }
            val report = when (connection) {
                MailConnection.IMAP -> imap.sync(progress)
                MailConnection.GOOGLE -> engine.sync(progress)
                MailConnection.NONE -> SyncReport(SyncMode.DISABLED)
            }
            if (report.mode != SyncMode.DISABLED) activity.record(connection, report, start, null)
            if (report.parsed > 0) notifier.onTransactionsChanged()
            Result.success(workDataOf(KEY_SUMMARY to report.summary(), KEY_EMAILS_READ to report.fetched))
        } catch (_: AuthRequiredException) {
            activity.record(connection, null, start, "auth")
            settings.setNeedsReauth(true)
            settings.lastResult("Gmail needs you to sign in again")
            Result.failure(workDataOf(KEY_SUMMARY to "auth"))
        } catch (e: MailAuthException) {
            activity.record(connection, null, start, "auth")
            settings.setNeedsReauth(true)
            settings.lastResult("Email sign-in failed: ${e.message}. Sign in again with a new app password.")
            Result.failure(workDataOf(KEY_SUMMARY to "auth"))
        } catch (e: IOException) {
            activity.record(connection, null, start, e.javaClass.simpleName)
            settings.lastResult("Sync failed: ${e.message}; will retry")
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_SUMMARY = "summary"
        /** Emails fetched so far (progress) or in all (output). */
        const val KEY_EMAILS_READ = "emails_read"
        private const val MAX_ATTEMPTS = 5
    }
}

object GmailScheduler {
    const val PERIODIC = "gmail-sync-periodic"
    const val NOW = "gmail-sync-now"

    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Once a night (around 2:30 am) on a network, when the battery isn't low. The refresh button on Home syncs
     * on demand, so nothing needs to wake the phone during the day.
     */
    fun schedulePeriodic(context: Context) {
        val now = java.time.ZonedDateTime.now()
        var night = now.with(java.time.LocalTime.of(2, 30))
        if (!night.isAfter(now)) night = night.plusDays(1)
        val request = PeriodicWorkRequestBuilder<GmailSyncWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(java.time.Duration.between(now, night).toMinutes(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // UPDATE replaces the hourly job older versions scheduled.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<GmailSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
    }
}
