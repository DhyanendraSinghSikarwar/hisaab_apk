package com.hisaab.parser

import com.hisaab.parser.extract.AccountExtractor
import com.hisaab.parser.extract.AmountExtractor
import com.hisaab.parser.extract.ChannelDetector
import com.hisaab.parser.extract.DateTimeExtractor
import com.hisaab.parser.extract.GenericPhrases
import com.hisaab.parser.extract.MerchantExtractor
import com.hisaab.parser.extract.RawMerchant
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
            amountMinor = money.minor, currency = money.currency, type = type, merchant = GenericPhrases.clean(merchant.name),
            upiId = raw0.vpa?.lowercase(), reference = ReferenceExtractor.normalize(ReferenceExtractor.extract(text, lower)),
            bankName = bankIn(text), last4 = account?.last4, accountKind = account?.kind ?: AccountKind.ACCOUNT,
            channel = channel, category = merchant.category, time = time, hasExplicitTime = explicit,
        )
    }

    /**
     * A payment app's notification ("Paid ₹50 to Raju", "₹200 received from Asha"). Null for offers,
     * cashback, requests, reminders, OTPs and failures, and for anything without an amount and a direction.
     */
    fun fromNotification(appName: String, title: String?, body: String?, postedAt: Long): ParsedTransaction? =
        readNotification(appName, listOf(title, body), postedAt).tx

    /** A notification outcome: the transaction, or a short generic [skipReason] (never the text) for the activity log. */
    class NotificationRead(val tx: ParsedTransaction?, val skipReason: String?)

    /**
     * Reads a payment app notification made of several text fields (title, text, big text, sub text, lines),
     * in any layout: amount and payee may sit in different fields, with or without a currency symbol.
     */
    fun readNotification(appName: String, parts: List<String?>, postedAt: Long, source: Source = Source.APP): NotificationRead {
        fun skip(reason: String) = NotificationRead(null, reason)
        val segments = parts.filterNotNull().flatMap { it.split(FIELD_SPLIT) }
            .map { TextNormalizer.normalize(it) }.filter { it.isNotBlank() }
            .flatMap { SENTENCE.split(it) }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val whole = segments.joinToString(" . ")
        if (whole.length < MIN_LENGTH) return skip("too short")
        if (OTP.containsMatchIn(whole)) return skip("otp or security text")
        if (FAILED.containsMatchIn(whole)) return skip("failed or pending payment")
        if (REMINDER.containsMatchIn(whole)) return skip("request, reminder or due notice")
        val kept = segments.filterNot { PROMO.containsMatchIn(it) || UI_TEXT.containsMatchIn(it) }
            .map { addCurrency(it) }
        if (kept.isEmpty()) return skip("offer or promotion")
        val text = kept.joinToString(" . ")
        val lower = text.lowercase()
        val signal = SIGNAL.find(text) ?: return skip("no payment wording")
        val type = if (signal.groups["cr"] != null) TransactionType.CREDIT else TransactionType.DEBIT
        if (TypeClassifier.classify(text, lower = lower) == TransactionType.TRANSFER) return skip("own transfer or card bill")
        val money = AmountExtractor.extract(text) ?: return skip("no amount")
        if (money.minor <= 0) return skip("no amount")

        var (name, vpa) = payee(kept, type)
        if (name == null && vpa == null && type == TransactionType.DEBIT) {
            name = BILL_KIND.find(text)?.let { it.groupValues[1].replaceFirstChar(Char::uppercase) + " bill" }
                ?: if (RECHARGE.containsMatchIn(text)) "Recharge" else null
        }
        val channel = ChannelDetector.detect(text, lower).let { if (it == Channel.OTHER && appName in UPI_APPS) Channel.UPI else it }
        val merchant = MerchantNormalizer.normalize(RawMerchant(name, vpa), type, channel, text)
        val account = AccountExtractor.extract(text)
        val draft = TransactionDraft(
            amountMinor = money.minor, currency = money.currency, type = type, merchant = GenericPhrases.clean(merchant.name),
            upiId = vpa ?: VPA.find(text)?.value?.lowercase(), reference = UTR.find(text)?.groupValues?.get(1) ?: ReferenceExtractor.normalize(ReferenceExtractor.extract(text, lower)),
            bankName = bankIn(text), last4 = account?.last4, accountKind = account?.kind ?: AccountKind.ACCOUNT,
            channel = channel, category = merchant.category,
            // A notification is about now: dates in it are usually "valid till" or offer dates.
            time = postedAt, hasExplicitTime = false,
        )
        return NotificationRead(toParsed(draft, type, source, appName, postedAt, confidence = 0.55f), null)
    }

    /** "Paid 250 to X", "250 sent": gives a bare amount next to a payment verb the symbol the extractor needs. */
    private fun addCurrency(s: String): String {
        if (CURRENCY_WORD.containsMatchIn(s)) return s
        return BARE_AFTER.replace(s) { "${it.groupValues[1]} INR ${it.groupValues[2]}" }
            .let { BARE_BEFORE.replace(it) { m -> "INR ${m.groupValues[1]} ${m.groupValues[2]}" } }
    }

    /** Payee (debit) or payer (credit): a name, or a UPI id when that is all the notification says. */
    private fun payee(segments: List<String>, type: TransactionType): Pair<String?, String?> {
        val candidates = ArrayList<String>()
        for (s in segments) {
            val found = if (type == TransactionType.CREDIT) {
                (SENDER_LEAD.find(s)?.groupValues?.get(1)?.let(::listOf) ?: emptyList()) + FROM_NAME.findAll(s).map { it.groupValues[1] }
            } else {
                (PAID_NAME.find(s)?.groupValues?.get(1)?.let(::listOf) ?: emptyList()) +
                    TO_NAME.findAll(s).map { it.groupValues[1] } + AT_NAME.findAll(s).map { it.groupValues[1] }
            }
            candidates += found
        }
        for (c in candidates) {
            val t = c.replace(TRAILING_NOISE, "").trim(' ', '-', '.', ',')
            if (t.length < 2 || NOT_NAME.containsMatchIn(t) || GenericPhrases.isGeneric(t)) continue
            val vpa = VPA.find(t)?.value
            if (vpa != null) {
                val rest = t.replace(vpa, "").trim(' ', '-', '(', ')')
                return (if (rest.length >= 2 && rest.any { it.isLetter() }) rest else null) to vpa.lowercase()
            }
            if (t.none { it.isLetter() }) continue
            return t to null
        }
        return null to null
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
        val UPI_APPS = setOf("PhonePe", "Google Pay", "Paytm", "BHIM", "BHIM SBI Pay", "CRED", "super.money", "Navi", "WhatsApp Pay", "Amazon Pay", "MobiKwik")
        val FIELD_SPLIT = Regex("""[\r\n]+|\s[•·|]\s|[•·]""")
        // Sentence end, but not the dot of "Dr." or "Mr.".
        val SENTENCE = Regex("""(?<!\b(?:Mr|Mrs|Ms|Dr|Smt|Shri|Sri)\.)(?<=[.!?])\s+""")
        private val I = RegexOption.IGNORE_CASE
        val OTP = Regex("""\botp\b|one[\s-]?time\s+pass|verification\s+code|\bcvv\b|\bupi\s+pin\b|\bpin\b""", I)
        val FAILED = Regex(
            """\b(?:fail(?:ed|ure|s)?|declined|unsuccessful|not\s+successful|could\s*n[o']?t|cancel(?:l)?ed|rejected|reversed|pending|""" +
                """processing|in\s+progress|refund\s+(?:initiated|in\s+progress)|will\s+be\s+(?:refunded|credited)|timed?\s*out|on\s+hold|""" +
                """bank\s+server|try\s+again)\b""", I,
        )
        val REMINDER = Regex(
            """\b(?:requested|requesting|payment\s+request|collect\s+request|requests?\s+(?:you|money)|remind(?:er|s)?|due|overdue|expir(?:es|ing|ed)|""" +
                """valid\s+(?:till|until)|bill\s+(?:is\s+)?generated|pay\s+(?:now|again|later)|recharge\s+now|pay\s+bill|scheduled|upcoming|""" +
                """will\s+be\s+(?:debited|deducted|charged)|set\s*up\s+autopay)\b""", I,
        )
        /** Offers, rewards and promotions: the sentence is dropped, the rest of the notification still counts. */
        val PROMO = Regex(
            """\b(?:cash\s*back|rewards?|scratch(?:\s*card)?|offers?|coupons?|vouchers?|won|win|claim|refer(?:ral)?|earn(?:ed|s)?|bonus|""" +
                """discount|get\s+up\s*to|lucky|spin|\d+\s*points?)\b|\d+\s*%""", I,
        )
        /** Buttons and links that notifications append. */
        val UI_TEXT = Regex(
            """^(?:view|tap|know|check|learn|download|track|explore|open|see|click|share|rate|swipe|repay|invite|enjoy|thank|split|add\s+(?:money|to))\b|\b(?:tap|click|swipe)\s+to\b""", I,
        )
        val SIGNAL = Regex(
            """(?<cr>\b(?:(?:sent|paid)\s+you|received|credited|refund(?:ed)?\s+(?:of\s+\S+\s+\S+\s+)?(?:processed|credited|successful)|money\s+received|payment\s+received)\b)""" +
                """|(?<db>\b(?:paid|sent|debited|spent|transferred|deducted|charged|purchased|money\s+sent|""" +
                """payment\b(?!.{0,60}?\breceived\b).{0,60}?\b(?:successful|done|completed|made|sent)|""" +
                """(?:recharge|bill)(?:\s+(?:payment\s+)?of\s+INR\s*[\d,.]+)?\s+(?:is\s+)?(?:successful|done|completed|paid))\b)""", I,
        )
        val CURRENCY_WORD = Regex("""\b(?:INR|USD|EUR|GBP|AED|SGD)\s*\d""", I)
        const val NUM = """(\d[\d,]*(?:\.\d{1,2})?)"""
        val BARE_AFTER = Regex("""\b(paid|sent|received|debited|credited|spent|payment\s+of|bill\s+of|recharge\s+of|you)\s+$NUM\b(?!\s*(?:%|days?|hours?|mins?|messages?|times?))""", I)
        val BARE_BEFORE = Regex("""^$NUM\s+(paid|sent|received|debited|credited)\b""", I)
        val AMOUNT_WORD = Regex("""\bINR\s*\d""", I)
        private const val NAME = """([A-Za-z0-9][A-Za-z0-9 &'*_\-./@]{1,50}?)"""
        private const val NAME_END =
            """(?=\s+(?:via|using|on|dated|from|ref|refno|utr|upi|for|at|with|txn|transaction|successfully|successful|id|a/c|account|bank|is|was|has|through|thru|date|time|status|amount|debited|credited|reference)\b|\s*[,;:(!]|$)"""
        val TO_NAME = Regex("""\bto\s+$NAME$NAME_END""", I)
        val AT_NAME = Regex("""\bat\s+$NAME$NAME_END""", I)
        /** "You paid Ravi Kumar INR 250": the payee sits between the verb and the amount. */
        val PAID_NAME = Regex("""\bpaid\s+(?!you\b|INR)$NAME\s+INR\s*\d""", I)
        val FROM_NAME =Regex("""\bfrom\s+$NAME$NAME_END""", I)
        val SENDER_LEAD = Regex("""^$NAME\s+(?:has\s+)?(?:sent|paid)\s+you\b""", I)
        val NOT_NAME = Regex("""^(?:your|you|my|a/c|ac|acct|account|bank|the|a|an|card|wallet|upi|inr|view|see|check|know|get|earn|avoid|us|me|self)\b|^x+\d""", I)
        val RECHARGE = Regex("""\b(?:mobile\s+|dth\s+|fastag\s+)?recharge\b""", I)
        val BILL_KIND = Regex("""\b(electricity|water|gas|broadband|postpaid|mobile|dth|phone|internet|insurance)\s+bill\b""", I)
        val TRAILING_NOISE = Regex("""(?:\s+(?:via|using|on|upi|through|thru|ref|utr)\b.*|\s*\(.*)$""", I)
        /** The 12-digit UPI reference the bank repeats in its own alert, which beats an app's own transaction id. */
        val UTR = Regex("""\b(?:utr|rrn|upi\s+ref(?:erence)?(?:\s+(?:no|id))?)\W{0,4}(\d{10,22})\b""", I)
        val VPA = Regex("""\b[a-z0-9][\w.\-]{1,}@[a-z][a-z0-9]{1,20}\b""", I)
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
