package com.hisaab.parser.rules

import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.text.Guarded
import com.hisaab.parser.text.rx

/**
 * Recognises a credit-card bill payment made from a bank account, wallet or UPI app: money that settles the card, not a spend.
 * Such a payment is stored as a TRANSFER (shown in Transactions as "Credit card bill", never counted as spend or income).
 * Only the bank/app side is judged here; a card's own "payment received" credit is a TRANSFER by [com.hisaab.parser.extract.TypeClassifier].
 */
object CardBill {
    /** Label of the note and merchant a recognised bill payment carries. */
    const val LABEL = "Credit card bill"

    private val BANKS = """(?:axis|hdfc|icici|sbi|kotak|idfc|yes|rbl|indusind|amex|citi|hsbc|au|bob|pnb|federal|standard\s+chartered|sc)"""

    /** Wording of a bank SMS, a payment-app notification or a net-banking email. */
    private val TEXT = Guarded(
        """\b(?:credit\s*card|cc)\s*(?:bill|bil|dues?|payment|pymt|pmt|repayment|outstanding)\b|""" +
            """\bcard\s+(?:bill|dues?)\s+(?:payment|paid|pymt)\b|""" +
            """\btowards\s+(?:your\s+)?(?:[A-Za-z&.]+\s+){0,3}credit\s*card\b|""" +
            """\bto\s+(?!your\b)(?:[A-Za-z&.]+\s+){0,3}credit\s*card\s+(?:no\.?\s*)?(?:x+|\*+)\d{4}\b|""" +
            """\b$BANKS\s*(?:bank\s+)?(?:credit\s+)?card\s+(?:bill|payment|pymt|dues?)\b|""" +
            """\bbank\s+(?:ltd|limited)\.?\s+(?:credit\s+)?cards?\b|""" +
            """\b(?:axis|hdfc|icici|sbi|kotak|idfc|yes|rbl|indusind|amex|au)(?:cc|creditcard)\b|""" +
            """\b(?:billdesk|bbps|bil)\W{0,3}(?:cc|credit\s*card)\b|""" +
            """\bcred\s*club\b|\bcred\.club\b|\bdreamplug\b""",
        "credit", "cc", "card", "cred", "dreamplug",
    )

    /** A payee that is a card issuer's card account, by name. "Axis Gold", "Credit Bazaar" or "CRED cashback store" are not. */
    private val NAME = rx(
        """^(?:[a-z&.]+\s+){0,4}credit\s*cards?(?:\s+(?:bill|payment|payments|dues?|no\.?|x+\d*|\*+\d*|\d{4}))*$|""" +
            """^(?:[a-z&.]+\s+){0,2}(?:cc|card)\s+(?:bill|payment|payments|dues?)(?:\s+\d{4})?$|""" +
            """^(?:$BANKS)\s*(?:cc|creditcard)$|^(?:sbi\s+cards?(?:\s+(?:and\s+payment\s+services|payment|payments))?|sbi\s*card)$|""" +
            """^cred(?:\s+club)?$|^dreamplug(?:\s+technologies)?$|^(?:card\s*payment|cardpayment|cc\s*payment)$|""" +
            """^[a-z&.]+(?:\s+[a-z&.]+){0,2}\s+bank\s+(?:ltd|limited)\.?\s+(?:credit\s+)?cards?$""",
    )

    private val ISSUER = rx("""$BANKS""")

    private val VPA_TOKENS = setOf(
        "creditcard", "cardpayment", "cardpay", "ccpay", "ccbill", "cardbill", "ccpayment",
        "axiscc", "hdfccc", "icicicc", "sbicc", "kotakcc", "idfccc", "yescc", "rblcc", "indusindcc", "amexcc", "aucc", "sbicard", "credclub",
    )

    private val PAID_TO_CARD = rx("""\b(?:paid|sent|payment)\b.{0,40}?\bto\s+(?!your\b)(?:[A-Za-z&.]+\s+){0,3}credit\s*cards?\b""")

    /** A payment app's notification that says the money went to a credit card ("Paid ₹6,774 to Axis Bank Credit Card"). */
    fun paidToCard(text: String): Boolean = PAID_TO_CARD.containsMatchIn(text)

    /** True when the message text reads as a card bill being paid from a bank account or app. */
    fun inText(text: String, lower: String = text.lowercase()): Boolean = TEXT.containsMatchIn(text, lower)

    /** True when the payee is a credit card or its issuer's payment collector: by display name or UPI id. */
    fun isPayee(name: String?, upiId: String?): Boolean {
        val n = name?.lowercase()?.replace(Regex("""[^a-z0-9&.* ]+"""), " ")?.replace(Regex("""\s+"""), " ")?.trim()
        if (!n.isNullOrEmpty() && NAME.containsMatchIn(n)) return true
        val local = upiId?.lowercase()?.substringBefore('@')?.takeIf { it.isNotEmpty() } ?: return false
        val tokens = local.split('.', '_', '-', '+').filter { it.isNotEmpty() }.map { it.trimEnd { c -> c.isDigit() } }
        if (tokens.any { it in VPA_TOKENS }) return true
        // A bare "cc" is a card only beside an issuer ("cc.axis"); "cc.sharma" is a person.
        if ("cc" in tokens && tokens.any { ISSUER.matches(it) }) return true
        return tokens.size >= 2 && tokens[0] == "cred" && tokens[1] == "club"
    }

    /**
     * A bill payment: the text says so, or the payee is a card. A payee match is ignored on a card's own debit ([onCard]): a
     * card cannot pay its own bill, so "Credit Card Cafe" there is a merchant. (The wording of a bank SMS may itself say "card".)
     */
    fun isBill(text: String?, name: String?, upiId: String?, onCard: Boolean = false): Boolean =
        (text != null && inText(text)) || (!onCard && isPayee(name, upiId))

    /** [tx] as a bill payment when it is a DEBIT that pays a credit card. Anything else is returned as it is. Idempotent. */
    fun mark(tx: ParsedTransaction, rawText: String?): ParsedTransaction {
        if (tx.type != TransactionType.DEBIT) return tx
        if (!isBill(rawText, tx.merchant, tx.upiId, onCard = tx.accountKind == AccountKind.CARD)) return tx
        return tx.copy(type = TransactionType.TRANSFER, category = Category.TRANSFER, merchant = tx.merchant ?: LABEL)
    }
}
