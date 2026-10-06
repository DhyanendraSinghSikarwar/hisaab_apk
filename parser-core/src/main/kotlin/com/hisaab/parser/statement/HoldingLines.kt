package com.hisaab.parser.statement

import kotlin.math.abs

/**
 * Reads the figures of one holdings-table row in a CAS or broker statement, whatever the column order:
 *
 *  - CDSL/NSDL demat: `INE002A01018 RELIANCE INDUSTRIES LTD 10.000 -- -- 10.000 2,950.25 29,502.50`
 *  - CDSL MF (with the RTA): `Parag Parikh Flexi Cap Fund - Direct Growth INF879O01027 12345678/90 1,234.567 78.1234 80,000.00 96,445.12 16,445.12 14.2%`
 *  - NSDL MF folios: `INF179K01BE2 HDFC Mid-Cap Fund - Direct Growth 1234567/89 500.000 50.00 25,000.00 150.234 75,117.00 50,117.00`
 *
 * The trailing run of numbers holds the units first. The value is the number that equals units × some price
 * (NAV or market price) to the right of it; when several such pairs exist, the right-most one is the
 * current value and an earlier one, or a number between the price and the value, is the amount invested.
 */
internal object HoldingLines {

    data class Row(
        val name: String,
        val units: Double?,
        val valueMinor: Long?,
        val investedMinor: Long?,
        /** True when units × price = value was found, so the figures are certainly the right columns. */
        val consistent: Boolean,
    )

    private val NUMERIC = Regex("""^\(?-?(?:\d{1,3}(?:,\d{2,3})+|\d+)(?:\.\d+)?\)?$""")
    private val PLACEHOLDER = Regex("""^(?:-+|--|NA|N\.A\.?|NIL|[\d.,]+%|\(?-?[\d.,]+%\)?)$""", RegexOption.IGNORE_CASE)
    private val CURRENCY = Regex("""^(?:INR|Rs\.?|₹)$""", RegexOption.IGNORE_CASE)
    private val FOLIO = Regex("""^[\d/]+$""")

    /** Splits [after] (the text after the ISIN) into name words and figures, and reads them. */
    fun read(before: String, after: String): Row? {
        val tokens = after.split(WS).filter { it.isNotEmpty() }
        // The trailing run: numbers, dashes, percentages and currency words, read from the end.
        var start = tokens.size
        while (start > 0) {
            val t = tokens[start - 1].trim(',', ';', '|')
            if (NUMERIC.matches(t) || PLACEHOLDER.matches(t) || CURRENCY.matches(t)) start-- else break
        }
        val run = tokens.subList(start, tokens.size).map { it.trim(',', ';', '|') }.filterNot { CURRENCY.matches(it) }
        // Placeholders keep their column, but count as no number.
        val nums: MutableList<Double?> = run.map { t -> if (NUMERIC.matches(t)) number(t) else null }.toMutableList()
        val raw = run.toMutableList()
        // A folio number made of digits only ("91012345678") sits before the units: never a quantity.
        while (raw.isNotEmpty() && raw.size > 2 && raw[0].all { it.isDigit() } && raw[0].length >= 6) { raw.removeAt(0); nums.removeAt(0) }

        // Name words, without a folio number ("12345678/90", "91012345678"); a short number stays ("NIFTY 50").
        val wordsAfter = tokens.subList(0, start).filterNot { FOLIO.matches(it) && (it.contains('/') || it.length >= 6) }.joinToString(" ")
        val name = cleanName(listOf(before, wordsAfter).filter { it.isNotBlank() }.joinToString(" "))
        if (nums.count { it != null } < 2) return null

        // Try the units at the first or second number (a name may end in a number: "NIFTY 50").
        for (s in 0..minOf(1, nums.size - 2)) {
            val units = nums[s] ?: continue
            if (units <= 0) continue
            val pairs = ArrayList<Pair<Int, Int>>()
            for (i in s + 1 until nums.size) {
                val price = nums[i] ?: continue
                if (price <= 0) continue
                for (j in i + 1 until nums.size) {
                    val v = nums[j] ?: continue
                    if (v <= 0) continue
                    if (abs(units * price - v) <= maxOf(v * 0.015, 1.0)) pairs += i to j
                }
            }
            if (pairs.isEmpty()) continue
            val best = pairs.maxBy { it.second }
            val value = nums[best.second]!!
            // Invested: an earlier units × average-cost pair, else a number between the price and the value.
            val invested = pairs.filter { it.second < best.second && it.second != best.first }.minByOrNull { it.second }?.let { nums[it.second] }
                ?: ((best.first + 1) until best.second).mapNotNull { nums[it] }.firstOrNull { it > 0 && it < value * 20 }
            val extraName = if (s == 1) raw[0] else ""
            return Row(
                cleanName(listOf(name, extraName).filter { it.isNotBlank() }.joinToString(" ")),
                units, minor(value), invested?.let(::minor), consistent = true,
            )
        }
        // No price column found: units first, value last.
        val present = nums.filterNotNull()
        val value = present.last()
        if (value <= 0) return null
        return Row(name, present.first(), minor(value), null, consistent = false)
    }

    fun number(t: String): Double? {
        val negative = t.startsWith("(") || t.startsWith("-")
        val v = t.trim('(', ')', '-').replace(",", "").toDoubleOrNull() ?: return null
        return if (negative) -v else v
    }

    private fun minor(rupees: Double): Long = Math.round(rupees * 100)

    private val LEADING_SERIAL = Regex("""^(?:\d{1,3}[.)]?\s+)+""")
    private val TRAILING_JUNK = Regex("""[\s#*|:-]+$""")

    // CDSL names a demat holding "RELIANCE INDUSTRIES LIMITED#EQUITY SHARES": the part before '#' is the name.
    fun cleanName(s: String): String = s.substringBefore('#').ifBlank { s }.replace(WS, " ").replace(LEADING_SERIAL, "").replace('#', ' ')
        .replace(Regex("""\s+"""), " ").replace(TRAILING_JUNK, "").trim().trim('-', '|', ':', ' ')

    private val WS = Regex("""\s+""")
}
