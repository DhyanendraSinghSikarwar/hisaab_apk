package com.hisaab.shared.repo

import android.content.Context
import com.hisaab.parser.hash.TransactionHasher
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.statement.InvestmentParser
import com.hisaab.parser.statement.NpsContribution
import com.hisaab.shared.db.TransactionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NPS messages from the CRAs (SMS or email). A holding value updates the NPS holding; a contribution adds to the
 * tier's invested total once, and becomes an investment transaction unless the bank already reported the payment.
 */
@Singleton
class NpsContributions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holdings: HoldingRepository,
    private val transactions: TransactionDao,
) {
    private val prefs by lazy { context.getSharedPreferences("nps-contributions", Context.MODE_PRIVATE) }

    /**
     * Reads [body] for an NPS contribution or holding value. Returns the investment to store, if the bank has not
     * reported it; null otherwise (also when the message held nothing about NPS).
     */
    suspend fun read(body: String, sender: String, receivedAt: Long, messageId: String, source: Source, subject: String? = null): IncomingMessage? {
        val c = InvestmentParser.npsContribution(body, receivedAt)
        if (c == null) {
            InvestmentParser.npsValue(body, receivedAt)?.let { holdings.record(listOf(it), source.name) }
            return null
        }
        return contribute(c, body, sender, receivedAt, messageId, source, subject)
    }

    /** Records contribution [c] once; returns the investment to store unless the bank already reported it. */
    suspend fun contribute(
        c: NpsContribution, body: String, sender: String, receivedAt: Long, messageId: String, source: Source, subject: String? = null,
    ): IncomingMessage? {
        if (!claim(c.key)) return null
        val at = if (c.date == java.time.Instant.ofEpochMilli(receivedAt).atZone(IST).toLocalDate()) receivedAt
        else c.date.atTime(12, 0).atZone(IST).toInstant().toEpochMilli()
        holdings.recordNpsContribution(c, at)
        // The bank's debit for the contribution usually comes a day or two before the CRA credits units.
        val from = c.date.minusDays(4).atStartOfDay(IST).toInstant().toEpochMilli()
        val to = c.date.plusDays(2).atStartOfDay(IST).toInstant().toEpochMilli()
        val already = transactions.findPotentialDuplicates(c.amountMinor, from, to)
            .any { it.type == TransactionType.DEBIT || it.type == TransactionType.INVESTMENT }
        if (already) return null
        return IncomingMessage(investment(c, at, sender, receivedAt, source), "$messageId#nps", body, subject = subject)
    }

    private fun investment(c: NpsContribution, at: Long, sender: String, receivedAt: Long, source: Source) = ParsedTransaction(
        amountMinor = c.amountMinor, currency = "INR", type = TransactionType.INVESTMENT, bankName = "NPS", accountLast4 = null,
        accountKind = AccountKind.ACCOUNT, merchant = if (c.tier == 2) "NPS Tier II" else "NPS Tier I", upiId = null, referenceNumber = null,
        channel = Channel.OTHER, balanceMinor = null, availableLimitMinor = null, transactionTime = at, hasExplicitTime = at == receivedAt,
        category = Category.INVESTMENT, source = source, sender = sender, messageTimestamp = receivedAt, confidence = 0.8f,
        transactionHash = TransactionHasher.hash(c.amountMinor, TransactionType.INVESTMENT, null, c.date, null),
    )

    /** True the first time [key] is seen: the same contribution can arrive by SMS and email, or be read again by a rescan. */
    @Synchronized
    private fun claim(key: String): Boolean {
        val set = prefs.getStringSet(KEYS, emptySet()).orEmpty()
        if (key in set) return false
        val next = (set + key).let { if (it.size > MAX_KEYS) it.toList().takeLast(MAX_KEYS).toSet() else it }
        prefs.edit().putStringSet(KEYS, next).apply()
        return true
    }

    private companion object {
        const val KEYS = "applied"
        const val MAX_KEYS = 500
        val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    }
}
