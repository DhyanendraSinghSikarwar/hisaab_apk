package com.hisaab.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.email.sync.GmailSettingsStore
import com.hisaab.parser.model.Source
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.repo.IncomingMessage
import com.hisaab.shared.repo.IngestReport
import com.hisaab.shared.repo.TransactionRepository
import com.hisaab.shared.repo.TransactionsChangedNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

/**
 * Scans the SMS inbox. Senders are filtered against bank and card-issuer headers before any body is read;
 * each batch of 500 is parsed in parallel on Dispatchers.Default and written in one Room transaction.
 * Incremental by default: it resumes after the newest SMS the previous run saw. A full scan reads the
 * same look-back window as email sync (Settings, "Fetch history").
 */
@HiltWorker
class OptimizedSmsReaderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val inbox: SmsInboxSource,
    private val registry: ParserRegistry,
    private val repository: TransactionRepository,
    private val settings: AppSettingsStore,
    private val mailSettings: GmailSettingsStore,
    private val notifier: TransactionsChangedNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(workDataOf(KEY_ERROR to "permission"))
        }
        val full = inputData.getBoolean(KEY_FULL, false)
        val since = if (full) {
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(mailSettings.read().lookbackDays.toLong())
        } else {
            settings.smsCursor()
        }
        val start = System.currentTimeMillis()
        var cursor = since
        var bankMessages = 0
        var parsed = 0
        var report = IngestReport.EMPTY

        val examined = inbox.readBatches(since, BATCH_SIZE, registry::accepts) { batch ->
            bankMessages += batch.size
            cursor = maxOf(cursor, batch.maxOf { it.receivedAt })
            val incoming = parseInParallel(batch)
            parsed += incoming.size
            report += repository.ingestBatch(incoming)
            setProgress(workDataOf(KEY_SCANNED to bankMessages, KEY_FOUND to parsed))
        }

        val seconds = (System.currentTimeMillis() - start) / 1000.0
        val summary = "$examined SMS checked, $bankMessages from banks, ${report.inserted} new, ${report.merged} merged, " +
            "${report.flagged} to review (${"%.1f".format(seconds)}s)"
        settings.smsScanned(cursor, summary)
        if (report.inserted + report.merged + report.flagged > 0) notifier.onTransactionsChanged()
        return Result.success(workDataOf(KEY_SCANNED to bankMessages, KEY_FOUND to parsed, KEY_SUMMARY to summary))
    }

    private suspend fun parseInParallel(batch: List<InboxSms>): List<IncomingMessage> = coroutineScope {
        val slice = (batch.size / PARALLELISM).coerceAtLeast(MIN_SLICE)
        batch.chunked(slice).map { part ->
            async(Dispatchers.Default) {
                part.mapNotNull { sms ->
                    registry.parse(sms.body, sms.sender, sms.receivedAt, Source.SMS)?.let { IncomingMessage(it, sms.messageId, sms.body) }
                }
            }
        }.awaitAll().flatten()
    }

    companion object {
        const val BATCH_SIZE = 500
        private val PARALLELISM = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        private const val MIN_SLICE = 50
        const val KEY_FULL = "full"
        const val KEY_SCANNED = "scanned"
        const val KEY_FOUND = "found"
        const val KEY_SUMMARY = "summary"
        const val KEY_ERROR = "error"
    }
}
