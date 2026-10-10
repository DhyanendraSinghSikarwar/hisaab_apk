package com.hisaab.parser

import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** One notification layout and what must come out of it. [type] null means it must not become a transaction. */
data class NotificationCase(
    val app: String, val title: String?, val body: String?,
    val type: TransactionType? = null, val rupees: Double? = null, val merchant: String? = null, val upi: String? = null,
)

object NotificationCases {
    private val D = TransactionType.DEBIT
    private val C = TransactionType.CREDIT

    val ALL = listOf(
        // PhonePe sent layouts (invented names and amounts).
        NotificationCase("PhonePe", "Payment successful", "₹250 paid to Ravi Kumar", D, 250.0, "Ravi Kumar"),
        NotificationCase("PhonePe", "Paid ₹250", "to Ravi Kumar", D, 250.0, "Ravi Kumar"),
        NotificationCase("PhonePe", "Sent ₹250", "to Ravi Kumar • UPI", D, 250.0, "Ravi Kumar"),
        NotificationCase("PhonePe", "Money sent to Ravi Kumar", "₹250", D, 250.0, "Ravi Kumar"),
        NotificationCase("PhonePe", "₹250 sent to Ravi Kumar", null, D, 250.0, "Ravi Kumar"),
        NotificationCase("PhonePe", "You paid ₹250 to Ravi Kumar", null, D, 250.0, "Ravi Kumar"),
        NotificationCase("PhonePe", "Payment successful", "You paid ₹1,250.50 to Meena Stores via PhonePe", D, 1250.50, "Meena Stores"),
        NotificationCase("PhonePe", "Paid 250 to Ravi Kumar", null, D, 250.0, "Ravi Kumar"),
        NotificationCase("PhonePe", "Paid Rs.250 to Ravi Kumar", null, D, 250.0, "Ravi Kumar"),
        NotificationCase("PhonePe", "Paid Rs. 250 to Ravi Kumar", null, D, 250.0, "Ravi Kumar"),
        NotificationCase("PhonePe", "Paid INR 250.00 to Ravi Kumar", null, D, 250.0, "Ravi Kumar"),
        NotificationCase("PhonePe", "Payment successful", "Paid ₹90 to ravi.k@ybl", D, 90.0, null, "ravi.k@ybl"),
        NotificationCase("PhonePe", "Sent ₹75", "to Lotus Stationers\nUPI Ref 526812340001", D, 75.0, "Lotus Stationers"),
        NotificationCase("PhonePe", "Transaction successful", "₹320 debited from HDFC Bank A/c XX1234", D, 320.0),
        // Received.
        NotificationCase("PhonePe", "₹500 received from Asha Verma", null, C, 500.0, "Asha Verma"),
        NotificationCase("PhonePe", "Received ₹500", "from Asha Verma", C, 500.0, "Asha Verma"),
        NotificationCase("PhonePe", "Received ₹500 from Asha Verma", "UPI • Credited to HDFC Bank", C, 500.0, "Asha Verma"),
        NotificationCase("PhonePe", "asha@ybl sent you ₹500", null, C, 500.0, null, "asha@ybl"),
        NotificationCase("PhonePe", "Asha Verma sent you ₹500", null, C, 500.0, "Asha Verma"),
        NotificationCase("Google Pay", "Asha Verma paid you ₹500", null, C, 500.0, "Asha Verma"),
        NotificationCase("Paytm", "Money received", "₹1,000 received from Asha Verma", C, 1000.0, "Asha Verma"),
        NotificationCase("Paytm", "Received 500 from Asha Verma", null, C, 500.0, "Asha Verma"),
        NotificationCase("BHIM", "Credited", "Rs 200.00 credited to your account from asha@okaxis", C, 200.0, null, "asha@okaxis"),
        // Mandates and bill payments.
        NotificationCase("PhonePe", "AutoPay", "₹199 paid to Netflix via AutoPay", D, 199.0, "Netflix"),
        NotificationCase("PhonePe", "Recharge of ₹299 successful", null, D, 299.0),
        NotificationCase("PhonePe", "Mobile recharge successful", "₹299 paid", D, 299.0),
        NotificationCase("Paytm", "Electricity bill of ₹1,200 paid", null, D, 1200.0),
        NotificationCase("Paytm", "Bill payment successful", "Your broadband bill of ₹799 has been paid", D, 799.0),
        // Other apps.
        NotificationCase("Google Pay", "₹250 paid to Swiggy", "Payment successful", D, 250.0, "Swiggy"),
        NotificationCase("Google Pay", "Payment successful", "₹144 paid. View Details", D, 144.0),
        NotificationCase("Google Pay", "You paid Ravi Kumar ₹250", "Tap to view", D, 250.0, "Ravi Kumar"),
        NotificationCase("Paytm", "Paid ₹60 to Ravi Kumar", "Paytm UPI • Ref 526812340002", D, 60.0, "Ravi Kumar"),
        NotificationCase("BHIM", "Payment of ₹80 successful", "Paid to Lotus Stationers", D, 80.0, "Lotus Stationers"),
        NotificationCase("CRED", "Payment successful", "₹2,000 sent to Ravi Kumar using UPI", D, 2000.0, "Ravi Kumar"),
        NotificationCase("Amazon Pay", "Paid ₹420", "to Fresh Basket", D, 420.0, "Fresh Basket"),
        NotificationCase("super.money", "₹150 paid to Lotus Stationers", "UPI Ref 526812340003", D, 150.0, "Lotus Stationers"),
        NotificationCase("Navi", "Sent ₹300 to Ravi Kumar", "UPI payment successful", D, 300.0, "Ravi Kumar"),
        NotificationCase("MobiKwik", "Payment of ₹55 to Green Taxis successful", null, D, 55.0, "Green Taxis"),
        NotificationCase("WhatsApp Pay", "You paid ₹100 to Ravi Kumar", null, D, 100.0, "Ravi Kumar"),
        NotificationCase("Samsung Wallet", "Payment completed", "₹640 spent at Book Hub", D, 640.0, "Book Hub"),
        NotificationCase("HDFC Bank", "Rs 250.00 debited", "A/c XX1234 trf to Ravi Kumar Ref 526812340004", D, 250.0),
        NotificationCase("ICICI iMobile", "Amount debited", "INR 1,500.00 debited from A/c XX4321 on 10-Oct-26", D, 1500.0),
        NotificationCase("Axis Mobile", "Credit alert", "INR 5,000.00 credited to A/c XX9988", C, 5000.0),
        // Not payments.
        NotificationCase("PhonePe", "Payment failed", "₹250 to Ravi Kumar could not be paid"),
        NotificationCase("PhonePe", "Payment pending", "₹250 to Ravi Kumar is being processed"),
        NotificationCase("PhonePe", "Payment declined", "Sent ₹250 to Ravi Kumar was declined by bank"),
        NotificationCase("PhonePe", "Refund initiated", "₹250 will be refunded to your account"),
        NotificationCase("PhonePe", "Transaction failed", "Paid ₹250 to Ravi Kumar failed. Try again"),
        NotificationCase("Google Pay", "You've won ₹50 cashback!", "Scratch your card now"),
        NotificationCase("Google Pay", "Cashback received ₹5", null),
        NotificationCase("PhonePe", "Get ₹100 cashback", "Pay now and win rewards"),
        NotificationCase("PhonePe", "Offer: flat ₹200 off", "Use code SAVE200 on your next payment"),
        NotificationCase("PhonePe", "Ravi Kumar requested ₹500", "Pay now"),
        NotificationCase("Paytm", "Your electricity bill of ₹1,240 is due tomorrow", "Pay now"),
        NotificationCase("Paytm", "Recharge now", "Your plan expires in 2 days. Recharge for ₹299"),
        NotificationCase("BHIM", "123456 is your OTP to pay ₹500", null),
        NotificationCase("BHIM", "Enter UPI PIN to pay ₹250", null),
        NotificationCase("PhonePe", "Scheduled payment", "₹199 will be debited tomorrow for Netflix"),
        NotificationCase("CRED", "Credit card bill paid", "₹5,000 paid towards your HDFC Bank Credit Card"),
        NotificationCase("PhonePe", "Check your balance", "Tap to view"),
        NotificationCase("PhonePe", "New message", "Hi"),
    )
}

/** A bank UPI SMS or a payment-app receipt email, and what must come out of it. */
data class ReceiptCase(
    val sender: String, val body: String, val source: com.hisaab.parser.model.Source,
    val type: TransactionType? = null, val rupees: Double? = null, val merchant: String? = null, val last4: String? = null, val ref: String? = null,
)

object ReceiptCases {
    private val D = TransactionType.DEBIT
    private val C = TransactionType.CREDIT
    private val SMS = com.hisaab.parser.model.Source.SMS
    private val MAIL = com.hisaab.parser.model.Source.EMAIL

    val ALL = listOf(
        ReceiptCase("VM-HDFCBK-S", "Sent Rs.250.00 from HDFC Bank A/c *1234 to RAVI on 10-10-26. UPI Ref 526812340010", SMS, D, 250.0, "Ravi", "1234", "526812340010"),
        ReceiptCase("VM-SBIINB-S", "Rs.250 debited from A/c XX1234 on 10-10-26 trf to RAVI Refno 526812340011", SMS, D, 250.0, "Ravi", "1234", "526812340011"),
        ReceiptCase("VM-SBIUPI-S", "Dear UPI user A/c X1234 debited by 250.0 on date 10Oct26 trf to RAVI KUMAR Refno 526812340012", SMS, D, 250.0, "Ravi Kumar", "1234", "526812340012"),
        ReceiptCase("AX-AXISBK-S", "Paid Rs.250 to RAVI from A/c XX1234 Ref 526812340013", SMS, D, 250.0, "Ravi", "1234", "526812340013"),
        ReceiptCase("VM-KOTAKB-S", "Sent Rs.250.00 from Kotak Bank AC X1234 to ravi@ybl on 10-10-26.UPI Ref 526812340015.", SMS, D, 250.0, null, "1234", "526812340015"),
        ReceiptCase("VM-ICICIB-S", "ICICI Bank Acct XX234 debited for Rs 250.00 on 10-Oct-26; RAVI credited. UPI:526812340016.", SMS, D, 250.0, "Ravi", "234", "526812340016"),
        ReceiptCase("VM-HDFCBK-S", "Rs.500.00 credited to A/c XX1234 on 10-10-26 by a/c linked to VPA asha@okaxis (UPI Ref No 526812340014)", SMS, C, 500.0, null, "1234", "526812340014"),
        ReceiptCase(
            "PhonePe <noreply@phonepe.com>",
            "Payment receipt. Paid to Ravi Kumar. Amount ₹250. Transaction ID T2610101234567890. UTR 526812340020. Debited from HDFC Bank XX1234. Date 10 Oct 2026",
            MAIL, D, 250.0, "Ravi Kumar", "1234", "526812340020",
        ),
        ReceiptCase(
            "Google Pay <googlepay-noreply@google.com>",
            "Transaction successful. ₹1,200 paid to Meena Stores via Google Pay. UPI transaction ID 526812340021. Paid from HDFC Bank XX1234",
            MAIL, D, 1200.0, "Meena Stores", "1234", "526812340021",
        ),
        ReceiptCase(
            "Paytm <no-reply@paytm.com>", "Money received. You have received ₹500 from Asha Verma. Transaction ID 526812340022",
            MAIL, C, 500.0, "Asha Verma", null, "526812340022",
        ),
        ReceiptCase("noreply@phonepe.com", "Payment failed. ₹250 to Ravi Kumar was not completed. Transaction ID T2610101234567891", MAIL),
        ReceiptCase("noreply@phonepe.com", "Your monthly statement is ready. Get ₹100 cashback on your next recharge. Offer valid till 31 Oct.", MAIL),
    )
}

class NotificationLayoutsTest {
    private val free = FreeTextParser()

    @Test
    fun `at least forty layouts are covered`() = assertNotNull(NotificationCases.ALL.size.takeIf { it >= 40 })

    @Test
    fun `every layout parses, or is skipped, as expected`() {
        for (c in NotificationCases.ALL) {
            val label = "${c.app}: ${c.title} / ${c.body}"
            val tx = free.fromNotification(c.app, c.title, c.body, 1_760_000_000_000L)
            if (c.type == null) { assertNull(tx, label); continue }
            assertNotNull(tx, label)
            assertEquals(c.type, tx!!.type, label)
            assertEquals(Math.round(c.rupees!! * 100), tx.amountMinor, label)
            if (c.merchant != null) assertEquals(c.merchant, tx.merchant, label)
            if (c.upi != null) assertEquals(c.upi, tx.upiId, label)
        }
    }

    @Test
    fun `fields combine in any order and duplicates are harmless`() {
        val a = free.readNotification("PhonePe", listOf("Paid ₹250", "to Ravi Kumar", "to Ravi Kumar • UPI", "Paid ₹250", "UPI"), 1L)
        assertEquals(25000L, a.tx!!.amountMinor)
        assertEquals("Ravi Kumar", a.tx!!.merchant)
        assertEquals(Channel.UPI, a.tx!!.channel)
        val b = free.readNotification("PhonePe", listOf("Payment failed", "₹250 paid to Ravi"), 1L)
        assertNull(b.tx)
        assertEquals("failed or pending payment", b.skipReason)
    }

    @Test
    fun `a mandate payment is an auto debit`() {
        val tx = free.fromNotification("PhonePe", "AutoPay", "₹199 paid to Netflix via AutoPay", 1L)
        assertEquals(Channel.AUTO_DEBIT, tx!!.channel)
    }

    @Test
    fun `bank UPI sms and payment app emails parse`() {
        val registry = com.hisaab.parser.registry.ParserRegistry.default()
        for (c in ReceiptCases.ALL) {
            val label = "${c.sender}: ${c.body}"
            val tx = registry.parse(c.body, c.sender, 1_760_000_000_000L, c.source)
            if (c.type == null) { assertNull(tx, label); continue }
            assertNotNull(tx, label)
            assertEquals(c.type, tx!!.type, label)
            assertEquals(Math.round(c.rupees!! * 100), tx.amountMinor, label)
            if (c.merchant != null) assertEquals(c.merchant, tx.merchant, label)
            if (c.last4 != null) assertEquals(c.last4, tx.accountLast4, label)
            if (c.ref != null) assertEquals(c.ref, tx.referenceNumber, label)
        }
    }

    @Test
    fun `payment app email senders are in the default whitelist`() {
        val senders = com.hisaab.parser.registry.ParserRegistry.default().defaultEmailSenders
        assertNotNull(senders.firstOrNull { it == "phonepe.com" })
    }
}
