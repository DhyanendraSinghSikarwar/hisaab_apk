package com.hisaab.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hisaab.app.csv.CsvTransfer
import com.hisaab.app.settings.AppSettings
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.settings.ThemeMode
import com.hisaab.app.sms.SmsScanScheduler
import com.hisaab.email.auth.ConnectResult
import com.hisaab.email.auth.GmailAuthManager
import com.hisaab.email.sync.GmailScheduler
import com.hisaab.email.sync.GmailSettings
import com.hisaab.email.sync.GmailSettingsStore
import com.hisaab.shared.db.ProcessedEmailDao
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.repo.TransactionRepository
import com.hisaab.shared.repo.TransactionsChangedNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsState(
    val app: AppSettings? = null,
    val gmail: GmailSettings? = null,
    val processedEmails: Int = 0,
    val gmailSyncing: Boolean = false,
    val smsScanning: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettingsStore,
    private val gmail: GmailSettingsStore,
    private val auth: GmailAuthManager,
    processed: ProcessedEmailDao,
    private val transactions: TransactionDao,
    private val repository: TransactionRepository,
    private val notifier: TransactionsChangedNotifier,
) : ViewModel() {
    private val work = WorkManager.getInstance(context)
    private fun running(infos: List<WorkInfo>) = infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    val state = combine(
        appSettings.settings, gmail.settings, processed.observeCount(),
        work.getWorkInfosForUniqueWorkFlow(GmailScheduler.NOW), work.getWorkInfosForUniqueWorkFlow(SmsScanScheduler.WORK_NAME),
    ) { a, g, count, gw, sw -> SettingsState(a, g, count, running(gw), running(sw)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private fun say(text: String) { _messages.trySend(text) }

    // Gmail

    suspend fun connect(activity: Activity): ConnectResult = auth.connect(activity).also(::afterConnect)

    suspend fun completeConsent(activity: Activity, data: Intent?, email: String?): ConnectResult =
        auth.completeConsent(activity, data, email).also(::afterConnect)

    private fun afterConnect(result: ConnectResult) {
        when (result) {
            is ConnectResult.Connected -> {
                GmailScheduler.schedulePeriodic(context)
                GmailScheduler.syncNow(context)
                say("Gmail connected${result.email?.let { " as $it" }.orEmpty()}. First sync started.")
            }
            is ConnectResult.Failed -> say(result.message)
            is ConnectResult.NeedsConsent -> Unit
        }
    }

    fun disconnectGmail() = viewModelScope.launch {
        GmailScheduler.cancel(context)
        auth.signOut()
        say("Signed out of Gmail. Access revoked and tokens deleted.")
    }

    fun setGmailEnabled(enabled: Boolean) = viewModelScope.launch {
        gmail.setEnabled(enabled)
        if (enabled) GmailScheduler.schedulePeriodic(context) else GmailScheduler.cancel(context)
    }

    fun setLookback(days: Int) = viewModelScope.launch { gmail.setLookbackDays(days) }
    fun setSenders(list: List<String>) = viewModelScope.launch { gmail.setSenders(list) }
    fun resetSenders() = viewModelScope.launch { gmail.resetSenders() }
    fun setReadPdf(value: Boolean) = viewModelScope.launch { gmail.setReadPdf(value) }
    fun syncGmailNow() = GmailScheduler.syncNow(context)

    // SMS

    fun rescanSms() = SmsScanScheduler.scan(context, full = true)
    fun setSmsEnabled(value: Boolean) = viewModelScope.launch { appSettings.setSmsEnabled(value) }

    // App

    fun setAppLock(value: Boolean) = viewModelScope.launch { appSettings.setAppLock(value) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { appSettings.setTheme(mode) }

    fun export(uri: Uri) = viewModelScope.launch {
        val count = withContext(Dispatchers.IO) {
            val rows = transactions.getAll()
            context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { CsvTransfer.export(rows, it) }
            rows.size
        }
        say("Exported $count transactions to ${DocumentFile.fromSingleUri(context, uri)?.name ?: "file"}")
    }

    fun import(uri: Uri) = viewModelScope.launch {
        try {
            val result = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.bufferedReader().use { CsvTransfer.import(it) }
            }
            val report = repository.importTransactions(result.rows)
            if (report.inserted > 0) notifier.onTransactionsChanged()
            say("Imported ${report.inserted}, skipped ${report.skipped} already present" + if (result.badLines > 0) ", ${result.badLines} unreadable lines" else "")
        } catch (e: Exception) {
            say("Import failed: ${e.message}")
        }
    }
}
