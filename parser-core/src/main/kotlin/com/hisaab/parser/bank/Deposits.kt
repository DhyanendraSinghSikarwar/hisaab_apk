package com.hisaab.parser.bank

import com.hisaab.parser.extract.Money
import com.hisaab.parser.text.TextNormalizer
import com.hisaab.parser.text.rx
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** A bank deposit account: fixed, recurring, or a Public Provident Fund account. */
enum class DepositKind { FD, RD, PPF }

/** What the bank does when a deposit matures. */
enum class DepositAction { PAYOUT, RENEW_PRINCIPAL, RENEW_ALL }

/**
 * What a bank's message or advice says about a deposit. Rides on a [LoanStatus] whose `outstandingMinor` is the
 * deposit's balance (an FD's principal, an RD's or PPF's running balance) and `principalMinor` the amount booked.
 */
data class DepositInfo(
    val kind: DepositKind,
    val maturity: LocalDate? = null,
    val maturityAmountMinor: Long? = null,
    /** Interest rate in basis points (7.10% = 710). */
    val rateBps: Int? = null,
    /** Renew or pay out on maturity; null when the message does not say. */
    val action: DepositAction? = null,
    /** Matured and paid out, or closed early: the deposit no longer exists. */
    val closed: Boolean = false,
)

/**
 * FD, RD and PPF messages from banks and India Post: booking and renewal advices ("FD No. XXXX5678 for INR 1,00,000
 * booked at 7.10% p.a., maturity 12-Mar-2027, maturity amount INR 1,07,281"), maturity and closure notes, RD
 * instalments and PPF deposits or interest with the running balance. Needs the deposit's own number; promotions
 * ("Book an FD at 7.5%") never name one.
 */
object DepositParser {
    private const val AMT = """(\d[\d,]*(?:\.\d{1,2})?)"""
    private const val NUM = """(?:a/c|ac|acct|account|receipt|rcpt)?\.?\s*(?:no\.?|number|ending(?:\s+(?:with|in))?)?\s*[:\-#]?\s*([Xx*\d][Xx*\d-]*\d)"""

    private val NUMBERED: List<Pair<DepositKind, Regex>> = listOf(
        DepositKind.PPF to rx("""(?:\bPPF\b|public\s+provident\s+fund)\s*$NUM"""),
        DepositKind.RD to rx("""(?:\bRD\b|recurring\s+deposit)\s*$NUM"""),
        DepositKind.FD to rx("""(?:\bFDR?\b|fixed\s+deposit|term\s+deposit|\bTD\b|\bSDR\b|\bMIS\b)\s*$NUM"""),
    )
    private val QUICK = rx("""\bPPF\b|provident\s+fund|\bRD\b|recurring\s+deposit|\bFDR?\b|fixed\s+deposit|term\s+deposit|\bTD\b|\bSDR\b|\bMIS\b""")
    private val CONTEXT = rx(
        """\b(?:booked|opened|created|placed|renewed|renewal|matur\w*|bal(?:ance)?|interest|instal+ment|deposited|credited|received|closed|closure|liquidated)\b""",
    )
    private val NOT_STATUS = rx("""\b(?:OTP|one\s+time\s+password|failed|unsuccessful|declined|offer|book\s+now|apply\s+now|earn\s+up\s+to|loan\s+against|overdraft)\b""")

    private val MATURITY_AMOUNT = rx(
        """\bmatur\w*\s+(?:amount|value|proceeds)\s*(?:\(\s*INR\s*\))?\s*(?:of|is|:|-|will\s+be|would\s+be)?\s*:?\s*(?:INR\s*)?$AMT|\bINR\s*$AMT\s+(?:on|at)\s+maturity""",
    )
    private val BALANCE = rx("""\b(?:total\s+|available\s+|avl\.?\s+|clear\s+|current\s+|closing\s+|updated\s+)?bal(?:ance)?\.?\s*(?:is|of|:|-|now)?\s*:?\s*INR\s*$AMT""")
    private val ANY_AMOUNT = rx("""\bINR\s*$AMT""")
    /** Words before an amount that make it something other than the amount deposited. */
    private val NOT_PRINCIPAL = rx("""matur\w*|interest|\bbal(?:ance)?\b|proceeds|limit|penalty|charges?""")
    private const val DATE = """(\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}|\d{4}-\d{2}-\d{2}|\d{1,2}(?:st|nd|rd|th)?[\s-][A-Za-z]{3,9}[\s,-]+\d{2,4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{4})"""
    private val MATURITY_DATE = rx(
        """\bmatur(?:ity|es|ing|ed|e)?\b(?:\s+date)?\s*(?:is|on|:|-|will\s+be|due)?\s*(?:on\s+)?:?\s*$DATE|\bdue\s+(?:for\s+maturity\s+)?on\s+$DATE|\bmaturity\s+date\s*[:\-]?\s*$DATE""",
    )
    private val RATE = rx("""(?:@|\bat\b|\brate\b(?:\s+of\s+interest)?|\bROI\b|\binterest\s+rate\b)\s*(?:of|is|:)?\s*(\d{1,2}(?:\.\d{1,2})?)\s*%""")
    private val NOT_RENEWED = rx("""\bnot\s+(?:be\s+)?(?:auto[\s-]?)?renew\w*""")
    private val RENEW = rx("""\bauto[\s-]?renew\w*|\brenew(?:ed|al|s)?\b""")
    private val PAYOUT = rx(
        """\bauto[\s-]?clos\w*|\bpay\s*-?\s*out\b|\bpaid\s+out\b|\bclosure\s+on\s+maturity|\bcredited\s+to\s+(?:your\s+)?(?:savings\s+|linked\s+|SB\s+)?(?:a/c|account)\b.{0,30}\bon\s+maturity|\bon\s+maturity\b.{0,60}\bwill\s+be\s+credited""" +
            """|\bmaturity\s+instructions?\s*[:\-]?\s*(?:credit|pay|close|closure|transfer)""",
    )
    private val INTEREST_OUT = rx("""\binterest\b.{0,40}\b(?:paid|credited|payout)\b|\bprincipal\s+(?:only\s+)?renew""")
    private val CLOSED = rx(
        """\b(?:has|have|was|is)\s+(?:been\s+)?(?:matured|closed|prematurely\s+closed|liquidated|redeemed)\b|\bmatured\b.{0,80}\b(?:credited|paid|proceeds)\b""" +
            """|\bproceeds\b(?:(?!\bwill\b).){0,60}\bcredited\b|\bpremature(?:ly)?\s+(?:closed|closure|withdrawal|withdrawn)\b""",
    )
    private val PPF_CLOSED = rx("""\b(?:account|a/c)\s+(?:has\s+been\s+|is\s+)?closed\b""")
    private val INDIA_POST = rx("""^(?:DOP|IPPB|INDPST|INDPOS|POSTAL)""")

    /** True when [text] might be about a deposit at all; a cheap first look. */
    fun mentionsDeposit(text: String): Boolean = QUICK.containsMatchIn(text)

    /**
     * The deposit status in [body] from [bank], or null when it names no deposit number, or is an OTP, offer or failure.
     * [timestamp] is when the bank sent it.
     */
    fun status(body: String, bank: String, timestamp: Long): LoanStatus? {
        val text = TextNormalizer.normalize(body)
        if (!QUICK.containsMatchIn(text) || NOT_STATUS.containsMatchIn(text) || !CONTEXT.containsMatchIn(text)) return null
        val (kind, last4) = numbered(text) ?: return null
        val info = info(text, kind)
        val principal = principal(text)
        val balance = BALANCE.find(text)?.let(::firstAmount)
        // An FD's balance is its principal; an RD's or PPF's instalment is not its balance.
        val outstanding = when {
            info.closed -> null
            balance != null -> balance
            kind == DepositKind.FD -> principal
            else -> null
        }
        return LoanStatus(nameOf(bank), last4, principal, outstanding, timestamp, deposit = info)
    }

    /**
     * True when [text] is about the deposit account itself (a booking advice, an RD instalment or PPF deposit received
     * on [accountLast4]), so it is not a transaction on a bank account: the money already left the savings account in
     * its own message. A debit from a savings account "towards RD XX9012" names a different account and stays.
     */
    fun aboutDepositOnly(text: String, accountLast4: String?): Boolean {
        if (!QUICK.containsMatchIn(text)) return false
        val (_, last4) = numbered(TextNormalizer.normalize(text)) ?: return false
        return accountLast4 == null || accountLast4 == last4
    }

    /** The terms in a deposit advice or statement; the kind is whatever the caller knows it to be. */
    fun info(text: String, kind: DepositKind): DepositInfo {
        val maturity = MATURITY_DATE.find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.let(::parseDate)
        val maturityAmount = MATURITY_AMOUNT.find(text)?.let(::firstAmount)
        val rate = RATE.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it in 0.5..20.0 }?.let { Math.round(it * 100).toInt() }
        val renewed = !NOT_RENEWED.containsMatchIn(text) && RENEW.containsMatchIn(text)
        val action = when {
            renewed && INTEREST_OUT.containsMatchIn(text) -> DepositAction.RENEW_PRINCIPAL
            renewed -> DepositAction.RENEW_ALL
            NOT_RENEWED.containsMatchIn(text) || PAYOUT.containsMatchIn(text) -> DepositAction.PAYOUT
            else -> null
        }
        val closed = !renewed && kind != DepositKind.PPF && CLOSED.containsMatchIn(text) ||
            kind == DepositKind.PPF && PPF_CLOSED.containsMatchIn(text)
        return DepositInfo(kind, maturity, maturityAmount, rate, action, closed)
    }

    /** The first deposit number in [text] and its kind; the last four digits name the account. */
    fun numbered(text: String): Pair<DepositKind, String>? {
        val hit = NUMBERED.mapNotNull { (k, r) -> r.find(text)?.let { Triple(k, it, it.groupValues[1]) } }
            .filter { it.third.count(Char::isDigit) >= 3 }
            .minByOrNull { it.second.range.first } ?: return null
        val digits = hit.third.filter { it.isLetterOrDigit() }
        val tail = digits.takeLast(4)
        val last4 = if (tail.any { it == 'X' || it == 'x' }) digits.substringAfterLast('X').substringAfterLast('x').takeIf { it.length >= 3 } ?: return null else tail
        return hit.first to last4
    }

    /** "DOPBNK" (the generic parser's name for India Post's sender) reads as India Post. */
    private fun nameOf(bank: String): String = if (INDIA_POST.containsMatchIn(bank.uppercase(Locale.ROOT))) "India Post" else bank

    /** The first amount not labelled as the maturity value, interest or a balance. */
    private fun principal(text: String): Long? = ANY_AMOUNT.findAll(text).firstOrNull { m ->
        !NOT_PRINCIPAL.containsMatchIn(text.substring(maxOf(0, m.range.first - 30), m.range.first))
    }?.let(::firstAmount)

    private fun firstAmount(m: MatchResult): Long? = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.let { Money.parse(it)?.minor }

    fun parseDate(s: String): LocalDate? {
        val t = s.trim().replace(Regex("""\s+"""), " ").replace(",", "").replace(Regex("""(\d)(?:st|nd|rd|th)\b"""), "$1")
        for (f in DATE_FORMATS) runCatching { return LocalDate.parse(t, f) }
        return null
    }

    private fun fmt(p: String): DateTimeFormatter = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH)
    private val DATE_FORMATS = listOf(
        "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "d/M/uu", "d-M-uu", "uuuu-MM-dd",
        "d MMM uuuu", "d-MMM-uuuu", "d MMM uu", "d-MMM-uu", "d MMMM uuuu", "d-MMMM-uuuu", "MMM d uuuu", "MMMM d uuuu",
    ).map(::fmt)
}
