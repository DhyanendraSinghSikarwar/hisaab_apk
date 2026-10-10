package com.hisaab.parser

import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.registry.ParserRegistry
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Credit-card spend, refund and international alerts from every issuer, in each layout generation, by SMS and by email.
 * The card's last four digits, the merchant and the available limit must come from the shared extractors, never from
 * the helpline number in the "Not you? ... BLOCK" boilerplate.
 */
class CardAlertsTest {
    /**
     * One card alert. [merchant] is a case-insensitive fragment of the expected merchant (null: any, or none).
     * [limit] is the available credit limit in major units.
     */
    data class Case(
        val issuer: String,
        val sender: String,
        val body: String,
        val amount: String,
        val last4: String,
        val merchant: String? = null,
        val limit: String? = null,
        val currency: String = "INR",
        val type: TransactionType = TransactionType.DEBIT,
        val source: Source = Source.SMS,
    ) {
        override fun toString() = "$issuer ${source.name.lowercase()} ${type.name.lowercase()}: ${body.replace('\n', ' ').take(60)}"
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `card alert parses`(c: Case) {
        val error = check(c)
        assertNull(error, error)
    }

    @Test
    fun `card issuer senders pass the pre-filter`() {
        for (s in SENDERS) assertTrue(registry.accepts(s), "pre-filter drops $s")
        for (s in listOf("VM-AMAZON", "AD-SWIGGY", "+919876543210", "JD-FLPKRT")) assertFalse(registry.accepts(s), s)
    }

    @Test
    fun `corpus covers every issuer`() {
        val perIssuer = cases().groupingBy { it.issuer }.eachCount()
        for ((issuer, n) in perIssuer) assertTrue(n >= 2, "$issuer has only $n fixtures")
        assertNotNull(perIssuer["Axis"])
    }

    companion object {
        private val registry = ParserRegistry.default()
        private val received = LocalDateTime.of(2026, 10, 8, 12, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        private val JUNK = Regex("""not\s+you|block|dispute|report|call\s|\d{6,}|sms\s""", RegexOption.IGNORE_CASE)

        private fun minor(major: String): Long = BigDecimal(major.replace(",", "")).movePointRight(2).toLong()

        /** The first thing that is wrong with how [c] parsed, or null. Also used by the scratch probe. */
        fun check(c: Case): String? {
            val tx = registry.parse(c.body, c.sender, received, c.source) ?: return "not parsed: $c"
            if (tx.type != c.type) return "type ${tx.type} != ${c.type}: $c"
            if (tx.amountMinor != minor(c.amount)) return "amount ${tx.amountMinor} != ${minor(c.amount)}: $c"
            if (tx.currency != c.currency) return "currency ${tx.currency} != ${c.currency}: $c"
            if (tx.accountKind != AccountKind.CARD) return "kind ${tx.accountKind}: $c"
            if (tx.accountLast4 != c.last4) return "last4 ${tx.accountLast4} != ${c.last4}: $c"
            if (c.merchant != null && tx.merchant?.contains(c.merchant, ignoreCase = true) != true) return "merchant '${tx.merchant}' lacks '${c.merchant}': $c"
            if (tx.merchant != null && JUNK.containsMatchIn(tx.merchant!!)) return "merchant '${tx.merchant}' is boilerplate: $c"
            if (c.limit != null && tx.availableLimitMinor != minor(c.limit)) return "limit ${tx.availableLimitMinor} != ${minor(c.limit)}: $c"
            if (tx.balanceMinor != null) return "card alert read a bank balance ${tx.balanceMinor}: $c"
            return null
        }

        private val SENDERS = listOf(
            "VK-AXISBK-S", "JD-HDFCBK-S", "AX-ICICIB", "AD-SBICRD", "VM-KOTAKB", "VM-IDFCFB", "VM-YESBNK", "VM-RBLCRD", "BZ-INDUSB",
            "AD-AMEXIN", "VM-HSBCIN", "AD-SCBNKS", "VM-CITIIN", "VM-FEDBNK", "JM-ONECRD", "VM-SLCEIT", "VM-BOBCRD", "VM-CANBNK",
            "VM-PNBSMS", "VM-CSBANK", "VM-IDBIBK", "VM-UNIONB", "VM-BOIIND", "VM-AUBANK",
        )

        private fun sms(issuer: String, sender: String, body: String, amount: String, last4: String, merchant: String? = null,
                        limit: String? = null, currency: String = "INR", type: TransactionType = TransactionType.DEBIT) =
            Case(issuer, sender, body, amount, last4, merchant, limit, currency, type, Source.SMS)

        private fun mail(issuer: String, sender: String, body: String, amount: String, last4: String, merchant: String? = null,
                        limit: String? = null, currency: String = "INR", type: TransactionType = TransactionType.DEBIT) =
            Case(issuer, sender, body, amount, last4, merchant, limit, currency, type, Source.EMAIL)

        private val CR =TransactionType.CREDIT

        @JvmStatic
        fun cases(): List<Case> = listOf(
            // ---- Axis ----
            sms("Axis", "VK-AXISBK-S", "Spent INR 1234.56\nAxis Bank Card no. XX4821\n07-10-26 23:16:18 IST\nCroma Retail\nAvl Limit: INR 185432.10\nNot you? SMS BLOCK 4821 to 919951860002", "1234.56", "4821", "Croma", "185432.10"),
            sms("Axis", "VK-AXISBK-S", "Spent INR1234.56\nAxis Bank Card no. XX4821\n07-10-26 23:16:18 IST\nCroma Retail\nAvl Limit: INR 1,85,432.10\nNot you? SMS BLOCK 4821 to 919951860002", "1234.56", "4821", "Croma", "185432.10"),
            sms("Axis", "VM-AXISBK","Spent. Card no. XX1234. INR 500. 05-10-26 12:30:11. AMAZON. Avl Lmt INR 95500. SMS BLOCK 1234 to 919951860002, if not you - Axis Bank", "500", "1234", "Amazon", "95500"),
            sms("Axis", "VM-AXISBK", "Spent\nCard no. XX7890\nINR 4500\n24-09-26 20:11:05 IST\nAMAZON PAY IN\nAvl Lmt INR 95500\nSMS BLOCK 7890 to 919951860002, if not you - Axis Bank", "4500", "7890", "Amazon", "95500"),
            sms("Axis", "AX-AXISBK-S", "Transaction Alert: INR 1,200.00 spent on Axis Bank Credit Card XX0567 at ZOMATO on 05-10-26 12:30:11 IST. Avl Limit: INR 98,800.00. Not you? SMS BLOCK 0567 to 919951860002", "1200", "0567", "Zomato", "98800"),
            sms("Axis", "AX-AXISBK-S", "Transaction alert on Axis Bank Credit Card no. XX0567 for INR 1,200.00 at ZOMATO on 05-10-26 12:30:11 IST. Avl Limit INR 98,800.00", "1200", "0567", "Zomato", "98800"),
            sms("Axis", "VK-AXISBK-S", "Spent USD 25\nAxis Bank Card no. XX4821\n05-10-26 23:16:18 IST\nOPENAI\nAvl Limit: INR 185432.10\nNot you? SMS BLOCK 4821 to 919951860002", "25", "4821", "Openai", "185432.10", "USD"),
            sms("Axis", "VK-AXISBK-S", "Refund of INR 1234.56\nAxis Bank Card no. XX4821\n08-10-26 09:05:11 IST\nCroma Retail\nAvl Limit: INR 186666.66", "1234.56", "4821", null, "186666.66", "INR", CR),
            sms("Axis", "VK-AXISBK-S", "Spent INR 1,499\nAxis Bank Credit Card no. XX4821\n07-10-26 23:16:18 IST\nSwiggy Ltd\nAvl Limit: INR 1,83,933.10\nNot you? SMS BLOCK 4821 to 919951860002", "1499", "4821", "Swiggy", "183933.10"),
            mail("Axis", "Axis Bank Alerts <alerts@axisbank.com>", "Dear Customer, INR 1,200.00 was spent on your Axis Bank Credit Card no. XX0567 at ZOMATO on 05-10-2026 12:30:11. Available limit: INR 98,800.00.", "1200", "0567", "Zomato", "98800"),

            // ---- HDFC ----
            sms("HDFC", "JD-HDFCBK-S", "Spent Rs.276.53 On HDFC Bank Card 2779 At UBERIND13513699 On 2026-10-01:18:24:32.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 2779 to 7308080808", "276.53", "2779", "Uber"),
            sms("HDFC", "JD-HDFCBK-S", "Rs.1,499.00 spent on HDFC Bank Credit Card XX1234 at AMAZON on 05-10-26 12:30:11. Avl bal/limit: Rs.1,98,501.00. Not you? Call 18002586161", "1499", "1234", "Amazon", "198501"),
            sms("HDFC", "VM-HDFCBK", "Thank you for using your HDFC Bank Credit Card ending 1234 for Rs 500 at SWIGGY on 05-10-26.", "500", "1234", "Swiggy"),
            sms("HDFC", "VM-HDFCBK", "Rs.500.00 debited via UPI from HDFC Bank RuPay Credit Card XX1234 to VPA swiggy@icici SWIGGY on 05-10-26. Ref 627012345678", "500", "1234", "Swiggy"),
            sms("HDFC", "VM-HDFCBK", "Update! INR 640.00 debited from HDFC Bank RuPay Credit Card XX1234 on 05-Oct-26 towards zomato@ybl. UPI Ref 627012345678", "640", "1234", "Zomato"),
            sms("HDFC", "VM-HDFCBK", "HDFC Bank Credit Card ending 1234: Transaction of INR 2,499.00 at AMAZON PAY INDIA on 05-Oct-2026. Available Credit Limit INR 3,40,000.00", "2499", "1234", "Amazon", "340000"),
            sms("HDFC", "JD-HDFCBK-S", "Spent USD 20.00 On HDFC Bank Card 2779 At ANTHROPIC On 2026-10-01:10:00:00.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 2779 to 7308080808", "20", "2779", "Anthropic", null, "USD"),
            sms("HDFC", "VM-HDFCBK", "Refund of Rs.1,299.00 has been credited to your HDFC Bank Credit Card XX1234 from FLIPKART on 05-10-26.", "1299", "1234", "Flipkart", null, "INR", CR),
            sms("HDFC", "VM-HDFCBK", "Rs.24,999.00 spent on HDFC Bank Credit Card XX1234 at CROMA on 05-10-26 12:30:11. Convert to EMI: SMS EMI 1234 to 5676712. Avl limit Rs.2,10,000.00", "24999", "1234", "Croma", "210000"),
            sms("HDFC", "VM-HDFCBK", "Spent Rs.2,499.00 On HDFC Bank Card 5678 At RAZ*AMAZON On 2026-09-24:21:14:05 Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 5678 to 7308080808", "2499", "5678", "Amazon"),
            mail("HDFC", "HDFC Bank InstaAlerts <alerts@hdfcbank.net>", "Dear Customer, Rs.1,499.00 has been debited from your HDFC Bank Credit Card ending 1234 towards AMAZON on 05 Oct, 2026 at 12:30:11. If you did not make this transaction, call 18002586161.", "1499", "1234", "Amazon"),

            // ---- ICICI ----
            sms("ICICI", "AX-ICICIB", "INR 1,249.00 spent using ICICI Bank Card XX4006 on 05-Oct-26 on SWIGGY. Avl Lmt: INR 2,92,578.00. To dispute call 18002662/SMS BLOCK 4006 to 9215676766", "1249", "4006", "Swiggy", "292578"),
            sms("ICICI", "AX-ICICIB", "Your ICICI Bank Credit Card XX4006 has been used for a transaction of INR 2,345.00 on Oct 05, 2026 at 03:12:44. Info: AMAZON PAY. The Available Credit Limit on your card is INR 1,50,000.00 and Total Credit Limit is INR 3,00,000.00.", "2345", "4006", "Amazon", "150000"),
            sms("ICICI", "AX-ICICIB", "USD 15.00 spent using ICICI Bank Card XX4006 on 05-Oct-26 on ANTHROPIC. Avl Lmt: INR 2,92,578.00. To dispute call 18002662", "15", "4006", "Anthropic", "292578", "USD"),
            sms("ICICI", "AX-ICICIB", "Dear Customer, your ICICI Bank Credit Card XX4006 has been credited with INR 1,249.00 on 06-Oct-26. Info: Refund-SWIGGY.", "1249", "4006", null, null, "INR", CR),
            sms("ICICI", "AX-ICICIB", "INR 5,000.00 spent on ICICI Bank Card XX4006 on 05-Oct-26 at MAKEMYTRIP. Avl Lmt: INR 1,00,000.00", "5000", "4006", "Makemytrip", "100000"),
            mail("ICICI", "ICICI Bank <credit_cards@icicibank.com>", "Dear Customer, Your ICICI Bank Credit Card XX4006 has been used for a transaction of INR 1,249.00 on Oct 05, 2026 at 06:15:10 PM. Info: SWIGGY. The Available Credit Limit on your card is INR 2,92,578.00.", "1249", "4006", "Swiggy", "292578"),

            // ---- SBI Card ----
            sms("SBI", "AD-SBICRD", "Rs.1,234.00 spent on your SBI Card ending 0096 at FLIPKART on 05/10/26. Trxn. not done by you? Report at https://sbicard.com/Dispute", "1234", "0096", "Flipkart"),
            sms("SBI", "AD-SBICRD", "USD 200.00 was spent on your SBI Corporate Card number ending with 96 at ANTHROPIC on 01/10/26. Trxn. not done by you? Report at https://sbicard.com/Dispute", "200", "96", "Anthropic", null, "USD"),
            sms("SBI", "AD-SBICRD", "Rs.2,500.00 spent on your SBI Card XXXX0096 at AMAZON on 05/10/26. Trxn. not done by you? Report at https://sbicard.com/Dispute", "2500", "0096", "Amazon"),
            sms("SBI", "AD-SBICRD", "Rs.12,000.00 spent on your SBI Card ending 0096 at CROMA on 05/10/26. Convert to EMI: SMS EMI 0096 to 56767. Trxn. not done by you? Report at https://sbicard.com/Dispute", "12000", "0096", "Croma"),
            sms("SBI", "AD-SBICRD", "Rs.1,234.00 credited to your SBI Card ending 0096 on 06/10/26 against transaction reversal at FLIPKART.", "1234", "0096", null, null, "INR", CR),
            mail("SBI", "SBI Card <statements@sbicard.com>", "Dear Cardholder, Thank you for using your SBI Card ending 0096 for Rs.1,234.00 at FLIPKART on 05/10/26.", "1234", "0096", "Flipkart"),

            // ---- Kotak ----
            sms("Kotak", "VM-KOTAKB", "Rs.1,234.00 spent on your Kotak Credit Card XX1234 at SWIGGY on 05-Oct-26. Avl limit Rs.1,00,000.00. Not you? Call 18602662666", "1234", "1234", "Swiggy", "100000"),
            sms("Kotak", "VM-KOTAKB", "Spent Rs.1,234.00 on Kotak Credit Card 1234 at AMAZON on 05-10-2026. Available limit Rs. 98766.00. If not you call 18602662666", "1234", "1234", "Amazon", "98766"),
            sms("Kotak", "VM-KOTAKB", "Transaction of Rs.899.00 on Kotak Credit Card xx2345 at SWIGGY on 05/10/2026. Avl limit Rs.45,000. Not you? Call 18602662666", "899", "2345", "Swiggy", "45000"),
            sms("Kotak", "VM-KOTAKB", "USD 30.00 spent on your Kotak Credit Card XX1234 at ANTHROPIC on 05-Oct-26. Avl limit Rs.95,000.00.", "30", "1234", "Anthropic", "95000", "USD"),
            mail("Kotak", "Kotak Bank <alerts@kotak.com>", "Dear Customer, your Kotak Credit Card xx1234 has been used for INR 500.00 at ZOMATO on 05-Oct-26.", "500", "1234", "Zomato"),

            // ---- IDFC FIRST ----
            sms("IDFC", "VM-IDFCFB", "Your IDFC FIRST Bank Credit Card XX1234 was used for INR 500 at ZOMATO on 05-Oct-26. Avl bal INR 99,500.00", "500", "1234", "Zomato", "99500"),
            sms("IDFC", "VM-IDFCFB", "INR 750.00 spent on your IDFC FIRST Bank Credit Card ending XX7788 at SWIGGY on 24-SEP-2026 at 13:20. Avl Limit: INR 99,250.00", "750", "7788", "Swiggy", "99250"),
            sms("IDFC", "VM-IDFCFB", "USD 12.00 spent on your IDFC FIRST Bank Credit Card ending 7788 at NETFLIX on 05-Oct-26. Avl Limit: INR 99,250.00", "12", "7788", "Netflix", "99250", "USD"),
            mail("IDFC", "IDFC FIRST Bank <alerts@idfcfirstbank.com>", "Dear Customer, your IDFC FIRST Bank Credit Card XX1234 was used for INR 500.00 at ZOMATO on 05-Oct-26. Avl limit INR 99,500.00", "500", "1234", "Zomato", "99500"),

            // ---- Yes Bank ----
            sms("Yes", "VM-YESBNK", "Rs.1,500.00 spent on YES BANK Credit Card ending 1234 at AMAZON on 05-10-2026. Avl Lmt Rs.50,000.00", "1500", "1234", "Amazon", "50000"),
            sms("Yes", "VM-YESBNK", "Txn of INR 800.00 on YES BANK Credit Card XX1234 at SWIGGY on 05-10-26. Available limit INR 49,200.00", "800", "1234", "Swiggy", "49200"),
            mail("Yes", "YES BANK <alerts@yesbank.in>", "Dear Customer, YES BANK Credit Card XX1234 has been used for INR 800.00 at SWIGGY on 05-Oct-26.", "800", "1234", "Swiggy"),

            // ---- AU ----
            sms("AU", "VM-AUBANK", "Your AU Bank Credit Card XX1234 was used for INR 500.00 at ZOMATO on 05-10-26. Available limit INR 45,000.00", "500", "1234", "Zomato", "45000"),
            sms("AU", "VM-AUBANK", "INR 500.00 spent on AU Small Finance Bank Credit Card ending 1234 at ZOMATO on 05-10-26.", "500", "1234", "Zomato"),
            mail("AU", "AU Small Finance Bank <alerts@aubank.in>", "Dear Customer, your AU Bank Credit Card XX1234 was used for INR 500.00 at ZOMATO on 05-Oct-26.", "500", "1234", "Zomato"),

            // ---- RBL ----
            sms("RBL", "VM-RBLCRD", "Dear Customer, you have made a transaction of INR 1,500.00 at AMAZON on your RBL Bank Credit Card XX1122 on 05-10-2026. Available limit INR 48,500.00", "1500", "1122", "Amazon", "48500"),
            sms("RBL", "VM-RBLCRD", "INR 1,500.00 spent on RBL Bank Credit Card ending 1122 at AMAZON on 05/10/26.", "1500", "1122", "Amazon"),
            sms("RBL", "VM-RBLCRD", "Rs. 2,000 has been spent on your RBL Bank Credit Card ending with 1122 at SWIGGY on 05-10-2026.", "2000", "1122", "Swiggy"),

            // ---- IndusInd ----
            sms("IndusInd", "BZ-INDUSB", "Your IndusInd Bank Credit Card ending 6789 has been used for INR 1,100.00 at ZOMATO on 24/09/26 at 21:30. Avl limit INR 1,98,900. If not done by you, call 18602677777", "1100", "6789", "Zomato", "198900"),
            sms("IndusInd", "BZ-INDUSB", "INR 900.00 spent on your IndusInd Bank Credit Card XX6789 at SWIGGY on 05-10-2026. Avl Limit INR 1,99,100.00", "900", "6789", "Swiggy", "199100"),
            mail("IndusInd", "IndusInd Bank <alerts@indusind.com>", "Dear Customer, your IndusInd Bank Credit Card ending 6789 has been used for INR 1,100.00 at ZOMATO on 24/09/26.", "1100", "6789", "Zomato"),

            // ---- American Express ----
            sms("Amex", "AD-AMEXIN", "Alert: You've spent INR 1,200.00 on your AMEX card ** 12345 at ZOMATO on 5 October 2026 at 03:45 PM IST. Call 18004190691 if this was not made by you.", "1200", "2345", "Zomato"),
            sms("Amex", "AD-AMEXIN", "Alert: You've spent INR 2,345.00 on your AMEX card ** 41005 at AMAZON on 24 September 2026 at 09:10 PM IST. Call 18004190691 if this was not made by you.", "2345", "1005", "Amazon"),
            sms("Amex", "AD-AMEXIN", "Alert: You've spent USD 49.00 on your AMEX card ** 12345 at OPENAI on 5 October 2026 at 03:45 PM IST. Call 18004190691 if this was not made by you.", "49", "2345", "Openai", null, "USD"),
            sms("Amex", "AD-AMEXIN", "Alert: A refund of INR 1,200.00 has been credited to your AMEX card ** 12345 from ZOMATO on 6 October 2026.", "1200", "2345", null, null, "INR", CR),

            // ---- HSBC ----
            sms("HSBC", "VM-HSBCIN", "Your HSBC credit card ending with 3344 has been used for INR 5,000.00 at MAKEMYTRIP on 05OCT26 at 14:22.", "5000", "3344", "Makemytrip"),
            sms("HSBC", "VM-HSBCIN", "INR 750.00 spent on your HSBC Credit Card ending 3344 at SWIGGY on 05-Oct-26. Available credit limit INR 1,49,250.00", "750", "3344", "Swiggy", "149250"),
            mail("HSBC", "HSBC India <alerts@hsbc.co.in>", "Dear Customer, your HSBC credit card ending 3344 has been used for INR 5,000.00 at MAKEMYTRIP on 05-Oct-26.", "5000", "3344", "Makemytrip"),

            // ---- Standard Chartered ----
            sms("SC", "AD-SCBNKS", "Txn of INR 1,299.00 done on your StanChart Credit Card no. XX5566 at AMAZON on 05/10/26.", "1299", "5566", "Amazon"),
            sms("SC", "AD-SCBNKS", "INR 2,000.00 spent on your Standard Chartered Credit Card ending 5566 at SWIGGY on 05-Oct-26. Available limit INR 78,000.00", "2000", "5566", "Swiggy", "78000"),
            mail("SC", "Standard Chartered <alerts@sc.com>", "Dear Customer, your Standard Chartered Credit Card ending 5566 has been used for INR 2,000.00 at SWIGGY on 05-Oct-26.", "2000", "5566", "Swiggy"),

            // ---- Citi ----
            sms("Citi", "VM-CITIIN", "Citi Alert: A purchase of INR 1,800.00 was made on your Citi Credit Card ending 7788 at AMAZON on 05-Oct-26. Available limit INR 1,20,000.00", "1800", "7788", "Amazon", "120000"),
            sms("Citi", "VM-CITIIN", "Citi Alert: INR 1,800.00 spent on your Citibank Credit Card ending 7788 at AMAZON on 05-Oct-26.", "1800", "7788", "Amazon"),

            // ---- Federal (Scapia, Fi, Jupiter ride on it) ----
            sms("Federal", "VM-FEDBNK", "INR 640.00 spent on your Federal Bank Credit Card ending 4455 at SWIGGY on 05-Oct-26. Available limit INR 59,360.00", "640", "4455", "Swiggy", "59360"),
            sms("Federal", "VM-FEDBNK", "Transaction of INR 450.00 on your Scapia Federal Credit Card ending 4455 at ZOMATO on 05-10-26.", "450", "4455", "Zomato"),
            sms("Federal", "VM-FEDBNK", "Rs.1,200.00 was spent on your Federal Bank Credit Card ending 4455 at AMAZON on 05/10/26.", "1200", "4455", "Amazon"),

            // ---- OneCard ----
            sms("OneCard", "JM-ONECRD", "Rs. 599 spent at NETFLIX on your OneCard ending 4321. Available limit: Rs. 49,401.", "599", "4321", "Netflix", "49401"),
            sms("OneCard", "JM-ONECRD", "INR 500 spent on your OneCard ending 1234 at SWIGGY", "500", "1234", "Swiggy"),
            sms("OneCard", "JM-ONECRD", "USD 20.00 spent on your OneCard ending 1234 at OPENAI. Available limit: INR 50,000.", "20", "1234", "Openai", "50000", "USD"),

            // ---- Bank of Baroda (BOBCARD) ----
            sms("BoB", "VM-BOBCRD", "INR 1,500.00 spent on your BOBCARD ending 3456 at AMAZON on 05-10-26. Avl limit INR 48,500.00", "1500", "3456", "Amazon", "48500"),
            sms("BoB", "VM-BOBCRD", "Rs.700.00 spent on your BOBCARD ending 3456 at ZOMATO on 05/10/26. Not you? Call 18001028", "700", "3456", "Zomato"),
            mail("BoB", "BOBCARD <alerts@bobcard.co.in>", "Dear Customer, BOBCARD ending 3456 has been used for INR 700.00 at ZOMATO on 05-Oct-26.", "700", "3456", "Zomato"),

            // ---- Canara ----
            sms("Canara", "VM-CANBNK", "INR 1,000.00 spent on your Canara Bank Credit Card ending 8899 at AMAZON on 05-10-26. Avl limit INR 39,000.00", "1000", "8899", "Amazon", "39000"),
            sms("Canara", "VM-CANBNK", "Rs.1,000.00 spent on Canara Bank Credit Card XX8899 at SWIGGY on 05/10/26.", "1000", "8899", "Swiggy"),

            // ---- PNB ----
            sms("PNB", "VM-PNBSMS", "INR 1,000.00 spent on your PNB Credit Card ending 8899 at AMAZON on 05-10-26. Avl limit INR 39,000.00", "1000", "8899", "Amazon", "39000"),
            mail("PNB", "PNB <alerts@pnb.co.in>", "Dear Customer, your PNB Credit Card XX8899 has been used for INR 1,000.00 at AMAZON on 05-Oct-26.", "1000", "8899", "Amazon"),

            // ---- CSB ----
            sms("CSB", "VM-CSBANK", "INR 1,000.00 spent on your CSB Bank Credit Card ending 8899 at AMAZON on 05-10-26. Avl limit INR 39,000.00", "1000", "8899", "Amazon", "39000"),
            sms("CSB", "VM-CSBANK", "Rs.1,000.00 spent on CSB Bank Credit Card XX8899 at SWIGGY on 05/10/26.", "1000", "8899", "Swiggy"),

            // ---- IDBI ----
            sms("IDBI", "VM-IDBIBK", "INR 1,000.00 spent on your IDBI Bank Credit Card ending 8899 at AMAZON on 05-10-26. Avl limit INR 39,000.00", "1000", "8899", "Amazon", "39000"),
            sms("IDBI", "VM-IDBIBK", "Rs.1,000.00 spent on IDBI Bank Credit Card XX8899 at SWIGGY on 05/10/26.", "1000", "8899", "Swiggy"),

            // ---- Union ----
            sms("Union", "VM-UNIONB", "INR 1,000.00 spent on your Union Bank Credit Card ending 8899 at AMAZON on 05-10-26. Avl limit INR 39,000.00", "1000", "8899", "Amazon", "39000"),
            sms("Union", "VM-UNIONB", "Rs.1,000.00 spent on Union Bank Credit Card XX8899 at SWIGGY on 05/10/26.", "1000", "8899", "Swiggy"),

            // ---- Bank of India ----
            sms("BoI", "VM-BOIIND", "INR 1,000.00 spent on your Bank of India Credit Card ending 8899 at AMAZON on 05-10-26. Avl limit INR 39,000.00", "1000", "8899", "Amazon", "39000"),
            sms("BoI", "VM-BOIIND", "Rs.1,000.00 spent on BOI Credit Card XX8899 at SWIGGY on 05/10/26.", "1000", "8899", "Swiggy"),

            // ---- Slice, Jupiter, Uni ----
            sms("Slice", "VM-SLCEIT", "INR 350.00 spent on your slice card ending 1234 at SWIGGY on 05-10-26. Available limit INR 14,650.00", "350", "1234", "Swiggy", "14650"),
            sms("Slice", "VM-SLCEIT", "Rs.350.00 spent on your slice credit card ending 1234 at ZOMATO on 05/10/26.", "350", "1234", "Zomato"),
            sms("Jupiter", "VM-FEDBNK", "INR 420.00 spent on your Edge CSB Bank RuPay Credit Card ending 6677 at SWIGGY on 05-10-26. Avl limit INR 29,580.00", "420", "6677", "Swiggy", "29580"),
            sms("Jupiter", "VM-CSBANK", "Rs.420.00 spent on your Jupiter Edge Credit Card XX6677 at ZOMATO on 05/10/26.", "420", "6677", "Zomato"),
            sms("Uni", "VM-UNICRD", "INR 300.00 spent on your Uni Credit Card ending 9988 at SWIGGY on 05-10-26. Available limit INR 9,700.00", "300", "9988", "Swiggy", "9700"),
            sms("Uni", "VM-UNICRD", "Rs.300.00 spent on your Uni Pay 1/3rd Card XX9988 at ZOMATO on 05/10/26.", "300", "9988", "Zomato"),
        )
    }
}
