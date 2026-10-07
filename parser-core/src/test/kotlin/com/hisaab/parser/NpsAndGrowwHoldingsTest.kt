package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.parser.statement.InvestmentParser
import com.hisaab.parser.statement.MfOrderParser
import com.hisaab.parser.statement.SpreadsheetLines
import com.hisaab.parser.statement.StatementKind
import com.hisaab.parser.statement.StatementParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NpsAndGrowwHoldingsTest {
    private val parser = StatementParser()

    // NPS SMS.

    @Test
    fun `CRA senders are routed to the investment parser`() {
        for (s in listOf("AX-NPSCRA", "VM-NSDLPR", "JD-PFRDAI", "VM-KFINTN", "AD-CAMSNP", "BZ-NPSTRS")) assertTrue(InvestmentParser.accepts(s), s)
        assertFalse(InvestmentParser.accepts("VM-HDFCBK"))
    }

    @Test
    fun `Protean contribution SMS is a Tier I contribution`() {
        val body = "Dear Subscriber, Contribution of Rs.5000.00 for PRAN XXXXXXXX1234 has been credited to your Tier I account on 22-09-2026 and units allotted. -NPSCRA"
        val c = InvestmentParser.npsContribution(body, Fixture.RECEIVED_AT)
        assertNotNull(c)
        assertEquals("1234", c!!.pranLast4)
        assertEquals(1, c.tier)
        assertEquals(500_000L, c.amountMinor)
        assertEquals(LocalDate.of(2026, 9, 22), c.date)
        assertEquals("NPS:1234", c.identifier)
    }

    @Test
    fun `KFintech Tier II contribution SMS`() {
        val body = "Units for Rs 2,000.00 contribution in PRAN 1100XXXX5678 Tier II have been allotted on 20-Sep-2026. - KFin CRA"
        val c = InvestmentParser.npsContribution(body, Fixture.RECEIVED_AT)!!
        assertEquals("5678", c.pranLast4)
        assertEquals(2, c.tier)
        assertEquals(200_000L, c.amountMinor)
        assertEquals("NPS:5678:T2", c.identifier)
        assertNull(InvestmentParser.parse(body, "VM-KFINTN", Fixture.RECEIVED_AT))
    }

    @Test
    fun `holding value SMS with the value before the PRAN`() {
        val body = "Total holding value Rs 4,56,789.00 as on 24-Sep-2026 for PRAN XXXXXXXX1234. -NPS Trust"
        assertNull(InvestmentParser.npsContribution(body, Fixture.RECEIVED_AT))
        val h = InvestmentParser.parse(body, "JD-NPSTRS", Fixture.RECEIVED_AT)!!
        assertEquals(HoldingKind.NPS, h.kind)
        assertEquals("NPS:1234", h.identifier)
        assertEquals(45_678_900L, h.valueMinor)
    }

    @Test
    fun `failed contribution is ignored`() {
        assertNull(InvestmentParser.npsContribution("Your contribution of Rs 5000 for PRAN XXXX1234 has failed. -NPSCRA", Fixture.RECEIVED_AT))
    }

    // NPS Statement of Transaction.

    private val npsSot = """
        NPS Transaction Statement for Tier I Account
        Protean eGov Technologies Limited - Central Recordkeeping Agency
        PRAN : 110012341234
        Subscriber Name : RAHUL SHARMA
        Statement Date : 24-Sep-2026
        Holdings as on 24-Sep-2026
        Current Valuation Summary
        Total Contribution (Rs) Total Withdrawal (Rs) Total Notional Gain/Loss (Rs) Value of your Holdings (Investment) as on 24-Sep-2026 (in Rs) Return on investment (XIRR)
        3,50,000.00 0.00 1,06,789.12 4,56,789.12 10.25%
        Investment Details - Scheme Wise Summary
        Scheme Name Total Units NAV (Rs) Value at NAV (Rs)
        SBI PENSION FUND SCHEME E - TIER I 4,000.0000 50.0000 2,00,000.00
        LIC PENSION FUND SCHEME C - TIER I 6,000.0000 30.0000 1,80,000.00
        HDFC PENSION FUND SCHEME G - TIER I 2,000.0000 38.3945 76,789.12
        Tier II Account
        Total Contribution (Rs) Total Withdrawal (Rs) Total Notional Gain/Loss (Rs) Value of your Holdings (Investment) as on 24-Sep-2026 (in Rs)
        50,000.00 0.00 6,789.00 56,789.00
        SBI PENSION FUND SCHEME E - TIER II 1,135.7800 50.0000 56,789.00
        Transaction Details
        22-Sep-2026 By Voluntary Contributions 5,000.00 100.0000 50.0000
    """.trimIndent()

    @Test
    fun `NPS statement of transaction gives value and contributions per tier`() {
        val r = parser.parse(npsSot, "NPS CRA <statements@npscra.nsdl.co.in>", Fixture.RECEIVED_AT)
        assertEquals(StatementKind.INVESTMENT, r.statementKind)
        assertTrue(r.transactions.isEmpty())
        val byId = r.holdings.associateBy { it.identifier }
        assertEquals(setOf("NPS:1234", "NPS:1234:T2"), byId.keys, r.holdings.toString())
        val t1 = byId.getValue("NPS:1234")
        assertEquals(HoldingKind.NPS, t1.kind)
        assertEquals(45_678_912L, t1.valueMinor)
        assertEquals(35_000_000L, t1.investedMinor)
        val t2 = byId.getValue("NPS:1234:T2")
        assertEquals(5_678_900L, t2.valueMinor)
        assertEquals(5_000_000L, t2.investedMinor)
    }

    // Groww mutual fund holdings statement.

    @Test
    fun `Groww holdings PDF text gives units, invested and current value`() {
        val text = """
            Groww
            Mutual Fund Holdings Statement
            Holdings as on 24-Sep-2026
            Scheme Name AMC Category Folio No. Units Invested Value Current Value Returns XIRR
            Parag Parikh Flexi Cap Fund Direct Growth PPFAS Mutual Fund Equity 12345678 1,234.567 80,000.00 96,445.12 16,445.12 14.25%
            Axis Small Cap Fund Direct Growth Axis Mutual Fund Equity 91011121 500.000 25,000.00 23,500.00 -1,500.00 -4.10%
            Total 1,05,000.00 1,19,945.12 14,945.12
        """.trimIndent()
        val r = parser.parse(text, "Groww <noreply@groww.in>", Fixture.RECEIVED_AT)
        assertEquals(StatementKind.INVESTMENT, r.statementKind)
        assertEquals(2, r.holdings.size, r.holdings.toString())
        val ppfas = r.holdings.first { it.identifier == MfOrderParser.identifierFor("Parag Parikh Flexi Cap Fund Direct Growth") }
        assertEquals(HoldingKind.MUTUAL_FUND, ppfas.kind)
        assertEquals("Parag Parikh Flexi Cap Fund Direct Growth", ppfas.name)
        assertEquals(1234.567, ppfas.units!!, 1e-6)
        assertEquals(8_000_000L, ppfas.investedMinor)
        assertEquals(9_644_512L, ppfas.valueMinor)
        val axis = r.holdings.first { it.name.startsWith("Axis Small Cap") }
        assertEquals(500.0, axis.units!!, 1e-6)
        assertEquals(2_500_000L, axis.investedMinor)
        assertEquals(2_350_000L, axis.valueMinor)
    }

    @Test
    fun `Groww holdings XLSX rows give units, invested and current value`() {
        val rows = listOf(
            listOf("Holdings statement as on 24-09-2026"),
            listOf("Scheme Name", "AMC", "Category", "Sub-category", "Folio No.", "Source", "Units", "Invested Value", "Current Value", "Returns", "XIRR"),
            listOf("Parag Parikh Flexi Cap Fund Direct Growth", "PPFAS Mutual Fund", "Equity", "Flexi Cap", "12345678", "Groww", "1234.567", "80000", "96445.12", "16445.12", "14.25%"),
            listOf("HDFC Index Fund Nifty 50 Direct Plan", "HDFC Mutual Fund", "Equity", "Index", "87654321", "External", "100.5", "20000", "21000", "1000", ""),
        )
        val r = parser.parse(SpreadsheetLines.toText(rows), "Groww <noreply@groww.in>", Fixture.RECEIVED_AT)
        assertEquals(2, r.holdings.size, r.holdings.toString())
        val ppfas = r.holdings.first { it.name == "Parag Parikh Flexi Cap Fund Direct Growth" }
        assertEquals(1234.567, ppfas.units!!, 1e-6)
        assertEquals(8_000_000L, ppfas.investedMinor)
        assertEquals(9_644_512L, ppfas.valueMinor)
        val hdfc = r.holdings.first { it.identifier == "MF:hdfc index nifty 50" }
        assertEquals(100.5, hdfc.units!!, 1e-6)
        assertEquals(2_000_000L, hdfc.investedMinor)
        assertEquals(2_100_000L, hdfc.valueMinor)
    }

    @Test
    fun `CAMS CAS cost value with a currency label is the amount invested`() {
        val text = """
            Consolidated Account Statement
            Folio No: 1234567 / 89
            P123-Parag Parikh Flexi Cap Fund - Direct Plan Growth - ISIN: INF879O01027(Advisor: DIRECT)
            Closing Unit Balance: 1,234.567 NAV on 24-Sep-2026: INR 78.1234 Cost Value (INR): 80,000.00 Market Value on 24-Sep-2026: INR 96,448.57
        """.trimIndent()
        val h = parser.parse(text, "CAMS <donotreply@camsonline.com>", Fixture.RECEIVED_AT).holdings.single()
        assertEquals("INF879O01027", h.identifier)
        assertEquals(8_000_000L, h.investedMinor)
        assertEquals(9_644_857L, h.valueMinor)
    }

    @Test
    fun `Groww and NPS statement subjects are fetched`() {
        assertTrue(ParserRegistry.isStatementSubject("Your Groww mutual fund statement"))
        assertTrue(ParserRegistry.isStatementSubject("Holdings statement for September 2026"))
        assertTrue(ParserRegistry.isStatementSubject("NPS Statement of Transaction"))
        assertTrue(ParserRegistry.isStatementSubject("NPS contribution credited"))
    }
}
