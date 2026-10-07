package com.hisaab.parser.statement

import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.text.rx
import java.util.Locale
import kotlin.math.abs

/**
 * Holdings tables whose rows carry no ISIN: Groww's mutual fund "Holdings statement" (PDF or XLSX) and similar app
 * statements. A header row names the figure columns ("Scheme Name  AMC  Category  Folio No.  Units  Invested Value
 * Current Value  Returns  XIRR"); each row below it ends in those figures. The figures are matched to the columns so
 * that they agree with each other (invested + returns = current value, units × NAV = value), which survives a blank
 * cell or a column the PDF text left out.
 *
 * A table in US dollars (INDmoney's US stocks statement: "Invested Value ($)", "$575.25") is converted at the
 * statement's own USD/INR rate; without one its rows are left out rather than counted as rupees.
 */
internal object HoldingTable {

    private enum class Col(val pct: Boolean = false) { UNITS, AVG_PRICE, PRICE, INVESTED, VALUE, RETURNS, PCT(true) }

    private val LABELS: List<Pair<Col, Regex>> = listOf(
        Col.INVESTED to rx("""invested\s+(?:value|amount|amt)|amount\s+invested|investment\s+(?:value|amount|cost)|total\s+investments?|cost\s+value|purchase\s+value|total\s+cost|buy\s+value|\binvested\b|\bcost\b"""),
        Col.VALUE to rx("""current\s+(?:market\s+|portfolio\s+)?(?:value|valuation|val\b|amount|amt)|cur\.?\s*val\b\.?|market\s+(?:value|val\b)|present\s+value|closing\s+value|\bvaluation\b|\bvalue\b|\bcurrent\b"""),
        Col.AVG_PRICE to rx("""avg\.?\s+(?:buy\s+)?(?:nav|price|cost)|average\s+(?:buy\s+)?(?:nav|price|cost)|buy\s+(?:nav|price)|purchase\s+(?:nav|price)"""),
        Col.PRICE to rx("""(?:current|latest|closing|market)\s+(?:nav|price)|\bNAV\b|\bLTP\b"""),
        Col.UNITS to rx("""\b(?:balance\s+|closing\s+)?units?\b|\bquantity\b|\bqty\b"""),
        Col.PCT to rx("""(?:profit|P\s*&\s*L|gains?)(?:\s*/\s*\(?loss\)?)?\s*\(?%\)?|\bXIRR\b|\bCAGR\b|returns?\s*\(\s*%\s*\)|returns?\s*%|%\s*returns?|abs(?:olute)?\.?\s+returns?|gain\s*\(?%\)?"""),
        Col.RETURNS to rx(
            """unreali[sz]ed\s+(?:P\s*&\s*L|gains?(?:\s*/\s*\(?loss\)?)?|profit(?:\s*/\s*\(?loss\)?)?)|\breturns?\b|\bgains?(?:\s*/\s*\(?loss\)?)?|\bP\s*&\s*L\b|\bprofit(?:\s*/\s*\(?loss\)?)?|unreali[sz]ed""",
        ),
    )
    private val NAME_HEAD = rx("""\b(?:scheme|fund|security|instrument|stock|company|holding|name|symbol|isin|scrip|script)\b""")
    private val FUND_HEAD = rx("""\b(?:scheme|fund|folio|NAV|AMC)\b""")
    private val TOTAL = rx("""^(?:grand\s+|sub\s*-?\s*)?total\b|\btotal\s*:|\bportfolio\s+(?:value|total)\b""")
    private val FUND_WORDS = rx("""\b(?:fund|scheme|plan|growth|IDCW|ETF|FoF|index|dividend)\b""")
    private val ETF_NAME = rx("""\bETF\b|\bBEES\b""")
    private val NAME_END = rx("""^(.*?\b(?:Growth|IDCW|Dividend|Bonus)(?:\s+(?:Option|Plan|Payout|Reinvestment))?)\b""")
    private val ISIN = Regex("""\b[A-Z]{2}[A-Z0-9]{9}\d\b""")
    private val COMMODITY = rx("""\bcommodit(?:y|ies)\b|\b(?:gold|silver)\b""")
    private val CELLS = Regex("""\s{2,}""")

    private val NUMERIC = Regex("""^[+-]?\(?[+-]?(?:\d{1,3}(?:,\d{2,3})+|\d+)(?:\.\d+)?\)?$""")
    private val PERCENT = Regex("""^\(?[+-]?\d[\d,]*(?:\.\d+)?\s*%\)?$""")
    private val PLACEHOLDER = Regex("""^(?:-+|NA|N/A|N\.A\.?|NIL)$""", RegexOption.IGNORE_CASE)
    private val CURRENCY = Regex("""^(?:INR|Rs\.?|₹|USD|US\$|\$)$""", RegexOption.IGNORE_CASE)
    private val CURRENCY_PREFIX = Regex("""^(?:₹|Rs\.?|INR|US\$|\$|USD)(?=[\d(+-])""", RegexOption.IGNORE_CASE)
    private val USD_HEAD = Regex("""\$|\bUSD\b|\bUS\s+stocks?\b""", RegexOption.IGNORE_CASE)
    private val TICKER = Regex("""^[A-Z]{1,5}(?:\.[A-Z])?$""")

    private class Tok(val value: Double?, val pct: Boolean)

    /** Holdings from every table in [lines] whose header names invested and current value. Rows with an ISIN are left to the caller. */
    fun read(lines: List<String>, asOf: Long, usdRate: Double? = null, hasIsin: (String) -> Boolean): List<HoldingSnapshot> {
        val out = LinkedHashMap<String, HoldingSnapshot>()
        var cols: List<Col>? = null
        var fundTable = false
        // Rupees per figure: 1 in a rupee table, the USD rate in a dollar table, null in a dollar table without a rate.
        var rate: Double? = 1.0
        var pending: String? = null
        var skip = -1
        for ((i, line) in lines.withIndex()) {
            if (i == skip) continue
            // The header, on one line or wrapped onto the next.
            val head = header(line) ?: lines.getOrNull(i + 1)?.takeIf { NAME_HEAD.containsMatchIn(line) }?.let { header("$line $it") }?.also { skip = i + 1 }
            if (head != null) {
                cols = head
                val headText = if (skip == i + 1) line + " " + lines[i + 1] else line
                fundTable = FUND_HEAD.containsMatchIn(line + " " + lines.getOrNull(i + 1).orEmpty())
                rate = if (USD_HEAD.containsMatchIn(headText)) usdRate else 1.0
                pending = null
                continue
            }
            val c = cols ?: continue
            val fx = rate ?: continue
            var isin: String? = null
            var row = line
            if (hasIsin(line)) {
                isin = ISIN.find(line)?.value ?: continue
                row = line.replace(isin, " ")
            }
            if (TOTAL.containsMatchIn(line.trim())) { pending = null; continue }
            val (namePart, toks) = split(row)
            if (toks.count { it.value != null } < 2) {
                // A name on its own line, its figures on the next.
                pending = if (namePart.any { it.isLetter() } && namePart.length < 120) namePart else null
                continue
            }
            val fig = align(toks, c) ?: continue
            var name = name(row, namePart)
            if (pending != null && (name.none { it.isLetter() } || name.split(' ').size <= 2)) name = HoldingLines.cleanName("$pending $name")
            pending = null
            if (name.length < 3 || TOTAL.containsMatchIn(name)) continue
            val value = (fig[Col.VALUE] ?: continue) * fx
            if (value <= 0) continue
            val invested = fig[Col.INVESTED]?.takeIf { it > 0 }?.times(fx) ?: fig[Col.RETURNS]?.let { value - it * fx }?.takeIf { it > 0 }
            val units = fig[Col.UNITS]?.takeIf { it > 0 }
            val kind = when {
                COMMODITY.containsMatchIn(line) -> HoldingKind.GOLD
                ETF_NAME.containsMatchIn(name) -> HoldingKind.ETF
                fundTable || FUND_WORDS.containsMatchIn(name) -> HoldingKind.MUTUAL_FUND
                else -> HoldingKind.STOCK
            }
            val id = when {
                fx != 1.0 && kind != HoldingKind.MUTUAL_FUND ->
                    "US:" + (name.split(' ').first().takeIf { TICKER.matches(it) } ?: MfOrderParser.normaliseScheme(name).uppercase(Locale.ROOT))
                isin != null -> isin
                kind == HoldingKind.STOCK -> "STOCK:" + MfOrderParser.normaliseScheme(name).uppercase(Locale.ROOT)
                else -> MfOrderParser.identifierFor(name)
            }
            val snap = HoldingSnapshot(kind, name, id, units, minor(value), invested?.let(::minor), asOf)
            // One scheme in two folios: the folios add up.
            out[id] = out[id]?.let { p ->
                p.copy(units = if (p.units == null && units == null) null else (p.units ?: 0.0) + (units ?: 0.0), valueMinor = (p.valueMinor ?: 0) + snap.valueMinor!!,
                    investedMinor = if (p.investedMinor == null && snap.investedMinor == null) null else (p.investedMinor ?: 0) + (snap.investedMinor ?: 0))
            } ?: snap
        }
        return out.values.toList()
    }

    /** The figure columns a header line names, in order; null unless it names both invested and current value. */
    private fun header(line: String): List<Col>? {
        if (!NAME_HEAD.containsMatchIn(line) && !line.contains("units", ignoreCase = true)) return null
        val cols = scan(line, LABELS).map { it.first }
        if (Col.VALUE !in cols || (Col.INVESTED !in cols && Col.UNITS !in cols)) return null
        // A header has words, not figures.
        if (line.split(Regex("""\s+""")).count { NUMERIC.matches(it.trim(',')) && it.contains('.') } > 0) return null
        return cols
    }

    /** Non-overlapping label matches, left to right; at one position the longest match wins. */
    fun <K> scan(text: String, labels: List<Pair<K, Regex>>): List<Pair<K, IntRange>> {
        val out = ArrayList<Pair<K, IntRange>>()
        var pos = 0
        while (pos < text.length) {
            val best = labels.mapNotNull { (k, r) -> r.find(text, pos)?.let { k to it.range } }
                .minWithOrNull(compareBy<Pair<K, IntRange>> { it.second.first }.thenByDescending { it.second.last })
                ?: break
            out += best
            pos = best.second.last + 1
        }
        return out
    }

    /** The words before the trailing run of figures, and the figures. */
    private fun split(line: String): Pair<String, List<Tok>> {
        val tokens = line.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        var start = tokens.size
        while (start > 0) {
            val t = tokens[start - 1].trim(',', ';', '|').replace(CURRENCY_PREFIX, "")
            if (NUMERIC.matches(t) || PERCENT.matches(t) || PLACEHOLDER.matches(t) || CURRENCY.matches(t)) start-- else break
        }
        val toks = tokens.subList(start, tokens.size).map { it.trim(',', ';', '|').replace(CURRENCY_PREFIX, "") }.filterNot { CURRENCY.matches(it) }.map { t ->
            when {
                PERCENT.matches(t) -> Tok(HoldingLines.number(t.replace("%", "").replace(" ", "")), true)
                NUMERIC.matches(t) -> Tok(HoldingLines.number(t.replace("+", "")), false)
                else -> Tok(null, false)
            }
        }
        return tokens.subList(0, start).joinToString(" ") to toks
    }

    /** The row's name: a spreadsheet row's first text cell, or a PDF row's words up to "Growth" / "IDCW". */
    private fun name(line: String, namePart: String): String {
        val cells = line.trim().split(CELLS)
        if (cells.size >= 3) cells.firstOrNull { c -> c.any { it.isLetter() } && !NUMERIC.matches(c) }?.let { return HoldingLines.cleanName(it) }
        val words = namePart.split(' ').filterNot { it.all { ch -> ch.isDigit() || ch == '/' } && it.length >= 6 }.joinToString(" ")
        val cut = NAME_END.find(words)?.groupValues?.get(1) ?: words
        return HoldingLines.cleanName(cut)
    }

    /**
     * Matches the figures to the columns, in order. Columns may be missing (a blank cell) and leading figures may be
     * skipped (a folio number); a percentage only fills a percentage column. The assignment whose figures agree wins.
     */
    private fun align(toks: List<Tok>, cols: List<Col>): Map<Col, Double>? {
        if (toks.size > 12 || cols.isEmpty()) return null
        var best: Map<Col, Double>? = null
        var bestScore = Double.NEGATIVE_INFINITY
        val assigned = arrayOfNulls<Col>(toks.size)

        fun evaluate() {
            val map = HashMap<Col, Double>()
            var penalty = 0.0
            var assignedCount = 0
            for ((i, t) in toks.withIndex()) {
                val c = assigned[i]
                if (c == null) { penalty += if (t.pct) 0.5 else 1.0; continue }
                assignedCount++
                if (!t.pct && c.pct) penalty += 0.25
                t.value?.let { map[c] = it }
            }
            penalty += (cols.size - assignedCount) * 1.0
            val value = map[Col.VALUE] ?: return
            if (value <= 0) return
            var score = -penalty
            val inv = map[Col.INVESTED]
            val ret = map[Col.RETURNS]
            val units = map[Col.UNITS]
            val price = map[Col.PRICE]
            val avg = map[Col.AVG_PRICE]
            if (inv != null && ret != null && abs(inv + ret - value) <= maxOf(1.0, value * 0.01)) score += 6
            if (units != null && price != null && abs(units * price - value) <= maxOf(1.0, value * 0.015)) score += 6
            if (units != null && avg != null && inv != null && abs(units * avg - inv) <= maxOf(1.0, inv * 0.015)) score += 4
            if (score > bestScore) { bestScore = score; best = map }
        }

        fun go(ti: Int, ci: Int) {
            if (ti == toks.size) { evaluate(); return }
            // Skip a figure: a leading folio number, or a percentage beside its amount ("16,445.12 (20.56%)").
            if (toks[ti].pct || assigned.take(ti).all { it == null }) { assigned[ti] = null; go(ti + 1, ci) }
            for (cj in ci until cols.size) {
                if (toks[ti].pct && !cols[cj].pct) continue
                assigned[ti] = cols[cj]
                go(ti + 1, cj + 1)
                assigned[ti] = null
            }
        }
        go(0, 0)
        return best
    }

    private fun minor(rupees: Double): Long = Math.round(rupees * 100)
}
