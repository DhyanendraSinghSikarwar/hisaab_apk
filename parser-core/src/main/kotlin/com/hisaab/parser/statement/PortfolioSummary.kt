package com.hisaab.parser.statement

import com.hisaab.parser.ParserConfig
import com.hisaab.parser.bank.DepositParser
import com.hisaab.parser.extract.Money
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.text.rx
import java.time.Instant

/**
 * INDmoney's portfolio summary email: invested and current value per asset class ("Mutual Funds ₹1,20,000 ₹1,35,400",
 * "US Stocks: Invested ₹50,000 · Current ₹58,200"). Each class becomes one aggregate holding, "AGG:INDMONEY:<class>",
 * which stands in until the funds or stocks are known one by one (from a CAS or a holdings statement); the holdings
 * store drops the aggregate then, so nothing is counted twice.
 */
object PortfolioSummary {
    /** Identifier prefix of an aggregate holding: a whole asset class at one platform. */
    const val AGGREGATE = "AGG:"

    private data class Cls(val key: String, val kind: HoldingKind, val name: String, val label: Regex)

    private val CLASSES = listOf(
        Cls("US", HoldingKind.STOCK, "US stocks (INDmoney)", rx("""^\W*(?:US\s+stocks?|US\s+equit(?:y|ies)|global\s+stocks?)\b""")),
        Cls("MF", HoldingKind.MUTUAL_FUND, "Mutual funds (INDmoney)", rx("""^\W*(?:mutual\s+funds?|MFs?)\b""")),
        Cls("STOCKS", HoldingKind.STOCK, "Indian stocks (INDmoney)", rx("""^\W*(?:indian\s+stocks?|IND\s+stocks?|stocks?|equit(?:y|ies)|shares)\b""")),
    )
    private val IS_SUMMARY = rx("""\bportfolio\b.{0,40}\b(?:summary|update|snapshot|report|overview)\b|\b(?:weekly|monthly|daily)\s+(?:portfolio|wealth|net\s*worth)\b|\bnet\s*worth\s+(?:summary|update|report)\b""")
    private val INDMONEY = rx("""indmoney""")
    private val AMOUNT = rx("""(?:₹|\bRs\.?|\bINR)\s*([\d,]+(?:\.\d{1,2})?)""")
    private val INVESTED = rx("""\binvested\b|\binvestment\b|\bcost\b""")
    private val CURRENT = rx("""\bcurrent\b|\bvalue\b|\bworth\b|\bmarket\b""")
    private val AS_ON = rx("""\bas\s+(?:on|of)\s*:?\s*(\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}|\d{1,2}(?:st|nd|rd|th)?[\s-][A-Za-z]{3,9}[\s,-]+\d{2,4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{4})""")

    /** The aggregate holdings in an INDmoney summary email; empty for any other email. */
    fun parse(text: String, subject: String?, sender: String, receivedAt: Long, config: ParserConfig = ParserConfig()): List<HoldingSnapshot> {
        val head = "${subject.orEmpty()} ${text.take(1_500)}"
        if (!INDMONEY.containsMatchIn("$sender $head") || !IS_SUMMARY.containsMatchIn(head)) return emptyList()
        val lines = text.lines().map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }
        val received = Instant.ofEpochMilli(receivedAt).atZone(config.zone).toLocalDate()
        val asOf = AS_ON.find(head)?.groupValues?.get(1)?.let(DepositParser::parseDate)
            ?.takeIf { !it.isAfter(received.plusDays(1)) && !it.isBefore(received.minusDays(31)) }
            ?.atTime(12, 0)?.atZone(config.zone)?.toInstant()?.toEpochMilli() ?: receivedAt
        // A table header ("Asset  Current Value  Invested") says which figure comes first.
        val currentFirst = lines.firstOrNull { INVESTED.containsMatchIn(it) && CURRENT.containsMatchIn(it) && AMOUNT.find(it) == null }
            ?.let { h -> CURRENT.find(h)!!.range.first < INVESTED.find(h)!!.range.first } ?: false
        val out = LinkedHashMap<String, HoldingSnapshot>()
        for ((i, line) in lines.withIndex()) {
            val cls = CLASSES.firstOrNull { it.label.containsMatchIn(line) } ?: continue
            if (cls.key in out) continue
            // The figures on the label's line, or on the next few lines (an HTML table read as text).
            var block = line
            var j = i + 1
            while (AMOUNT.findAll(block).count() < 2 && j < lines.size && j <= i + 4 && CLASSES.none { it.label.containsMatchIn(lines[j]) }) {
                block += " " + lines[j]
                j++
            }
            val figures = figures(block, currentFirst) ?: continue
            val (invested, value) = figures
            out[cls.key] = HoldingSnapshot(cls.kind, cls.name, "${AGGREGATE}INDMONEY:${cls.key}", null, value, invested, asOf)
        }
        return out.values.toList()
    }

    /** Invested and current value in [block]: by their labels when it has them, else by position. */
    private fun figures(block: String, currentFirst: Boolean): Pair<Long?, Long>? {
        val amounts = AMOUNT.findAll(block).mapNotNull { m -> Money.parse(m.groupValues[1])?.minor?.let { m.range.first to it } }.toList()
        if (amounts.isEmpty()) return null
        fun labelled(label: Regex): Long? = label.findAll(block).firstNotNullOfOrNull { l -> amounts.firstOrNull { it.first > l.range.last }?.second }
        val inv = labelled(INVESTED)
        val cur = labelled(CURRENT)
        if (inv != null && cur != null && inv != cur) return inv to cur
        if (amounts.size == 1) return null to amounts[0].second
        val (a, b) = amounts[0].second to amounts[1].second
        return if (currentFirst) b to a else a to b
    }
}
