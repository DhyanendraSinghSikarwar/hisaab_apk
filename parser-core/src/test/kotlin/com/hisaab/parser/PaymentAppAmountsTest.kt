package com.hisaab.parser

import com.hisaab.parser.model.AccountKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Payment-app notifications: the amount is the payment, never a balance, offer or call to pay; the bank is read only when named. */
class PaymentAppAmountsTest {
    private val parser = FreeTextParser()
    private val at = 1_790_000_000_000L

    private fun read(vararg parts: String) = parser.readNotification("PhonePe", parts.toList(), at).tx

    @Test
    fun `balance line before the payment is not the amount`() {
        val tx = read("Avl bal Rs 5,000", "Paid ₹250 to Ravi Kumar")
        assertEquals(25000L, tx!!.amountMinor)
    }

    @Test
    fun `balance clause in the same sentence is cut off`() {
        assertEquals(25000L, read("₹250 debited, Avl Bal ₹4,750", "to Ravi Kumar")!!.amountMinor)
    }

    @Test
    fun `rupees off promo next to a payment is ignored`() {
        val tx = read("Paid ₹250 to Ravi Kumar", "Get ₹10 off on your next payment")
        assertEquals(25000L, tx!!.amountMinor)
        assertEquals("Ravi Kumar", tx.merchant)
    }

    @Test
    fun `pay now promo with an amount is not a payment and does not hide one`() {
        assertNull(read("Pay now ₹1,299", "Your plan renews soon"))
        assertEquals(25000L, read("Paid ₹250 to Ravi Kumar", "Pay now ₹1,299 and get rewards")!!.amountMinor)
    }

    @Test
    fun `cashback line is not the payment`() {
        assertEquals(25000L, read("₹5 cashback received", "Paid ₹250 to Ravi Kumar")!!.amountMinor)
        assertNull(read("You got ₹5 cashback"))
    }

    @Test
    fun `bank and last 4 are read only when the notification names them`() {
        val named = read("Payment successful", "₹320 debited from HDFC Bank A/c XX1234")!!
        assertEquals("HDFC Bank", named.bankName)
        assertEquals("1234", named.accountLast4)
        val plain = read("Paid ₹250 to Ravi Kumar")!!
        assertNull(plain.accountLast4)
        assertEquals("PhonePe", plain.bankName)
    }

    @Test
    fun `a card named in the notification is a card`() {
        val tx = read("Paid ₹450 to Swiggy", "via Axis Bank Credit Card XX0775")!!
        assertEquals("Axis Bank", tx.bankName)
        assertEquals("0775", tx.accountLast4)
        assertEquals(AccountKind.CARD, tx.accountKind)
        assertNotNull(tx.merchant)
    }
}
