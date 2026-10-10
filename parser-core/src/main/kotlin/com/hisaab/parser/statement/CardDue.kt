package com.hisaab.parser.statement

import com.hisaab.parser.text.rx
import java.time.LocalDate
import java.time.ZoneId

/**
 * What a card issuer's "statement generated / bill due" message (SMS or email) says about a credit card, or, when only
 * [paymentMinor] is set, a payment it received. Not a transaction.
 */
data class CardDue(
    val bank: String,
    val last4: String,
    val totalDueMinor: Long?,
    val minDueMinor: Long?,
    val dueEpochDay: Long?,
    val statementEpochDay: Long? = null,
    /** A payment the issuer acknowledged ("payment of Rs 5,000 received"); then the due figures are null. */
    val paymentMinor: Long? = null,
    /** When the message was sent. */
    val at: Long = 0L,
)

/** Dates and amounts as card statements and messages print them. */
internal object CardFigures {
    private const val MON = """(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"""

    /** 12/10/2026, 12-10-26, 2026-10-12, 12-Oct-2026, 12 October 2026, 12 Oct, October 12, 2026. */
    const val DATE_PATTERN = """(?<!\d)(?:\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}|\d{4}-\d{2}-\d{2}|""" +
        """\d{1,2}(?:st|nd|rd|th)?[\s-]+$MON\b\.?(?:[\s,-]+\d{4}\b|-\d{2}\b)?|$MON\b\.?\s+\d{1,2}(?:st|nd|rd|th)?,?\s+\d{4})"""
    val DATE = rx(DATE_PATTERN)

    /** An amount with optional currency and trailing Cr/Dr: group 1 after a currency sign, group 2 with commas or paise, group 3 Cr/Dr. */
    val AMOUNT = rx(
        """(?:(?:\b(?:rs\.?|inr)|₹|`)\s*(\d+(?:,\d{2,3})*(?:\.\d{1,2})?)|(?<![\d,.])(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+\.\d{1,2})(?![\d]|\.\d))""" +
            """(?:\s*\b(cr|dr)\b)?""",
    )

    /** An amount and whether it is a credit (Cr). */
    data class Amount(val minor: Long, val credit: Boolean)

    fun amounts(s: String): List<Amount> = AMOUNT.findAll(s).mapNotNull { m ->
        val n = m.groupValues[1].ifEmpty { m.groupValues[2] }
        minor(n)?.let { Amount(it, m.groupValues[3].equals("cr", ignoreCase = true)) }
    }.toList()

    fun minor(number: String): Long? = number.replace(",", "").trimEnd('.').toBigDecimalOrNull()
        ?.movePointRight(2)?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()?.takeIf { it in 0..1_000_000_000_000L }

    private val NUMERIC = Regex("""^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})$""")
    private val ISO = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val DAY_FIRST = Regex("""^(\d{1,2})(?:st|nd|rd|th)?[\s-]+([a-z]+)\.?(?:[\s,-]+(\d{2,4}))?$""", RegexOption.IGNORE_CASE)
    private val MONTH_FIRST = Regex("""^([a-z]+)\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})$""", RegexOption.IGNORE_CASE)
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private fun month(name: String): Int? = MONTHS.indexOf(name.take(3).lowercase()).takeIf { it >= 0 }?.plus(1)

    private fun year(y: String): Int = y.toInt().let { if (y.length <= 2) 2000 + it else it }

    /**
     * Reads a date in any of the forms above. A date without a year takes the year of [ref] (today when null): a future
     * date ([forward], a due date) is moved to next year when it would fall well before [ref], a past date (a statement
     * date) to last year when it would fall well after.
     */
    fun parse(s: String, ref: LocalDate? = null, forward: Boolean = true): LocalDate? = runCatching {
        val t = s.trim().replace(Regex("""\s+"""), " ")
        NUMERIC.find(t)?.let { return LocalDate.of(year(it.groupValues[3]), it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        ISO.find(t)?.let { return LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
        MONTH_FIRST.find(t)?.let { m ->
            val mo = month(m.groupValues[1]) ?: return null
            return LocalDate.of(m.groupValues[3].toInt(), mo, m.groupValues[2].toInt())
        }
        val m = DAY_FIRST.find(t) ?: return null
        val mo = month(m.groupValues[2]) ?: return null
        val y = m.groupValues[3]
        if (y.isNotEmpty()) return LocalDate.of(year(y), mo, m.groupValues[1].toInt())
        val base = ref ?: LocalDate.now()
        val d = LocalDate.of(base.year, mo, minOf(m.groupValues[1].toInt(), java.time.YearMonth.of(base.year, mo).lengthOfMonth()))
        when {
            forward && d.isBefore(base.minusDays(30)) -> d.plusYears(1)
            !forward && d.isAfter(base.plusDays(30)) -> d.minusYears(1)
            else -> d
        }
    }.getOrNull()
}

/** Reads card due and payment messages. See [CardDue]. */
object CardDueParser {
    private const val DATE = CardFigures.DATE_PATTERN
    private const val CCY = """(?:\b(?:rs\.?|inr)|₹)"""
    private const val AMT = """(\d[\d,]*(?:\.\d{1,2})?)"""
    /** A number after a currency sign, or one with paise or thousands separators: never a bare "02" from a date. */
    private const val NUMX = """(?:$CCY\s*(?=\d)|(?=\d[\d,]*\.\d|\d{1,3},\d{2,3}))$AMT"""

    private val CARD = rx("""\bcards?\b""")
    private val LAST4 = rx("""(?:[x*]{2,}[\s-]?|ending\s+(?:with\s+|in\s+|no\.?\s*)?|\bno\.?\s*|\bnumber\s+)(\d{4})\b""")
    private val MIN = rx("""min(?:imum)?\.?(?:\s+(?:amt|amount|payment))?\.?\s+(?:due|payable)\b[^\d]{0,25}?$NUMX""")
    private val TOTAL = rx(
        """(?:total\s+(?:(?:amount|amt|payment)\.?\s+)?(?:due|payable|outstanding)|(?:total\s+)?amount\s+(?:due|payable)|""" +
            """total\s+dues?|new\s+balance|outstanding\s+(?:balance|amount)|bill\s+amount)\b[^\d]{0,25}?$NUMX""",
    )
    private val DUE_DATE = rx("""(?:due\s+(?:date|on|by)|pay(?:ment)?\s+(?:by|before)|payable\s+by|on\s+or\s+before)\s*(?:is|:|-)?\s*($DATE)""")
    private val STATEMENT_DATE = rx("""(?:statement|bill)\s+(?:dated|date|generated\s+on|generated\s+for|of)\s*(?:is|:|-)?\s*($DATE)""")
    private val STATEMENT_WORD = rx("""\b(?:statement|bill|due)\b""")
    private val FAILED = rx("""\b(?:fail(?:ed|ure)?|declin\w+|unsuccessful|not\s+successful|reversed|reject\w*)\b""")
    private val PAYMENT = listOf(
        rx("""payment\s+of\s+$CCY\s*$AMT[^.]{0,80}?\b(?:received|credited|posted|successful|made|paid)\b"""),
        rx("""(?:received|credited|posted|thank\s+you\s+for)\s+(?:your\s+)?payment\s+of\s+$CCY\s*$AMT"""),
        rx("""$CCY\s*$AMT\s+(?:has\s+been\s+|was\s+)?(?:received|credited)\s+(?:towards|against|on|in)\b[^.]{0,40}card"""),
    )
    private val BANKS = listOf(
        rx("""\bHDFC\b""") to "HDFC Bank", rx("""\bICICI\b""") to "ICICI Bank", rx("""\bSBI\b|state\s+bank""") to "SBI",
        rx("""\bAxis\b""") to "Axis Bank", rx("""\bKotak\b""") to "Kotak Mahindra Bank", rx("""\bIDFC\b""") to "IDFC FIRST Bank",
        rx("""\bYes\s+Bank\b""") to "Yes Bank", rx("""\bIndusInd\b""") to "IndusInd Bank", rx("""\bAmerican\s+Express\b|\bAmex\b""") to "American Express",
        rx("""\bHSBC\b""") to "HSBC", rx("""\bRBL\b""") to "RBL Bank", rx("""\bAU\s+(?:Small\s+Finance\s+)?Bank\b""") to "AU Small Finance Bank",
        rx("""\bFederal\s+Bank\b""") to "Federal Bank", rx("""\bStandard\s+Chartered\b""") to "Standard Chartered",
    )

    /** The issuer named in [text], or null. */
    fun bankIn(text: String): String? = BANKS.firstOrNull { it.first.containsMatchIn(text) }?.second

    /**
     * The card figures in [body], or null when it is not about a credit card's bill or payment. [bank] is the issuer the
     * sender belongs to; when null it is read from the text.
     */
    fun parse(body: String, bank: String?, timestamp: Long, zone: ZoneId = ZoneId.of("Asia/Kolkata")): CardDue? {
        val text = body.replace(Regex("""[\s ]+"""), " ").trim()
        if (!CARD.containsMatchIn(text)) return null
        val last4 = LAST4.find(text)?.groupValues?.get(1) ?: return null
        val issuer = bank ?: bankIn(text) ?: return null
        val received = java.time.Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

        val minMatch = MIN.find(text)
        val min = minMatch?.groupValues?.get(1)?.let(CardFigures::minor)
        // The minimum's own words must not be read as the total ("Minimum Amount Due").
        val rest = minMatch?.let { text.replaceRange(it.range, " ".repeat(it.range.last - it.range.first + 1)) } ?: text
        val total = TOTAL.find(rest)?.groupValues?.get(1)?.let(CardFigures::minor)
        val stmt = STATEMENT_DATE.find(text)?.groupValues?.get(1)?.let { CardFigures.parse(it, received, forward = false) }
        val due = DUE_DATE.find(text)?.groupValues?.get(1)?.let { CardFigures.parse(it, stmt ?: received, forward = true) }

        if ((total != null || min != null) && STATEMENT_WORD.containsMatchIn(text) && (total != null || due != null)) {
            return CardDue(issuer, last4, total, min, due?.toEpochDay(), stmt?.toEpochDay(), at = timestamp)
        }
        if (FAILED.containsMatchIn(text)) return null
        for (p in PAYMENT) {
            val paid = p.find(text)?.groupValues?.get(1)?.let(CardFigures::minor)?.takeIf { it > 0 } ?: continue
            return CardDue(issuer, last4, null, null, null, paymentMinor = paid, at = timestamp)
        }
        return null
    }
}
