package com.hisaab.parser

import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source

interface BankParser {
    /** Display name of the bank, e.g. "HDFC Bank". */
    val bankName: String

    /**
     * Keys the registry maps to this parser in O(1): six-character SMS headers ("HDFCBK")
     * and email domains ("hdfcbank.net").
     */
    val senderKeys: Set<String>

    fun canHandle(sender: String, body: String): Boolean

    /** Returns null when the message is not a completed transaction (OTP, promo, failed, reminder...). */
    fun parse(body: String, sender: String, timestamp: Long, source: Source): ParsedTransaction?
}
