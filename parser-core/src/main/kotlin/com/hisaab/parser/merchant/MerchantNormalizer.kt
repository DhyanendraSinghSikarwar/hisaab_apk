package com.hisaab.parser.merchant

import com.hisaab.parser.text.rx
import com.hisaab.parser.extract.RawMerchant
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType

data class NormalizedMerchant(val name: String?, val category: Category)

/** Turns "ACH D- TP ACH ZERODHA-1234567" or "swiggy.upi@axisbank" into a display name and a category. */
object MerchantNormalizer {
    private val SEGMENT_SPLIT = rx("""\s*[/|]\s*""")
    private val HYPHEN_SPLIT = rx("""\s*-\s*""")
    private val CODE_LIKE = rx("""^(?:.*\d{4,}.*|[A-Z]{4}0[A-Z0-9]{6}|[X*\d\s]+|P2[AMP]|[A-Z]{2}\d+)$""")
    private val NON_WORD = rx("""[^A-Z0-9&' ]+""")
    private val SPACES = rx("""\s+""")

    private val NOISE_PREFIX = setOf(
        "ACH", "NACH", "ECS", "TP", "D", "DR", "CR", "UPI", "IMPS", "NEFT", "RTGS", "POS", "VPA", "MMT", "BIL", "INF",
        "TO", "FROM", "BY", "SI", "MANDATE", "AUTOPAY", "PAYMENT", "TXN", "TRF", "TRANSFER", "PAY", "CMS", "ATD", "ONL", "VIN",
    )
    private val NOISE_SUFFIX = setOf("PVT", "LTD", "PRIVATE", "LIMITED", "LLP", "INC", "CO", "UPI", "PAYMENTS", "PAYMENT")
    private val VPA_NOISE = setOf("UPI", "PAY", "PAYTM", "RAZORPAY", "RZP", "BHARATPE", "PAYU", "CASHFREE", "MERCHANT", "OKAXIS", "OKICICI")

    /** "UBERIND13513699", "AMAZONPAY1234": letters glued to a terminal or order number. */
    private val IFSC = rx("""[A-Z]{4}0[A-Z0-9]{6}""")
    private val NAME_THEN_ID = rx("""^([A-Z]{3,}?)(?:IND|INDIA|IN)?\d{4,}$""")

    private val SALARY = rx("""\b(?:SALARY|SAL\s+CR|PAYROLL)\b""")

    fun normalize(raw: RawMerchant, type: TransactionType, channel: Channel, fullText: String): NormalizedMerchant {
        if (channel == Channel.ATM && type == TransactionType.DEBIT) return NormalizedMerchant("ATM", Category.CASH)

        val name = raw.name?.let(::clean) ?: raw.vpa?.let(::fromVpa)
        val upper = name?.uppercase()
        val tokens = upper?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
        val known = upper?.let { MerchantDirectory.lookup(it, tokens) }
        val display = known?.name ?: name?.let(::titleCase)

        val category = when {
            type == TransactionType.TRANSFER -> Category.TRANSFER
            type == TransactionType.INVESTMENT -> Category.INVESTMENT
            type == TransactionType.CREDIT -> when {
                SALARY.containsMatchIn(fullText.uppercase()) -> Category.SALARY
                fullText.contains("refund", ignoreCase = true) || fullText.contains("revers", ignoreCase = true) -> Category.REFUND
                else -> Category.INCOME
            }
            known != null -> known.category
            else -> MerchantDirectory.categoryByKeyword(tokens) ?: Category.OTHER
        }
        return NormalizedMerchant(display, category)
    }

    /** The directory category for a raw merchant string, used to promote DEBIT to INVESTMENT. */
    fun categoryOf(raw: RawMerchant): Category? {
        val name = raw.name?.let(::clean) ?: raw.vpa?.let(::fromVpa) ?: return null
        val upper = name.uppercase()
        return MerchantDirectory.lookup(upper, upper.split(' '))?.category
    }

    internal fun clean(raw: String): String? {
        var s = raw.uppercase()
        if ('@' in s) return fromVpa(s)
        if (!IFSC.matches(s.trim())) NAME_THEN_ID.matchEntire(s.trim())?.let { s = it.groupValues[1] }
        // "NEFT/CITIN5202.../ACME CORP" and "CITIN5202...-ACME CORP": keep the first segment that is a name.
        val segments = s.split(SEGMENT_SPLIT).flatMap { seg ->
            val parts = seg.split(HYPHEN_SPLIT)
            if (parts.size > 1 && parts.any { CODE_LIKE.matches(it.trim()) }) parts else listOf(seg)
        }
        s = segments.map { it.trim() }.firstOrNull { it.isNotEmpty() && !CODE_LIKE.matches(it) && !isOnlyNoise(it) } ?: return null
        s = NON_WORD.replace(s, " ")
        val words = SPACES.split(s.trim()).filter { it.isNotEmpty() }.toMutableList()
        while (words.isNotEmpty() && words.first() in NOISE_PREFIX) words.removeAt(0)
        while (words.size > 1 && words.last() in NOISE_SUFFIX) words.removeAt(words.lastIndex)
        val result = words.joinToString(" ")
        return result.takeIf { it.length >= 2 && it.any(Char::isLetter) }
    }

    private fun isOnlyNoise(seg: String): Boolean =
        NON_WORD.replace(seg, " ").trim().split(SPACES).all { it in NOISE_PREFIX || it.isEmpty() }

    internal fun fromVpa(vpa: String): String? {
        val local = vpa.substringBefore('@').uppercase()
        val words = local.split('.', '_', '-').filter { w -> w.isNotEmpty() && w !in VPA_NOISE && w.any(Char::isLetter) }
            .map { it.trimEnd { c -> c.isDigit() } }
            .filter { it.length >= 2 }
        return words.joinToString(" ").takeIf { it.isNotEmpty() }
    }

    private fun titleCase(s: String): String =
        s.split(' ').joinToString(" ") { w ->
            if (w.length <= 3 && w.all { it.isUpperCase() || it.isDigit() } && w in KEEP_UPPER) w
            else w.lowercase().replaceFirstChar { it.titlecase() }
        }

    private val KEEP_UPPER = setOf("ATM", "IRCTC", "LIC", "KFC", "HP", "BP", "SBI", "HDFC", "ICICI", "IDFC", "PNB", "AU", "NSE", "BSE")
}
