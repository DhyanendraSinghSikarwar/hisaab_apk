package com.hisaab.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hisaab.app.MainActivity
import com.hisaab.app.R
import com.hisaab.app.i18n.t
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.ui.format.Money
import com.hisaab.shared.repo.BalanceGap
import com.hisaab.shared.repo.BalanceReconciler
import com.hisaab.shared.repo.BalanceUpdateNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * "HDFC ••2779 balance updated · ₹1,240 not explained". One notification, replaced as more balances update during the
 * day; each account at most once a day. Follows the new-transaction notification setting and channel.
 */
@Singleton
class BalanceNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettingsStore,
) : BalanceUpdateNotifier {
    private val prefs by lazy { context.getSharedPreferences("balance-updates", Context.MODE_PRIVATE) }

    /** Today's lines, so a later update joins the same notification. */
    private val today = LinkedHashMap<String, String>()
    private var todayDay = -1L

    override suspend fun onBalancesUpdated(gaps: List<BalanceGap>) {
        if (gaps.isEmpty() || !settings.settings.first().transactionNotifications) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val day = LocalDate.now(IST).toEpochDay()
        val fresh = synchronized(this) {
            if (day != todayDay) { today.clear(); todayDay = day }
            val due = gaps.filter { BalanceReconciler.due(prefs.getLong(it.key, -1L).takeIf { d -> d >= 0 }, day) }
            if (due.isEmpty()) return
            prefs.edit().apply { due.forEach { putLong(it.key, day) } }.apply()
            due.forEach { today[it.key] = line(it) }
            today.values.toList()
        }
        ensureChannel()
        val single = fresh.size == 1
        val first = gaps.first { it.key in today }
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, NewTransactionNotifier.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_statement)
            .setContentTitle(if (single) t("{what} updated", "what" to first.label) else t("{n} balances updated", "n" to fresh.size))
            .setContentText(if (single) "${amount(first)} · ${t("transactions may be missing")}" else fresh.joinToString(" · "))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (!single) b.setStyle(NotificationCompat.InboxStyle().also { s -> fresh.forEach(s::addLine) }.setSummaryText(t("transactions may be missing")))
        try { manager.notify(NOTIFICATION_ID, b.build()) } catch (_: SecurityException) { }
    }

    private fun amount(g: BalanceGap): String = t("{amount} not explained", "amount" to Money.format(abs(g.unexplainedMinor), showPaise = false))

    private fun line(g: BalanceGap): String = "${g.label} · ${amount(g)}"

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(NewTransactionNotifier.CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(NewTransactionNotifier.CHANNEL, t("New transactions"), NotificationManager.IMPORTANCE_LOW).apply {
                description = t("Each new transaction, with buttons to change its category")
            })
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 9_000
        val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    }
}
