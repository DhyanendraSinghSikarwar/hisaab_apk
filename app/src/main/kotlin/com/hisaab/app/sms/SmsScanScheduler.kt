package com.hisaab.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object SmsScanScheduler {
    const val WORK_NAME = "sms-scan"

    fun scan(context: Context, full: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<OptimizedSmsReaderWorker>()
            .setInputData(workDataOf(OptimizedSmsReaderWorker.KEY_FULL to full))
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, if (full) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    /** The refresh button: everything since the last scan plus the last [days] days, started even if a scan is queued. */
    fun scanRecent(context: Context, days: Int = 14) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
        val request = OneTimeWorkRequestBuilder<OptimizedSmsReaderWorker>()
            .setInputData(workDataOf(OptimizedSmsReaderWorker.KEY_RECENT_DAYS to days))
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Catches up on SMS that arrived while the app was not running, or with [full], rescans the look-back window. */
    fun scanIfPermitted(context: Context, full: Boolean = false) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) scan(context, full)
    }
}
