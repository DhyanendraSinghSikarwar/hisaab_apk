package com.hisaab.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.hisaab.app.ApplicationScope
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.parser.model.Source
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.parser.statement.InvestmentParser
import com.hisaab.shared.repo.HoldingRepository
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

/** Parses a bank SMS the moment it arrives, so the transaction shows up without a scan. */
class SmsReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun registry(): ParserRegistry
        fun repository(): TransactionRepository
        fun notifier(): TransactionsChangedNotifier
        fun settings(): AppSettingsStore
        fun holdings(): HoldingRepository
        fun newTransactions(): com.hisaab.app.notify.NewTransactionNotifier
        @ApplicationScope fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val registry = deps.registry()

        // A long SMS arrives as several parts; join them per sender.
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val bySender = messages.filter { it.originatingAddress != null }.groupBy { it.originatingAddress!! }
            .filterKeys { registry.accepts(it) || InvestmentParser.accepts(it) }
        if (bySender.isEmpty()) return

        val pending = goAsync()
        deps.scope().launch {
            try {
                if (!deps.settings().settings.first().smsEnabled) return@launch
                var changed = false
                for ((sender, parts) in bySender) {
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    val sentAt = parts.first().timestampMillis
                    if (InvestmentParser.accepts(sender)) {
                        InvestmentParser.parse(body, sender, sentAt)?.let { deps.holdings().record(listOf(it), "SMS") }
                        continue
                    }
                    val tx = registry.parse(body, sender, System.currentTimeMillis(), Source.SMS) ?: continue
                    val messageId = SmsIds.of(sender, sentAt, body)
                    val outcome = deps.repository().ingest(IncomingMessage(tx, messageId, body))
                    if (outcome != IngestOutcome.ALREADY_PROCESSED) changed = true
                    deps.newTransactions().onIngested(outcome, "SMS", messageId)
                }
                if (changed) deps.notifier().onTransactionsChanged()
            } finally {
                pending.finish()
            }
        }
    }
}
