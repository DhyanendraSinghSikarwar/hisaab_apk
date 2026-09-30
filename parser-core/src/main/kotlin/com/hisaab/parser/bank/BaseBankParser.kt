package com.hisaab.parser.bank

import com.hisaab.parser.BankParser
import com.hisaab.parser.ParserConfig
import com.hisaab.parser.extract.AccountExtractor
import com.hisaab.parser.extract.AmountExtractor
import com.hisaab.parser.extract.BalanceExtractor
import com.hisaab.parser.extract.ChannelDetector
import com.hisaab.parser.extract.DateTimeExtractor
import com.hisaab.parser.extract.DateTimeParts
import com.hisaab.parser.extract.MerchantExtractor
import com.hisaab.parser.extract.Money
import com.hisaab.parser.extract.RawMerchant
import com.hisaab.parser.extract.ReferenceExtractor
import com.hisaab.parser.extract.TypeClassifier
import com.hisaab.parser.hash.TransactionHasher
import com.hisaab.parser.merchant.MerchantNormalizer
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.registry.SenderKeys
import com.hisaab.parser.rules.RejectionRules
import com.hisaab.parser.template.Template
import com.hisaab.parser.text.TextNormalizer
import java.time.Instant

/**
 * The shared pipeline: normalize -> reject non-transactions -> bank templates -> generic extractors.
 * A bank parser declares its sender keys and the templates for the messages the generic extractors
 * get wrong. Everything else is inherited.
 */
abstract class BaseBankParser(protected val config: ParserConfig) : BankParser {
    protected abstract val smsHeaders: Set<String>
    protected abstract val emailDomains: Set<String>
    protected open val smsTemplates: List<Template> = emptyList()
    protected open val emailTemplates: List<Template> = emptyList()

    override val senderKeys: Set<String> by lazy { smsHeaders.map { it.uppercase() }.toSet() + emailDomains.map { it.lowercase() } }

    // Emails often reuse the SMS wording, so email parsing tries the email templates first, then the SMS ones.
    private val emailThenSmsTemplates: List<Template> by lazy { emailTemplates + smsTemplates }

    /** The email domains to put in the Gmail `from:` query. */
    val emailSenders: Set<String> get() = emailDomains

    override fun canHandle(sender: String, body: String): Boolean = SenderKeys.candidates(sender).any { it in senderKeys }

    protected open fun bankNameFor(sender: String): String = bankName

    override fun parse(body: String, sender: String, timestamp: Long, source: Source): ParsedTransaction? {
        val normalized = TextNormalizer.normalize(body)
        val text = if (source == Source.EMAIL) TextNormalizer.trimEmail(normalized) else normalized
        if (text.length < MIN_LENGTH) return null
        val lower = text.lowercase()
        if (RejectionRules.shouldReject(text, lower)) return null

        val templates = if (source == Source.EMAIL) emailThenSmsTemplates else smsTemplates
        val hit = templates.firstNotNullOfOrNull { it.match(text) }

        val money = hit?.amount?.let { Money.parse(it, hit.currency ?: "INR") } ?: AmountExtractor.extract(text) ?: return null
        var type = TypeClassifier.classify(text, hit?.type, lower) ?: return null

        val genericAccount = AccountExtractor.extract(text)
        val last4 = hit?.account?.takeLast(4) ?: genericAccount?.last4
        val kind = hit?.accountKind ?: genericAccount?.kind ?: AccountKind.ACCOUNT
        val reference = ReferenceExtractor.normalize(hit?.reference ?: ReferenceExtractor.extract(text, lower))

        // A message with neither an account nor a reference is almost always marketing.
        if (last4 == null && reference == null && hit == null) return null

        val channel = ChannelDetector.detect(text, lower)
        val raw = if (hit?.merchant != null || hit?.vpa != null) RawMerchant(hit.merchant, hit.vpa)
        else MerchantExtractor.extract(text, type, lower)

        if (type == TransactionType.DEBIT &&
            (TypeClassifier.looksLikeInvestment(text, lower) || MerchantNormalizer.categoryOf(raw) == Category.INVESTMENT)
        ) {
            type = TransactionType.INVESTMENT
        }
        val merchant = MerchantNormalizer.normalize(raw, type, channel, text)

        val balance = hit?.balance?.let(Money::parseSigned) ?: BalanceExtractor.balance(text, lower)
        val limit = if (kind == AccountKind.CARD) BalanceExtractor.limit(text, lower) else null

        val (time, explicit) = resolveTime(DateTimeExtractor.extract(text, lower), timestamp)
        val date = Instant.ofEpochMilli(time).atZone(config.zone).toLocalDate()

        var confidence = 0.5f
        if (last4 != null) confidence += 0.15f
        if (reference != null) confidence += 0.15f
        if (merchant.name != null) confidence += 0.1f
        if (hit != null) confidence += 0.1f

        return ParsedTransaction(
            amountMinor = money.minor,
            currency = money.currency,
            type = type,
            bankName = bankNameFor(sender),
            accountLast4 = last4,
            accountKind = kind,
            merchant = merchant.name,
            upiId = raw.vpa?.lowercase(),
            referenceNumber = reference,
            channel = channel,
            balanceMinor = if (kind == AccountKind.CARD) null else balance,
            availableLimitMinor = limit,
            transactionTime = time,
            hasExplicitTime = explicit,
            category = merchant.category,
            source = source,
            sender = sender,
            messageTimestamp = timestamp,
            confidence = confidence.coerceAtMost(1f),
            transactionHash = TransactionHasher.hash(money.minor, type, last4, date, reference),
        )
    }

    /** The date in the text wins when it is plausible; the time of day comes from the text or the message. */
    private fun resolveTime(parts: DateTimeParts, messageTs: Long): Pair<Long, Boolean> {
        val msg = Instant.ofEpochMilli(messageTs).atZone(config.zone)
        val date = parts.date ?: return messageTs to false
        val msgDate = msg.toLocalDate()
        if (date.isAfter(msgDate.plusDays(1)) || date.isBefore(msgDate.minusDays(config.maxBackdateDays))) return messageTs to false
        parts.time?.let { return date.atTime(it).atZone(config.zone).toInstant().toEpochMilli() to true }
        if (date == msgDate) return messageTs to false
        return date.atTime(msg.toLocalTime()).atZone(config.zone).toInstant().toEpochMilli() to false
    }

    private companion object {
        const val MIN_LENGTH = 20
    }
}
