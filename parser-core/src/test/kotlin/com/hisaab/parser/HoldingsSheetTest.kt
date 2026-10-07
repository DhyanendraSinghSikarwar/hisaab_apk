package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.statement.MfOrderParser
import com.hisaab.parser.statement.SpreadsheetLines
import com.hisaab.parser.statement.StatementKind
import com.hisaab.parser.statement.StatementParser
import com.hisaab.parser.statement.StatementResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** Groww-style holdings export (Personal Details, HOLDING SUMMARY, HOLDINGS AS ON ...), read as rows or as text lines. */
class HoldingsSheetTest {
    private val parser = StatementParser()
    private val sender = "Groww <noreply@groww.in>"

    private val head = listOf("Scheme Name", "AMC", "Category", "Sub-category", "Folio No.", "Source", "Units", "Invested Value", "Current Value", "Returns", "XIRR")

    // scheme, amc, category, sub-category, folio, source, units, invested, current. One scheme has one NAV in all its folios.
    private val data = listOf(
        listOf("ICICI Prudential Retirement Fund Hybrid Aggressive Plan Direct Growth", "ICICI Prudential Mutual Fund", "Hybrid", "Aggressive Hybrid", "1001", "Groww", "300", "30000", "33150"),
        listOf("Parag Parikh Flexi Cap Fund Direct Growth", "PPFAS Mutual Fund", "Equity", "Flexi Cap", "2002", "Groww", "400", "24000", "30400"),
        listOf("Parag Parikh Flexi Cap Fund Direct Growth", "PPFAS Mutual Fund", "Equity", "Flexi Cap", "2003", "Groww", "100", "6500", "7600"),
        listOf("SBI Gold Direct Plan Growth", "SBI Mutual Fund", "Commodities", "Gold", "3001", "Groww", "1500", "22500", "24600"),
        listOf("SBI Gold Direct Plan Growth", "SBI Mutual Fund", "Commodities", "Gold", "3002", "External", "500", "7000", "8200"),
        listOf("Invesco India PSU Equity Fund Direct Growth", "Invesco Mutual Fund", "Equity", "Sectoral/Thematic", "4001", "Groww", "250", "12500", "14250"),
        listOf("Invesco India PSU Equity Fund Direct Growth", "Invesco Mutual Fund", "Equity", "Sectoral/Thematic", "4002", "Groww", "50", "2800", "2850"),
        listOf("LIC MF Gold ETF FoF Direct Growth", "LIC Mutual Fund", "Commodities", "Gold", "5001", "Groww", "1000", "15000", "16400"),
        listOf("LIC MF Gold ETF FoF Direct Growth", "LIC Mutual Fund", "Commodities", "Gold", "5002", "Groww", "200", "2900", "3280"),
        listOf("SBI Multi Asset Allocation Fund Direct Growth", "SBI Mutual Fund", "Hybrid", "Multi Asset Allocation", "6001", "Groww", "600", "30000", "32400"),
        listOf("SBI Multi Asset Allocation Fund Direct Growth", "SBI Mutual Fund", "Hybrid", "Multi Asset Allocation", "6002", "Groww", "100", "5500", "5400"),
        listOf("Axis Small Cap Fund Direct Growth", "Axis Mutual Fund", "Equity", "Small Cap", "7001", "Groww", "500", "25000", "23500"),
    )
    private val totalInvested = 183_700.0
    private val totalValue = 202_030.0

    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    private fun row(d: List<String>): List<String> = d + listOf(fmt(d[8].toDouble() - d[7].toDouble()), "9.5%")

    private fun sheet(totals: Pair<String, String> = fmt(totalInvested) to fmt(totalValue), asOn: String = "2026-10-06"): List<List<String>> =
        listOf(
            listOf("Personal Details"),
            listOf("Name", ""), listOf("Mobile Number", ""), listOf("PAN", ""),
            listOf(""),
            listOf("HOLDING SUMMARY"),
            listOf("Total Investments", "Current Portfolio Value", "Profit/Loss", "Profit/Loss %", "XIRR"),
            listOf(totals.first, totals.second, fmt(totalValue - totalInvested), "9.98%", "7.84%"),
            listOf(""),
            listOf("HOLDINGS AS ON $asOn"),
            head,
        ) + data.map { row(it) }

    private fun assertSheet(r: StatementResult) {
        assertEquals(StatementKind.INVESTMENT, r.statementKind)
        // Seven schemes: the folios of one scheme are summed, none dropped.
        assertEquals(7, r.holdings.size, r.holdings.joinToString("\n"))
        fun h(name: String) = r.holdings.single { it.name == name }
        val ppfas = h("Parag Parikh Flexi Cap Fund Direct Growth")
        assertEquals(500.0, ppfas.units!!, 1e-6)
        assertEquals(3_050_000L, ppfas.investedMinor)
        assertEquals(3_800_000L, ppfas.valueMinor)
        assertEquals(MfOrderParser.identifierFor("Parag Parikh Flexi Cap Fund Direct Growth"), ppfas.identifier)
        assertEquals(HoldingKind.MUTUAL_FUND, ppfas.kind)
        val gold = h("SBI Gold Direct Plan Growth")
        assertEquals(2000.0, gold.units!!, 1e-6)
        assertEquals(2_950_000L, gold.investedMinor)
        assertEquals(3_280_000L, gold.valueMinor)
        assertEquals(HoldingKind.GOLD, gold.kind)
        assertEquals(HoldingKind.GOLD, h("LIC MF Gold ETF FoF Direct Growth").kind)
        val psu = h("Invesco India PSU Equity Fund Direct Growth")
        assertEquals(300.0, psu.units!!, 1e-6)
        assertEquals(1_530_000L, psu.investedMinor)
        assertEquals(1_710_000L, psu.valueMinor)
        val multi = h("SBI Multi Asset Allocation Fund Direct Growth")
        assertEquals(3_550_000L, multi.investedMinor)
        assertEquals(3_780_000L, multi.valueMinor)
        // Totals match the HOLDING SUMMARY.
        assertEquals(totalInvested, r.holdings.sumOf { it.investedMinor!! } / 100.0, 1e-6)
        assertEquals(totalValue, r.holdings.sumOf { it.valueMinor!! } / 100.0, 1e-6)
        val check = r.holdingsCheck!!
        assertFalse(check.mismatch)
        assertEquals(18_370_000L, check.statedInvestedMinor)
        assertEquals(20_203_000L, check.statedValueMinor)
        // Units x NAV: a scheme's value is its units at the NAV every folio was priced at.
        val nav = mapOf(
            "ICICI Prudential Retirement Fund Hybrid Aggressive Plan Direct Growth" to 110.5, "Parag Parikh Flexi Cap Fund Direct Growth" to 76.0,
            "SBI Gold Direct Plan Growth" to 16.4, "Invesco India PSU Equity Fund Direct Growth" to 57.0, "LIC MF Gold ETF FoF Direct Growth" to 16.4,
            "SBI Multi Asset Allocation Fund Direct Growth" to 54.0, "Axis Small Cap Fund Direct Growth" to 47.0,
        )
        for (s in r.holdings) assertEquals(nav.getValue(s.name) * s.units!!, s.valueMinor!! / 100.0, 0.01, s.name)
        assertTrue(abs(r.holdings.first().asOf - Fixture.RECEIVED_AT) < 40L * 86_400_000)
    }

    @Test
    fun `holdings sheet rows are summed per scheme and match the summary`() {
        assertSheet(parser.parse(SpreadsheetLines.toText(sheet()), sender, Fixture.RECEIVED_AT))
    }

    @Test
    fun `holdings sheet as csv text`() {
        val csv = sheet().joinToString("\r\n") { r -> r.joinToString(",") { c -> if (c.contains(',')) "\"$c\"" else c } }
        assertSheet(parser.parse(SpreadsheetLines.toText(SpreadsheetLines.parseCsv(csv)), sender, Fixture.RECEIVED_AT))
    }

    @Test
    fun `holdings sheet as text lines with two-space cells`() {
        val text = sheet().joinToString("\n") { r -> r.filter { it.isNotEmpty() }.joinToString("  ") }
        assertSheet(parser.parse(text, sender, Fixture.RECEIVED_AT))
    }

    @Test
    fun `a stated total the rows miss by more than a rupee raises the flag but keeps the rows`() {
        val r = parser.parse(SpreadsheetLines.toText(sheet(totals = "183700" to "205030")), sender, Fixture.RECEIVED_AT)
        assertEquals(7, r.holdings.size)
        assertTrue(r.holdingsCheck!!.mismatch)
        assertEquals(20_203_000L, r.holdingsCheck!!.rowsValueMinor)
        val near = parser.parse(SpreadsheetLines.toText(sheet(totals = "183700.6" to "202030.4")), sender, Fixture.RECEIVED_AT)
        assertFalse(near.holdingsCheck!!.mismatch)
    }

    @Test
    fun `a summary without usable rows invents nothing`() {
        val rows = sheet().take(11) + listOf(listOf("Some Fund Direct Growth", "AMC", "Equity", "Large Cap", "1", "Groww", "", "", "", "", ""))
        val r = parser.parse(SpreadsheetLines.toText(rows), sender, Fixture.RECEIVED_AT)
        assertTrue(r.holdings.isEmpty(), r.holdings.toString())
        assertNull(r.holdingsCheck?.rowsValueMinor)
        assertFalse(r.holdingsCheck?.mismatch ?: false)
    }

    @Test
    fun `numbers with commas, rupee signs and brackets, header below other rows`() {
        val rows = listOf(
            listOf("Client report"), listOf("Generated 06-Oct-2026"), listOf(""),
            listOf("Fund Name", "Folio", "Platform", "Closing Units", "Total Investment", "Market Value", "P&L", "Returns %"),
            listOf("Kotak Emerging Equity Fund Direct Growth", "123", "ET Money", "1,234.567", "₹80,000.00", "₹96,445.12", "16,445.12", "20.56%"),
            listOf("Axis Small Cap Fund Direct Growth", "456", "ET Money", "500", "25,000", "23,500", "(1,500)", "(6.00%)"),
            listOf(""),
            listOf("Total", "", "", "", "1,05,000", "1,19,945.12", "14,945.12"),
        )
        val r = parser.parse(SpreadsheetLines.toText(rows), "ET Money <noreply@etmoney.com>", Fixture.RECEIVED_AT)
        assertEquals(2, r.holdings.size, r.holdings.toString())
        val k = r.holdings.first { it.name.startsWith("Kotak") }
        assertEquals(1234.567, k.units!!, 1e-6)
        assertEquals(8_000_000L, k.investedMinor)
        assertEquals(9_644_512L, k.valueMinor)
        assertEquals(2_350_000L, r.holdings.first { it.name.startsWith("Axis") }.valueMinor)
    }

    @Test
    fun `INDmoney short headers and Zerodha Coin holdings`() {
        val ind = listOf(
            listOf("Fund Name", "Invested", "Current", "Returns"),
            listOf("Nippon India Small Cap Fund Direct Growth", "40000", "52000", "12000"),
        )
        val a = parser.parse(SpreadsheetLines.toText(ind), "INDmoney <no-reply@indmoney.com>", Fixture.RECEIVED_AT).holdings.single()
        assertEquals(4_000_000L, a.investedMinor)
        assertEquals(5_200_000L, a.valueMinor)
        val coin = listOf(
            listOf("Instrument", "Qty.", "Avg. cost", "LTP", "Invested", "Cur. val", "P&L", "Net chg.", "Day chg."),
            listOf("Parag Parikh Flexi Cap Fund - Direct Plan - Growth", "100", "60", "76", "6000", "7600", "1600", "26.67%", "0.30%"),
        )
        val b = parser.parse(SpreadsheetLines.toText(coin), "Zerodha <no-reply@zerodha.com>", Fixture.RECEIVED_AT).holdings.single()
        assertEquals(100.0, b.units!!, 1e-6)
        assertEquals(600_000L, b.investedMinor)
        assertEquals(760_000L, b.valueMinor)
    }

    @Test
    fun `broker stock csv with symbol and ISIN`() {
        val rows = listOf(
            listOf("Symbol", "ISIN", "Quantity", "Avg price", "LTP", "Current value"),
            listOf("RELIANCE", "INE002A01018", "10", "2500", "2900", "29000"),
            listOf("TCS", "INE467B01029", "5", "3500", "4000", "20000"),
        )
        val r = parser.parse(SpreadsheetLines.toText(rows), "Broker <no-reply@broker.in>", Fixture.RECEIVED_AT)
        assertEquals(setOf("INE002A01018", "INE467B01029"), r.holdings.map { it.identifier }.toSet(), r.holdings.toString())
        assertEquals(2_900_000L, r.holdings.first { it.identifier == "INE002A01018" }.valueMinor)
    }

    @Test
    fun `two statements of the same schemes give the same identifiers, one per scheme`() {
        val a = parser.parse(SpreadsheetLines.toText(sheet()), sender, Fixture.RECEIVED_AT).holdings
        val b = parser.parse(SpreadsheetLines.toText(sheet(asOn = "2026-10-07")), sender, Fixture.RECEIVED_AT).holdings
        assertEquals(a.map { it.identifier }.sorted(), b.map { it.identifier }.sorted())
        assertEquals(a.size, a.map { it.identifier }.toSet().size)
    }
}
