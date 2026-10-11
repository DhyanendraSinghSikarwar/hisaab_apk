package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.parser.rules.CardBill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Paying a credit card's bill is a transfer: neither a spend nor income. Spends on the card and payments to others stay as they are. */
class CardBillTest {
    private val registry = ParserRegistry.default()
    private val free = FreeTextParser()
    private val at = Fixture.RECEIVED_AT

    private fun sms(sender: String, body: String) = registry.parse(body, sender, at, Source.SMS)

    private fun assertBill(body: String, sender: String = "VM-HDFCBK") {
        val tx = sms(sender, body)
        assertNotNull(tx, body)
        assertEquals(TransactionType.TRANSFER, tx!!.type, body)
        assertEquals(Category.TRANSFER, tx.category, body)
        assertEquals(677_464L, tx.amountMinor, body)
    }

    private fun assertSpend(body: String, sender: String = "VM-HDFCBK") {
        val tx = sms(sender, body)
        assertNotNull(tx, body)
        assertEquals(TransactionType.DEBIT, tx!!.type, body)
        assertTrue(tx.category != Category.TRANSFER, body)
    }

    @Test
    fun `bank debit towards a card is a bill payment`() {
        assertBill("Rs.6,774.64 debited from A/c XX1234 on 10-10-26 towards Axis Bank Credit Card XX0775. Avl bal Rs.20,000.00")
        assertBill("Rs.6,774.64 debited from A/c XX1234 on 10-10-26 towards your HDFC Bank Credit Card XX5678. Avl bal Rs.20,000.00")
        assertBill("INR 6,774.64 debited from HDFC Bank XX1234 on 10-OCT-26. Info: CC BILL PAYMENT. Avl bal:INR 20,000.00")
        assertBill("INR 6,774.64 debited from HDFC Bank XX1234 on 10-OCT-26. Info: HDFC CC payment. Avl bal:INR 20,000.00")
        assertBill("INR 6,774.64 debited from HDFC Bank XX1234 on 10-OCT-26. Info: SBI Card payment. Avl bal:INR 20,000.00")
        assertBill("INR 6,774.64 debited from HDFC Bank XX1234 on 10-OCT-26. Info: UPI-ICICI CREDIT CARD-icicicc@icici. Avl bal:INR 20,000.00")
    }

    @Test
    fun `bank debit through a bill collector or issuer name is a bill payment`() {
        assertBill("INR 6,774.64 debited from HDFC Bank XX1234 on 10-OCT-26. Info: UPI-AXIS BANK CREDIT CARD-cc.axis@axisbank. Avl bal:INR 20,000.00")
        assertBill("INR 6,774.64 debited from HDFC Bank XX1234 on 10-OCT-26. Info: UPI-AXISCC-axiscc@axisbank. Avl bal:INR 20,000.00")
        assertBill("INR 6,774.64 debited from HDFC Bank XX1234 on 10-OCT-26. Info: UPI-CRED CLUB-cred.club@axisb. Avl bal:INR 20,000.00")
        assertBill("INR 6,774.64 debited from HDFC Bank XX1234 on 10-OCT-26. Info: BILLDESK-CC. Avl bal:INR 20,000.00")
        assertBill("Rs.6,774.64 debited from A/c XX1234 on 10-10-26 via IMPS to AXIS BANK LTD CARD Ref 526812340032. Avl bal Rs.20,000.00")
    }

    @Test
    fun `card side payment received is a transfer, never income`() {
        val tx = sms("AX-AXISBK", "Payment of Rs 6,774.64 received towards your Axis Bank Credit Card XX0775 on 12-10-26. Thank you.")!!
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(AccountKind.CARD, tx.accountKind)
        assertEquals(Category.TRANSFER, tx.category)
        val posted = sms("AX-AXISBK", "Rs 6,774.64 payment credited to your Axis Bank Credit Card XX0775 on 12-10-26. Avl limit Rs 90,000.00")
        if (posted != null) assertEquals(TransactionType.TRANSFER, posted.type)
    }

    @Test
    fun `net banking email of a card bill payment is a transfer`() {
        val tx = registry.parse(
            "Dear Customer,\n\nCredit card bill payment successful. Rs.6,774.64 has been debited from your account XX1234 towards credit card XX0775 on 10-10-26.\n\nWarm Regards,\nHDFC Bank",
            "HDFC Bank InstaAlerts <alerts@hdfcbank.net>", at, Source.EMAIL,
        )
        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER, tx!!.type)
        assertEquals(Category.TRANSFER, tx.category)
    }

    @Test
    fun `payment app notifications that pay a card are kept as transfers`() {
        for ((app, title, body) in listOf(
            Triple("PhonePe", "Paid ₹6,774", "to Axis Bank Credit Card"),
            Triple("Google Pay", "Payment successful", "₹6,774 paid to Axis Bank Credit Card"),
            Triple("Paytm", "Payment successful", "Paid ₹6,774 to cc.axis@axisbank"),
            Triple("Amazon Pay", "Paid ₹6,774", "to HDFC Bank Credit Card"),
            Triple("MobiKwik", "Payment successful", "₹6,774 paid towards your ICICI Credit Card"),
            Triple("CRED", "Credit card bill paid", "₹6,774 paid towards your HDFC Bank Credit Card"),
        )) {
            val tx = free.fromNotification(app, title, body, 1_760_000_000_000L)
            assertNotNull(tx, "$app $title $body")
            assertEquals(TransactionType.TRANSFER, tx!!.type, "$app $title $body")
            assertEquals(Category.TRANSFER, tx.category, "$app $title $body")
            assertEquals(677_400L, tx.amountMinor)
        }
    }

    @Test
    fun `payments to others and spends on a card are never bills`() {
        assertSpend("INR 2,500.00 debited from HDFC Bank XX1234 on 10-OCT-26. Info: UPI-AXIS GOLD-axisgold@ybl. Avl bal:INR 20,000.00".replace("2,500.00", "6,774.64"))
        assertSpend("INR 6,774.64 debited from HDFC Bank XX1234 on 10-OCT-26. Info: UPI-CREDIT BAZAAR-creditbazaar@ybl. Avl bal:INR 20,000.00")
        assertSpend("INR 6,774.64 debited from HDFC Bank XX1234 on 10-OCT-26. Info: UPI-CRED CASHBACK STORE-credstore@axisb. Avl bal:INR 20,000.00")
        assertSpend("INR 6,774.64 debited from HDFC Bank XX1234 on 10-OCT-26. Info: UPI-CC SHARMA-cc.sharma@okaxis. Avl bal:INR 20,000.00")
        val card = sms("VM-HDFCBK", "Rs.6,774.64 spent via HDFC Bank Card xx5678 at AXIS GOLD on 2026-09-20:22:01:45 Avl Lmt: Rs 1,23,456.00")!!
        assertEquals(TransactionType.DEBIT, card.type)
        assertTrue(card.category != Category.TRANSFER)
        val notif = free.fromNotification("PhonePe", "Paid ₹6,774", "to Credit Bazaar", 1_760_000_000_000L)!!
        assertEquals(TransactionType.DEBIT, notif.type)
    }

    @Test
    fun `payee rules match cards and issuers only`() {
        assertTrue(CardBill.isPayee("Axis Bank Credit Card", null))
        assertTrue(CardBill.isPayee("Credit Card Bill Payment", null))
        assertTrue(CardBill.isPayee("HDFCCC", null))
        assertTrue(CardBill.isPayee("SBI Card", null))
        assertTrue(CardBill.isPayee("Cred", null))
        assertTrue(CardBill.isPayee(null, "cc.axis@axisbank"))
        assertTrue(CardBill.isPayee(null, "icicicc1234@icici"))
        assertTrue(CardBill.isPayee(null, "cardpayment@hdfcbank"))
        assertFalse(CardBill.isPayee("Axis Gold", null))
        assertFalse(CardBill.isPayee("Credit Bazaar", null))
        assertFalse(CardBill.isPayee("Credit Card Cafe", null))
        assertFalse(CardBill.isPayee("CRED Cashback Store", "credstore@axisb"))
        assertFalse(CardBill.isPayee("Ravi Kumar", "cc.sharma@okaxis"))
    }

    @Test
    fun `a card cannot pay its own bill, so card debits are never marked`() {
        val card = sms("VM-HDFCBK", "Rs.499.00 spent via HDFC Bank Card xx5678 at CREDIT CARD CAFE on 2026-09-20:22:01:45 Avl Lmt: Rs 1,23,456.00")!!
        assertEquals(TransactionType.DEBIT, CardBill.mark(card, "payment").type)
    }

    @Test
    fun `mark turns a plain debit to a card into a transfer and is idempotent`() {
        val plain = sms("VM-HDFCBK", "INR 1,200.00 debited from HDFC Bank XX1234 on 10-OCT-26. Info: UPI-RAVI-ravi@ybl. Avl bal:INR 20,000.00")!!
        assertEquals(TransactionType.DEBIT, plain.type)
        val typed = plain.copy(merchant = "HDFC Credit Card", source = Source.MANUAL, category = Category.BILLS)
        val once = CardBill.mark(typed, null)
        assertEquals(TransactionType.TRANSFER, once.type)
        assertEquals(Category.TRANSFER, once.category)
        assertEquals(once, CardBill.mark(once, null))
        assertEquals(TransactionType.DEBIT, CardBill.mark(plain, "paid to ravi").type)
    }
}
