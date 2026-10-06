package com.hisaab.parser.extract

import com.hisaab.parser.text.rx
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.TransactionType

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

    /** Words before the other party's number: "to A/c XX5678", "beneficiary a/c", "from A/c" on a credit. */
    private val PAYEE_BEFORE = rx("""\b(?:to|benef\w*|bene|payee|recipient|in\s+favou?r\s+of)\s+(?:the\s+)?(?:\w+\s+){0,2}$""")
    private val PAYER_BEFORE = rx("""\b(?:from|by|remitter|sender)\s+(?:the\s+)?(?:\w+\s+){0,2}$""")
    private val YOUR = rx("""\b(?:your|ur|own|self)\b""")

    /**
     * The user's account. With [type], a number written as the other party's ("sent to A/c XX5678",
     * "received from A/c XX9999") is skipped, so a payee never shows up as the user's account.
     */
    fun extract(text: String, type: TransactionType? = null): AccountRef? {
        if (type == null) return first(text)
        val other = if (type == TransactionType.CREDIT) PAYER_BEFORE else PAYEE_BEFORE
        val mine = LABELLED.findAll(text).firstOrNull { m ->
            val before = text.substring(maxOf(0, m.range.first - 30), m.range.first)
            !other.containsMatchIn(before) || YOUR.containsMatchIn(before)
        }
        if (mine != null) return labelled(text, mine)
        // Only the other party's number is written: better no account than the wrong one.
        return if (LABELLED.containsMatchIn(text)) null else first(text)
    }

    private fun labelled(text: String, m: MatchResult): AccountRef {
        val digits = m.groupValues[3]
        val isCard = m.groups[2] != null ||
            CARD_WORD.containsMatchIn(text.substring(maxOf(0, m.range.first - 15), m.range.first))
        return AccountRef(digits.takeLast(4), if (isCard) AccountKind.CARD else AccountKind.ACCOUNT)
    }

    private fun first(text: String): AccountRef? {
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
