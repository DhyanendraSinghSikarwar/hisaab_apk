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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Off the main thread, so a cold start never waits on DataStore or WorkManager.
        appScope.launch {
            SmsScanScheduler.scanIfPermitted(this@HisaabApplication)
            com.hisaab.app.notify.AlertsWorker.schedule(this@HisaabApplication)
            runCatching { updater.checkIfDue() }
            if (gmailSettings.settings.first().enabled) GmailScheduler.schedulePeriodic(this@HisaabApplication)
        }
    }
}
