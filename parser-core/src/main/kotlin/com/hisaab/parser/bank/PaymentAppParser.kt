package com.hisaab.parser.bank

import com.hisaab.parser.FreeTextParser
import com.hisaab.parser.ParserConfig
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source
import com.hisaab.parser.text.TextNormalizer

/**
 * Payment-app receipts by email or SMS (PhonePe, Google Pay, Paytm, CRED, MobiKwik, Amazon Pay): "Paid to Ravi Kumar,
 * Transaction ID T2610…, UTR …, Debited from HDFC Bank XX1234". Read with the notification rules, so failed, pending,
 * offer and reminder mails are skipped and the UPI reference lets dedup merge it with the bank's own alert.
 */
class PaymentAppParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName: String = "UPI app"
    override val smsHeaders: Set<String> = setOf("PHNEPE", "PHONPE", "PHONEP", "CREDCL", "MOBIKW")
    override val emailDomains: Set<String> = setOf(
        "phonepe.com", "noreply@phonepe.com", "googlepay-noreply@google.com", "payments-noreply@google.com", "noreply-payments@pay.google.com",
        "pay.google.com", "paytm.com", "paytm.in", "cred.club", "mobikwik.com", "amazonpay.in", "freecharge.in", "super.money",
    )

    private val reader = FreeTextParser(config)

    private fun appFor(sender: String): String {
        val s = sender.lowercase()
        return when {
            "phonepe" in s || s.contains("phnepe") || s.contains("phonpe") || s.contains("phonep") -> "PhonePe"
            "google" in s -> "Google Pay"
            "paytm" in s -> "Paytm"
            "cred" in s -> "CRED"
            "mobikwik" in s || "mobikw" in s -> "MobiKwik"
            "amazon" in s -> "Amazon Pay"
            "freecharge" in s -> "Freecharge"
            "super.money" in s -> "super.money"
            else -> "UPI app"
        }
    }

    override fun parse(body: String, sender: String, timestamp: Long, source: Source): ParsedTransaction? {
        val text = if (source == Source.EMAIL) TextNormalizer.trimEmail(TextNormalizer.normalize(body)) else body
        return reader.readNotification(appFor(sender), listOf(text), timestamp, source).tx
    }
}
