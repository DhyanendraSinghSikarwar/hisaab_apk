package com.hisaab.parser.statement

import com.hisaab.parser.extract.Money
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.text.rx
import kotlin.math.abs

/**
 * An NPS Statement of Transaction (Protean/NSDL, KFintech or CAMS CRA): the PRAN, the valuation summary ("Total
 * Contribution", "Total Notional Gain/Loss", "Value of your Holdings" or "Total Holding Value") and the scheme-wise
 * units, NAV and value. One holding per tier: "NPS:<last 4 of PRAN>" for Tier I, "NPS:<last 4>:T2" for Tier II.
 */
internal object NpsStatement {

    private enum class Key { CONTRIBUTION, WITHDRAWAL, GAIN, VALUE, RETURN }

    private val IS_NPS = rx("""\bNPS\b|national\s+pension|pension\s+fund|\bCRA\b|\bPFRDA\b""")
    private val PRAN = rx("""\bPRAN\b(?:\s*(?:No\.?|Number))?[^0-9]{0,20}[x*\d]*?(\d{4})\b""")
    private val TIER = rx("""\bTier\s*[-:]?\s*(II|2|I|1)\b""")
    private val SCHEME_ROW = rx("""\bscheme\s*[-:]?\s*(?:E|C|G|A)\b|\bpension\s+fund\b|\bscheme\b.{0,60}\btier\b""")
    private val LABELS = listOf(
        Key.CONTRIBUTION to rx("""total\s+contributions?(?:\s+amount)?|contributions?\s+(?:amount\s+)?(?:till\s+date|made)|total\s+amount\s+invested"""),
        Key.WITHDRAWAL to rx("""total\s+withdrawals?"""),
        Key.GAIN to rx("""total\s+notional\s+gain\s*/\s*loss|notional\s+gain\s*/\s*loss|notional\s+gain|gain\s*/\s*loss|total\s+gain"""),
        Key.VALUE to rx(
            """value\s+of\s+(?:your\s+)?holdings?(?:\s*\(\s*investment\s*\))?|total\s+holding\s+value|holding\s+value|total\s+value(?:\s+of\s+holdings?)?|current\s+value|total\s+valuation|\bvaluation\b""",
        ),
        Key.RETURN to rx("""return\s+on\s+investment(?:\s*\(\s*XIRR\s*\))?|\bXIRR\b"""),
    )
    private val DATE = rx("""\b\d{1,2}[/.-](?:\d{1,2}|[A-Za-z]{3,9})[/.-]\d{2,4}\b|\b\d{1,2}\s+[A-Za-z]{3,9},?\s+\d{4}\b""")
    private val AMOUNT = Regex("""(?<![\d.,])(\d{1,3}(?:,\d{2,3})+\.\d{2}|\d+\.\d{2})(?![\d%]|\s*%)""")
    private val NUMBER = Regex("""(?<![\d.,A-Za-z])\d[\d,]*(?:\.\d+)?(?![\d%A-Za-z])""")

    fun isNps(lines: List<String>): Boolean {
        val head = lines.take(60).joinToString(" ")
        return PRAN.containsMatchIn(head) && IS_NPS.containsMatchIn(head)
    }

    fun read(lines: List<String>, asOf: Long): List<HoldingSnapshot> {
        if (!isNps(lines)) return emptyList()
        val last4 = lines.take(60).firstNotNullOfOrNull { PRAN.find(it)?.groupValues?.get(1) } ?: return emptyList()
        val summary = HashMap<Int, HashMap<Key, Long>>()
        val schemes = HashMap<Int, Long>()
        var tier = 1
        for ((i, line) in lines.withIndex()) {
            val plain = DATE.replace(line, " ")
            val amounts = AMOUNT.findAll(plain).toList()
            // A section heading: "Tier II Account", "Transaction Details - Tier I".
            if (amounts.isEmpty()) TIER.find(line)?.let { tier = tierOf(it.groupValues[1]) }

            val found = HoldingTable.scan(plain, LABELS)
            if (found.isNotEmpty()) {
                val next = lines.getOrNull(i + 1)?.let { DATE.replace(it, " ") }.orEmpty()
                val below = if (HoldingTable.scan(next, LABELS).isEmpty()) AMOUNT.findAll(next).map { it.groupValues[1] }.toMutableList() else mutableListOf()
                val into = summary.getOrPut(tier) { HashMap() }
                for ((idx, entry) in found.withIndex()) {
                    val (key, range) = entry
                    if (key == Key.RETURN) continue
                    val end = found.getOrNull(idx + 1)?.second?.first ?: plain.length
                    val same = AMOUNT.find(plain.substring(range.last + 1, end))?.groupValues?.get(1)
                    val raw = same ?: below.removeFirstOrNull() ?: continue
                    val v = Money.parse(raw)?.minor ?: 0L
                    into.putIfAbsent(key, v)
                }
                continue
            }

            // Scheme-wise rows: "SBI PENSION FUND SCHEME E - TIER I 4,567.1234 45.6789 2,08,623.45".
            if (SCHEME_ROW.containsMatchIn(line)) {
                val nums = NUMBER.findAll(plain).map { it.value.replace(",", "").toDoubleOrNull() }.filterNotNull().toList()
                if (nums.size < 2) continue
                val rowTier = TIER.findAll(line).lastOrNull()?.let { tierOf(it.groupValues[1]) } ?: tier
                val value = consistentValue(nums) ?: nums.last()
                if (value > 0) schemes[rowTier] = (schemes[rowTier] ?: 0L) + Math.round(value * 100)
            }
        }
        val tiers = (summary.keys + schemes.keys).sorted()
        return tiers.mapNotNull { t ->
            val s = summary[t].orEmpty()
            val value = s[Key.VALUE]?.takeIf { it > 0 } ?: schemes[t] ?: return@mapNotNull null
            if (value <= 0) return@mapNotNull null
            val gain = s[Key.GAIN]
            val invested = s[Key.CONTRIBUTION]?.takeIf { it > 0 }?.let { it - (s[Key.WITHDRAWAL] ?: 0L) }?.takeIf { it > 0 }
                ?: gain?.let { value - it }?.takeIf { it > 0 }
            HoldingSnapshot(HoldingKind.NPS, InvestmentParser.npsName(last4, t), InvestmentParser.npsIdentifier(last4, t), null, value, invested, asOf)
        }
    }

    /** The value in "<units> <NAV> <value>": the last number that equals the two before it multiplied. */
    private fun consistentValue(nums: List<Double>): Double? {
        for (k in nums.size - 1 downTo 2) {
            val v = nums[k]
            if (abs(nums[k - 2] * nums[k - 1] - v) <= maxOf(1.0, v * 0.01)) return v
        }
        return null
    }

    private fun tierOf(s: String): Int = if (s.equals("II", true) || s == "2") 2 else 1
}
