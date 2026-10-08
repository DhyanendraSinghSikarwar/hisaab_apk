package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.registry.ParserRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Real messages from user feedback (numbers changed), and the free-text paths for screenshots and app notifications. */
class FeedbackCasesTest {
    private val registry = ParserRegistry.default()
    private val free = FreeTextParser()

    @Test
    fun `card number with only two digits is still a card spend, in its own currency`() {
        val tx = registry.parse(
            "USD200.00 was spent on your SBI Corporate Card number ending with 96 at ANTHROPIC on 01/10/26. " +
                "Available Credit Limit: Rs.59,165.07, If this trxn. wasn't done by you, call 18605003000",
            "VM-SBICRD-S", Fixture.RECEIVED_AT, Source.SMS,
        )
        assertNotNull(tx)
        assertEquals(20000L, tx!!.amountMinor) // the USD amount, not the limit
        assertEquals("USD", tx.currency)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals("96", tx.accountLast4)
        assertEquals(AccountKind.CARD, tx.accountKind)
        assertEquals(5916507L, tx.availableLimitMinor)
    }

    @Test
    fun `debit card withdrawal is cash, keeps the account balance, and is marked as a debit card`() {
        val tx = registry.parse(
            "Withdrawn Rs.10000 From HDFC Bank Card x2139 At +NALLAGANDLA 3 On 2026-09-20:14:20:34 Bal Rs.573096.22 " +
                "Not You? Call 18002586161/SMS BLOCK DC 2139 to 7308080808",
            "AD-HDFCBK-S", Fixture.RECEIVED_AT, Source.SMS,
        )
        assertNotNull(tx)
        assertEquals(1000000L, tx!!.amountMinor)
        assertEquals(AccountKind.CARD, tx.accountKind)
        assertTrue(tx.isDebitCard)
        assertEquals(Channel.ATM, tx.channel)
        assertEquals(Category.CASH, tx.category)
        assertEquals(57309622L, tx.balanceMinor)
    }

    @Test
    fun `credit card spends are not marked as debit cards`() {
        val tx = registry.parse("Rs.349.00 spent via HDFC Bank Card xx5678 at UBER INDIA on 2026-09-20:22:01:45 Avl Lmt: Rs 1,23,456.00",
            "VM-HDFCBK", Fixture.RECEIVED_AT, Source.SMS)
        assertFalse(tx!!.isDebitCard)
        assertNull(tx.balanceMinor)
    }

    @Test
    fun `paying a card bill from the bank is a transfer, not spending`() {
        for (body in listOf(
            "Rs 12,345.00 debited from A/c XX1234 on 05-09-26 towards your HDFC Bank Credit Card XX5678. Avl Bal Rs 40,000.00",
            "INR 8,000.00 debited from A/c XX1234 for CC bill payment on 05-09-26. Ref 526812341111",
        )) {
            val tx = registry.parse(body, "VM-HDFCBK", Fixture.RECEIVED_AT, Source.SMS)
            assertEquals(TransactionType.TRANSFER, tx!!.type, body)
        }
    }

    @Test
    fun `insurers are recognised as insurance`() {
        val tx = registry.parse("Rs 18,500.00 debited from A/c XX1234 on 05-09-26 to STAR HEALTH AND ALLIED INSURANCE. Ref 526812342222",
            "VM-HDFCBK", Fixture.RECEIVED_AT, Source.SMS)
        assertEquals(Category.INSURANCE, tx!!.category)
    }

    @Test
    fun `payment app notifications become transactions`() {
        val paid = free.fromNotification("Google Pay", "₹250 paid to Swiggy", "Payment successful", Fixture.RECEIVED_AT)
        assertNotNull(paid)
        assertEquals(25000L, paid!!.amountMinor)
        assertEquals(TransactionType.DEBIT, paid.type)
        assertEquals(Source.APP, paid.source)

        val got = free.fromNotification("PhonePe", "Received ₹1,200 from Asha Verma", null, Fixture.RECEIVED_AT)
        assertEquals(TransactionType.CREDIT, got!!.type)
        assertEquals(120000L, got.amountMinor)
    }

    @Test
    fun `offers, requests and reminders from payment apps are ignored`() {
        for (text in listOf(
            "You've won ₹50 cashback! Scratch your card now",
            "Raju has requested ₹500 from you. Pay now",
            "Your electricity bill of ₹1,240 is due tomorrow. Pay now",
            "Payment of ₹300 to Zomato failed. Money will be refunded",
            "123456 is your OTP to pay ₹500",
        )) assertNull(free.fromNotification("App", text, null, Fixture.RECEIVED_AT), text)
    }

    @Test
    fun `a payment screenshot gives a draft with amount, merchant and reference`() {
        val ocr = "Paid to\nSWIGGY\n₹ 456\nCompleted\n25 Sep 2026, 10:12 am\nUPI transaction ID\n526812349999\nTo: SWIGGY\nswiggy@icici\nFrom: HDFC Bank 1234"
        val d = free.draft(ocr, Fixture.RECEIVED_AT)
        assertNotNull(d)
        assertEquals(45600L, d!!.amountMinor)
        assertEquals(TransactionType.DEBIT, d.type)
        assertEquals("526812349999", d.reference)
        assertEquals("HDFC Bank", d.bankName)
    }

    @Test
    fun `a balance-only email is not a credit`() {
        val body = "Dear Customer, Greetings from HDFC Bank! Your available balance in account ending XX5152 is Rs. INR 1,155.70 " +
            "as on 17-OCT-25 Your account is maintained at HDFC Bank, KOYALI. If you ve deposited a cheque, the amount will " +
            "reflect after clearance. For real-time balance updates, you can call us at 1800 270 3333."
        assertNull(registry.parse(body, "alerts@hdfcbank.net", Fixture.RECEIVED_AT, Source.EMAIL))
    }

    @Test
    fun `a merchant name glued to a terminal id is recognised`() {
        val tx = registry.parse(
            "Spent Rs.276.53 On HDFC Bank Card 2779 At UBERIND13513699 On 2026-10-01:18:24:32.Not You? " +
                "To Block+Reissue Call 18002586161/SMS BLOCK CC 2779 to 7308080808",
            "VM-HDFCBK", Fixture.RECEIVED_AT, Source.SMS,
        )
        assertEquals("Uber", tx!!.merchant)
        assertEquals(Category.TRANSPORT, tx.category)
        assertEquals("2779", tx.accountLast4)
    }

    @Test
    fun `the payee's account is never taken as the user's`() {
        val tx = registry.parse(
            "Rs.5,000.00 transferred to A/c XX9876 from your A/c XX1234 on 05-09-26 via IMPS. Ref 526812345678",
            "VM-HDFCBK", Fixture.RECEIVED_AT, Source.SMS,
        )
        assertEquals("1234", tx!!.accountLast4)
        val onlyPayee = registry.parse(
            "Rs.5,000.00 sent to A/c XX9876 on 05-09-26 via IMPS. Ref 526812345679",
            "VM-HDFCBK", Fixture.RECEIVED_AT, Source.SMS,
        )
        assertNull(onlyPayee?.accountLast4)
    }

    @Test
    fun `ui phrases from notifications never become a merchant`() {
        for (phrase in listOf("View Details", "Know more", "Check balance", "Tap to view", "Download", "Learn more", "Track order", "Explore", "Open app")) {
            val got = free.fromNotification("Google Pay", "Payment successful", "₹144 paid. $phrase", Fixture.RECEIVED_AT)
            assertNotNull(got, phrase)
            assertNull(got!!.merchant, phrase)
        }
        val real = free.fromNotification("Google Pay", "₹250 paid to Swiggy", "View Details", Fixture.RECEIVED_AT)
        assertEquals("Swiggy", real!!.merchant)
    }

    @Test
    fun `listed Indian banks are recognised by their sender code`() {
        val cases = mapOf(
            "VM-NSBANK-S" to "Nagrik Sahakari Bank", "AX-CENTBK-S" to "Central Bank of India", "VK-MAHABK-S" to "Bank of Maharashtra",
            "AD-CANBNK-S" to "Canara Bank", "JD-UNIONB-S" to "Union Bank of India", "TM-BOIIND-S" to "Bank of India",
            "AX-FEDBNK-S" to "Federal Bank", "VM-SIBSMS-S" to "South Indian Bank", "BP-JKBANK-S" to "Jammu & Kashmir Bank",
            "AD-SARBNK-S" to "Saraswat Bank", "VK-PYTMPB-S" to "Paytm Payments Bank", "VM-UCOBNK-S" to "UCO Bank",
        )
        for ((sender, bank) in cases) {
            val tx = registry.parse(
                "Dear Customer, A/c XXXXXX4821 is debited by Rs.1,250.00 on 05-10-2026 via UPI/527812345678. Avl Bal Rs.18,340.50",
                sender, Fixture.RECEIVED_AT, Source.SMS,
            )
            assertNotNull(tx, sender)
            assertEquals(bank, tx!!.bankName, sender)
            assertEquals(TransactionType.DEBIT, tx.type, sender)
            assertEquals(125000L, tx.amountMinor, sender)
            assertEquals("4821", tx.accountLast4, sender)
        }
    }

    @Test
    fun `no two banks claim one sender code`() {
        val keys = registry.parsers.flatMap { it.senderKeys }
        assertEquals(keys.size, keys.toSet().size)
    }
}
