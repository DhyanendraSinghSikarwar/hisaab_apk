package com.hisaab.parser.statement

import com.hisaab.parser.ParserConfig
import com.hisaab.parser.extract.Money
import com.hisaab.parser.text.rx
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** One mutual fund purchase (SIP instalment or lump sum) confirmed by an investing app or the fund's registrar. */
data class MfOrder(
    /** The app or registrar that sent it: Groww, Zerodha Coin, CAMS... */
    val platform: String,
    val schemeName: String,
    val folio: String?,
    /** Amount invested, in paise. */
    val amountMinor: Long?,
    val units: Double?,
    /** NAV per unit, in rupees. */
    val nav: Double?,
    val date: LocalDate,
    val isSip: Boolean,
) {
    /** Holding identifier for a fund known only from these emails: "MF:" + the normalised scheme name. */
    val identifier: String get() = MfOrderParser.identifierFor(schemeName)
}

/**
 * "SIP instalment processed", "Order successful", "Units allotted" emails from Groww, Zerodha Coin, Kuvera,
 * INDmoney, Paytm Money, ET Money, and CAMS/KFintech transaction confirmations. Reads the scheme, amount,
 * units, NAV and date. Reminders, failed or cancelled orders, and redemptions are not purchases and are ignored.
 */
class MfOrderParser(private val config: ParserConfig = ParserConfig()) {

    fun parse(text: String, subject: String?, sender: String, receivedAt: Long): MfOrder? {
        val body = normalise(listOfNotNull(subject, text).joinToString("\n"))
        val flat = body.replace('\n', ' ')
        if (!ABOUT_FUNDS.containsMatchIn(flat)) return null
        if (!PURCHASE.containsMatchIn(flat)) return null
        if (NOT_DONE.containsMatchIn(flat) || REDEMPTION.containsMatchIn(flat)) return null
        if (!DONE.containsMatchIn(flat)) return null

        val scheme = scheme(body) ?: return null
        val units = UNITS_LABEL.find(flat)?.groupValues?.get(1)?.let(::num) ?: UNITS_AFTER.find(flat)?.groupValues?.get(1)?.let(::num)
        val nav = NAV.find(flat)?.groupValues?.get(1)?.let(::num)?.takeIf { it > 0 }
        val amount = (AMOUNT_LABEL.find(flat) ?: AMOUNT_OF.find(flat) ?: AMOUNT_ANY.find(flat))
            ?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.let { Money.parse(it)?.minor }
            ?: if (units != null && nav != null) Math.round(units * nav * 100) else null
        if (amount == null && units == null) return null
        val received = Instant.ofEpochMilli(receivedAt).atZone(config.zone).toLocalDate()
        val date = (DATE_LABELLED.find(flat)?.groupValues?.get(1) ?: DATE_ON.find(flat)?.groupValues?.get(1))?.let(::parseDate)
            ?.takeIf { !it.isAfter(received.plusDays(1)) && !it.isBefore(received.minusDays(45)) } ?: received
        val folio = FOLIO.find(flat)?.groupValues?.get(1)?.trimEnd('/', '.')
        return MfOrder(platformOf(sender, flat), scheme, folio, amount, units?.takeIf { it > 0 }, nav, date, SIP.containsMatchIn(flat))
    }

    private fun scheme(body: String): String? {
        for (line in body.lines()) {
            SCHEME_LABEL.find(line)?.let { m -> clean(m.groupValues[1])?.let { return it } }
        }
        val flat = body.replace('\n', ' ')
        for (rx in SCHEME_SENTENCES) {
            for (m in rx.findAll(flat)) clean(m.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun clean(raw: String): String? {
        val s = raw.replace(TRAILING_JUNK, "").replace(Regex("""\s+"""), " ").trim().trim('-', ',', '.', ':', '|', ' ', '"', '\'')
            .replace(LEADING_JUNK, "")
        if (s.length < 6 || s.length > 120) return null
        if (!SCHEME_WORD.containsMatchIn(s)) return null
        return s
    }

    private fun parseDate(s: String): LocalDate? {
        val t = s.trim().replace(Regex("""\s+"""), " ").replace(",", "").replace(Regex("""(\d)(?:st|nd|rd|th)\b"""), "$1")
        for (f in DATE_FORMATS) runCatching { return LocalDate.parse(t, f) }
        return null
    }

    companion object {
        /** Scheme names to one key: lower case, no punctuation, without "direct", "plan", "growth", "option", "fund". */
        fun normaliseScheme(name: String): String = name.lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .split(' ').filter { it.isNotEmpty() && it !in STOP_WORDS }
            .joinToString(" ")

        fun identifierFor(schemeName: String): String = "MF:" + normaliseScheme(schemeName)

        private val STOP_WORDS = setOf("direct", "dir", "plan", "growth", "gr", "g", "option", "opt", "fund", "the", "scheme", "dp", "gw", "mf", "mutual")

        private fun num(s: String): Double? = s.replace(",", "").toDoubleOrNull()

        private fun normalise(s: String): String = s
            .replace(' ', ' ')
            .replace(Regex("""(?:₹|\bINR\b\.?|\bRs\b\.?)\s*""", RegexOption.IGNORE_CASE), "Rs. ")
            .lines().joinToString("\n") { it.replace(Regex("""[ \t]+"""), " ").trim() }

        private const val AMT = """([\d,]+(?:\.\d{1,2})?)(?![\d.])"""
        private const val DEC = """([\d,]*\d\.\d+|\d[\d,]*)"""
        private const val D = """(\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}|\d{4}-\d{2}-\d{2}|\d{1,2}(?:st|nd|rd|th)?[\s-][A-Za-z]{3,9}[\s,-]+\d{2,4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{4})"""

        private val ABOUT_FUNDS = rx("""\bmutual\s+funds?\b|\bfund\b|\bSIP\b|\bNAV\b|\bfolio\b|\bunits?\b""")
        private val PURCHASE = rx("""\bSIP\b|systematic\s+investment|\bpurchase\b|\binvest(?:ment|ed)?\b|\border\b|\bunits?\s+(?:have\s+been\s+|are\s+|were\s+)?(?:allotted|alloted|allocated|credited)\b|\ballotment\b|\blumpsum\b""")
        private val DONE = rx("""\bsuccess(?:ful(?:ly)?)?\b|\bprocessed\b|\ballot+ed\b|\ballotment\b|\ballocated\b|\bconfirm(?:ed|ation)\b|\bexecuted\b|\bcompleted\b|\bplaced\b|\bcredited\b|\binvested\b""")
        private val NOT_DONE = rx("""\b(?:failed|failure|unsuccessful|rejected|cancel+ed|cancel+ation|could\s+not|couldn't|insufficient|bounced?|skipped|paused|reminder|upcoming|due\s+(?:on|tomorrow|today)|will\s+be\s+(?:debited|deducted)|scheduled\s+for|mandate\s+(?:registration|setup|approved))\b""")
        private val REDEMPTION = rx("""\bredeem(?:ed)?\b|\bredemption\b|\bwithdrawal\b|\bswitch[\s-]?out\b|\bSWP\b|\bsell\s+order\b""")
        private val SIP = rx("""\bSIP\b|systematic\s+investment""")

        private val SCHEME_LABEL = rx("""(?:(?:^|[|,;]\s*)(?:scheme|fund)(?:\s+name)?\s*[:\-]|\b(?:scheme|fund)\s+name\s*[:\-]?)\s*(.+?)(?=\s+(?:folio|amount|units?|nav|transaction|txn|date|order|plan\s+type|option\s*:)\b|\s*[|;]|$)""")
        private val SCHEME_SENTENCES = listOf(
            // "SIP instalment for Parag Parikh Flexi Cap Fund Direct Growth has been processed"
            rx("""(?:\bSIP\b(?:\s+instal+ments?)?|\binvestment|\border|\bpurchase|\binvested)(?:\s+of\s+Rs\.\s*[\d,]+(?:\.\d+)?)?\s+(?:for|in|into)\s+(?:the\s+)?(.+?)(?=\s+(?:has|have|is|was|were|under|with|on|at|via|through|of\s+Rs|for\s+Rs|folio)\b|\s*[(,;!]|\.\s|\.$|$)"""),
            // "Units allotted in Axis Bluechip Fund - Direct Growth"
            rx("""\bunits?\s+(?:of|in)\s+(.+?)(?=\s+(?:has|have|is|was|were|under|with|on|at|folio)\b|\s*[(,;!]|\.\s|\.$|$)"""),
        )
        private val SCHEME_WORD = rx("""\b(?:fund|funds|plan|growth|ETF|FoF|IDCW|index|scheme|bees)\b""")
        private val TRAILING_JUNK = rx("""\s*(?:-\s*)?(?:folio.*|\(.*)$""")
        private val LEADING_JUNK = rx("""^(?:your|the|a|an)\s+""")

        private val UNITS_LABEL = rx("""\bunits?(?:\s+(?:allotted|alloted|allocated|credited|purchased|bought))?\s*(?:\(\s*no\.?\s*\))?\s*[:\-]?\s*$DEC""")
        private val UNITS_AFTER = rx("""$DEC\s+units?\b""")
        private val NAV = rx("""\bNAV\b(?:\s*\(\s*Rs\.\s*\))?(?:\s+(?:per\s+unit|of|at|is|was|applied|allotted))*\s*[:\-]?\s*(?:Rs\.\s*)?([\d,]*\d\.\d+|\d[\d,]*)""")
        private val AMOUNT_LABEL = rx("""\b(?:investment|sip|order|purchase|instal+ment|transaction|gross|net)?\s*amount(?:\s+(?:invested|paid|debited))?\s*(?:\(\s*Rs\.\s*\))?\s*[:\-]?\s*(?:Rs\.\s*)$AMT|\bamount(?:\s+(?:invested|paid))?\s*(?:\(\s*Rs\.\s*\))?\s*[:\-]\s*(?:Rs\.\s*)?$AMT""")
        private val AMOUNT_OF = rx("""\b(?:of|for|worth)\s+Rs\.\s*$AMT""")
        private val AMOUNT_ANY = rx("""Rs\.\s*$AMT(?!\s*(?:per\s+unit|/unit))""")
        private val FOLIO = rx("""\bfolio(?:\s+(?:no\.?|number|#))?\s*[:\-]?\s*([\d/]{5,})""")
        private val DATE_LABELLED = rx("""\b(?:allotment|nav|transaction|trade|order|txn|investment|purchase|processing|instal+ment|sip)\s+date\s*[:\-]?\s*$D""")
        private val DATE_ON = rx("""\b(?:on|dated)\s+$D""")

        private fun fmt(p: String): DateTimeFormatter = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH)
        private val DATE_FORMATS = listOf(
            "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "d/M/uu", "d-M-uu", "uuuu-MM-dd",
            "d MMM uuuu", "d-MMM-uuuu", "d MMM uu", "d-MMM-uu", "d MMMM uuuu", "d-MMMM-uuuu", "MMM d uuuu", "MMMM d uuuu",
        ).map(::fmt)

        private val PLATFORMS = listOf(
            "groww" to "Groww", "zerodha" to "Zerodha Coin", "kuvera" to "Kuvera", "indmoney" to "INDmoney", "paytmmoney" to "Paytm Money",
            "paytm money" to "Paytm Money", "etmoney" to "ET Money", "et money" to "ET Money", "camsonline" to "CAMS", "kfintech" to "KFintech",
            "karvy" to "KFintech", "upstox" to "Upstox", "angelone" to "Angel One", "dhan" to "Dhan", "mfcentral" to "MF Central",
            "bsestarmf" to "BSE StAR MF", "mfuindia" to "MF Utility", "scripbox" to "Scripbox", "fisdom" to "Fisdom",
        )

        fun platformOf(sender: String, text: String = ""): String {
            val s = sender.lowercase(Locale.ROOT)
            PLATFORMS.firstOrNull { s.contains(it.first) }?.let { return it.second }
            val t = text.lowercase(Locale.ROOT)
            PLATFORMS.firstOrNull { t.contains(it.first) }?.let { return it.second }
            return sender.substringBefore('<').trim().trim('"').ifEmpty { "Mutual fund" }
        }

        /** Domains these confirmations come from, for the email sender whitelist. */
        val SENDER_DOMAINS = listOf(
            "groww.in", "zerodha.com", "zerodha.net", "kuvera.in", "indmoney.com", "paytmmoney.com", "etmoney.com", "camsonline.com",
            "kfintech.com", "mfcentral.com", "bsestarmf.in", "mfuindia.com", "scripbox.com", "fisdom.com",
        )
    }
}
