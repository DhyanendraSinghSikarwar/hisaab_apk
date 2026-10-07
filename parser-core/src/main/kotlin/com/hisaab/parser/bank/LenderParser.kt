package com.hisaab.parser.bank

import com.hisaab.parser.ParserConfig
import com.hisaab.parser.extract.ChannelDetector
import com.hisaab.parser.extract.Money
import com.hisaab.parser.extract.ReferenceExtractor
import com.hisaab.parser.hash.TransactionHasher
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.rules.RejectionRules
import com.hisaab.parser.template.Template.Companion.AMT
import com.hisaab.parser.text.TextNormalizer
import com.hisaab.parser.text.rx
import java.time.Instant

/** A non-bank lender (NBFC or fintech): the name its loans are shown under, and the senders it writes from. */
data class Lender(val name: String, val smsHeaders: Set<String>, val emailDomains: Set<String>)

/**
 * What a lender's message says about a loan when it is not a payment: a disbursal, the outstanding principal,
 * or an EMI reminder. Amounts in paise; [last4] is the end of the loan number.
 *
 * With [deposit] set it is a bank's FD, RD or PPF account instead ([DepositParser]): [lender] is the bank,
 * [outstandingMinor] the deposit's balance and [principalMinor] the amount booked.
 */
data class LoanStatus(
    val lender: String,
    val last4: String,
    val principalMinor: Long?,
    val outstandingMinor: Long?,
    val at: Long,
    val deposit: DepositInfo? = null,
)

/** The lenders with their own parser. Their loans become loan accounts named after them. */
object Lenders {
    val ALL: List<Lender> = listOf(
        Lender("Propelld", setOf("PROPLD", "PRPLDF", "PROPEL", "PRPELD", "PRPLDS"), setOf("propelld.com")),
        Lender(
            "Aditya Birla Capital", setOf("ABCLTD", "ABCFIN", "ABCAPL", "ABCDAP", "ADBIRL", "ABFLTD", "ABHFLT"),
            setOf("adityabirlacapital.com", "abcd.adityabirlacapital.com"),
        ),
        Lender("Bajaj Finance", setOf("BAJAJF", "BAJFIN", "BAJAJL", "BFLEMI"), setOf("bajajfinserv.in", "bajajfinance.in")),
        Lender("Tata Capital", setOf("TATACP", "TATCAP", "TCLOAN"), setOf("tatacapital.com")),
        Lender("HDFC Credila", setOf("CREDLA", "CREDIL", "HCREDL"), setOf("credila.com", "hdfccredila.com")),
        Lender("Avanse", setOf("AVANSE", "AVNSFS"), setOf("avanse.com")),
        Lender("InCred", setOf("INCRED", "INCRDF"), setOf("incred.com")),
        Lender("SMFG India Credit", setOf("SMFGIN", "SMFGIC", "FULRTN", "FULTON"), setOf("smfgindiacredit.com", "fullertonindia.com")),
        Lender("Home Credit", setOf("HOMECR", "HCINDA", "HMCRDT"), setOf("homecredit.co.in")),
        Lender("KreditBee", setOf("KRDBEE", "KBEEIN", "KREDTB"), setOf("kreditbee.in")),
        Lender("Navi", setOf("NAVIFN", "NAVILN", "NAVIAP"), setOf("navi.com")),
        Lender("Poonawalla Fincorp", setOf("POONAW", "PFLTDL", "PFLLON"), setOf("poonawallafincorp.com")),
        Lender("HDB Financial", setOf("HDBFSL", "HDBFIN", "HDBFSS"), setOf("hdbfs.com")),
        Lender("L&T Finance", setOf("LTFINC", "LNTFIN", "LTFSMS"), setOf("ltfs.com")),
        Lender("Hero FinCorp", setOf("HEROFC", "HEROFN"), setOf("herofincorp.com")),
        Lender("Cholamandalam Finance", setOf("CHOLAF", "CHOLAL"), setOf("chola.murugappa.com")),
        Lender("Mahindra Finance", setOf("MMFSLT", "MAHFIN", "MMFINL"), setOf("mahindrafinance.com")),
    )

    private val NAMES: Set<String> = ALL.map { it.name.lowercase() }.toSet()

    /** True when [bankName] is one of these lenders: every account under it is a loan. */
    fun isLender(bankName: String): Boolean = bankName.lowercase() in NAMES

    fun parsers(config: ParserConfig = ParserConfig()): List<LenderParser> = ALL.map { LenderParser(it, config) }
}

/**
 * A lender's SMS and email. An EMI paid ("EMI of Rs.12,345 for your loan a/c no. XXXX1234 has been received") is a
 * DEBIT filed under EMI & Loans on the loan account; a disbursal, an outstanding figure or a reminder is a [LoanStatus].
 */
class LenderParser(val lender: Lender, config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = lender.name
    override val smsHeaders = lender.smsHeaders
    override val emailDomains = lender.emailDomains

    override fun parse(body: String, sender: String, timestamp: Long, source: Source): ParsedTransaction? {
        val text = textOf(body, source) ?: return null
        val lower = text.lowercase()
        val reason = RejectionRules.reasonFor(text, lower)
        if (reason != null && reason !in SOFT) return null
        if (DISBURSED.containsMatchIn(text) || !PAID.containsMatchIn(text)) return null
        val money = emiAmount(text) ?: return null
        val last4 = loanLast4(text)
        val reference = ReferenceExtractor.normalize(ReferenceExtractor.extract(text, lower))
        val date = Instant.ofEpochMilli(timestamp).atZone(config.zone).toLocalDate()
        return ParsedTransaction(
            amountMinor = money.minor, currency = money.currency, type = TransactionType.DEBIT, bankName = lender.name,
            accountLast4 = last4, accountKind = AccountKind.ACCOUNT, merchant = lender.name, upiId = null,
            referenceNumber = reference, channel = ChannelDetector.detect(text, lower), balanceMinor = outstanding(text),
            availableLimitMinor = null, transactionTime = timestamp, hasExplicitTime = false, category = Category.EMI_LOAN,
            source = source, sender = sender, messageTimestamp = timestamp, confidence = if (last4 != null) 0.9f else 0.7f,
            transactionHash = TransactionHasher.hash(money.minor, TransactionType.DEBIT, last4, date, reference),
        )
    }

    /** The loan facts in a message that is not a payment; null when it names no loan or is an OTP, promotion or failure. */
    fun status(body: String, timestamp: Long, source: Source): LoanStatus? {
        val text = textOf(body, source) ?: return null
        val disbursed = DISBURSED.containsMatchIn(text)
        val reason = RejectionRules.reasonFor(text, text.lowercase())
        // "Congratulations! Your loan has been disbursed" reads as a promotion.
        if (reason != null && reason !in SOFT && !(reason == "promo" && disbursed)) return null
        if (!disbursed && PAID.containsMatchIn(text)) return null
        val last4 = loanLast4(text) ?: return null
        val principal = if (disbursed) principalOf(text) else null
        return LoanStatus(lender.name, last4, principal, outstanding(text) ?: principal, timestamp)
    }

    private fun textOf(body: String, source: Source): String? {
        val normalized = TextNormalizer.normalize(body)
        val text = if (source == Source.EMAIL) TextNormalizer.trimEmail(normalized) else normalized
        return text.takeIf { it.length >= 20 }
    }

    internal companion object {
        /** Rejection reasons that still leave loan facts to read: reminders, future debits, balance notes. */
        private val SOFT = setOf("future", "reminder", "balance")

        private const val VERB = """(?:received|paid|debited|deducted|collected|realised|realized|cleared)"""
        /** A completed repayment. "will be debited" and "not received" are not. */
        val PAID = rx(
            """\b(?:EMI|instal+ment|repayment|payment|INR\s*$AMT)\b.{0,80}?(?<!\bbe )(?<!\bnot )\b$VERB\b""" +
                """|\b(?:received|debited|deducted|collected)\b.{0,40}?\b(?:towards|for|against)\b.{0,20}?\b(?:EMI|loan|instal+ment|LAN)\b""" +
                """|\breceived\s+(?:your\s+|the\s+)?(?:EMI|instal+ment|repayment|payment)\b""" +
                """|\bthank(?:s|\s+you)\s+for\s+(?:your\s+|the\s+|making\s+(?:the\s+)?)?(?:EMI\s+)?(?:re)?payment\b""",
        )
        val DISBURSED = rx("""\b(?:disbursed|disbursal|disbursement)\b""")

        private val EMI_AMOUNT = rx("""\b(?:EMI|instal+ment|repayment|payment)\s+(?:amount\s+)?(?:of\s+|:\s*|-\s*)?INR\s*($AMT)""")
        private val ANY_AMOUNT = rx("""\bINR\s*($AMT)""")
        /** Words before an amount that make it something other than the EMI. */
        private val NOT_EMI = rx("""outstanding|principal|balance|\bbal\b|\bPOS\b|loan\s+(?:of|amount)|sanction|disburs|limit|overdue|charges?\b""")
        private val OUTSTANDING = rx(
            """\b(?:outstanding|balance\s+principal)\b(?!\s+(?:EMI|dues?|instal))(?:\s+(?:principal|loan|amount|balance))*.{0,40}?\bINR\s*($AMT)""" +
                """|\bPOS\b\s*(?:is\s*|of\s*)?[:\-]?\s*INR\s*($AMT)""",
        )
        private val PRINCIPAL = rx(
            """\b(?:loan|loan\s+amount|sanctioned\s+amount)\s+(?:of\s+)?INR\s*($AMT)|\bINR\s*($AMT)\b.{0,40}?\bdisburs|\bdisburs\w*\b.{0,30}?\bINR\s*($AMT)""",
        )
        private const val NUMBER = """([A-Z0-9*]*\d[A-Z0-9]{2,})"""
        private val LOAN_NUMBER = rx(
            """\b(?:loan\s+(?:a/c|ac|acct|account|id|no|number)|LAN|agreement|contract)\b\.?\s*""" +
                """(?:(?:no|number|ending(?:\s+(?:with|in))?)\b\.?\s*)?[:\-#]?\s*$NUMBER""" +
                """|\b(?:for|towards|on|against)\s+(?:your\s+)?loan\s+$NUMBER""",
        )

        fun emiAmount(text: String): Money? {
            EMI_AMOUNT.find(text)?.let { m -> Money.parse(m.groupValues[1])?.let { return it } }
            for (m in ANY_AMOUNT.findAll(text)) {
                val before = text.substring(maxOf(0, m.range.first - 40), m.range.first)
                if (NOT_EMI.containsMatchIn(before)) continue
                Money.parse(m.groupValues[1])?.let { return it }
            }
            return null
        }

        fun outstanding(text: String): Long? = OUTSTANDING.find(text)?.let { m ->
            m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.let { Money.parse(it)?.minor }
        }

        fun principalOf(text: String): Long? = PRINCIPAL.find(text)?.let { m ->
            m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.let { Money.parse(it)?.minor }
        }

        /** The last 4 characters of the loan number: "XXXX1234" -> "1234", "PROP12345" -> "2345", "4XXXXX123" -> "123". */
        fun loanLast4(text: String): String? {
            val m = LOAN_NUMBER.find(text) ?: return null
            val number = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.uppercase()?.filter { it.isLetterOrDigit() } ?: return null
            val tail = number.takeLast(4)
            if ('X' !in tail) return tail
            return number.substringAfterLast('X').takeIf { it.length >= 3 }
        }
    }
}
