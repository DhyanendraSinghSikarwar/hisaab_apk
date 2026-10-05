package com.hisaab.parser.statement

import com.hisaab.parser.extract.Money
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.registry.SenderKeys
import com.hisaab.parser.text.TextNormalizer
import com.hisaab.parser.text.rx

/**
 * Investment balances that arrive by SMS, such as EPFO's passbook balance. These are not bank
 * transactions: an employer's PF contribution never touches the user's account. They update a holding instead.
 */
object InvestmentParser {
    // "Dear 10XXXXXX1234, your passbook balance against MH/BAN/0012345/000/0001234 is Rs. 1,23,456/-. Contribution of ..."
    private val EPF_BALANCE = rx("""passbook\s+balance\s+against\s+(\S+?)\s+is\s+INR\s*([\d,]+(?:\.\d{1,2})?)""")
    private val EPF_ALT = rx("""\b(?:EPF|PF)\s+(?:a/c\s+)?balance\s+(?:is|of)\s+INR\s*([\d,]+(?:\.\d{1,2})?)""")

    // "Your NPS a/c PRAN XXXXXXXX1234 holding value as on 30-09-2026 is Rs. 4,56,789.12"
    private val NPS_VALUE = rx("""\bPRAN\b[^0-9]{0,20}[x*\d]*?(\d{4})\b.{0,120}?\b(?:holding|balance|value|corpus)\b.{0,60}?\bINR\s*([\d,]+(?:\.\d{1,2})?)""")

    /** SMS senders whose messages are investment updates. Looked at before any body is read. */
    fun accepts(sender: String): Boolean = SenderKeys.smsHeader(sender).let {
        it.startsWith("EPFO") || it == "EPFIND" || it == "UMANGB" || it.contains("NPS") || it.contains("PRAN") || it == "PFRDAI" || it == "NSDLPR"
    }

    fun parse(body: String, sender: String, timestamp: Long): HoldingSnapshot? {
        if (!accepts(sender)) return null
        val text = TextNormalizer.normalize(body)
        EPF_BALANCE.find(text)?.let { m ->
            val member = m.groupValues[1].trimEnd('.', ',')
            val value = Money.parse(m.groupValues[2])?.minor ?: return null
            return HoldingSnapshot(HoldingKind.EPF, "EPF ••${member.takeLast(4)}", "EPF:$member", null, value, null, timestamp)
        }
        NPS_VALUE.find(text)?.let { m ->
            val value = Money.parse(m.groupValues[2])?.minor ?: return null
            return HoldingSnapshot(HoldingKind.NPS, "NPS ••${m.groupValues[1]}", "NPS:${m.groupValues[1]}", null, value, null, timestamp)
        }
        EPF_ALT.find(text)?.let { m ->
            val value = Money.parse(m.groupValues[1])?.minor ?: return null
            return HoldingSnapshot(HoldingKind.EPF, "EPF", "EPF:default", null, value, null, timestamp)
        }
        return null
    }
}
