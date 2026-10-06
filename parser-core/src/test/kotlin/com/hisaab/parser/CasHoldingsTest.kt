package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.statement.StatementKind
import com.hisaab.parser.statement.StatementParser
import com.hisaab.parser.statement.SpreadsheetLines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CasHoldingsTest {
    private val parser = StatementParser()
    private val ist = ZoneId.of("Asia/Kolkata")
    private val received = LocalDateTime.of(2026, 6, 3, 9, 30).atZone(ist).toInstant().toEpochMilli()

    // The text PdfBox gives for a CDSL CAS (MAY2026_..._TXN.pdf): demat holdings, then mutual fund units held with the RTA.
    private val cdslCas = """
        CDSL
        Consolidated Account Statement
        Statement for the period from 01-05-2026 to 31-05-2026
        Holdings as on 31-05-2026
        DP Name : ZERODHA BROKING LIMITED   BO ID : 1208160012345678
        STATEMENT OF TRANSACTIONS FOR THE PERIOD FROM 01-05-2026 TO 31-05-2026
        Date Transaction Particulars Debit Credit Balance
        05-05-2026 INE040A01034 HDFC BANK LTD BY CM 5.000 25.000
        HOLDING STATEMENT AS ON 31-05-2026
        ISIN Security Current Bal. Frozen Bal. Pledge Bal. Pledge Setup Bal. Free Bal. Lock-in Bal. Earmarked Bal. Market Price / Face Value Value (₹)
        INE002A01018 RELIANCE INDUSTRIES LIMITED#EQUITY SHARES 10.000 -- -- -- 10.000 -- -- 2,950.25 29,502.50
        INF204KB14I2 NIPPON INDIA ETF NIFTY 50 BEES 100.000 -- -- -- 100.000 -- -- 285.40 28,540.00
        INE040A01034 HDFC BANK LIMITED#EQUITY SHARES 25.000 -- -- -- 25.000 -- -- 1,912.80 47,820.00
        Portfolio Value ₹ 1,05,862.50
        MUTUAL FUND UNITS HELD WITH MF/RTA
        Scheme Name ISIN Folio No. Closing Bal. (Units) NAV (₹) Cumulative Amount Invested (₹) Valuation (₹) Unrealised Profit/(Loss) (₹) Annualised XIRR
        Parag Parikh Flexi Cap Fund - Direct Plan Growth INF879O01027 12345678/90 1,234.567 78.1234 80,000.00 96,448.57 16,448.57 14.20%
        Axis Bluechip Fund - Direct
        Plan Growth INF846K01EW2 91012345678
        500.000 52.10 20,000.00 26,050.00 6,050.00 9.10%
    """.trimIndent()

    @Test
    fun `CDSL CAS gives every holding with its value`() {
        val r = parser.parse(cdslCas, "CDSL <eCAS@cdslstatement.com>", received)
        assertEquals(StatementKind.INVESTMENT, r.statementKind)
        assertTrue(r.transactions.isEmpty())
        assertEquals(5, r.holdings.size, r.holdings.toString())
        val byIsin = r.holdings.associateBy { it.identifier }

        val reliance = byIsin.getValue("INE002A01018")
        assertEquals("RELIANCE INDUSTRIES LIMITED", reliance.name)
        assertEquals(HoldingKind.STOCK, reliance.kind)
        assertEquals(10.0, reliance.units!!, 0.0)
        assertEquals(2_950_250L, reliance.valueMinor)

        val bees = byIsin.getValue("INF204KB14I2")
        assertEquals(HoldingKind.ETF, bees.kind)
        assertEquals(100.0, bees.units!!, 0.0) // not the "50" in its name
        assertEquals(2_854_000L, bees.valueMinor)

        // The transaction row above the holdings does not stand in for the HDFC Bank holding.
        assertEquals(4_782_000L, byIsin.getValue("INE040A01034").valueMinor)

        val ppfas = byIsin.getValue("INF879O01027")
        assertEquals("Parag Parikh Flexi Cap Fund - Direct Plan Growth", ppfas.name)
        assertEquals(HoldingKind.MUTUAL_FUND, ppfas.kind)
        assertEquals(1234.567, ppfas.units!!, 0.0001) // not the folio number
        assertEquals(9_644_857L, ppfas.valueMinor) // the valuation, not the gain after it
        assertEquals(8_000_000L, ppfas.investedMinor)

        // Name split over two lines, figures wrapped onto the next line, and a digits-only folio.
        val axis = byIsin.getValue("INF846K01EW2")
        assertEquals("Axis Bluechip Fund - Direct Plan Growth", axis.name)
        assertEquals(500.0, axis.units!!, 0.0)
        assertEquals(2_605_000L, axis.valueMinor)
        assertEquals(2_000_000L, axis.investedMinor)

        assertEquals(22_836_107L, r.holdings.sumOf { it.valueMinor ?: 0 })
        // Values are as on the statement date, not the day the email came.
        val asOf = LocalDate.of(2026, 5, 31).atTime(12, 0).atZone(ist).toInstant().toEpochMilli()
        assertTrue(r.holdings.all { it.asOf == asOf })
    }

    @Test
    fun `NSDL CAS mutual fund folio rows give value and cost`() {
        val text = """
            NSDL Consolidated Account Statement
            Mutual Fund Folios (F)
            ISIN ISIN Description Folio No. No. of Units Average Cost Per Units (₹) Total Cost (₹) Current NAV per unit (₹) Current Value (₹) Unrealised Profit/(Loss) (₹)
            INF179K01BE2 HDFC Mid-Cap Opportunities Fund - Direct Plan - Growth 1234567/89 500.000 50.00 25,000.00 150.234 75,117.00 50,117.00
        """.trimIndent()
        val h = parser.parse(text, "NSDL <nsdl-cas@nsdl.co.in>", Fixture.RECEIVED_AT).holdings.single()
        assertEquals("HDFC Mid-Cap Opportunities Fund - Direct Plan - Growth", h.name)
        assertEquals(500.0, h.units!!, 0.0)
        assertEquals(7_511_700L, h.valueMinor)
        assertEquals(2_500_000L, h.investedMinor)
    }

    @Test
    fun `CAMS closing balance wrapped over lines is still read`() {
        val text = """
            Consolidated Account Statement
            P8DG-Parag Parikh Flexi Cap Fund - Direct Plan Growth - ISIN: INF879O01027(Advisor: DIRECT) Registrar : CAMS
            Folio No: 12345678 / 90
            Closing Unit Balance: 1,000.000
            NAV on 31-May-2026: INR 80.10
            Total Cost Value: 60,000.00 Market Value on 31-May-2026: INR 80,100.00
        """.trimIndent()
        val h = parser.parse(text, "CAMS <donotreply@camsonline.com>", received).holdings.single()
        assertEquals("Parag Parikh Flexi Cap Fund - Direct Plan Growth", h.name)
        assertEquals(8_010_000L, h.valueMinor)
        assertEquals(6_000_000L, h.investedMinor)
    }

    @Test
    fun `CAMS closing balance without a market value is valued at units times NAV`() {
        val text = """
            Consolidated Account Statement
            Axis Bluechip Fund - Direct Growth - ISIN: INF846K01EW2(Advisor: DIRECT)
            Closing Unit Balance: 200.000 NAV on 31-May-2026: INR 52.10
        """.trimIndent()
        val h = parser.parse(text, "CAMS <donotreply@camsonline.com>", received).holdings.single()
        assertEquals(1_042_000L, h.valueMinor)
        assertNull(h.investedMinor)
    }

    // Spreadsheets.

    @Test
    fun `xls style bank rows become statement transactions`() {
        val rows = listOf(
            listOf("HDFC BANK Ltd.", "", "Statement of account"),
            listOf("Account No :", "50100012345678"),
            listOf("Date", "Narration", "Chq./Ref.No.", "Value Dt", "Withdrawal Amt.", "Deposit Amt.", "Closing Balance"),
            listOf("01/09/26", "UPI-SWIGGY-swiggy@icici-ICIC0000001-123456789012", "0000123456789012", "01/09/26", "450", "", "52340.5"),
            listOf("03/09/26", "NEFT CR-ACME CORP-SALARY SEP", "N123456", "03/09/26", "0", "85000", "137340.5"),
        )
        val lines = SpreadsheetLines.toLines(rows)
        assertTrue(lines.any { it.contains("450.00 Dr") && it.endsWith("52340.50") }, lines.joinToString("\n"))
        val r = parser.parse(lines.joinToString("\n"), "HDFC Bank <emailstatements.cc@hdfcbank.net>", Fixture.RECEIVED_AT)
        assertEquals(2, r.transactions.size, r.transactions.toString())
        assertEquals(TransactionType.DEBIT, r.transactions[0].type)
        assertEquals(45_000L, r.transactions[0].amountMinor)
        assertEquals(TransactionType.CREDIT, r.transactions[1].type)
        assertEquals(8_500_000L, r.transactions[1].amountMinor)
    }

    @Test
    fun `excel day serials in a date column become dates`() {
        assertEquals("09/12/2025", SpreadsheetLines.serialDate("46000"))
        val lines = SpreadsheetLines.toLines(
            listOf(listOf("Txn Date", "Description", "Debit", "Credit", "Balance"), listOf("46000", "ATM WDL", "2000", "", "10000")),
        )
        assertEquals("09/12/2025  ATM WDL  2000.00 Dr  10000.00", lines.last())
    }

    @Test
    fun `csv with quoted commas is split into cells`() {
        val rows = SpreadsheetLines.parseCsv("﻿Date,Description,Amount,Type\r\n\"05/09/2026\",\"AMAZON, PAY\",\"1,499.00\",DR\r\n")
        assertEquals(listOf("05/09/2026", "AMAZON, PAY", "1,499.00", "DR"), rows[1])
        assertEquals("05/09/2026  AMAZON, PAY  1499.00  DR", SpreadsheetLines.toLines(rows).last())
    }
}
