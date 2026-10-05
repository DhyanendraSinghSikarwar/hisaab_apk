package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.statement.InvestmentParser
import com.hisaab.parser.statement.StatementParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatementParserTest {
    private val parser = StatementParser()

    @Test
    fun `credit card statement rows become card transactions`() {
        val text = """
            HDFC Bank Credit Card Statement
            Card No: 4893 XXXX XXXX 5678
            Statement Date 20/09/2026  Total Amount Due 12,345.00  Minimum Amount Due 620.00
            Date Transaction Description Amount (in Rs.)
            02/09/2026 AMAZON PAY INDIA MUMBAI 1,499.00
            05/09/2026 SWIGGY BANGALORE 456.50
            10/09/2026 PAYMENT RECEIVED - THANK YOU 8,000.00 Cr
            12/09/2026 REFUND FLIPKART 299.00 Cr
        """.trimIndent()
        val r = parser.parse(text, "HDFC Bank <statements@hdfcbank.net>", Fixture.RECEIVED_AT)
        assertEquals(AccountKind.CARD, r.kind)
        assertEquals("5678", r.last4)
        assertEquals("HDFC Bank", r.bankName)
        assertEquals(4, r.transactions.size)
        val (amazon, swiggy, payment, refund) = r.transactions
        assertEquals(149900L, amazon.amountMinor); assertEquals(TransactionType.DEBIT, amazon.type)
        assertEquals(45650L, swiggy.amountMinor)
        assertEquals(TransactionType.TRANSFER, payment.type)
        assertEquals(TransactionType.CREDIT, refund.type)
        assertTrue(r.transactions.all { it.source == Source.STATEMENT && it.accountLast4 == "5678" })
    }

    @Test
    fun `bank statement direction comes from the running balance`() {
        val text = """
            State Bank of India  Account Statement  A/c No XXXXXXX1234
            Date Narration Debit Credit Balance
            Opening Balance 50,000.00
            01-09-2026 UPI/526812345678/SWIGGY 450.00 49,550.00
            03-09-2026 NEFT SALARY ACME CORP 75,000.00 1,24,550.00
            05-09-2026 ATM WDL MUMBAI 2,000.00 1,22,550.00
        """.trimIndent()
        val r = parser.parse(text, "SBI <cbssbi.cas@alerts.sbi.co.in>", Fixture.RECEIVED_AT)
        assertEquals(AccountKind.ACCOUNT, r.kind)
        assertEquals("1234", r.last4)
        assertEquals(listOf(TransactionType.DEBIT, TransactionType.CREDIT, TransactionType.DEBIT), r.transactions.map { it.type })
        assertEquals(listOf(45000L, 7500000L, 200000L), r.transactions.map { it.amountMinor })
        assertEquals(12255000L, r.transactions.last().balanceMinor)
    }

    @Test
    fun `CAMS CAS gives mutual fund holdings`() {
        val text = """
            Consolidated Account Statement
            Folio No: 1234567 / 89
            128TSDGG-Axis Bluechip Fund - Direct Growth - ISIN: INF846K01EW2(Advisor: DIRECT)
            Closing Unit Balance: 123.456 NAV on 30-Sep-2026: INR 45.67 Total Cost Value: 5,000.00 Market Value on 30-Sep-2026: INR 5,638.12
            P8DG-Parag Parikh Flexi Cap Fund - Direct Plan Growth - ISIN: INF879O01027(Advisor: DIRECT)
            Closing Unit Balance: 1,000.000 NAV on 30-Sep-2026: INR 80.10 Total Cost Value: 60,000.00 Market Value on 30-Sep-2026: INR 80,100.00
        """.trimIndent()
        val r = parser.parse(text, "CAMS <donotreply@camsonline.com>", Fixture.RECEIVED_AT)
        assertTrue(r.transactions.isEmpty())
        assertEquals(2, r.holdings.size)
        val axis = r.holdings.first()
        assertEquals(HoldingKind.MUTUAL_FUND, axis.kind)
        assertEquals("Axis Bluechip Fund - Direct Growth", axis.name)
        assertEquals("INF846K01EW2", axis.identifier)
        assertEquals(563812L, axis.valueMinor)
        assertEquals(500000L, axis.investedMinor)
        assertEquals(123.456, axis.units!!, 0.0001)
    }

    @Test
    fun `demat statement lines with an ISIN give stock holdings`() {
        val text = """
            NSDL Consolidated Account Statement
            ISIN Security Current Bal. Market Price Value
            INE009A01021 INFOSYS LIMITED 10 1,500.00 15,000.00
            INE002A01018 RELIANCE INDUSTRIES LTD 5 2,900.00 14,500.00
        """.trimIndent()
        val r = parser.parse(text, "NSDL <nsdl-cas@nsdl.co.in>", Fixture.RECEIVED_AT)
        assertEquals(2, r.holdings.size)
        assertEquals(HoldingKind.STOCK, r.holdings[0].kind)
        assertEquals("INFOSYS LIMITED", r.holdings[0].name)
        assertEquals(1500000L, r.holdings[0].valueMinor)
        assertEquals(10.0, r.holdings[0].units!!, 0.0)
    }

    @Test
    fun `card statement summary is read whether values share the line or sit below the labels`() {
        val below = """
            ICICI Bank Credit Card Statement  Card 4375 XXXX XXXX 9012
            Statement Date Payment Due Date Total Amount Due Minimum Amount Due
            20/09/2026 08/10/2026 24,560.75 1,230.00
            Credit Limit: 2,00,000.00  Available Credit Limit: 1,75,439.25
            02/09/2026 MYNTRA BANGALORE 2,499.00
        """.trimIndent()
        val r = parser.parse(below, "ICICI Bank <cards@icicibank.com>", Fixture.RECEIVED_AT)
        assertEquals(com.hisaab.parser.statement.StatementKind.CREDIT_CARD, r.statementKind)
        assertEquals(2456075L, r.summary.totalDueMinor)
        assertEquals(123000L, r.summary.minDueMinor)
        assertEquals(java.time.LocalDate.of(2026, 10, 8), r.summary.dueDate)
        assertEquals(java.time.LocalDate.of(2026, 9, 20), r.summary.statementDate)
        assertEquals(20000000L, r.summary.creditLimitMinor)
        assertEquals(17543925L, r.summary.availableMinor)
        assertEquals(1, r.transactions.size)
    }

    @Test
    fun `bank statement summary gives opening, debits, credits and closing, not the table header`() {
        val text = """
            HDFC Bank Statement of account  Account No : 50100012345678
            Date Narration Chq./Ref.No. Value Dt Withdrawal Amt. Deposit Amt. Closing Balance
            02/09/26 UPI-SWIGGY-SWIGGY@ICICI 0000123 02/09/26 450.00 49,550.00
            STATEMENT SUMMARY
            Opening Balance Dr Count Cr Count Debits Credits Closing Bal
            50,000.00 1 0 450.00 0.00 49,550.00
        """.trimIndent()
        val s = parser.parse(text, "HDFC Bank <hdfcbanksmartstatement@hdfcbank.net>", Fixture.RECEIVED_AT).summary
        assertEquals(5000000L, s.openingMinor)
        assertEquals(45000L, s.debitsMinor)
        assertEquals(0L, s.creditsMinor)
        assertEquals(4955000L, s.closingMinor)
    }

    @Test
    fun `card summary with a previous balance column keeps each value with its label`() {
        val text = """
            Axis Bank Credit Card Statement  Card No 5241 XXXX XXXX 3344
            Previous Balance Payments/Credits Purchases/Debits Total Amount Due
            10,000.00 10,000.00 3,250.50 3,250.50
        """.trimIndent()
        val s = parser.parse(text, "Axis Bank <cc.statements@axisbank.com>", Fixture.RECEIVED_AT).summary
        assertEquals(1000000L, s.openingMinor)
        assertEquals(1000000L, s.creditsMinor)
        assertEquals(325050L, s.debitsMinor)
        assertEquals(325050L, s.totalDueMinor)
    }

    @Test
    fun `CAS is an investment statement`() {
        val r = parser.parse("Consolidated Account Statement\nINE009A01021 INFOSYS LIMITED 10 1,500.00 15,000.00", "NSDL <cas@nsdl.co.in>", Fixture.RECEIVED_AT)
        assertEquals(com.hisaab.parser.statement.StatementKind.INVESTMENT, r.statementKind)
    }

    @Test
    fun `NPS holding SMS updates the NPS balance`() {
        val h = InvestmentParser.parse(
            "Your NPS a/c PRAN XXXXXXXX1234 holding value as on 30-09-2026 is Rs. 4,56,789.12. Login to view details.",
            "VM-NPSTRS", Fixture.RECEIVED_AT,
        )
        assertEquals(HoldingKind.NPS, h!!.kind)
        assertEquals(45678912L, h.valueMinor)
        assertEquals("NPS:1234", h.identifier)
    }

    @Test
    fun `EPFO passbook SMS updates the EPF balance`() {
        val h = InvestmentParser.parse(
            "Dear 10XXXXXX1234, your passbook balance against MH/BAN/0012345/000/0001234 is Rs. 1,23,456/-. " +
                "Contribution of Rs. 12,345/- for due month 082026 has been received.",
            "VM-EPFOHO", Fixture.RECEIVED_AT,
        )
        assertNotNull(h)
        assertEquals(HoldingKind.EPF, h!!.kind)
        assertEquals(12345600L, h.valueMinor)
        assertNull(InvestmentParser.parse("Your passbook balance is Rs 500", "VM-HDFCBK", Fixture.RECEIVED_AT))
    }
}
