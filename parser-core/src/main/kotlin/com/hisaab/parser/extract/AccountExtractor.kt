package com.hisaab.parser.extract

import com.hisaab.parser.text.rx
import com.hisaab.parser.model.AccountKind

data class AccountRef(val last4: String, val kind: AccountKind)

/** Pulls the masked account or card number ("A/c XX1234", "Card ending 5678", "4xxx xxxx xxxx 9012"). */
object AccountExtractor {
    private val LABELLED = rx(
        """(?:\b(a/c|ac|acct|account)|(card))(?:\s*(?:no|number|num)\.?)?(?:\s*(?:ending|ends)(?:\s+(?:with|in))?)?""" +
            """\s*[:.\-]?\s*(?:\d{0,6}[x*#.][x*#.\s]*)?(\d{3,6})\b""")
    private val MASKED = rx("""(?<![\w*])[x*]{2,}(\d{3,4})\b""")
    private val CARD_WORD = rx("""\bcard\b""")
    /** Some issuers print only two digits: "SBI Corporate Card number ending with 96". */
    private val SHORT_ENDING = rx("""\b(card|a/c|ac|acct|account)(?:\s*(?:no|number|num)\.?)?\s+(?:ending|ends)(?:\s+(?:with|in))?\s*[x*]*(\d{2})\b""")

    fun extract(text: String): AccountRef? {
        LABELLED.find(text)?.let { m ->
            val digits = m.groupValues[3]
            val isCard = m.groups[2] != null ||
                CARD_WORD.containsMatchIn(text.substring(maxOf(0, m.range.first - 15), m.range.first))
            return AccountRef(digits.takeLast(4), if (isCard) AccountKind.CARD else AccountKind.ACCOUNT)
        }
        MASKED.find(text)?.let { m ->
            val kind = if (CARD_WORD.containsMatchIn(text)) AccountKind.CARD else AccountKind.ACCOUNT
            return AccountRef(m.groupValues[1], kind)
        }
        SHORT_ENDING.find(text)?.let { m ->
            return AccountRef(m.groupValues[2], if (m.groupValues[1].equals("card", ignoreCase = true)) AccountKind.CARD else AccountKind.ACCOUNT)
        }
        return null
    }

    fun kindOf(text: String): AccountKind = extract(text)?.kind ?: AccountKind.ACCOUNT
}
