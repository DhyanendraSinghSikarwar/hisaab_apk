package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.parser.statement.StatementKind
import com.hisaab.parser.statement.StatementParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Messages and statements end to end, as the app reads them: SMS and email through the registry, statement text through
 * [StatementParser]. Asserts the transaction that would be stored: amount, type, account, category, balance.
 */
class SmsToTransactionTest {
    private val registry = ParserRegistry.default()
    private val at = Fixture.RECEIVED_AT

    private fun sms(sender: String, body: String) = registry.parse(body, sender, at, Source.SMS)

    @Test
    fun `UPI debit with balance is a spend on the account and states its balance`() {
        val tx = sms("VM-HDFCBK", "UPDATE: INR 1,240.00 debited from HDFC Bank XX2779 on 24-SEP-26. Info: UPI-SWIGGY-swiggy@icici. Avl bal:INR 23,410.00")
        assertNotNull(tx)
        tx!!
        assertEquals(124_000L, tx.amountMinor)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals("HDFC Bank", tx.bankName)
        assertEquals("2779", tx.accountLast4)
        assertEquals(AccountKind.ACCOUNT, tx.accountKind)
        assertEquals(Category.FOOD, tx.category)
        assertEquals(2_341_000L, tx.balanceMinor)
    }

    @Test
    fun `salary credit is income on the account`() {
        val tx = sms(
            "VM-HDFCBK",
            "Update! INR 85,000.00 deposited in HDFC Bank A/c XX2779 on 01-SEP-26 for NEFT Cr-CITI0000001-ACME CORP SALARY-XXXXXX-CITIN52026090112345. Avl bal INR 1,08,410.00.",
        )!!
        assertEquals(8_500_000L, tx.amountMinor)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals("2779", tx.accountLast4)
        assertEquals(10_841_000L, tx.balanceMinor)
    }

    @Test
    fun `credit card spend states the available limit, not a balance`() {
        val tx = sms("VM-HDFCBK", "Rs.349.00 spent via HDFC Bank Card xx5678 at UBER INDIA on 2026-09-20:22:01:45 Avl Lmt: Rs 1,23,456.00")!!
        assertEquals(34_900L, tx.amountMinor)
        assertEquals(AccountKind.CARD, tx.accountKind)
        assertEquals("5678", tx.accountLast4)
        assertEquals(Category.TRANSPORT, tx.category)
        assertEquals(12_345_600L, tx.availableLimitMinor)
        assertNull(tx.balanceMinor)
    }

    @Test
    fun `SIP debit is an investment, not a spend`() {
        val tx = sms("VM-HDFCBK", "UPDATE: INR 5,000.00 debited from HDFC Bank XX2779 on 05-SEP-26. Info: ACH D- TP ACH ZERODHA-1234567. Avl bal:INR 23,410.00")!!
        assertEquals(TransactionType.INVESTMENT, tx.type)
        assertEquals(500_000L, tx.amountMinor)
    }

    @Test
    fun `card bill payment is a transfer`() {
        val tx = sms("VM-HDFCBK", "Payment of Rs 12,345.00 received towards your HDFC Bank Credit Card XX5678 on 18-09-26.")!!
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(AccountKind.CARD, tx.accountKind)
    }

    @Test
    fun `OTPs, offers and declines are not transactions`() {
        assertNull(sms("VM-HDFCBK", "123456 is your OTP for txn of INR 1,500.00 at AMAZON on HDFC Bank card ending 5678. Valid for 5 mins."))
        assertNull(sms("VM-HDFCBK", "Get a Personal Loan of up to Rs.40 lakh, pre-approved for you! Apply now: hdfcbk.io/x7Yt"))
        assertNull(sms("VM-HDFCBK", "Transaction of Rs.500.00 on HDFC Bank Card x5678 at FLIPKART has been declined due to insufficient balance."))
    }

    @Test
    fun `bank alert email is the same transaction as its SMS`() {
        val email = registry.parse(
            "Dear Customer,\n\nRs.450.00 has been debited from account **2779 to VPA swiggy@icici SWIGGY on 24-09-26.\n\n" +
                "Your UPI transaction reference number is 526812345678.\n\nWarm Regards,\nHDFC Bank",
            "HDFC Bank InstaAlerts <alerts@hdfcbank.net>", at, Source.EMAIL,
        )!!
        val text = registry.parse(
            "Sent Rs.450.00\nFrom HDFC Bank A/C *2779\nTo SWIGGY\nOn 24/09/26\nRef 526812345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
            "VM-HDFCBK", at, Source.SMS,
        )!!
        assertEquals(text.amountMinor, email.amountMinor)
        assertEquals(text.accountLast4, email.accountLast4)
        assertEquals(text.referenceNumber, email.referenceNumber)
        assertEquals(text.transactionHash, email.transactionHash)
    }

    @Test
    fun `bank statement rows become transactions with the closing balance`() {
        val text = """
            HDFC Bank
            Statement of Account
            Account No: XXXXXXXX2779
            Statement From 01/09/2026 To 24/09/2026
            Opening Balance Dr Count Cr Count Debits Credits Closing Bal
            25,000.00 2 1 6,240.00 85,000.00 1,03,760.00
            Date Narration Chq./Ref.No. Value Dt Withdrawal Amt. Deposit Amt. Closing Balance
            01/09/26 NEFT CR-ACME CORP SALARY 01/09/26 85,000.00 1,10,000.00
            05/09/26 UPI-SWIGGY-SWIGGY@ICICI 05/09/26 1,240.00 1,08,760.00
            10/09/26 ATM WDL MUMBAI ANDHERI 10/09/26 5,000.00 1,03,760.00
        """.trimIndent()
        val r = StatementParser().parse(text, "HDFC Bank <estatement@hdfcbank.net>", at)
        assertEquals(StatementKind.BANK, r.statementKind)
        assertEquals("2779", r.last4)
        assertEquals(3, r.transactions.size, r.transactions.toString())
        val (salary, food, cash) = r.transactions
        assertEquals(TransactionType.CREDIT, salary.type)
        assertEquals(8_500_000L, salary.amountMinor)
        assertEquals(TransactionType.DEBIT, food.type)
        assertEquals(124_000L, food.amountMinor)
        assertEquals(TransactionType.DEBIT, cash.type)
        assertEquals(10_376_000L, r.summary.closingMinor)
        assertEquals(Source.STATEMENT, cash.source)
    }
}
