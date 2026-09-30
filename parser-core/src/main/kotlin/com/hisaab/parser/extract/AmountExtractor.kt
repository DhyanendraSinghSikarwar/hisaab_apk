package com.hisaab.parser.extract

import com.hisaab.parser.text.Guarded
import com.hisaab.parser.text.rx

/** Finds the transaction amount: the first currency amount that is not a balance, limit, or due figure. */
object AmountExtractor {
    private const val NUMBER = """\d[\d,]*(?:\.\d{1,2})?"""
    private val CURRENCY_AMOUNT = rx("""\b(INR|USD|EUR|GBP|AED|SGD|AUD|CAD|JPY|CHF|SAR|QAR)\s*($NUMBER)""")
    private val BARE_AMOUNT = rx("""\b(?:debited|credited)\s+(?:by|for|with)\s+($NUMBER)\b""")
    private val NON_TXN_CONTEXT = rx("""(?:bal(?:ance)?|lmt|limit|due|outstanding)\b[^0-9]{0,30}$""")
    private const val CONTEXT_WINDOW = 45

    fun extract(text: String): Money? {
        for (m in CURRENCY_AMOUNT.findAll(text)) {
            val start = m.range.first
            val before = text.substring(maxOf(0, start - CONTEXT_WINDOW), start)
            if (NON_TXN_CONTEXT.containsMatchIn(before)) continue
            Money.parse(m.groupValues[2], m.groupValues[1])?.let { return it }
        }
        return BARE_AMOUNT.find(text)?.let { Money.parse(it.groupValues[1]) }
    }
}

/** Available balance of the account, and available limit of a card. */
object BalanceExtractor {
    private val BALANCE = Guarded(
        """(?:\b|(?<=avl)|(?<=avbl))bal(?:ance)?\b[^0-9]{0,40}?\b(?:INR)\s*(-?\d[\d,]*(?:\.\d{1,2})?)""", "bal")
    private val LIMIT = Guarded("""\b(?:lmt|limit)\b[^0-9]{0,30}?\bINR\s*(\d[\d,]*(?:\.\d{1,2})?)""", "lmt", "limit")

    fun balance(text: String, lower: String = text.lowercase()): Long? = BALANCE.find(text, lower)?.let { Money.parseSigned(it.groupValues[1]) }

    fun limit(text: String, lower: String = text.lowercase()): Long? = LIMIT.find(text, lower)?.let { Money.parse(it.groupValues[1])?.minor }
}
