package com.hisaab.parser

import com.hisaab.parser.bank.DepositAction
import com.hisaab.parser.bank.DepositKind
import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.statement.InvestmentParser
import com.hisaab.parser.statement.StatementKind
import com.hisaab.parser.statement.StatementParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Statement text (as read from the PDF or spreadsheet) through [StatementParser]: the holdings and deposits it yields. */
class StatementToHoldingsTest {
    private val parser = StatementParser()
    private val at = Fixture.RECEIVED_AT

    @Test
    fun `EPF member passbook gives one EPF holding with value and contributions`() {
        val text = """
            Employees' Provident Fund Organisation, India
            Member Passbook
            Establishment ID/Name MHBAN0012345000 / ACME SOFTWARE PVT LTD
            Member ID/Name MHBAN00123450000001234 / RAHUL SHARMA
            UAN 100123451234
            Wage Month Transaction Date Transaction Type Particulars EPF Wages EPS Wages Employee Share Employer Share Pension Contribution
            OB Int. Updated upto 31/03/2026 1,20,000 85,000 30,000
            Apr-2026 15-05-2026 CR Cont. For Due-Month 052026 15,000 15,000 1,800 550 1,250
            May-2026 15-06-2026 CR Cont. For Due-Month 062026 15,000 15,000 1,800 550 1,250
            Int. Updated upto 31/03/2026 9,000 2,700 0
            Total Contributions for the year [2026] 3,600 1,100 2,500
            Closing Balance as on 30/09/2026 1,34,400 89,350 32,500
        """.trimIndent()
        val r = parser.parse(text, "EPFO <no-reply@epfindia.gov.in>", at)
        assertEquals(StatementKind.INVESTMENT, r.statementKind)
        assertTrue(r.transactions.isEmpty(), "contributions are not bank transactions")
        val h = r.holdings.single()
        assertEquals(HoldingKind.EPF, h.kind)
        // The same key the EPFO SMS gives for "MH/BAN/0012345/000/0001234".
        assertEquals("EPF:MHBAN00123450000001234", h.identifier)
        assertEquals(
            h.identifier,
            InvestmentParser.parse(
                "Dear 10XXXXXX1234, your passbook balance against MH/BAN/0012345/000/0001234 is Rs. 2,56,250/-. " +
                    "Contribution of Rs. 3,600/- for due month 092026 has been received.",
                "VM-EPFOHO", at,
            )!!.identifier,
        )
        assertEquals(25_625_000L, h.valueMinor) // 1,34,400 + 89,350 + 32,500
        assertEquals(25_625_000L - 1_170_000L, h.investedMinor) // less this year's interest
    }

    @Test
    fun `PPF account statement updates the PPF account balance, not transactions`() {
        val text = """
            State Bank of India
            Public Provident Fund Account Statement
            PPF Account No: 00000012345673456
            Statement Period 01-04-2026 to 24-09-2026
            Date Description Debit Credit Balance
            01-04-2026 Opening Balance 2,33,333.00
            05-04-2026 BY TRANSFER FROM SB 10,000.00 2,43,333.00
            05-09-2026 BY TRANSFER FROM SB 12,345.00 2,55,678.00
        """.trimIndent()
        val r = parser.parse(text, "SBI <cbssbi.info@alerts.sbi.co.in>", at)
        assertEquals(StatementKind.INVESTMENT, r.statementKind)
        assertTrue(r.transactions.isEmpty())
        val d = r.deposits.single()
        assertEquals("SBI", d.lender)
        assertEquals("3456", d.last4)
        assertEquals(DepositKind.PPF, d.deposit!!.kind)
        assertEquals(25_567_800L, d.outstandingMinor)
    }

    @Test
    fun `FD advice PDF gives the deposit, its maturity and renewal`() {
        val text = """
            ICICI Bank
            Fixed Deposit Advice
            Deposit Account No: XXXXXXXX8899
            Deposit Amount: 2,50,000.00
            Rate of Interest: 7.20% p.a.
            Date of Deposit: 20-09-2026
            Maturity Date: 20-09-2028
            Maturity Amount: 2,88,945.00
            Maturity Instruction: Credit to my savings account
        """.trimIndent()
        val r = parser.parse(text, "ICICI Bank <alerts@icicibank.com>", at)
        val d = r.deposits.single()
        assertEquals("ICICI Bank", d.lender)
        assertEquals("8899", d.last4)
        assertEquals(25_000_000L, d.outstandingMinor)
        assertEquals(DepositKind.FD, d.deposit!!.kind)
        assertEquals(LocalDate.of(2028, 9, 20), d.deposit!!.maturity)
        assertEquals(28_894_500L, d.deposit!!.maturityAmountMinor)
        assertEquals(720, d.deposit!!.rateBps)
        assertEquals(DepositAction.PAYOUT, d.deposit!!.action)
        assertTrue(r.transactions.isEmpty())
    }

    @Test
    fun `INDmoney US stocks statement is valued in rupees at its own rate`() {
        val text = """
            INDmoney US Stocks Account Statement
            Holdings as on 24-Sep-2026
            USD/INR: 83.50
            Symbol Name Quantity Avg Cost ($) Current Price ($) Invested Value ($) Current Value ($)
            AAPL Apple Inc 2.5000 180.00 230.10 450.00 575.25
            VOO Vanguard S&P 500 ETF 1.0000 450.00 520.00 450.00 520.00
            Total $900.00 $1,095.25
        """.trimIndent()
        val r = parser.parse(text, "INDmoney <statements@indmoney.com>", at)
        assertEquals(StatementKind.INVESTMENT, r.statementKind)
        val byId = r.holdings.associateBy { it.identifier }
        assertEquals(setOf("US:AAPL", "US:VOO"), byId.keys, r.holdings.toString())
        val aapl = byId.getValue("US:AAPL")
        assertEquals(HoldingKind.STOCK, aapl.kind)
        assertEquals(Math.round(575.25 * 83.5 * 100), aapl.valueMinor)
        assertEquals(Math.round(450.0 * 83.5 * 100), aapl.investedMinor)
        assertEquals(HoldingKind.ETF, byId.getValue("US:VOO").kind)
    }

    @Test
    fun `a dollar table without a rate is left out rather than counted as rupees`() {
        val text = """
            INDmoney US Stocks Account Statement
            Holdings as on 24-Sep-2026
            Symbol Name Quantity Avg Cost ($) Current Price ($) Invested Value ($) Current Value ($)
            AAPL Apple Inc 2.5000 180.00 230.10 450.00 575.25
        """.trimIndent()
        assertTrue(parser.parse(text, "INDmoney <statements@indmoney.com>", at).holdings.isEmpty())
    }

    @Test
    fun `CDSL CAS forwarded by INDmoney gives stocks and funds by ISIN`() {
        val text = """
            CDSL Consolidated Account Statement (CAS)
            Statement for the period from 01-08-2026 to 31-08-2026
            Holdings as on 31-08-2026
            ISIN Security Current Bal Free Bal Market Price Value
            INE002A01018 RELIANCE INDUSTRIES LTD#EQUITY SHARES 10.000 10.000 2,950.25 29,502.50
            INE040A01034 HDFC BANK LIMITED#EQUITY SHARES 20.000 20.000 1,650.00 33,000.00
            MUTUAL FUND UNITS HELD WITH RTA
            Scheme Name ISIN Folio No Closing Bal Units NAV Cumulative Amount Invested Valuation Unrealised Profit/Loss
            PPFAS Mutual Fund - Parag Parikh Flexi Cap Fund - Direct Plan Growth INF879O01027 12345678/90 1,234.567 78.1234 80,000.00 96,448.57 16,448.57
        """.trimIndent()
        val r = parser.parse(text, "INDmoney <cas@indmoney.com>", at)
        val byId = r.holdings.associateBy { it.identifier }
        assertEquals(setOf("INE002A01018", "INE040A01034", "INF879O01027"), byId.keys, r.holdings.toString())
        assertEquals(2_950_250L, byId.getValue("INE002A01018").valueMinor)
        assertEquals(HoldingKind.STOCK, byId.getValue("INE040A01034").kind)
        val fund = byId.getValue("INF879O01027")
        assertEquals(HoldingKind.MUTUAL_FUND, fund.kind)
        assertEquals(9_644_857L, fund.valueMinor)
        assertEquals(8_000_000L, fund.investedMinor)
    }

    @Test
    fun `Groww stocks holdings table keys stocks by name`() {
        val text = """
            Groww
            Stocks Holdings Statement
            Holdings as on 24-Sep-2026
            Stock Name Quantity Average Buy Price Buy Value Closing Price Closing Value Unrealised P&L
            Reliance Industries 10 2,500.00 25,000.00 2,950.25 29,502.50 4,502.50
        """.trimIndent()
        val h = parser.parse(text, "Groww <noreply@groww.in>", at).holdings.single()
        assertEquals(HoldingKind.STOCK, h.kind)
        assertEquals("STOCK:RELIANCE INDUSTRIES", h.identifier)
        assertEquals(2_950_250L, h.valueMinor)
        assertEquals(2_500_000L, h.investedMinor)
        assertNotNull(h.units)
    }
}
