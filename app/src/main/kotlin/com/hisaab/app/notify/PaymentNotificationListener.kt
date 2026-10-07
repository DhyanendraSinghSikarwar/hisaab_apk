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
        // WhatsApp also posts chats: only its payment notifications are read, never a conversation.
        if (sbn.packageName in CHAT_APPS && !isChatPayment(n, body)) return

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
            "com.samsung.android.spay" to "Samsung Wallet",
            "com.samsung.android.spaymini" to "Samsung Pay Mini",
            "com.whatsapp" to "WhatsApp Pay",
            "com.whatsapp.w4b" to "WhatsApp Pay",
        )

        /** Apps that mix payments with chats. */
        private val CHAT_APPS = setOf("com.whatsapp", "com.whatsapp.w4b")

        /** "You paid ₹500 to Asha", "₹200 received from Ravi", "Payment of ₹500 successful". */
        private val CHAT_PAYMENT = Regex(
            """^\s*(?:you\s+(?:have\s+)?(?:paid|sent|received)|payment\s+(?:of|to|from|successful|received|sent|completed)|(?:₹|rs\.?|inr)\s?[\d,]+(?:\.\d{1,2})?\s+(?:paid|sent|received))""",
            RegexOption.IGNORE_CASE,
        )

        /** A payment notification, not a message: no conversation style, not a message category, and payment wording up front. */
        private fun isChatPayment(n: Notification, body: String?): Boolean {
            if (n.category == Notification.CATEGORY_MESSAGE) return false
            if (n.extras?.containsKey(Notification.EXTRA_MESSAGES) == true) return false
            val channel = if (android.os.Build.VERSION.SDK_INT >= 26) n.channelId.orEmpty() else ""
            return body != null && CHAT_PAYMENT.containsMatchIn(body) && (channel.isEmpty() || !channel.contains("chat", ignoreCase = true))
        }

        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        fun component(context: Context) = ComponentName(context, PaymentNotificationListener::class.java)
    }
}
