package com.hisaab.parser

import com.hisaab.parser.bank.DepositAction
import com.hisaab.parser.bank.DepositKind
import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.registry.ParserRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * FD, RD and PPF messages, end to end through the registry: the deposit status they give (account, balance,
 * maturity, renew or pay out) and whether they also count as a transaction.
 */
class DepositMessagesTest {
    private val registry = ParserRegistry.default()
    private val at = Fixture.RECEIVED_AT

    private fun status(body: String, sender: String, source: Source = Source.SMS) = registry.loanStatus(body, sender, at, source)

    @Test
    fun `HDFC FD booking SMS opens an FD with principal, rate, maturity and amount`() {
        val body = "Your FD No. XXXXXXXX5678 for INR 1,00,000.00 has been booked on 24-09-2026 for 1 year at 7.10% p.a. " +
            "Maturity date: 24-09-2027. Maturity amount INR 1,07,281.00. It will be auto renewed on maturity. -HDFC Bank"
        val s = status(body, "VM-HDFCBK")
        assertNotNull(s)
        val d = s!!.deposit!!
        assertEquals("HDFC Bank", s.lender)
        assertEquals("5678", s.last4)
        assertEquals(DepositKind.FD, d.kind)
        assertEquals(10_000_000L, s.principalMinor)
        assertEquals(10_000_000L, s.outstandingMinor)
        assertEquals(710, d.rateBps)
        assertEquals(LocalDate.of(2027, 9, 24), d.maturity)
        assertEquals(10_728_100L, d.maturityAmountMinor)
        assertEquals(DepositAction.RENEW_ALL, d.action)
        assertFalse(d.closed)
        // The advice is about the deposit, not money moving on a bank account.
        assertNull(registry.parse(body, "VM-HDFCBK", at, Source.SMS))
    }

    @Test
    fun `SBI term deposit opened with payout on maturity`() {
        val s = status(
            "Dear Customer, your Term Deposit A/c no. XXXXX4321 has been opened for Rs.50,000 with maturity date 12-Mar-2027 " +
                "and maturity value Rs. 53,550. Proceeds will be credited to your SB a/c on maturity. -SBI",
            "AD-SBIINB",
        )!!
        assertEquals("4321", s.last4)
        assertEquals(5_000_000L, s.outstandingMinor)
        assertEquals(LocalDate.of(2027, 3, 12), s.deposit!!.maturity)
        assertEquals(5_355_000L, s.deposit!!.maturityAmountMinor)
        assertEquals(DepositAction.PAYOUT, s.deposit!!.action)
        assertFalse(s.deposit!!.closed)
    }

    @Test
    fun `ICICI FD renewal gives the new maturity`() {
        val s = status(
            "Dear Customer, your Fixed Deposit XX9876 of Rs 2,00,000 has been renewed for 12 months at 6.90% p.a. New maturity date 15-Oct-2027. -ICICI Bank",
            "JD-ICICIB",
        )!!
        assertEquals("9876", s.last4)
        assertEquals(DepositKind.FD, s.deposit!!.kind)
        assertEquals(20_000_000L, s.outstandingMinor)
        assertEquals(LocalDate.of(2027, 10, 15), s.deposit!!.maturity)
        assertEquals(DepositAction.RENEW_ALL, s.deposit!!.action)
        assertEquals(690, s.deposit!!.rateBps)
    }

    @Test
    fun `FD matured and paid out is closed`() {
        val s = status(
            "Your FD A/c XX5678 has matured on 24-09-2026 and the proceeds of INR 1,07,281.00 have been credited to your A/c XX1234. -HDFC Bank",
            "VM-HDFCBK",
        )!!
        assertEquals("5678", s.last4)
        assertTrue(s.deposit!!.closed)
        assertNull(s.outstandingMinor)
        assertEquals(LocalDate.of(2026, 9, 24), s.deposit!!.maturity)
    }

    @Test
    fun `RD instalment SMS gives the running balance, not the instalment`() {
        val s = status("RD A/c XX9012: instalment of Rs 5,000 received on 24-09-2026. Total balance Rs 60,000. Maturity date 01-Apr-2028. -Axis Bank", "VM-AXISBK")!!
        assertEquals(DepositKind.RD, s.deposit!!.kind)
        assertEquals("9012", s.last4)
        assertEquals(6_000_000L, s.outstandingMinor)
        assertEquals(LocalDate.of(2028, 4, 1), s.deposit!!.maturity)
    }

    @Test
    fun `PPF deposit and interest SMS update the PPF balance and are not income`() {
        val deposit = "Rs.10,000.00 deposited in PPF A/c No. XXXX3456 on 05-09-2026. Available Bal Rs 2,55,678.00 -SBI"
        val s = status(deposit, "AD-SBIINB")!!
        assertEquals(DepositKind.PPF, s.deposit!!.kind)
        assertEquals("3456", s.last4)
        assertEquals(25_567_800L, s.outstandingMinor)
        assertNull(registry.parse(deposit, "AD-SBIINB", at, Source.SMS))

        val interest = "Your PPF A/c XXXXXXX3456 has been credited with interest of Rs. 12,345.00 on 31-03-2026. Balance: Rs. 2,45,678.00 -SBI"
        val i = status(interest, "AD-SBIINB")!!
        assertEquals(24_567_800L, i.outstandingMinor)
        assertFalse(i.deposit!!.closed)
    }

    @Test
    fun `India Post PPF from a generic sender is named India Post`() {
        val s = status("Dear Customer, Rs 5000 deposited in your PPF a/c ending 3456 on 03/09/2026. Balance Rs 1,50,000. - India Post", "VM-DOPBNK")
        assertNotNull(s)
        assertEquals("India Post", s!!.lender)
        assertEquals(15_000_000L, s.outstandingMinor)
    }

    @Test
    fun `a savings debit towards an RD stays a transaction on the savings account`() {
        val body = "INR 5,000.00 debited from A/c XX1234 on 24-09-26 towards RD A/c XX9012. Avl bal INR 45,000.00 -HDFC Bank"
        val tx = registry.parse(body, "VM-HDFCBK", at, Source.SMS)
        assertNotNull(tx)
        assertEquals("1234", tx!!.accountLast4)
        assertEquals(500_000L, tx.amountMinor)
        assertTrue(tx.type == TransactionType.DEBIT || tx.type == TransactionType.INVESTMENT)
        // ... and still tells about the RD.
        assertEquals("9012", status(body, "VM-HDFCBK")!!.last4)
    }

    @Test
    fun `FD offers and OTPs are not deposits`() {
        assertNull(status("Book an FD online and earn up to 7.75% p.a. Apply now: hdfcbk.io/fd", "VM-HDFCBK"))
        assertNull(status("123456 is the OTP to book your Fixed Deposit of Rs 50,000. Do not share. -ICICI Bank", "JD-ICICIB"))
        assertNull(status("Rs.500 debited from A/c XX1234 for FD booking charges", "VM-HDFCBK"))
    }

    @Test
    fun `FD advice email from a bank, with an offer in its footer`() {
        val body = """
            Dear Customer,
            Thank you for opening a Fixed Deposit with us. Your Fixed Deposit A/c No. 50300012347777 for Rs. 3,00,000.00 has been booked.
            Rate of interest: 7.25% p.a. Maturity Date: 05-Oct-2028. Maturity Amount: Rs. 3,46,512.00. Maturity instruction: Auto renewal of principal and interest.
            Regards,
            HDFC Bank. Book an FD now and earn up to 7.75%.
        """.trimIndent()
        val s = status(body, "HDFC Bank <alerts@hdfcbank.net>", Source.EMAIL)!!
        assertEquals("7777", s.last4)
        assertEquals(30_000_000L, s.outstandingMinor)
        assertEquals(725, s.deposit!!.rateBps)
        assertEquals(LocalDate.of(2028, 10, 5), s.deposit!!.maturity)
        assertEquals(34_651_200L, s.deposit!!.maturityAmountMinor)
        assertEquals(DepositAction.RENEW_ALL, s.deposit!!.action)
        assertNull(registry.parse(body, "HDFC Bank <alerts@hdfcbank.net>", at, Source.EMAIL))
    }

    @Test
    fun `deposit advices and summaries are fetched by subject`() {
        assertTrue(ParserRegistry.isStatementSubject("Fixed Deposit Advice - A/c XX7777"))
        assertTrue(ParserRegistry.isStatementSubject("Your PPF account statement"))
        assertTrue(ParserRegistry.isStatementSubject("Your weekly portfolio summary"))
        assertFalse(ParserRegistry.isStatementSubject("Your Swiggy order"))
    }

    @Test
    fun `lender messages still give loan status`() {
        val s = registry.loanStatus(
            "Dear Customer, your loan a/c no. PROP12345 has been disbursed. Loan amount Rs 2,00,000. -Propelld", "VM-PROPLD", at, Source.SMS,
        )
        assertNotNull(s)
        assertNull(s!!.deposit)
    }
}
