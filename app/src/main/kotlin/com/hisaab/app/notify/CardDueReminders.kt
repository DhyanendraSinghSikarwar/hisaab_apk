package com.hisaab.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisaab.app.ApplicationScope
import com.hisaab.app.MainActivity
import com.hisaab.app.R
import com.hisaab.app.i18n.t
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.ui.accounts.isCreditCardRow
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.repo.CardDues
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The reminders for one card bill: [offsetDays] is the number of days to the due date at which it fires. */
enum class DueStage(val offsetDays: Int) { DAYS_5(5), DAYS_2(2), DUE_DAY(0), OVERDUE(-1) }

/** Which reminder, if any, is next for a card bill. Pure, so the staging is testable without Android. */
object CardDueReminderPlan {
    /** Reminders stop this many days after the due date. */
    const val OVERDUE_WINDOW = 7L

    /**
     * The stage to send on [now] for a bill due on [dueDate], or null. Only the most urgent stage reached is sent (a card first
     * seen 1 day before the due date gets the 2-day reminder, not also the 5-day one), and a stage already in [sentStages]
     * is never repeated. A [paid] bill, or one snoozed until after [now], gets nothing.
     */
    fun next(now: LocalDate, dueDate: LocalDate, sentStages: Set<DueStage>, paid: Boolean, snoozedUntil: LocalDate?): DueStage? {
        if (paid) return null
        if (snoozedUntil != null && now.isBefore(snoozedUntil)) return null
        val days = ChronoUnit.DAYS.between(now, dueDate)
        if (days < -OVERDUE_WINDOW) return null
        val stage = DueStage.entries.filter { days <= it.offsetDays }.minByOrNull { it.offsetDays } ?: return null
        return stage.takeIf { it !in sentStages }
    }
}

private val Context.cardDueStore: DataStore<Preferences> by preferencesDataStore(name = "card_due_reminders")

/** What was sent, marked paid or snoozed, per card and due date. Stays on the phone. */
@Singleton
class CardDueReminderStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.cardDueStore

    /** Stages already sent, as "cardId|dueEpochDay|STAGE". */
    private val sent = stringSetPreferencesKey("sent")
    /** Bills marked paid, as "cardId|dueEpochDay". */
    private val paid = stringSetPreferencesKey("paid")
    /** One-day suppressions, as "cardId|dueEpochDay|untilEpochDay". */
    private val snoozed = stringSetPreferencesKey("snoozed")

    /** Keys of bills marked paid, "cardId|dueEpochDay". */
    val paidKeys: Flow<Set<String>> = store.data.map { it[paid].orEmpty() }

    suspend fun sentStages(cardId: Long, due: LocalDate): Set<DueStage> = store.data.first()[sent].orEmpty()
        .mapNotNull { k -> k.split('|').takeIf { it.size == 3 && it[0] == cardId.toString() && it[1] == due.toEpochDay().toString() }?.let { runCatching { DueStage.valueOf(it[2]) }.getOrNull() } }
        .toSet()

    suspend fun snoozedUntil(cardId: Long, due: LocalDate): LocalDate? = store.data.first()[snoozed].orEmpty()
        .mapNotNull { k -> k.split('|').takeIf { it.size == 3 && it[0] == cardId.toString() && it[1] == due.toEpochDay().toString() }?.get(2)?.toLongOrNull() }
        .maxOrNull()?.let(LocalDate::ofEpochDay)

    suspend fun isPaid(cardId: Long, due: LocalDate): Boolean = paidKey(cardId, due) in store.data.first()[paid].orEmpty()

    /** Records [stage] and every earlier one as sent. */
    suspend fun markSent(cardId: Long, due: LocalDate, stage: DueStage) = store.edit { p ->
        val add = DueStage.entries.filter { it.offsetDays >= stage.offsetDays }.map { "$cardId|${due.toEpochDay()}|${it.name}" }
        p[sent] = prune(p[sent].orEmpty() + add, 1)
    }

    suspend fun markPaid(cardId: Long, due: LocalDate) = store.edit { p -> p[paid] = prune(p[paid].orEmpty() + paidKey(cardId, due), 1) }
    suspend fun undoPaid(cardId: Long, due: LocalDate) = store.edit { p -> p[paid] = p[paid].orEmpty() - paidKey(cardId, due) }

    suspend fun snooze(cardId: Long, due: LocalDate, until: LocalDate) = store.edit { p ->
        p[snoozed] = prune(p[snoozed].orEmpty().filterNot { it.startsWith("$cardId|${due.toEpochDay()}|") }.toSet() + "$cardId|${due.toEpochDay()}|${until.toEpochDay()}", 1)
    }

    /** Drops entries whose due date (field [dueAt]) is over 120 days old. */
    private fun prune(keys: Set<String>, dueAt: Int): Set<String> {
        val cutoff = LocalDate.now(Periods.zone).toEpochDay() - 120
        return keys.filter { k -> (k.split('|').getOrNull(dueAt)?.toLongOrNull() ?: Long.MAX_VALUE) >= cutoff }.toSet()
    }

    companion object {
        fun paidKey(cardId: Long, due: LocalDate) = "$cardId|${due.toEpochDay()}"
    }
}

/** Sends the card bill reminders: at most one notification per card per stage, none for a paid or snoozed bill. */
@Singleton
class CardDueReminderChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: AccountDao,
    private val statements: StatementDao,
    private val transactions: TransactionDao,
    private val settings: AppSettingsStore,
    private val store: CardDueReminderStore,
) {
    private val lock = Mutex()

    suspend fun check() = lock.withLock {
        val manager = NotificationManagerCompat.from(context)
        val today = LocalDate.now(Periods.zone)
        val cards = accounts.observeWithActivity(Periods.startOfMonth(System.currentTimeMillis())).first().filter { it.isCreditCardRow && !it.hidden }
        if (cards.isEmpty()) return@withLock
        val all = statements.observeAll().first()
        val enabled = settings.settings.first().cardDueReminders
        val from = today.minusDays(CardDues.WINDOW_DAYS).atStartOfDay(Periods.zone).toInstant().toEpochMilli()
        for (a in cards) {
            val mine = all.filter { it.last4 == a.last4 && (it.bankName == null || it.bankName.equals(a.bankName, ignoreCase = true)) }
            val since = mine.mapNotNull { it.statementEpochDay }.maxOrNull()?.let { LocalDate.ofEpochDay(it).atStartOfDay(Periods.zone).toInstant().toEpochMilli() }
            val flow = transactions.cardFlowSince(a.id, minOf(from, since ?: from)).first()
            val p = CardDues.position(mine, a.currentBalanceMinor, flow, today, Periods.zone)
            val billed = p.billedMinor?.takeIf { it > 0 }
            val due = p.dueDay
            // Paid (billed back to zero) or no bill: nothing pending.
            if (billed == null || due == null) { manager.cancel(notificationId(a.id)); continue }
            if (!enabled) continue
            val stage = CardDueReminderPlan.next(today, due, store.sentStages(a.id, due), store.isPaid(a.id, due), store.snoozedUntil(a.id, due))
            if (stage == null) { if (store.isPaid(a.id, due)) manager.cancel(notificationId(a.id)); continue }
            if (!manager.areNotificationsEnabled()) continue
            val name = a.nickname?.takeIf { it.isNotBlank() } ?: a.bankName
            if (send(a.id, name, due, ChronoUnit.DAYS.between(today, due), billed, p.minDueMinor?.takeIf { it > 0 }, p.estimated)) store.markSent(a.id, due, stage)
        }
    }

    private fun send(cardId: Long, name: String, due: LocalDate, days: Long, billed: Long, min: Long?, estimated: Boolean): Boolean {
        ensureBillsChannel(context)
        val title = when {
            days < 0 -> t("{card} bill overdue", "card" to name)
            days == 0L -> t("{card} bill due today", "card" to name)
            days == 1L -> t("{card} bill due tomorrow", "card" to name)
            else -> t("{card} bill due in {days} days", "card" to name, "days" to days)
        }
        val amount = Money.format(billed, showPaise = false)
        val date = due.format(DateTimeFormatter.ofPattern("d MMM"))
        val body = (if (estimated) t("About {amount} · pay by {date}.", "amount" to amount, "date" to date) else t("{amount} · pay by {date}.", "amount" to amount, "date" to date)) +
            (min?.let { " " + t("Minimum {amount}.", "amount" to Money.format(it, showPaise = false)) } ?: "")
        val id = notificationId(cardId)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(Intent.ACTION_VIEW, Uri.parse("hisaab://account/$cardId"), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, BILLS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_statement)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true)
            .addAction(0, t("Mark paid"), action(cardId, due, CardDueActionReceiver.ACTION_PAID, id + 1))
            .addAction(0, t("Remind tomorrow"), action(cardId, due, CardDueActionReceiver.ACTION_SNOOZE, id + 2))
            .build()
        return try { NotificationManagerCompat.from(context).notify(id, n); true } catch (_: SecurityException) { false }
    }

    private fun action(cardId: Long, due: LocalDate, act: String, request: Int) = PendingIntent.getBroadcast(
        context, request,
        Intent(context, CardDueActionReceiver::class.java).setAction(act)
            .putExtra(CardDueActionReceiver.EXTRA_CARD, cardId).putExtra(CardDueActionReceiver.EXTRA_DUE, due.toEpochDay()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        /** One notification per card: a later stage replaces the earlier one. Offset by 3 so the action request codes (+1, +2) stay unique. */
        fun notificationId(cardId: Long): Int = 30_000 + cardId.toInt() * 3
    }
}

private fun notificationId(cardId: Long) = CardDueReminderChecker.notificationId(cardId)

/** "Mark paid" and "Remind tomorrow" on a card bill reminder. */
class CardDueActionReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun store(): CardDueReminderStore
        @ApplicationScope fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val card = intent.getLongExtra(EXTRA_CARD, -1).takeIf { it >= 0 } ?: return
        val due = intent.getLongExtra(EXTRA_DUE, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay) ?: return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val pending = goAsync()
        deps.scope().launch {
            try {
                when (intent.action) {
                    ACTION_PAID -> deps.store().markPaid(card, due)
                    ACTION_SNOOZE -> deps.store().snooze(card, due, LocalDate.now(Periods.zone).plusDays(1))
                    else -> return@launch
                }
                NotificationManagerCompat.from(context).cancel(notificationId(card))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAID = "com.hisaab.app.CARD_DUE_PAID"
        const val ACTION_SNOOZE = "com.hisaab.app.CARD_DUE_SNOOZE"
        const val EXTRA_CARD = "card"
        const val EXTRA_DUE = "due"
    }
}
