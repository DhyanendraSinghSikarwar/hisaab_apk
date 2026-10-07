package com.hisaab.email.sync

/**
 * Keeps an on-phone record of each email sync run: counts, timing and an error class only, never mail content.
 * The app's activity log implements it.
 */
fun interface SyncActivity {
    /** [report] is null when the run failed; [error] is then a class name such as "IOException" or "auth". */
    suspend fun record(connection: MailConnection, report: SyncReport?, startedAt: Long, error: String?)
}
