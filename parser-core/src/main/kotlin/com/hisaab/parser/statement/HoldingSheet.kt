package com.hisaab.parser.statement

import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.text.rx
import java.util.Locale

/**
 * Holdings read by column from a spreadsheet ([SpreadsheetLines] keeps the cells of such a sheet apart with [SEP]).
 * The header row is found by [HoldingColumns], so Groww, Zerodha, Kuvera, INDmoney, broker and registrar exports all
 * go the same way. Cells may be text ("1250.75", "4.1%", "(1,500)", "₹1,200 Cr"); folio numbers and ISINs are never read
 * as amounts. Missing figures are worked out only from others on the row (invested = units × average price, current =
 * units × price); nothing is made up. Rows of one scheme or stock in several folios or lots are summed.
 */
internal object HoldingSheet {
    /** Separates the cells of a sheet row in the text lines. */
    const val SEP = "¦"

    private val TOTAL = rx("""^(?:grand\s+|sub\s*-?\s*)?totals?\b|\bportfolio\s+total\b""")
    private val ISIN = Regex("""^[A-Z]{2}[A-Z0-9]{9}\d$""")
    private val COMMODITY = rx("""\bcommodit(?:y|ies)\b|\b(?:gold|silver)\b""")
    private val ETF_NAME = rx("""\bETF\b|\bBEES\b""")
    private val FUND_WORDS = rx("""\b(?:fund|scheme|plan|growth|IDCW|FoF|index|dividend)\b""")
    private val PLACEHOLDER = Regex("""^(?:-+|NA|N/A|N\.A\.?|NIL|NULL)$""", RegexOption.IGNORE_CASE)
    private val CURRENCY = Regex("""^(?:₹|Rs\.?|INR)\s*""", RegexOption.IGNORE_CASE)
    private val PLAIN = Regex("""^[+-]?\d+(?:\.\d+)?$""")

    /** True when [line] is a sheet row in cell form. */
    fun isRow(line: String) = SEP in line

    fun cells(line: String): List<String> = line.split(SEP).map { it.trim() }

    /** A figure in a cell: plain, grouped, with a currency sign, in brackets, or with Cr/Dr; null for text, "-" and percentages. */
    fun number(raw: String, allowPct: Boolean = false): Double? {
        var s = raw.trim().replace(' ', ' ')
        if (s.isEmpty() || PLACEHOLDER.matches(s)) return null
        if (s.endsWith("%")) { if (!allowPct) return null; s = s.dropLast(1).trim() }
        var negative = false
        val tail = Regex("""\s*(Cr|Dr)\.?$""", RegexOption.IGNORE_CASE).find(s)
        if (tail != null) { negative = tail.groupValues[1].equals("Dr", true); s = s.substring(0, tail.range.first) }
        s = CURRENCY.replace(s.trim(), "").trim()
        if (s.startsWith("(") && s.endsWith(")")) { negative = !negative; s = s.substring(1, s.length - 1).trim() }
        s = CURRENCY.replace(s, "").replace(",", "").replace(" ", "")
        if (!PLAIN.matches(s)) return null
        val v = s.toDouble()
        return if (negative) -v else v
    }

    /**
     * The holdings of every table in [lines], or null when the lines hold no sheet header (a PDF, or a sheet in
     * dollars): the caller then reads them as text. [platform] names the app when the sender says so; a Source column wins.
     */
    fun read(lines: List<String>, asOf: Long, platform: String?): List<HoldingSnapshot>? {
        var cols: Map<HoldingField, Int>? = null
        var sawHeader = false
        val out = LinkedHashMap<String, HoldingSnapshot>()
        val platforms = HashMap<String, LinkedHashSet<String>>()
        for (line in lines) {
            if (!isRow(line)) { continue }
            val cells = cells(line)
            if (cells.all { it.isEmpty() }) { cols = null; continue }
            if (HoldingColumns.isHeader(cells)) { cols = HoldingColumns.map(cells); sawHeader = true; continue }
            val c = cols ?: continue
            val first = cells.firstOrNull { it.isNotEmpty() }.orEmpty()
            if (TOTAL.containsMatchIn(first)) { cols = null; continue }
            fun cell(f: HoldingField) = c[f]?.let { cells.getOrNull(it) }.orEmpty()
            fun num(f: HoldingField) = number(cell(f))
            val name = cell(HoldingField.NAME).ifEmpty { cell(HoldingField.SYMBOL) }.replace(Regex("""\s+"""), " ").trim()
            if (name.length < 2 || name.none { it.isLetter() } || TOTAL.containsMatchIn(name)) continue
            val units = num(HoldingField.UNITS)
            val gain = num(HoldingField.GAIN)
            val current = num(HoldingField.CURRENT)
                ?: units?.let { u -> num(HoldingField.PRICE)?.let { u * it } }
                ?: num(HoldingField.INVESTED)?.let { i -> gain?.let { i + it } }
            if (current == null || current <= 0) continue
            val invested = (num(HoldingField.INVESTED) ?: units?.let { u -> num(HoldingField.AVG_PRICE)?.let { u * it } } ?: gain?.let { current - it })
                ?.takeIf { it > 0 }
            val isin = (cell(HoldingField.ISIN).uppercase(Locale.ROOT)).takeIf { ISIN.matches(it) }
            val category = cell(HoldingField.CATEGORY) + " " + cell(HoldingField.SUB_CATEGORY)
            val equityIsin = isin?.startsWith("INE") == true
            val fundish = !equityIsin && (isin?.startsWith("INF") == true || cell(HoldingField.FOLIO).isNotEmpty() || cell(HoldingField.AMC).isNotEmpty() ||
                FUND_WORDS.containsMatchIn(name))
            val kind = when {
                !equityIsin && (COMMODITY.containsMatchIn(category) || (fundish && COMMODITY.containsMatchIn(name))) -> HoldingKind.GOLD
                ETF_NAME.containsMatchIn(name) || category.contains("ETF", ignoreCase = true) -> HoldingKind.ETF
                fundish -> HoldingKind.MUTUAL_FUND
                else -> HoldingKind.STOCK
            }
            val id = isin ?: if (kind == HoldingKind.STOCK) "STOCK:" + MfOrderParser.normaliseScheme(name).uppercase(Locale.ROOT) else MfOrderParser.identifierFor(name)
            val snap = HoldingSnapshot(kind, name, id, units?.takeIf { it > 0 }, minor(current), invested?.let(::minor), asOf)
            cell(HoldingField.SOURCE).ifEmpty { platform.orEmpty() }.takeIf { it.isNotEmpty() }?.let { platforms.getOrPut(id) { LinkedHashSet() } += it }
            // One scheme or stock in several folios or lots: the figures add up.
            out[id] = out[id]?.let { p ->
                p.copy(
                    units = if (p.units == null && snap.units == null) null else (p.units ?: 0.0) + (snap.units ?: 0.0),
                    valueMinor = (p.valueMinor ?: 0) + snap.valueMinor!!,
                    investedMinor = if (p.investedMinor == null && snap.investedMinor == null) null else (p.investedMinor ?: 0) + (snap.investedMinor ?: 0),
                )
            } ?: snap
        }
        if (!sawHeader) return null
        return out.map { (id, s) -> s.copy(platform = platforms[id]?.joinToString(", ")) }
    }

    private fun minor(rupees: Double): Long = Math.round(rupees * 100)
}
