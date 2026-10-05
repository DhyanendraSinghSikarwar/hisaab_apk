package com.hisaab.parser.extract

import com.hisaab.parser.text.Guarded

/** UPI RRN, IMPS/NEFT UTR, or bank transaction id. Uppercased, alphanumeric only. */
object ReferenceExtractor {
    private val SLASHED = Guarded("""\b(?:UPI|IMPS|NEFT|RTGS)/(?:[A-Z0-9]{2,4}/)?([A-Z0-9]{9,22})/""", "upi/", "imps/", "neft/", "rtgs/")
    private val LABELLED = Guarded(
        """\b(?:UPI\s*transaction\s*reference\s*(?:number|no\.?)|(?:UPI|IMPS|NEFT|RTGS)(?:\s*Ref(?:erence|\.)?)?(?:\s*(?:No|Number|ID)\.?)?|""" +
            """RRN|UTR(?:\s*No\.?)?|Refno|Ref(?:erence)?(?:\s*(?:No|Number|ID)\.?|\s*#)?|""" +
            """(?:Txn|Trxn|Transaction)\s*(?:ID|No\.?|Number|Ref(?:erence)?(?:\s*No\.?)?))""" +
            """\s*(?:is\s*)?[:.\-#]?\s*\(?\s*([A-Z0-9]{6,22})\b""",
        "upi", "imps", "neft", "rtgs", "rrn", "utr", "ref", "txn", "trxn", "transaction",
    )
    private const val MIN_DIGITS = 5

    fun extract(text: String, lower: String = text.lowercase()): String? {
        SLASHED.find(text, lower)?.let { return it.groupValues[1].uppercase() }
        var m = LABELLED.find(text, lower)
        while (m != null) {
            val ref = m.groupValues[1]
            // "UPI transaction" also fits the pattern; a real reference is mostly digits. Retry from the next
            // character, so "UPI transaction ID 5268..." still finds the "transaction ID" label inside the miss.
            if (ref.count { it.isDigit() } >= MIN_DIGITS) return ref.uppercase()
            m = LABELLED.regex.find(text, m.range.first + 1)
        }
        return null
    }

    fun normalize(ref: String?): String? =
        ref?.filter { it.isLetterOrDigit() }?.uppercase()?.takeIf { it.length >= 6 }
}
