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

    /** Words before the other party's number: "to A/c XX5678", "from A/c" on a credit. */
    private val TO_BEFORE = rx("""\bto\s+(?:the\s+)?(?:[\w'’]+\s+){0,2}$""")
    private val PAYER_BEFORE = rx("""\b(?:from|by|remitter|sender)\s+(?:the\s+)?(?:[\w'’]+\s+){0,2}$""")
    /** Always the other party, whatever the direction: "Beneficiary A/c XX9876", "in favour of a/c". */
    private val BENEFICIARY_BEFORE = rx("""\b(?:benef\w*|bene|payee|recipient|in\s+favou?r\s+of)\s*[:\-]?\s*(?:the\s+)?(?:[\w'’]+\s+){0,2}$""")
    private val YOUR = rx("""\b(?:your|ur|own|self)\b""")

    /** A masked number right after these words is the user's phone or UPI ID, not an account. */
    private val PHONE_BEFORE = rx(
        """(?:\b(?:mobile|mob|phone|ph|cell|cellphone|contact|call|whatsapp|registered|upi\s+id|vpa|(?:sms|msg)\s+to)\b""" +
            """[\s:.\-#]*(?:(?:no|number|num|is|linked|of|with)\b[\s:.\-#]*){0,3}|\+91[\s\-]?|\b91-)$""")
    /** "XXXXXX6810@ybl", "9876546810@axl": the digits are part of a UPI ID. */
    private val VPA_AFTER = rx("""^[\w.\-]*@[a-z]""")

    private const val WINDOW = 30

    private class Candidate(val start: Int, val digits: IntRange, val last4: String, val kind: AccountKind, val labelled: Boolean)

    /**
     * The user's account. A number inside a UPI ID or written as a phone number is never taken, nor one
     * labelled as the beneficiary's. With [type], a number written as the other party's ("sent to
     * A/c XX5678", "received from A/c XX9999") is skipped too, so a payee never shows up as the user's
     * account; if the only number in the message is the other party's, the result is null.
     */
    fun extract(text: String, type: TransactionType? = null): AccountRef? {
        for (c in candidates(text)) {
            if (isPhoneOrVpa(text, c) || isOtherParty(text, c, type)) continue
            return AccountRef(c.last4, c.kind)
        }
        return null
    }

    /** Labelled numbers first, then bare masked ones, then two-digit endings; each number once. */
    private fun candidates(text: String): Sequence<Candidate> = sequence {
        val seen = ArrayList<IntRange>(4)
        fun fresh(r: IntRange): Boolean = seen.none { it.first <= r.last && r.first <= it.last }.also { if (it) seen += r }
        for (m in LABELLED.findAll(text)) {
            val g = m.groups[3]!!
            if (!fresh(g.range)) continue
            val isCard = m.groups[2] != null ||
                CARD_WORD.containsMatchIn(text.substring(maxOf(0, m.range.first - 15), m.range.first))
            yield(Candidate(m.range.first, g.range, g.value.takeLast(4), if (isCard) AccountKind.CARD else AccountKind.ACCOUNT, true))
        }
        val masked = MASKED.findAll(text).toList()
        if (masked.isNotEmpty()) {
            val kind = if (CARD_WORD.containsMatchIn(text)) AccountKind.CARD else AccountKind.ACCOUNT
            for (m in masked) {
                val g = m.groups[1]!!
                if (!fresh(g.range)) continue
                yield(Candidate(m.range.first, g.range, g.value, kind, false))
            }
        }
        for (m in SHORT_ENDING.findAll(text)) {
            val g = m.groups[2]!!
            if (!fresh(g.range)) continue
            val kind = if (m.groupValues[1].equals("card", ignoreCase = true)) AccountKind.CARD else AccountKind.ACCOUNT
            yield(Candidate(m.range.first, g.range, g.value, kind, true))
        }
    }

    private fun isPhoneOrVpa(text: String, c: Candidate): Boolean {
        val after = text.substring(c.digits.last + 1, minOf(text.length, c.digits.last + 40))
        if (VPA_AFTER.containsMatchIn(after)) return true
        // A label ("A/c", "Card") says it is an account; only a bare masked number can be a phone.
        if (c.labelled) return false
        return PHONE_BEFORE.containsMatchIn(text.substring(maxOf(0, c.start - WINDOW), c.start))
    }

    private fun isOtherParty(text: String, c: Candidate, type: TransactionType?): Boolean {
        val before = text.substring(maxOf(0, c.start - WINDOW), c.start)
        if (counterparty(BENEFICIARY_BEFORE, before)) return true
        val other = when (type) {
            null -> return false
            TransactionType.CREDIT -> PAYER_BEFORE
            else -> TO_BEFORE
        }
        return counterparty(other, before)
    }

    /** The cue word matches and is not qualified as the user's own ("to your A/c" is the user's). */
    private fun counterparty(cue: Regex, before: String): Boolean {
        val m = cue.find(before) ?: return false
        return !YOUR.containsMatchIn(m.value)
    }

    fun kindOf(text: String): AccountKind = extract(text)?.kind ?: AccountKind.ACCOUNT
}
