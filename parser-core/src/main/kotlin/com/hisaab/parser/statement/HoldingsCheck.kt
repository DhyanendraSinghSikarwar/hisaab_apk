package com.hisaab.parser.statement

import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.text.rx
import kotlin.math.abs

/**
 * The totals a holdings sheet states in its summary block ("Total Investments", "Current Portfolio Value") beside what
 * the rows add up to. [mismatch] is set when a stated total and the rows' sum differ by more than [TOLERANCE_MINOR];
 * the rows are recorded regardless. A figure the rows do not carry is null, never made up.
 */
data class HoldingsCheck(
    val statedInvestedMinor: Long?,
    val statedValueMinor: Long?,
    val rowsInvestedMinor: Long?,
    val rowsValueMinor: Long?,
    val mismatch: Boolean,
) {
    companion object {
        /** One rupee. */
        const val TOLERANCE_MINOR = 100L
    }
}

/** Reads the summary block of a holdings sheet and compares it to the rows read. */
object HoldingsCheckReader {

    private enum class Key { INVESTED, VALUE, OTHER }

    private val LABELS: List<Pair<Key, Regex>> = listOf(
        Key.INVESTED to rx("""total\s+investments?|investments?\s+(?:value|amount)|invested\s+(?:value|amount)|\binvested\b|total\s+cost"""),
        Key.VALUE to rx("""current\s+(?:portfolio\s+|market\s+)?(?:value|amount)|portfolio\s+value|market\s+value|total\s+value"""),
        Key.OTHER to rx("""profit\s*/?\s*loss\s*%?|\bP\s*&\s*L\b|\bxirr\b|\breturns?\s*%?|\bgains?\b"""),
    )
    private val TABLE_WORDS = rx("""\b(?:scheme|fund|name|folio|units?|symbol|instrument|isin)\b""")
    private val FIGURE = Regex("""^(?:₹|Rs\.?|INR)?\(?[+-]?(?:\d{1,3}(?:,\d{2,3})+|\d+)(?:\.\d+)?\)?\s*%?$""", RegexOption.IGNORE_CASE)
    private val CELLS = Regex("""\s{2,}|\t|\|""")

    /** The check for [lines] and the [holdings] read from them; null when the sheet states no totals. */
    fun check(lines: List<String>, holdings: List<HoldingSnapshot>): HoldingsCheck? {
        val (invested, value) = stated(lines)
        if (invested == null && value == null) return null
        val rowsInvested = holdings.mapNotNull { it.investedMinor }.takeIf { it.isNotEmpty() }?.sum()
        val rowsValue = holdings.mapNotNull { it.valueMinor }.takeIf { it.isNotEmpty() }?.sum()
        fun off(stated: Long?, rows: Long?) = stated != null && rows != null && abs(stated - rows) > HoldingsCheck.TOLERANCE_MINOR
        return HoldingsCheck(invested, value, rowsInvested, rowsValue, off(invested, rowsInvested) || off(value, rowsValue))
    }

    /** Stated total invested and current value, in paise: a label row over a figures row, or "label  figure" rows. */
    fun stated(lines: List<String>): Pair<Long?, Long?> {
        var invested: Long? = null
        var value: Long? = null
        fun put(k: Key, v: Double?) {
            val m = v?.let { Math.round(it * 100) } ?: return
            if (k == Key.INVESTED && invested == null) invested = m
            if (k == Key.VALUE && value == null) value = m
        }
        for ((i, line) in lines.withIndex()) {
            val text = line.trim()
            if (text.length > 160 || TABLE_WORDS.containsMatchIn(text)) continue
            val labels = HoldingTable.scan(text, LABELS)
            if (labels.isEmpty()) continue
            val tail = text.substring(labels.last().second.last + 1)
            if (labels.size == 1 && labels[0].second.first == 0 && labels[0].first != Key.OTHER) {
                // "Total Investments  200700.2"
                cells(tail).firstOrNull()?.let { put(labels[0].first, number(it)) }
                continue
            }
            if (labels.size < 2) continue
            // Labels on this line, their figures on the next.
            val next = cells(lines.getOrNull(i + 1).orEmpty())
            if (next.isEmpty() || !next.all { FIGURE.matches(it) }) continue
            if (next.size == labels.size) labels.forEachIndexed { j, l -> put(l.first, number(next[j])) }
            else if (labels[0].first == Key.INVESTED && labels[1].first == Key.VALUE && next.size >= 2) { put(Key.INVESTED, number(next[0])); put(Key.VALUE, number(next[1])) }
        }
        return invested to value
    }

    private fun cells(s: String): List<String> =
        s.trim().split(CELLS).map { it.trim() }.filter { it.isNotEmpty() }.flatMap { c -> if (c.count { it == ' ' } > 0 && c.split(' ').all { FIGURE.matches(it) }) c.split(' ') else listOf(c) }

    private fun number(t: String): Double? = HoldingLines.number(t.replace("%", "").replace(Regex("""^(?:₹|Rs\.?|INR)""", RegexOption.IGNORE_CASE), "").trim())
}
