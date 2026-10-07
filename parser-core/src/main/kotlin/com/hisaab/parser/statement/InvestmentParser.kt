package com.hisaab.parser.statement

import com.hisaab.parser.ParserConfig
import com.hisaab.parser.extract.Money
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.registry.SenderKeys
import com.hisaab.parser.text.TextNormalizer
import com.hisaab.parser.text.rx
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** One NPS contribution credited to a PRAN, from a CRA's SMS or email ("Contribution of Rs.5000 for PRAN XXXX1234 credited"). */
data class NpsContribution(
    /** Last four digits of the PRAN. */
    val pranLast4: String,
    /** 1 for Tier I, 2 for Tier II. */
    val tier: Int,
    val amountMinor: Long,
    val date: LocalDate,
) {
    /** The NPS holding this contribution adds to: "NPS:1234" (Tier I) or "NPS:1234:T2". */
    val identifier: String get() = InvestmentParser.npsIdentifier(pranLast4, tier)

    /** Stable key, so the same contribution read twice (SMS and email, or a rescan) counts once. */
    val key: String get() = "nps|$pranLast4|$tier|$date|$amountMinor"
}

/**
 * Investment balances that arrive by SMS, such as EPFO's passbook balance and NPS holding values. These are not
 * bank transactions: an employer's PF contribution never touches the user's account. They update a holding instead.
 * NPS contribution messages from the CRAs (Protean, KFintech, CAMS) are read by [npsContribution].
 */
object InvestmentParser {
    // "Dear 10XXXXXX1234, your passbook balance against MH/BAN/0012345/000/0001234 is Rs. 1,23,456/-. Contribution of ..."
    private val EPF_BALANCE = rx("""passbook\s+balance\s+against\s+(\S+?)\s+is\s+INR\s*([\d,]+(?:\.\d{1,2})?)""")
    private val EPF_CONTRIBUTION = rx("""\bcontribution\s+of\s+INR\s*([\d,]+(?:\.\d{1,2})?).{0,80}?\b(?:received|credited|deposited)\b""")
    private val EPF_ALT = rx("""\b(?:EPF|PF)\s+(?:a/c\s+)?balance\s+(?:is|of)\s+INR\s*([\d,]+(?:\.\d{1,2})?)""")

    // "Your NPS a/c PRAN XXXXXXXX1234 holding value as on 30-09-2026 is Rs. 4,56,789.12"
    private val NPS_VALUE = rx("""\bPRAN\b[^0-9]{0,20}[x*\d]*?(\d{4})\b.{0,120}?\b(?:holding|balance|value|corpus)\b.{0,60}?\bINR\s*([\d,]+(?:\.\d{1,2})?)""")

    // "Total holding value Rs 4,56,789 as on 30-Sep-2026 for PRAN XXXXXXXX1234"
    private val NPS_VALUE_FIRST = rx("""\b(?:holding|corpus|portfolio|account)\s+(?:value|balance)\b.{0,40}?\bINR\s*([\d,]+(?:\.\d{1,2})?).{0,100}?\bPRAN\b[^0-9]{0,20}[x*\d]*?(\d{4})\b""")

    private val PRAN = rx("""\bPRAN\b(?:\s*(?:No\.?|Number))?[^0-9]{0,20}[x*\d]*?(\d{4})\b""")
    private val TIER_TWO = rx("""\bTier\s*[-:]?\s*(?:II|2)\b""")
    private val NPS_CONTEXT = rx("""\bNPS\b|\bPRAN\b|national\s+pension|\bCRA\b""")
    private val CONTRIBUTION = rx("""\bcontribution|\bunits?\s+(?:have\s+been\s+|are\s+|were\s+)?(?:allotted|alloted|allocated|credited)\b|\bcredited\s+(?:to|in|into)\s+(?:your\s+)?(?:NPS|PRAN|Tier)|\bD-?Remit\b""")
    private val CONTRIBUTION_DONE = rx("""\bcredited\b|\ballot+ed\b|\ballocated\b|\breceived\b|\binvested\b|\bprocessed\b|\bsuccessful(?:ly)?\b|\bupdated\b""")
    private val NOT_DONE = rx("""\b(?:failed|failure|rejected|unsuccessful|pending|reminder|due\s+(?:on|date)|not\s+been|could\s+not|will\s+be|returned|reversed)\b""")
    private val HOLDING_WORDS = rx("""\b(?:holding|corpus|portfolio)\s+(?:value|balance)\b|\btotal\s+value\b""")
    private val AMOUNT = rx("""\bINR\s*([\d,]+(?:\.\d{1,2})?)""")
    private val DATE_ON = rx("""\b(?:on|dated|date)\s*:?\s*(\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}|\d{1,2}[\s-][A-Za-z]{3,9}[\s,-]+\d{2,4})""")

    /** SMS senders whose messages are investment updates. Looked at before any body is read. */
    fun accepts(sender: String): Boolean = SenderKeys.smsHeader(sender).let {
        it.startsWith("EPFO") || it == "EPFIND" || it == "UMANGB" || it.contains("NPS") || it.contains("PRAN") ||
            it.startsWith("PFRDA") || it in NPS_HEADERS
    }

    /** CRA and regulator SMS headers without "NPS" in them: Protean (NSDL), KFintech and CAMS CRAs, PFRDA. */
    private val NPS_HEADERS = setOf("NSDLPR", "NSDLCR", "NSDLNP", "PROTEN", "PRTEAN", "PROTNC", "KFINTN", "KFNCRA", "KFCRA", "KCRA", "KCRANP", "CAMSNP", "CAMSCR")

    fun parse(body: String, sender: String, timestamp: Long): HoldingSnapshot? {
        if (!accepts(sender)) return null
        val text = TextNormalizer.normalize(body)
        epf(text, timestamp)?.let { return it }
        npsValue(text, timestamp)?.let { return it }
        EPF_ALT.find(text)?.let { m ->
            val value = Money.parse(m.groupValues[1])?.minor ?: return null
            return HoldingSnapshot(HoldingKind.EPF, "EPF", EPF_DEFAULT, null, value, null, timestamp, epfContribution(text))
        }
        return null
    }

    /**
     * An investment update in an email from EPFO, UMANG or an NPS CRA: the EPF passbook balance or an NPS holding value.
     * Any sender; the caller decides which emails to read.
     */
    fun parseEmail(body: String, timestamp: Long): HoldingSnapshot? {
        val text = TextNormalizer.normalize(body)
        return epf(text, timestamp) ?: npsValue(text, timestamp)
    }

    /** "your passbook balance against MH/BAN/0012345/000/0001234 is Rs. 1,23,456/-": keyed by the member id, separators dropped. */
    private fun epf(text: String, timestamp: Long): HoldingSnapshot? {
        val m = EPF_BALANCE.find(text) ?: return null
        val member = epfMemberKey(m.groupValues[1].trimEnd('.', ','))
        val value = Money.parse(m.groupValues[2])?.minor ?: return null
        return HoldingSnapshot(HoldingKind.EPF, "EPF ••${member.takeLast(4)}", "EPF:$member", null, value, null, timestamp, epfContribution(text))
    }

    /** The contribution an EPFO SMS reports beside the balance ("Contribution of Rs. 12,345/- for due month 082026 has been received"). */
    private fun epfContribution(text: String): Long? = EPF_CONTRIBUTION.find(text)?.let { Money.parse(it.groupValues[1])?.minor }?.takeIf { it > 0 }

    /** "MH/BAN/0012345/000/0001234" and "MHBAN00123450000001234" are one member id. */
    fun epfMemberKey(raw: String): String = raw.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT)

    /** The identifier of an EPF balance whose member id the message did not give. */
    const val EPF_DEFAULT = "EPF:default"

    /** An NPS holding value in an SMS or email body ("holding value ... is Rs. 4,56,789.12" for a PRAN). */
    fun npsValue(body: String, timestamp: Long): HoldingSnapshot? {
        val text = TextNormalizer.normalize(body)
        val (last4, amount) = NPS_VALUE.find(text)?.let { it.groupValues[1] to it.groupValues[2] }
            ?: NPS_VALUE_FIRST.find(text)?.let { it.groupValues[2] to it.groupValues[1] }
            ?: return null
        val value = Money.parse(amount)?.minor ?: return null
        val tier = if (TIER_TWO.containsMatchIn(text)) 2 else 1
        return HoldingSnapshot(HoldingKind.NPS, npsName(last4, tier), npsIdentifier(last4, tier), null, value, null, timestamp)
    }

    /**
     * An NPS contribution credited to a PRAN: "Contribution of Rs.5000.00 for PRAN XXXXXXXX1234 has been credited",
     * "Units for Rs 5000 contribution in PRAN 1100XXXX1234 Tier I have been allotted". Not for failed or pending ones,
     * and not for holding-value messages.
     */
    fun npsContribution(body: String, timestamp: Long, config: ParserConfig = ParserConfig()): NpsContribution? {
        val text = TextNormalizer.normalize(body)
        if (!NPS_CONTEXT.containsMatchIn(text) || !CONTRIBUTION.containsMatchIn(text) || !CONTRIBUTION_DONE.containsMatchIn(text)) return null
        if (NOT_DONE.containsMatchIn(text) || HOLDING_WORDS.containsMatchIn(text)) return null
        val last4 = PRAN.find(text)?.groupValues?.get(1) ?: return null
        val amount = AMOUNT.find(text)?.groupValues?.get(1)?.let { Money.parse(it)?.minor } ?: return null
        val received = Instant.ofEpochMilli(timestamp).atZone(config.zone).toLocalDate()
        val date = DATE_ON.find(text)?.groupValues?.get(1)?.let(::parseDate)
            ?.takeIf { !it.isAfter(received.plusDays(1)) && !it.isBefore(received.minusDays(45)) } ?: received
        return NpsContribution(last4, if (TIER_TWO.containsMatchIn(text)) 2 else 1, amount, date)
    }

    /** "NPS:1234" for Tier I, "NPS:1234:T2" for Tier II. */
    fun npsIdentifier(pranLast4: String, tier: Int): String = if (tier == 2) "NPS:$pranLast4:T2" else "NPS:$pranLast4"

    fun npsName(pranLast4: String, tier: Int): String = if (tier == 2) "NPS ••$pranLast4 Tier II" else "NPS ••$pranLast4"

    private fun parseDate(s: String): LocalDate? {
        val t = s.trim().replace(Regex("""\s+"""), " ").replace(",", "")
        for (f in DATE_FORMATS) runCatching { return LocalDate.parse(t, f) }
        return null
    }

    private fun fmt(p: String): DateTimeFormatter = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH)
    private val DATE_FORMATS = listOf(
        "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "d/M/uu", "d-M-uu", "d MMM uuuu", "d-MMM-uuuu", "d MMM uu", "d-MMM-uu", "d MMMM uuuu",
    ).map(::fmt)
}
