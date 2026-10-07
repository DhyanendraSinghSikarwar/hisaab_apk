package com.hisaab.parser.statement

import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.text.rx

/**
 * An EPFO member passbook (PDF from the EPFO portal or UMANG): the member id and the "Closing Balance" row, whose
 * last three figures are the employee share, the employer share and the pension contribution. The value is their
 * sum; the amount invested is that less the interest the passbook credits ("Int. Updated upto 31/03/2026").
 * One holding per member id: "EPF:<member id without separators>", the same key the EPFO SMS gives.
 */
internal object EpfPassbook {
    private val IS_EPF = rx("""employees'?\s*provident\s+fund|\bEPFO\b|member\s+passbook""")
    private val MEMBER = rx("""\bmember\s+id(?:\s*/\s*name)?\s*[:\-]?\s*([A-Z]{2}\s*/?\s*[A-Z]{3}[A-Z0-9/\s]{8,30}?\d{3,})""")
    private val CLOSING = rx("""\bclosing\s+balance\b""")
    private val INTEREST = rx("""\bint(?:erest)?\.?\s+updated\b""")
    private val OPENING = rx("""\bOB\b|opening\s+balance""")
    private val DATE = Regex("""\b\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}\b|\b\d{1,2}[\s-][A-Za-z]{3}[\s-]\d{2,4}\b""")
    private val MONTH_CODE = Regex("""\b(?:0[1-9]|1[0-2])\d{4}\b""")
    private val NUMBER = Regex("""(?<![\w.,/])\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?(?![\w,/])|(?<![\w.,/])\d+(?:\.\d{1,2})?(?![\w.,/%])""")

    fun isEpf(lines: List<String>): Boolean {
        val head = lines.take(40).joinToString(" ")
        return IS_EPF.containsMatchIn(head) && MEMBER.containsMatchIn(head)
    }

    fun read(lines: List<String>, asOf: Long): List<HoldingSnapshot> {
        if (!isEpf(lines)) return emptyList()
        val member = lines.take(40).firstNotNullOfOrNull { MEMBER.find(it)?.groupValues?.get(1) }?.let(InvestmentParser::epfMemberKey) ?: return emptyList()
        var closing: Long? = null
        var interest = 0L
        for ((i, line) in lines.withIndex()) {
            if (CLOSING.containsMatchIn(line)) {
                // The figures can wrap onto the next line.
                val shares = shares(line).ifEmpty { shares(lines.getOrNull(i + 1).orEmpty()) }
                if (shares.isNotEmpty()) closing = shares.sum()
            } else if (INTEREST.containsMatchIn(line) && !OPENING.containsMatchIn(line)) {
                interest += shares(line).sum()
            }
        }
        val value = closing?.takeIf { it > 0 } ?: return emptyList()
        val invested = (value - interest).takeIf { interest > 0 && it > 0 }
        return listOf(HoldingSnapshot(HoldingKind.EPF, "EPF ••${member.takeLast(4)}", "EPF:$member", null, value, invested, asOf))
    }

    /** The employee, employer and pension figures at the end of a row, in paise (fewer when the row has fewer). */
    private fun shares(line: String): List<Long> {
        val plain = MONTH_CODE.replace(DATE.replace(line, " "), " ")
        val nums = NUMBER.findAll(plain).map { it.value }.toList()
        if (nums.isEmpty()) return emptyList()
        return nums.takeLast(3).mapNotNull { n -> n.replace(",", "").toDoubleOrNull()?.let { Math.round(it * 100) } }
    }
}
