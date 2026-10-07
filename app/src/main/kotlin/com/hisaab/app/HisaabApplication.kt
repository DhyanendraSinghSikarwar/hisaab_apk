package com.hisaab.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hisaab.app.sms.SmsScanScheduler
import com.hisaab.email.sync.GmailScheduler
import com.hisaab.email.sync.GmailSettingsStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HisaabApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var gmailSettings: GmailSettingsStore
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var updater: com.hisaab.app.update.Updater
    @Inject lateinit var accounts: com.hisaab.shared.db.AccountDao
    @Inject lateinit var transactions: com.hisaab.shared.repo.TransactionRepository
    @Inject lateinit var appSettings: com.hisaab.app.settings.AppSettingsStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @Inject lateinit var languages: com.hisaab.app.i18n.LanguageStore

    override fun onCreate() {
        super.onCreate()
        // The chosen language pack loads first, so screens appear in it right away.
        appScope.launch { languages.restore() }
        // Off the main thread, so a cold start never waits on DataStore or WorkManager.
        appScope.launch {
            SmsScanScheduler.scanIfPermitted(this@HisaabApplication)
            com.hisaab.app.notify.AlertsWorker.schedule(this@HisaabApplication)
            com.hisaab.app.sync.NightlySync.schedule(this@HisaabApplication)
            // Deposits paid out on maturity leave the lists; renewed ones stay.
            runCatching { accounts.closeMatured(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toEpochDay()) }
            runCatching { updater.checkIfDue() }
            if (gmailSettings.settings.first().enabled) GmailScheduler.schedulePeriodic(this@HisaabApplication)
        }
        appScope.launch { reparseOnce() }
    }

    /**
     * One-time repair after the account-attribution parser fix: re-reads stored messages so a masked mobile
     * number or a payee's account stops showing as one of the user's accounts. Runs once per [REPARSE_KEY].
     */
    private suspend fun reparseOnce() {
        val prefs = getSharedPreferences("maintenance", MODE_PRIVATE)
        if (prefs.getBoolean(REPARSE_KEY, false)) return
        runCatching {
            val digits = appSettings.settings.first().profile.phone.orEmpty().filter { it.isDigit() }
            val report = transactions.reparseStored(digits.takeIf { it.length >= 10 }?.takeLast(4))
            android.util.Log.i("Artha", "Reparse: ${report.changed} transactions fixed, ${report.accountsDeleted} accounts removed")
        }.onSuccess { prefs.edit().putBoolean(REPARSE_KEY, true).apply() }
            .onFailure { android.util.Log.w("Artha", "Reparse failed: ${it.javaClass.simpleName}") }
    }

    private companion object {
        const val REPARSE_KEY = "reparse_v1"
    }
}
