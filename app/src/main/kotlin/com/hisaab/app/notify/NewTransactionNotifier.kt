package com.hisaab.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hisaab.app.ApplicationScope
import com.hisaab.app.MainActivity
import com.hisaab.app.R
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.ui.format.Money
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionSourceDao
import com.hisaab.shared.repo.IngestOutcome
import com.hisaab.shared.repo.TransactionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "₹250 at Swiggy · Food & Dining". Shown for a transaction that just arrived (a new SMS or payment-app
 * notification, never a rescan). Expanding it shows up to three other likely categories as buttons; one tap
 * saves that category and remembers it for the merchant, without opening the app.
 */
@Singleton
class NewTransactionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionDao,
    private val sources: TransactionSourceDao,
    private val settings: AppSettingsStore,
) {
    suspend fun onIngested(outcome: IngestOutcome, source: String, messageId: String) {
        if (outcome != IngestOutcome.INSERTED) return
        if (!settings.settings.first().transactionNotifications) return
        val id = sources.transactionIdFor(source, messageId) ?: return
        val tx = transactions.getById(id) ?: return
        if (tx.type == TransactionType.TRANSFER) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()

        val verb = if (tx.type == TransactionType.CREDIT) "received from" else "at"
        val who = tx.merchant ?: tx.bankName
        val open = PendingIntent.getActivity(
            context, id.toInt(),
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("hisaab://transaction/$id"), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_statement)
            .setContentTitle("${Money.format(tx.amountMinor, tx.currency)} $verb $who")
            .setContentText("${tx.category.label}${tx.accountLast4?.let { " · ••$it" }.orEmpty()} · expand to change")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        for (alt in alternatives(tx.category, tx.type)) {
            val action = PendingIntent.getBroadcast(
                context, (id * 31 + alt.ordinal).toInt(),
                Intent(context, CategoryActionReceiver::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_CATEGORY, alt.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, alt.label, action)
        }
        try { manager.notify(NOTIFICATION_BASE + id.toInt(), b.build()) } catch (_: SecurityException) { }
    }

    /**
     * Other likely categories: the ones this user picks most often for the same kind of transaction in the
     * last three months, then sensible defaults. Never the one already chosen.
     */
    private suspend fun alternatives(current: Category, type: TransactionType): List<Category> {
        val since = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        val used = transactions.categoryUsage(type.name, since).map { it.category }
        val defaults = if (type == TransactionType.CREDIT) listOf(Category.SALARY, Category.REFUND, Category.INCOME, Category.TRANSFER)
        else listOf(Category.FOOD, Category.GROCERIES, Category.SHOPPING, Category.TRANSPORT, Category.BILLS)
        return (used + defaults).distinct().filter { it != current && it != Category.OTHER }.take(3)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "New transactions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Each new transaction, with buttons to change its category"
            })
        }
    }

    companion object {
        const val CHANNEL = "new_transactions"
        const val NOTIFICATION_BASE = 10_000
        const val EXTRA_ID = "tx"
        const val EXTRA_CATEGORY = "category"
    }
}

/** A category button on the new-transaction notification: saves it, remembers it for the merchant, and confirms. */
class CategoryActionReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun repository(): TransactionRepository
        @ApplicationScope fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(NewTransactionNotifier.EXTRA_ID, -1).takeIf { it > 0 } ?: return
        val category = intent.getStringExtra(NewTransactionNotifier.EXTRA_CATEGORY)?.let { runCatching { Category.valueOf(it) }.getOrNull() } ?: return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val pending = goAsync()
        deps.scope().launch {
            try {
                deps.repository().setCategory(listOf(id), category)
                val n = NotificationCompat.Builder(context, NewTransactionNotifier.CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_statement)
                    .setContentTitle("Saved as ${category.label}")
                    .setContentText("Hisaab will use it for this merchant from now on.")
                    .setTimeoutAfter(4_000)
                    .setAutoCancel(true)
                    .build()
                try { NotificationManagerCompat.from(context).notify(NewTransactionNotifier.NOTIFICATION_BASE + id.toInt(), n) } catch (_: SecurityException) { }
            } finally {
                pending.finish()
            }
        }
    }
}
