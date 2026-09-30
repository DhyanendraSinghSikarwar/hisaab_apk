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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val report = when (settings.read().connection) {
            MailConnection.IMAP -> imap.sync()
            MailConnection.GOOGLE -> engine.sync()
            MailConnection.NONE -> SyncReport(SyncMode.DISABLED)
        }
        if (report.parsed > 0) notifier.onTransactionsChanged()
        Result.success(workDataOf(KEY_SUMMARY to report.summary()))
    } catch (_: AuthRequiredException) {
        settings.setNeedsReauth(true)
        settings.lastResult("Gmail needs you to sign in again")
        Result.failure(workDataOf(KEY_SUMMARY to "auth"))
    } catch (e: MailAuthException) {
        settings.setNeedsReauth(true)
        settings.lastResult("Email sign-in failed: ${e.message}. Sign in again with a new app password.")
        Result.failure(workDataOf(KEY_SUMMARY to "auth"))
    } catch (e: IOException) {
        settings.lastResult("Sync failed: ${e.message}; will retry")
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_SUMMARY = "summary"
        private const val MAX_ATTEMPTS = 5
    }
}

object GmailScheduler {
    const val PERIODIC = "gmail-sync-periodic"
    const val NOW = "gmail-sync-now"

    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<GmailSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
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
