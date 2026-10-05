package com.hisaab.parser

import com.hisaab.parser.extract.AccountExtractor
import com.hisaab.parser.extract.AmountExtractor
import com.hisaab.parser.extract.ChannelDetector
import com.hisaab.parser.extract.DateTimeExtractor
import com.hisaab.parser.extract.MerchantExtractor
import com.hisaab.parser.extract.ReferenceExtractor
import com.hisaab.parser.extract.TypeClassifier
import com.hisaab.parser.hash.TransactionHasher
import com.hisaab.parser.merchant.MerchantNormalizer
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.rules.RejectionRules
import com.hisaab.parser.text.TextNormalizer
import com.hisaab.parser.text.rx
import java.time.Instant
import java.time.LocalDateTime

/** What could be read from text that has no bank sender: a screenshot, or a payment app's notification. Any field may be missing. */
data class TransactionDraft(
    val amountMinor: Long?,
    val currency: String,
    val type: TransactionType?,
    val merchant: String?,
    val upiId: String?,
    val reference: String?,
    val bankName: String?,
    val last4: String?,
    val accountKind: AccountKind,
    val channel: Channel,
    val category: Category,
    val time: Long,
    val hasExplicitTime: Boolean,
)

/**
 * Reads transactions out of free text with the same extractors the bank parsers use, but without a
 * sender to say which bank it is. A screenshot gives a [TransactionDraft] for the user to confirm;
 * a payment-app notification is accepted only when it clearly describes a completed payment.
 */
class FreeTextParser(private val config: ParserConfig = ParserConfig()) {

    fun draft(raw: String, timestamp: Long): TransactionDraft? {
        val text = TextNormalizer.normalize(raw)
        val lower = text.lowercase()
        val money = AmountExtractor.extract(text) ?: return null
        val type = TypeClassifier.classify(text, lower = lower)
        val account = AccountExtractor.extract(text)
        val channel = ChannelDetector.detect(text, lower)
        val raw0 = MerchantExtractor.extract(text, type ?: TransactionType.DEBIT, lower)
        val merchant = MerchantNormalizer.normalize(raw0, type ?: TransactionType.DEBIT, channel, text)
        val (time, explicit) = resolveTime(text, lower, timestamp)
        return TransactionDraft(
            amountMinor = money.minor, currency = money.currency, type = type, merchant = merchant.name,
            upiId = raw0.vpa?.lowercase(), reference = ReferenceExtractor.normalize(ReferenceExtractor.extract(text, lower)),
            bankName = bankIn(text), last4 = account?.last4, accountKind = account?.kind ?: AccountKind.ACCOUNT,
            channel = channel, category = merchant.category, time = time, hasExplicitTime = explicit,
        )
    }

    /**
     * A payment app's notification ("Paid ₹50 to Raju", "₹200 received from Asha"). Null for offers,
     * cashback, requests, reminders, OTPs and failures, and for anything without an amount and a direction.
     */
    fun fromNotification(appName: String, title: String?, body: String?, postedAt: Long): ParsedTransaction? {
        val raw = listOfNotNull(title, body).joinToString(". ").trim()
        if (raw.length < MIN_LENGTH) return null
        val text = TextNormalizer.normalize(raw)
        val lower = text.lowercase()
        if (RejectionRules.shouldReject(text, lower) || NOT_A_PAYMENT.containsMatchIn(text) || !PAYMENT.containsMatchIn(text)) return null
        val d = draft(raw, postedAt) ?: return null
        val type = d.type ?: return null
        if (type == TransactionType.TRANSFER) return null
        // A notification is about now: dates in it are usually "valid till" or offer dates.
        return toParsed(d.copy(time = postedAt, hasExplicitTime = false), type, Source.APP, appName, postedAt, confidence = 0.55f)
    }

    fun toParsed(
        d: TransactionDraft, type: TransactionType, source: Source, sender: String, messageTimestamp: Long, confidence: Float = 0.9f,
    ): ParsedTransaction {
        val amount = requireNotNull(d.amountMinor) { "amount" }
        val date = Instant.ofEpochMilli(d.time).atZone(config.zone).toLocalDate()
        return ParsedTransaction(
            amountMinor = amount, currency = d.currency, type = type, bankName = d.bankName ?: sender,
            accountLast4 = d.last4, accountKind = d.accountKind, merchant = d.merchant, upiId = d.upiId,
            referenceNumber = d.reference, channel = d.channel, balanceMinor = null, availableLimitMinor = null,
            transactionTime = d.time, hasExplicitTime = d.hasExplicitTime, category = d.category, source = source,
            sender = sender, messageTimestamp = messageTimestamp, confidence = confidence,
            transactionHash = TransactionHasher.hash(amount, type, d.last4, date, d.reference),
        )
    }

    private fun resolveTime(text: String, lower: String, fallback: Long): Pair<Long, Boolean> {
        val parts = DateTimeExtractor.extract(text, lower)
        val date = parts.date ?: return fallback to false
        val time = parts.time ?: Instant.ofEpochMilli(fallback).atZone(config.zone).toLocalTime()
        val at = LocalDateTime.of(date, time).atZone(config.zone).toInstant().toEpochMilli()
        // A screenshot is of something that already happened.
        return if (at > fallback + DAY_MS) fallback to false else at to (parts.time != null)
    }

    private fun bankIn(text: String): String? = BANKS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second

    private companion object {
        const val MIN_LENGTH = 8
        const val DAY_MS = 24 * 60 * 60 * 1000L
        val PAYMENT = rx("""\b(?:paid|sent|received|debited|credited|payment\s+(?:of|to|successful)|transferred|money\s+(?:sent|received)|spent)\b""")
        val NOT_A_PAYMENT = rx(
            """\b(?:cashback|reward|scratch\s*card|offer|coupon|voucher|won|win|requested|requesting|request|remind|reminder|due|pending|""" +
                """processing|expires?|expiring|bill\s+(?:is\s+)?generated|recharge\s+now|pay\s+now|claim|refer)\b""",
        )
        val BANKS = listOf(
            rx("""\bHDFC\b""") to "HDFC Bank", rx("""\bICICI\b""") to "ICICI Bank", rx("""\bSBI\b|state\s+bank""") to "SBI",
            rx("""\bAxis\b""") to "Axis Bank", rx("""\bKotak\b""") to "Kotak Mahindra Bank", rx("""\bIDFC\b""") to "IDFC FIRST Bank",
            rx("""\bYes\s+Bank\b""") to "Yes Bank", rx("""\bBank\s+of\s+Baroda\b|\bBoB\b""") to "Bank of Baroda",
            rx("""\bPNB\b|Punjab\s+National""") to "Punjab National Bank", rx("""\bAU\s+(?:Small\s+Finance\s+)?Bank\b""") to "AU Small Finance Bank",
            rx("""\bIndusInd\b""") to "IndusInd Bank", rx("""\bFederal\s+Bank\b""") to "Federal Bank", rx("""\bCanara\b""") to "Canara Bank",
            rx("""\bUnion\s+Bank\b""") to "Union Bank of India", rx("""\bHSBC\b""") to "HSBC", rx("""\bRBL\b""") to "RBL Bank",
        )
    }
}
