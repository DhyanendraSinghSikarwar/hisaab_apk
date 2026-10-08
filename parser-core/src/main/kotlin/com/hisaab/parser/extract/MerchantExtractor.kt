package com.hisaab.parser.extract

import com.hisaab.parser.text.Guarded
import com.hisaab.parser.text.rx
import com.hisaab.parser.model.TransactionType

/** The counterparty as written in the message. [MerchantNormalizer] turns it into a display name. */
data class RawMerchant(val name: String?, val vpa: String?)

object MerchantExtractor {
    private const val BODY = """[A-Z0-9][A-Z0-9 &'*_\-/@.]{0,50}?"""
    private const val END =
        """(?=\s+(?:on|dated|ref|refno|rrn|utr|upi|imps|neft|via|for|using|with|avl|avbl|bal|not|if|from|at|thru|through|and|txn|info|call|is|has|was)\b|\s*[.,;:(|!]|\s+-|$)"""
    private const val NOT_A_NAME =
        """(?!(?:your|you|the|a/c|ac|acct|account|card|mobile|vpa|beneficiary|neft|imps|upi|rtgs|inr|usd|a|an|block|dispute|report|ur|u|inform|avoid|know)\b)"""

    private val SLASHED = Guarded("""\b(?:UPI|IMPS|NEFT|RTGS)/(?:[A-Z0-9]{2,4}/)?[A-Z0-9]{9,22}/([^/]+?)(?=/|\s+(?:Not|Avl|Axis|If)\b|\.(?:\s|$)|$)""", "upi/", "imps/", "neft/", "rtgs/")
    private val VPA_WITH_NAME = Guarded("""\b(?:to|from|by)\s+VPA\s+([\w.\-]+@[\w.\-]+?)(?:\s+(${BODY}))?$END""", "vpa")
    private val BENEFICIARY = Guarded("""\bbeneficiary(?:\s+name)?\s*[:\-]\s*($BODY)$END""", "beneficiary")
    private val AT = Guarded("""\bat\s+$NOT_A_NAME($BODY)$END""", "at ")
    private val VPA = Guarded("""\b([a-z0-9][\w.\-]{1,}@[a-z][a-z0-9]{1,20})\b(?!\.[a-z])""", "@")
    private val TO = Guarded("""\b(?:trf\s+to|transferred\s+to|paid\s+to|sent\s+to|to)\s+$NOT_A_NAME($BODY)$END""", "to ")
    private val FROM = Guarded("""\b(?:from|by)\s+$NOT_A_NAME($BODY)$END""", "from ", "by ")
    private val TOWARDS = Guarded("""\btowards\s+$NOT_A_NAME($BODY)$END""", "towards")
    private val INFO = Guarded("""\bInfo\s*[:\-]\s*(.{2,60}?)(?=\.\s|\s+Avl\b|\s+The\b|$)""", "info")
    private val ACH = Guarded("""\b(?:ACH|NACH|ECS)(?:[\s\-]*(?:DR|D|CR))?[\s\-/]+([A-Z][A-Z0-9 &.'\-]+?)(?=/|-\d|\.(?:\s|$)|\s+Avl\b|$)""", "ach", "ecs")

    private val HAS_LETTER = rx("""[A-Za-z]{2,}""")

    fun extract(text: String, type: TransactionType, lower: String = text.lowercase()): RawMerchant {
        SLASHED.find(text, lower)?.let { m -> valid(m.groupValues[1])?.let { return RawMerchant(it, null) } }
        VPA_WITH_NAME.find(text, lower)?.let { m ->
            return RawMerchant(valid(m.groupValues[2]), m.groupValues[1])
        }
        BENEFICIARY.find(text, lower)?.let { m -> valid(m.groupValues[1])?.let { return RawMerchant(it, null) } }

        val ordered = if (type == TransactionType.CREDIT) {
            listOf(FROM, INFO, VPA, ACH)
        } else {
            listOf(AT, VPA, INFO, ACH, TO, TOWARDS)
        }
        for (rx in ordered) {
            for (m in rx.findAll(text, lower)) {
                val candidate = valid(m.groupValues[1]) ?: continue
                return if (rx === VPA) RawMerchant(null, candidate) else RawMerchant(candidate, vpaIn(candidate))
            }
        }
        return RawMerchant(null, null)
    }

    private fun vpaIn(s: String): String? = VPA.regex.find(s)?.groupValues?.get(1)

    private fun valid(s: String?): String? {
        val t = s?.trim()?.trim('-', '/', '.', ' ') ?: return null
        if (t.length < 2 || !HAS_LETTER.containsMatchIn(t) || GenericPhrases.isGeneric(t)) return null
        return t
    }
}
