package com.hisaab.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hisaab.app.sms.SmsScanScheduler
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Once a night, while the phone is idle and the battery isn't low: catches up on any SMS the live receiver
 * missed. Email has its own nightly job (GmailScheduler). New SMS still arrive in real time.
 */
class NightlySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SmsScanScheduler.scanIfPermitted(applicationContext)
        return Result.success()
    }
}

object NightlySync {
    private const val NAME = "nightly-sync"

    /** Delay from now until the next 2:30 am. */
    fun untilNight(now: ZonedDateTime = ZonedDateTime.now()): Duration {
        var next = now.with(LocalTime.of(2, 30))
        if (!next.isAfter(now)) next = ZonedDateTime.of(LocalDate.from(now).plusDays(1), LocalTime.of(2, 30), now.zone)
        return Duration.between(now, next)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NightlySyncWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(untilNight().toMinutes(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresDeviceIdle(false).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
