package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.parser.statement.HoldingColumns
import com.hisaab.parser.statement.HoldingField
import com.hisaab.parser.statement.HoldingProfiles
import com.hisaab.parser.statement.MfOrderParser
import com.hisaab.parser.statement.SpreadsheetLines
import com.hisaab.parser.statement.StatementParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** Holdings exports of Groww (.xlsx layout), Zerodha, Kuvera, INDmoney, brokers and registrars, read by column names. */
class HoldingPlatformsTest {
    private val parser = StatementParser()

    // name | AMC | category | sub | folio | units | invested | current | returns | xirr, laid out as Groww's export stores them.
    // Invented funds and amounts: a scheme held in two folios, gold and silver, a 12-digit folio, losses.
    private val groww = """
        Alpha Bluechip Fund Direct Growth|Alpha Mutual Fund|Equity|Large Cap|10203040|120.5|12000|13450.25|1450.25|9.1%
        Beta Gold Fund Direct Growth|Beta Mutual Fund|Commodities|Gold|900011122233|80.25|4000|4810.4|810.4|18.2%
        Gamma Flexi Cap Fund Direct Growth|Gamma Mutual Fund|Equity|Flexi Cap|55667788|45.125|5000|4720.1|-279.9|-4.3%
        Alpha Bluechip Fund Direct Growth|Alpha Mutual Fund|Equity|Large Cap|99887766|30|3000|3348.6|348.6|7.2%
        Delta Silver FoF Direct Growth|Delta Mutual Fund|Commodities|Silver|412345678901|150|2500|2360.75|-139.25|-5.6%
        Beta Gold Fund Direct Growth|Beta Mutual Fund|Commodities|Gold|31313131|10.5|600|629.4|29.4|4.9%
        Epsilon Hybrid Fund Direct Growth|Epsilon Mutual Fund|Hybrid|Aggressive Hybrid|77001122|60.333|3500.5|3611.95|111.45|3.2%
        Zeta Mid Cap Fund Direct Growth|Zeta Mutual Fund|Equity|Mid Cap|8080808|25|1999.9|2201.3|201.4|10.1%
    """.trimIndent().lines().map { it.split('|') }

    private val growwHead = listOf("Scheme Name", "AMC", "Category", "Sub-category", "Folio No.", "Source", "Units", "Invested Value", "Current Value", "Returns", "XIRR")

    // The summary states the value 2 paise off the rows' sum, as Groww's rounding does.
    private fun growwSheet(): List<List<String>> = listOf(
        listOf("Total Investments", "Current Portfolio Value", "Profit/Loss", "Profit/Loss %", "XIRR"),
        listOf("32600.4", "35132.73", "2532.33", "7.77%", "8.4%"),
        emptyList(), emptyList(),
        listOf("HOLDINGS AS ON 2026-10-06"),
        emptyList(),
        growwHead,
    ) + groww.map { r -> r.take(5) + listOf("Groww") + r.drop(5) }

    private fun minor(s: String) = BigDecimal(s).movePointRight(2).toLong()

    @Test
    fun `Groww sheet gives one holding per scheme with summed figures`() {
        val r = parser.parse(SpreadsheetLines.toText(growwSheet()), "Groww <noreply@groww.in>", Fixture.RECEIVED_AT)
        val schemes = groww.groupBy { it[0] }
        assertEquals(schemes.size, r.holdings.size, r.holdings.joinToString("\n"))
        assertEquals(6, schemes.size)
        for ((name, rows) in schemes) {
            val h = r.holdings.single { it.name == name }
            assertEquals(rows.sumOf { BigDecimal(it[5]) }.toDouble(), h.units!!, 1e-6, name)
            assertEquals(rows.sumOf { minor(it[6]) }, h.investedMinor, name)
            assertEquals(rows.sumOf { minor(it[7]) }, h.valueMinor, name)
            // Gain = value - invested, as the sheet's Returns column states it.
            assertEquals(rows.sumOf { BigDecimal(it[8]) }.toDouble(), (h.valueMinor!! - h.investedMinor!!) / 100.0, 0.02, name)
            assertEquals(if (rows[0][2] == "Commodities") HoldingKind.GOLD else HoldingKind.MUTUAL_FUND, h.kind, name)
            assertEquals(MfOrderParser.identifierFor(name), h.identifier)
            assertEquals("Groww", h.platform)
        }
        assertEquals(1, r.holdings.count { it.name == "Alpha Bluechip Fund Direct Growth" })
        val gold = r.holdings.single { it.name == "Beta Gold Fund Direct Growth" }
        assertEquals(90.75, gold.units!!, 1e-6)
        // A long folio never becomes units or value.
        assertTrue(r.holdings.all { it.units!! < 1000 && it.valueMinor!! < 5_000_000L })
        val invested = r.holdings.sumOf { it.investedMinor!! }
        val value = r.holdings.sumOf { it.valueMinor!! }
        assertEquals(3_260_040L, invested)
        assertEquals(3_513_275L, value)
        val check = r.holdingsCheck!!
        assertEquals(3_260_040L, check.statedInvestedMinor)
        assertEquals(3_513_273L, check.statedValueMinor)
        assertFalse(check.mismatch)
    }

    @Test
    fun `second Groww sheet replaces by identifier, the summary sheet of a workbook is read too`() {
        val workbook = listOf(growwHead) + groww.take(3).map { r -> r.take(5) + listOf("Groww") + r.drop(5) } +
            emptyList() + listOf(listOf("Summary"), listOf("Total Investments", "21000"), listOf("Current Value", "22980.75"))
        val r = parser.parse(SpreadsheetLines.toText(workbook), "Groww <noreply@groww.in>", Fixture.RECEIVED_AT)
        assertEquals(3, r.holdings.size)
        assertEquals(2_100_000L, r.holdingsCheck!!.statedInvestedMinor)
    }

    @Test
    fun `numbers as text with rupee signs, commas, brackets, Cr and Dr`() {
        val rows = listOf(
            listOf("Report"), emptyList(),
            listOf("Scheme Name", "Folio", "Units", "Invested Value (₹)", "Current Value (₹)", "Returns"),
            listOf("Alpha Equity Fund Direct Growth", "123456789012", "1,234.5", "₹ 80,000.00", "₹96,445.12", "16,445.12"),
            listOf("Beta Debt Fund Direct Growth", "98765", "(10)", "25,000", "23,500 Cr", "(1,500.00)"),
            listOf("Total", "", "", "105000", "119945.12", ""),
            listOf("Disclaimer Fund Direct Growth", "1", "1", "1", "999", "0"),
        )
        val r = parser.parse(SpreadsheetLines.toText(rows), "Groww <a@groww.in>", Fixture.RECEIVED_AT)
        assertEquals(2, r.holdings.size, r.holdings.toString())
        val a = r.holdings.single { it.name.startsWith("Alpha") }
        assertEquals(1234.5, a.units!!, 1e-9)
        assertEquals(9_644_512L, a.valueMinor)
        val b = r.holdings.single { it.name.startsWith("Beta") }
        assertEquals(2_350_000L, b.valueMinor)
        assertEquals(2_500_000L, b.investedMinor)
    }

    @Test
    fun `csv with BOM and tab or semicolon delimiters`() {
        for (d in listOf("\t", ";", ",")) {
            val csv = "﻿Symbol${d}ISIN${d}Quantity${d}Average buy price${d}Closing price${d}Closing value\r\nRELIANCE${d}INE002A01018${d}10${d}2500${d}2900${d}29000\r\n"
            val h = parser.parse(SpreadsheetLines.toText(SpreadsheetLines.parseCsv(csv)), "Groww <a@groww.in>", Fixture.RECEIVED_AT).holdings.single()
            assertEquals("INE002A01018", h.identifier, d)
            assertEquals(2_500_000L, h.investedMinor, d)
        }
    }

    @Test
    fun `transaction sheets are not holdings`() {
        val rows = listOf(
            listOf("Scheme Name", "Date", "Type", "Units", "NAV", "Amount"),
            listOf("Alpha Equity Fund Direct Growth", "01/09/2026", "Purchase", "10", "100", "1000"),
        )
        assertTrue(parser.parse(SpreadsheetLines.toText(rows), "Groww <a@groww.in>", Fixture.RECEIVED_AT).holdings.isEmpty())
    }

    @Test
    fun `column synonyms normalise case, punctuation and unit brackets`() {
        val cases = mapOf(
            "Unrealized P&L Pct." to HoldingField.GAIN_PCT, "Unrealised P&L" to HoldingField.GAIN, "Returns (%)" to HoldingField.GAIN_PCT,
            "CURRENT VALUE (INR)" to HoldingField.CURRENT, "Cur. val" to HoldingField.CURRENT, "Avg. cost" to HoldingField.AVG_PRICE,
            "Average buy price" to HoldingField.AVG_PRICE, "Buy value" to HoldingField.INVESTED, "Closing value" to HoldingField.CURRENT,
            "Closing price" to HoldingField.PRICE, "Previous Closing Price" to HoldingField.PRICE, "Qty." to HoldingField.UNITS,
            "Quantity Available" to HoldingField.UNITS, "Folio No." to HoldingField.FOLIO, "Sub-category" to HoldingField.SUB_CATEGORY,
            "Profit/Loss %" to HoldingField.GAIN_PCT, "Total Investments" to HoldingField.INVESTED, "Trading Symbol" to HoldingField.SYMBOL,
        )
        for ((h, f) in cases) assertEquals(f, HoldingColumns.fieldOf(h), h)
        assertEquals(null, HoldingColumns.fieldOf("Quantity Long Term"))
        assertEquals(null, HoldingColumns.fieldOf("Quantity Pledged (Margin)"))
    }

    private fun sampleRow(header: List<String>, platform: String): List<String> = header.map { h ->
        when (HoldingColumns.fieldOf(h)) {
            HoldingField.NAME -> "Alpha Equity Fund Direct Growth"
            HoldingField.SYMBOL -> "ALPHA"
            HoldingField.ISIN -> "INE002A01018"
            HoldingField.FOLIO -> "123456789012"
            HoldingField.SOURCE -> platform
            HoldingField.UNITS -> "10"
            HoldingField.AVG_PRICE -> "100"
            HoldingField.INVESTED -> "1000"
            HoldingField.CURRENT -> "1200"
            HoldingField.PRICE -> "120"
            HoldingField.GAIN -> "200"
            HoldingField.GAIN_PCT -> "20%"
            HoldingField.XIRR -> "9%"
            else -> "x"
        }
    }

    @Test
    fun `every platform profile header is read and its platform named`() {
        for (p in HoldingProfiles.ALL) {
            assertTrue(HoldingColumns.isHeader(p.header), p.label)
            val sender = "${p.platform.lowercase().replace(" ", "")}.com"
            val rows = listOf(listOf("Holdings as on 06-10-2026"), emptyList(), p.header, sampleRow(p.header, p.platform))
            val r = parser.parse(SpreadsheetLines.toText(rows), sender, Fixture.RECEIVED_AT)
            val h = r.holdings.singleOrNull()
            assertTrue(h != null, "${p.label}: ${r.holdings}")
            assertEquals(120_000L, h!!.valueMinor, p.label)
            assertEquals(100_000L, h.investedMinor, p.label)
            assertEquals(10.0, h.units!!, 1e-9, p.label)
            assertEquals(p.platform, h.platform, p.label)
        }
    }

    @Test
    fun `Zerodha Console holdings value units at the previous close`() {
        val p = HoldingProfiles.ALL.first { it.label == "Zerodha Console" }
        val rows = listOf(
            p.header,
            listOf("INFY", "INE009A01021", "IT", "10", "0", "10", "0", "0", "1500", "1600", "1000", "6.67"),
            listOf("GOLDBEES", "INF204KB17I5", "Gold", "100", "0", "0", "0", "0", "50", "60", "1000", "20"),
        )
        val r = parser.parse(SpreadsheetLines.toText(rows), "Zerodha <no-reply@zerodha.com>", Fixture.RECEIVED_AT)
        val infy = r.holdings.single { it.identifier == "INE009A01021" }
        assertEquals(1_600_000L, infy.valueMinor)
        assertEquals(1_500_000L, infy.investedMinor)
        assertEquals(HoldingKind.STOCK, infy.kind)
        assertEquals(HoldingKind.GOLD, r.holdings.single { it.identifier == "INF204KB17I5" }.kind)
    }

    @Test
    fun `holdings mails from the investment platforms are fetched`() {
        for (s in listOf("Your holdings statement", "Portfolio holdings report", "Holdings", "Mutual fund statement", "Balance statement as on 06 Oct")) {
            assertTrue(ParserRegistry.isStatementSubject(s), s)
        }
        for (d in listOf("groww.in", "zerodha.com", "kuvera.in", "indmoney.com", "paytmmoney.com", "etmoney.com", "upstox.com", "angelone.in", "dhan.co", "icicidirect.com", "hdfcsec.com", "kotaksecurities.com")) {
            assertTrue(ParserRegistry.default().defaultEmailSenders.any { it.contains(d) }, d)
        }
    }
}
