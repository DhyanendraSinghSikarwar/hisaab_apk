package com.hisaab.parser.text

/**
 * Produces the single canonical form every extractor works on:
 * one line, single spaces, no invisible characters, and every rupee spelling written as "INR ".
 */
object TextNormalizer {
    private const val ZERO_WIDTH = "​‌‍⁠﻿­"
    private const val ODD_SPACES = "   \t\u000B\u000C"
    private const val SENTENCE_END = ".!?:,;"
    private val CURRENCY = rx("""(?:₹|\bRs\.?|\bINR\.?)\s*[:.]?\s*(?=-?\d)""")

    // Where a bank email's footer begins: support numbers, disclaimers, and promos that would
    // otherwise trip the rejection rules or feed the extractors noise.
    private val EMAIL_FOOTER = rx(
        """\b(?:if you (?:did not|have not|haven't|do not)|if this (?:transaction|txn) (?:was|is) not|""" +
            """in case you have not|this is an? (?:system|computer|auto)|please do not reply|disclaimer|""" +
            """warm regards|regards,|thank you for banking|to unsubscribe|never share your|""" +
            """for any (?:queries|query|clarification))""",
    )
    private const val EMAIL_MAX_CHARS = 3000
    private const val EMAIL_MIN_BODY = 40

    fun normalize(raw: String): String {
        if (raw.isEmpty()) return raw
        // One pass: drop invisible characters, collapse runs of space, and turn a line break into
        // a sentence end, since most SMS templates use line breaks as field separators.
        val sb = StringBuilder(raw.length + 16)
        var space = false
        var lineBreak = false
        for (c in raw) {
            when {
                c == '\n' -> lineBreak = true
                c == '\r' || ZERO_WIDTH.indexOf(c) >= 0 -> Unit
                c == ' ' || ODD_SPACES.indexOf(c) >= 0 -> space = true
                else -> {
                    if (sb.isNotEmpty()) {
                        if (lineBreak) sb.append(if (SENTENCE_END.indexOf(sb[sb.length - 1]) >= 0) " " else ". ")
                        else if (space) sb.append(' ')
                    }
                    space = false
                    lineBreak = false
                    sb.append(c)
                }
            }
        }
        return CURRENCY.replace(sb, "INR ")
    }

    /** Cuts an already-normalized email body down to its transaction paragraph. */
    fun trimEmail(normalized: String): String {
        val capped = if (normalized.length > EMAIL_MAX_CHARS) normalized.substring(0, EMAIL_MAX_CHARS) else normalized
        val footer = EMAIL_FOOTER.findAll(capped).firstOrNull { it.range.first >= EMAIL_MIN_BODY }
        return if (footer == null) capped else capped.substring(0, footer.range.first).trim()
    }
}
