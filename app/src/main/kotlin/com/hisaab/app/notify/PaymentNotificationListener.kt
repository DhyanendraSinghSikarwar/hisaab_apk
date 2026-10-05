package com.hisaab.app.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.hisaab.app.ApplicationScope
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.parser.FreeTextParser
import com.hisaab.shared.repo.IncomingMessage
import com.hisaab.shared.repo.IngestOutcome
import com.hisaab.shared.repo.TransactionRepository
import com.hisaab.shared.repo.TransactionsChangedNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * UPI payments often get no bank SMS (many banks skip small UPI amounts), but the payment app always
 * shows a notification. With the user's opt-in, this reads notifications from known payment apps only,
 * turns "Paid ₹50 to Raju" into a transaction, and lets dedup merge it with a later SMS or email.
 * Notifications from every other app are ignored without being read.
 */
class PaymentNotificationListener : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun repository(): TransactionRepository
        fun notifier(): TransactionsChangedNotifier
        fun settings(): AppSettingsStore
        fun newTransactions(): NewTransactionNotifier
        @ApplicationScope fun scope(): CoroutineScope
    }

    private val parser = FreeTextParser()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = PAYMENT_APPS[sbn.packageName] ?: return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        deps.scope().launch {
            if (!deps.settings().settings.first().appNotificationsEnabled) return@launch
            val tx = parser.fromNotification(app, title, body, sbn.postTime) ?: return@launch
            val raw = listOfNotNull(title, body).joinToString("\n")
            // The same notification is often re-posted (updated); its key plus text identifies it.
            val id = "app:" + sha("${sbn.packageName}|${sbn.key}|$raw")
            val outcome = deps.repository().ingest(IncomingMessage(tx, id, raw, sourceName = "APP"))
            if (outcome != IngestOutcome.ALREADY_PROCESSED) deps.notifier().onTransactionsChanged()
            deps.newTransactions().onIngested(outcome, "APP", id)
        }
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(16).joinToString("") { "%02x".format(it) }

    companion object {
        val PAYMENT_APPS = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "com.phonepe.app" to "PhonePe",
            "net.one97.paytm" to "Paytm",
            "in.org.npci.upiapp" to "BHIM",
            "com.dreamplug.androidapp" to "CRED",
            "in.amazon.mShop.android.shopping" to "Amazon Pay",
            "com.mobikwik_new" to "MobiKwik",
            "com.naviapp" to "Navi",
            "money.super.payments" to "super.money",
            "com.freecharge.android" to "Freecharge",
            "com.sbi.upi" to "BHIM SBI Pay",
            "com.sbi.lotusintouch" to "YONO SBI",
            "com.snapwork.hdfc" to "HDFC Bank",
            "com.csam.icici.bank.imobile" to "ICICI iMobile",
            "com.axis.mobile" to "Axis Mobile",
            "com.msf.kbank.mobile" to "Kotak Bank",
        )

        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        fun component(context: Context) = ComponentName(context, PaymentNotificationListener::class.java)
    }
}
