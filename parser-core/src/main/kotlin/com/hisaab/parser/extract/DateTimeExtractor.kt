package com.hisaab.parser.extract

import com.hisaab.parser.text.Guarded
import com.hisaab.parser.text.rx
import java.time.LocalDate
import java.time.LocalTime

data class DateTimeParts(val date: LocalDate?, val time: LocalTime?)

/** Understands the date spellings Indian banks use: 25/09/26, 25-Sep-2026, 25Sep26, 2026-09-25:10:15:32, Sep 25, 2026. */
object DateTimeExtractor {
    private const val MONTHS = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"
    private val MONTH_NAMES = MONTHS.split('|').toTypedArray()
    private val ISO = rx("""\b(20\d{2})[-/:](\d{1,2})[-/:](\d{1,2})(?:[\sT:]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
    private val DMY = rx("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})\b""")
    private val D_MON_Y = Guarded("""\b(\d{1,2})[-\s/]?($MONTHS)[a-z]*\.?[-\s/,]*(\d{4}|\d{2})\b""", *MONTH_NAMES)
    private val MON_D_Y = Guarded("""\b($MONTHS)[a-z]*\.?\s+(\d{1,2}),?\s+(\d{4})\b""", *MONTH_NAMES)
    private val TIME = rx("""\b([01]?\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?(?:\s*([ap])\.?m\b\.?)?""")
    private const val TIME_NEAR_DATE = 30

    private val MONTH_INDEX: Map<String, Int> =
        MONTHS.split('|').withIndex().associate { (i, m) -> m to i + 1 }

    fun extract(text: String, lower: String = text.lowercase()): DateTimeParts {
        // The earliest date wins, so each later pattern only searches the text before the best match so far.
        var scope = text
        var bestEnd = 0
        var date: LocalDate? = null
        var isoTime: LocalTime? = null

        fun take(m: MatchResult, d: LocalDate?, time: LocalTime?) {
            if (d == null) return
            date = d
            isoTime = time
            bestEnd = m.range.last + 1
            scope = text.substring(0, m.range.first)
        }

        ISO.find(scope)?.let { m ->
            val time = if (m.groupValues[4].isNotEmpty()) timeOf(m.groupValues[4], m.groupValues[5], m.groupValues[6], "") else null
            take(m, dateOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()), time)
        }
        DMY.find(scope)?.let { m -> take(m, dateOf(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt()), null) }
        if (D_MON_Y.allows(lower)) D_MON_Y.regex.find(scope)?.let { m ->
            take(m, dateOf(year(m.groupValues[3]), MONTH_INDEX.getValue(m.groupValues[2].lowercase()), m.groupValues[1].toInt()), null)
        }
        if (MON_D_Y.allows(lower)) MON_D_Y.regex.find(scope)?.let { m ->
            take(m, dateOf(m.groupValues[3].toInt(), MONTH_INDEX.getValue(m.groupValues[1].lowercase()), m.groupValues[2].toInt()), null)
        }

        if (isoTime != null) return DateTimeParts(date, isoTime)
        if (text.indexOf(':') < 0) return DateTimeParts(date, null)
        // Prefer a time printed right after the date; otherwise the first time in the message.
        val near = if (date != null) TIME.find(text, bestEnd)?.takeIf { it.range.first - bestEnd <= TIME_NEAR_DATE } else null
        val time = near ?: TIME.find(text)
        return DateTimeParts(date, time?.let { timeOf(it.groupValues[1], it.groupValues[2], it.groupValues[3], it.groupValues[4]) })
    }

    private fun year(y: String): Int = if (y.length == 2) 2000 + y.toInt() else y.toInt()

    private fun dateOf(y: Int, m: Int, d: Int): LocalDate? =
        if (m !in 1..12 || d !in 1..31 || y !in 2000..2099) null
        else try { LocalDate.of(y, m, d) } catch (_: java.time.DateTimeException) { null }

    private fun timeOf(h: String, m: String, s: String, ampm: String): LocalTime? {
        var hour = h.toInt()
        when (ampm.lowercase()) {
            "p" -> if (hour < 12) hour += 12
            "a" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23) return null
        return LocalTime.of(hour, m.toInt(), if (s.isEmpty()) 0 else s.toInt())
    }
}
