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

    /** Catches up on SMS that arrived while the app was not running. */
    fun scanIfPermitted(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) scan(context)
    }
}
