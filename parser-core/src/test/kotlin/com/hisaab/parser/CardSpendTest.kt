package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.registry.ParserRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Online credit-card spends (a purchase on a laptop, confirmed with an OTP). The OTP SMS itself must be
 * rejected; the spend alert that follows must be counted, including from issuers with no dedicated parser.
 */
class CardSpendTest {
    private val registry = ParserRegistry.default()

    data class Spend(val sender: String, val body: String, val amountMinor: Long, val last4: String, val currency: String = "INR")

    @ParameterizedTest
    @MethodSource("spends")
    fun `online card spend is counted`(s: Spend) {
        assertTrue(registry.accepts(s.sender), "SMS pre-filter drops ${s.sender}")
        val tx = registry.parse(s.body, s.sender, Fixture.RECEIVED_AT, Source.SMS)
        assertNotNull(tx, s.body)
        assertEquals(TransactionType.DEBIT, tx!!.type, s.body)
        assertEquals(s.amountMinor, tx.amountMinor, s.body)
        assertEquals(s.last4, tx.accountLast4, s.body)
        assertEquals(AccountKind.CARD, tx.accountKind, s.body)
        assertEquals(s.currency, tx.currency, s.body)
    }

    @ParameterizedTest
    @MethodSource("otps")
    fun `otp and declined attempts are not counted`(sender: String, body: String) {
        assertNull(registry.parse(body, sender, Fixture.RECEIVED_AT, Source.SMS), body)
    }

    @Test
    fun `pre-filter still skips senders that are not banks`() {
        for (s in listOf("VM-AMAZON", "AD-SWIGGY", "+919876543210", "JD-FLPKRT", "VK-ZOMATO")) assertFalse(registry.accepts(s), s)
    }

    companion object {
        @JvmStatic
        fun spends() = listOf(
            Spend("JD-HDFCBK-S", "Spent Rs.2,499.00 On HDFC Bank Card 5678 At RAZ*AMAZON On 2026-09-24:21:14:05 Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 5678 to 7308080808", 249900, "5678"),
            Spend("VM-HDFCBK", "Txn Rs.1,250.00\nOn HDFC Bank Card 5678\nAt MAKEMYTRIP\nby UPI 626712345678\nOn 24-09\nNot You?\nCall 18002586161/SMS BLOCK CC 5678 to 7308080808", 125000, "5678"),
            Spend("AX-ICICIB", "INR 3,200.00 spent using ICICI Bank Card XX9012 on 24-Sep-26 on IND*Amazon. Avl Limit: INR 96,800.00. If not you, call 1800 2662/SMS BLOCK 9012 to 9215676766.", 320000, "9012"),
            Spend("AD-SBICRD", "Rs.1,799.00 spent on your SBI Credit Card ending with 3456 at FLIPKART on 24/09/26. Trxn. not done by you? Report at https://sbicard.com/Dispute", 179900, "3456"),
            Spend("VM-AXISBK", "Spent\nCard no. XX7890\nINR 4500\n24-09-26 20:11:05 IST\nAMAZON PAY IN\nAvl Lmt INR 95500\nSMS BLOCK 7890 to 919951860002, if not you - Axis Bank", 450000, "7890"),
            Spend("VM-KOTAKB", "Transaction of Rs.899.00 on Kotak Credit Card xx2345 at SWIGGY on 24/09/2026. Avl limit Rs.45,000. Not you? Call 18602662666", 89900, "2345"),
            Spend("BZ-INDUSB", "Your IndusInd Bank Credit Card ending 6789 has been used for INR 1,100.00 at ZOMATO on 24/09/26 at 21:30. Avl limit INR 1,98,900. If not done by you, call 18602677777", 110000, "6789"),
            Spend("AD-AMEXIN", "Alert: You've spent INR 2,345.00 on your AMEX card ** 41005 at AMAZON on 24 September 2026 at 09:10 PM IST. Call 18004190691 if this was not made by you.", 234500, "1005"),
            Spend("VM-RBLCRD", "Dear Customer, you have made a transaction of INR 1,500.00 at AMAZON on your RBL Bank Credit Card XX1122 on 24-09-2026.", 150000, "1122"),
            Spend("VM-HSBCIN", "Your HSBC credit card ending with 3344 has been used for INR 5,000.00 at MAKEMYTRIP on 24SEP26 at 14:22.", 500000, "3344"),
            Spend("AD-SCBANK", "Txn of INR 1,299.00 done on your StanChart Credit Card no. XX5566 at AMAZON on 24/09/26.", 129900, "5566"),
            Spend("VM-IDFCFB", "INR 750.00 spent on your IDFC FIRST Bank Credit Card ending XX7788 at SWIGGY on 24-SEP-2026 at 13:20. Avl Limit: INR 99,250.00", 75000, "7788"),
            Spend("JM-ONECRD", "Rs. 599 spent at NETFLIX on your OneCard ending 4321. Available limit: Rs. 49,401.", 59900, "4321"),
            Spend("VM-HDFCBK", "USD 12.99 spent on HDFC Bank Card x5678 at OPENAI *CHATGPT on 2026-09-24:10:00:00. Not You? Call 18002586161", 1299, "5678", "USD"),
        )

        @JvmStatic
        fun otps() = listOf(
            arrayOf("JD-HDFCBK-S", "OTP is 482910 for transaction of INR 2,499.00 at AMAZON on HDFC Bank Card 5678. Valid for 10 mins. Do not share."),
            arrayOf("BZ-INDUSB", "Your OTP to complete the purchase of INR 1,100.00 at ZOMATO with IndusInd Credit Card XX6789 is 482910. Do not share it with anyone."),
            arrayOf("VM-IDFCFB", "482910 is the OTP for your transaction of INR 750.00 at SWIGGY on IDFC FIRST Bank Credit Card XX7788. Valid for 3 minutes."),
            arrayOf("VM-HSBCIN", "Transaction of INR 5,000.00 at MAKEMYTRIP on your HSBC credit card ending 3344 was declined."),
            arrayOf("AD-AMEXIN", "Use 482910 as the one time password to authenticate the transaction of INR 2,345.00 at AMAZON on your AMEX card ** 41005."),
        )
    }
}
