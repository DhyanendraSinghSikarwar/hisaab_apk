package com.hisaab.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hisaab.app.MainActivity
import com.hisaab.app.R
import com.hisaab.app.i18n.t
import com.hisaab.email.statement.LockedStatementNotifier
import com.hisaab.shared.db.StatementEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "HDFC Bank statement detected: enter its password to read it." Tapping opens the unlock dialog for that
 * statement. The notification shows only the issuer and file name, never any figures.
 */
@Singleton
class StatementNotifications @Inject constructor(@ApplicationContext private val context: Context) : LockedStatementNotifier {

    override fun onLocked(statement: StatementEntity) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            context, statement.id.toInt(),
            Intent(Intent.ACTION_VIEW, Uri.parse("hisaab://statements?unlock=${statement.id}"), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_statement)
            .setContentTitle(statement.bankName?.let { t("{bank} statement detected", "bank" to it) } ?: t("A statement detected"))
            .setContentText(t("It's password protected. Tap to enter the password so DhanKosh can read it."))
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                t("{file} is password protected. Tap to enter its password. It's read and kept only on this phone.", "file" to statement.fileName),
            ))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            manager.notify(NOTIFICATION_BASE + statement.id.toInt(), n)
        } catch (_: SecurityException) {
            // Notification permission was revoked between the check and the post.
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, t("Statements"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = t("A statement arrived that needs its password")
                },
            )
        }
    }

    private companion object {
        const val CHANNEL = "statements"
        const val NOTIFICATION_BASE = 4000
    }
}
