package com.hisaab.parser.bank

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

/** NBFC and fintech lenders: EMIs become debits on a loan account; disbursals and reminders become loan statuses. */
class LenderParserTest {
    private val registry = ParserRegistry.default()
    private val at = Fixture.RECEIVED_AT

    @Test
    fun `propelld sms emi received is a debit on the loan account`() {
        val tx = registry.parse(
            "Dear Customer, EMI of Rs.12,345 for your loan a/c no. XXXX1234 has been received on 05-10-2026. Thank you. - Propelld",
            "VM-PROPLD-S", at, Source.SMS,
        )
        assertNotNull(tx)
        assertEquals(1_234_500L, tx!!.amountMinor)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals("Propelld", tx.bankName)
        assertEquals("Propelld", tx.merchant)
        assertEquals("1234", tx.accountLast4)
        assertEquals(AccountKind.ACCOUNT, tx.accountKind)
        assertEquals(Category.EMI_LOAN, tx.category)
    }

    @Test
    fun `propelld email takes the last 4 of an alphanumeric loan number`() {
        val tx = registry.parse(
            "Dear Student,\nWe have received your EMI payment of Rs. 8,500.00 towards your loan account PRPL0012345 on 05-10-2026.\nThank you,\nTeam Propelld",
            "Propelld <noreply@propelld.com>", at, Source.EMAIL,
        )
        assertNotNull(tx)
        assertEquals(850_000L, tx!!.amountMinor)
        assertEquals("2345", tx.accountLast4)
        assertEquals(Category.EMI_LOAN, tx.category)
    }

    @Test
    fun `propelld reminder is not a transaction but names the loan`() {
        val body = "Your EMI of Rs 8,500 towards Loan Account No. PROP12345 is due on 05-Nov-2026. Please keep sufficient balance. - Propelld"
        assertNull(registry.parse(body, "VM-PROPLD", at, Source.SMS))
        val s = registry.loanStatus(body, "VM-PROPLD", at, Source.SMS)
        assertEquals(LoanStatus("Propelld", "2345", null, null, at), s)
    }

    @Test
    fun `future emi debit is not a payment`() {
        val body = "Your EMI of Rs 8,500 for loan a/c XXXX1234 will be debited on 05-11-2026. - Propelld"
        assertNull(registry.parse(body, "VM-PROPLD", at, Source.SMS))
        assertEquals("1234", registry.loanStatus(body, "VM-PROPLD", at, Source.SMS)?.last4)
    }

    @Test
    fun `disbursal sets the principal and the outstanding amount`() {
        val body = "Congratulations! Loan of Rs 2,00,000 has been disbursed to your account. Loan A/c XXXX5678. - Propelld"
        assertNull(registry.parse(body, "JD-PRPLDF", at, Source.SMS))
        val s = registry.loanStatus(body, "JD-PRPLDF", at, Source.SMS)
        assertEquals(LoanStatus("Propelld", "5678", 20_000_000L, 20_000_000L, at), s)
    }

    @Test
    fun `abcd emi debit carries the outstanding principal as the loan balance`() {
        val tx = registry.parse(
            "Dear Customer, Rs 8,500 debited towards EMI for loan LAN ABCD000123 on 05-10-2026. Outstanding principal Rs 1,45,000. - Aditya Birla Capital",
            "AX-ABCDAP-S", at, Source.SMS,
        )
        assertNotNull(tx)
        assertEquals(850_000L, tx!!.amountMinor)
        assertEquals("Aditya Birla Capital", tx.bankName)
        assertEquals("0123", tx.accountLast4)
        assertEquals(14_500_000L, tx.balanceMinor)
        assertEquals(Category.EMI_LOAN, tx.category)
    }

    @Test
    fun `abcd email from the app subdomain`() {
        val tx = registry.parse(
            "Dear Customer,\nThank you for your payment of Rs. 9,200.00 towards your Personal Loan Account No. ABFL0098765. " +
                "Your outstanding principal is Rs. 1,20,000.00.\nRegards,\nAditya Birla Capital",
            "ABCD <noreply@abcd.adityabirlacapital.com>", at, Source.EMAIL,
        )
        assertNotNull(tx)
        assertEquals(920_000L, tx!!.amountMinor)
        assertEquals("8765", tx.accountLast4)
        assertEquals(12_000_000L, tx.balanceMinor)
    }

    @Test
    fun `otp promo and bounce are ignored`() {
        val otp = "123456 is your OTP to sign the loan agreement for Loan A/c XXXX5678. Do not share. - Propelld"
        val promo = "You are eligible for a pre-approved Personal Loan of Rs 3,00,000 from ABCD. Apply now"
        val bounce = "EMI of Rs 8,500 for loan a/c XXXX1234 has bounced. Pay now to avoid charges. - Aditya Birla Capital"
        for ((body, sender) in listOf(otp to "VM-PROPLD", promo to "VM-ABCDAP", bounce to "VM-ABCLTD")) {
            assertNull(registry.parse(body, sender, at, Source.SMS), body)
            assertNull(registry.loanStatus(body, sender, at, Source.SMS), body)
        }
    }

    @Test
    fun `nach debit from a bank to a lender is an EMI named like the lender`() {
        val hdfc = registry.parse(
            "UPDATE: INR 8,500.00 debited from HDFC Bank XX4321 on 05-OCT-26. Info: ACH D- TP ACH PROPELLD-1234567. Avl bal:INR 23,410.00",
            "VM-HDFCBK", at, Source.SMS,
        )
        assertNotNull(hdfc)
        assertEquals(TransactionType.DEBIT, hdfc!!.type)
        assertEquals("Propelld", hdfc.merchant)
        assertEquals(Category.EMI_LOAN, hdfc.category)
        assertEquals(Channel.AUTO_DEBIT, hdfc.channel)

        val au = registry.parse(
            "INR 9,200.00 debited from AU Bank A/c XX1234 on 05-Sep-26 towards NACH-ADITYA BIRLA FINANCE. Bal INR 44,491.50",
            "VM-AUBANK", at, Source.SMS,
        )
        assertNotNull(au)
        assertEquals("Aditya Birla Capital", au!!.merchant)
        assertEquals(Category.EMI_LOAN, au.category)
    }

    @Test
    fun `lenders are registered for sms and email`() {
        assertTrue(registry.resolve("VM-PROPLD") is LenderParser)
        assertTrue(registry.resolve("AD-ABCLTD-S") is LenderParser)
        assertTrue(registry.resolve("x@adityabirlacapital.com") is LenderParser)
        assertTrue(Lenders.isLender("Aditya Birla Capital"))
        assertFalse(Lenders.isLender("HDFC Bank"))
        val senders = registry.defaultEmailSenders
        for (d in listOf("propelld.com", "adityabirlacapital.com", "abcd.adityabirlacapital.com", "bajajfinserv.in")) assertTrue(d in senders, d)
    }
}
