package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.statement.InvestmentParser
import com.hisaab.parser.statement.MfOrderParser
import com.hisaab.parser.statement.PortfolioSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Fund and investment emails through the public parsers: SIP and order confirmations (Groww, INDmoney), INDmoney's
 * portfolio summary, EPFO and NPS emails. Asserts what lands in the holding (identifier, value, invested).
 */
class FundEmailsTest {
    private val at = Fixture.RECEIVED_AT
    private val orders = MfOrderParser()

    @Test
    fun `Groww SIP email and a CAS row for the same fund share one holding key`() {
        val o = orders.parse(
            "Your SIP instalment of ₹5,000 for Parag Parikh Flexi Cap Fund Direct Growth has been processed successfully.\n" +
                "Units allotted 61.234\nNAV ₹81.6543\nNAV date 23 Sep 2026",
            "SIP instalment processed", "Groww <noreply@groww.in>", at,
        )!!
        assertEquals(500_000L, o.amountMinor)
        assertEquals(LocalDate.of(2026, 9, 23), o.date)
        assertEquals(MfOrderParser.identifierFor("Parag Parikh Flexi Cap Fund - Direct Plan - Growth"), o.identifier)
    }

    @Test
    fun `INDmoney mutual fund order email`() {
        val o = orders.parse(
            """
                Hi Rahul,
                Your mutual fund order has been placed successfully.
                Fund Name: Axis Small Cap Fund Direct Growth
                Amount: ₹10,000.00
                Order date: 22 Sep 2026
                Units will be allotted at the applicable NAV.
                Team INDmoney
            """.trimIndent(),
            "Order successful", "INDmoney <noreply@indmoney.com>", at,
        )
        assertNotNull(o)
        assertEquals("INDmoney", o!!.platform)
        assertEquals("Axis Small Cap Fund Direct Growth", o.schemeName)
        assertEquals(1_000_000L, o.amountMinor)
        assertEquals(LocalDate.of(2026, 9, 22), o.date)
    }

    @Test
    fun `failed SIP is not a purchase`() {
        assertNull(orders.parse("Your SIP instalment for Axis Small Cap Fund Direct Growth has failed due to insufficient balance.", "SIP failed", "Groww <noreply@groww.in>", at))
    }

    @Test
    fun `INDmoney portfolio summary table gives one aggregate per class`() {
        val text = """
            Hi Rahul, here is your weekly portfolio summary as on 24 Sep 2026.
            Asset Invested Current Value Returns
            Mutual Funds ₹1,20,000 ₹1,35,400 +12.83%
            US Stocks ₹50,000 ₹58,200 +16.40%
            Indian Stocks ₹80,000 ₹76,500 -4.38%
            Total ₹2,50,000 ₹2,70,100
            Team INDmoney
        """.trimIndent()
        val hs = PortfolioSummary.parse(text, "Your weekly portfolio summary", "INDmoney <updates@indmoney.com>", at).associateBy { it.identifier }
        assertEquals(setOf("AGG:INDMONEY:MF", "AGG:INDMONEY:US", "AGG:INDMONEY:STOCKS"), hs.keys)
        val mf = hs.getValue("AGG:INDMONEY:MF")
        assertEquals(HoldingKind.MUTUAL_FUND, mf.kind)
        assertEquals(12_000_000L, mf.investedMinor)
        assertEquals(13_540_000L, mf.valueMinor)
        assertEquals(5_820_000L, hs.getValue("AGG:INDMONEY:US").valueMinor)
        assertEquals(8_000_000L, hs.getValue("AGG:INDMONEY:STOCKS").investedMinor)
        assertEquals(7_650_000L, hs.getValue("AGG:INDMONEY:STOCKS").valueMinor)
    }

    @Test
    fun `INDmoney summary with labelled figures on separate lines`() {
        val text = """
            Your monthly portfolio update
            Mutual Funds
            Current: ₹1,35,400
            Invested: ₹1,20,000
            US Stocks
            Current: ₹58,200
            Invested: ₹50,000
        """.trimIndent()
        val hs = PortfolioSummary.parse(text, null, "INDmoney <updates@indmoney.com>", at).associateBy { it.identifier }
        assertEquals(13_540_000L, hs.getValue("AGG:INDMONEY:MF").valueMinor)
        assertEquals(12_000_000L, hs.getValue("AGG:INDMONEY:MF").investedMinor)
        assertEquals(5_000_000L, hs.getValue("AGG:INDMONEY:US").investedMinor)
    }

    @Test
    fun `an email that is not an INDmoney summary gives nothing`() {
        assertTrue(PortfolioSummary.parse("Mutual Funds ₹1,20,000 ₹1,35,400", "Weekly portfolio summary", "Groww <noreply@groww.in>", at).isEmpty())
        assertTrue(PortfolioSummary.parse("Your US Stocks order of ₹5,000 is placed", "Order placed", "INDmoney <noreply@indmoney.com>", at).isEmpty())
    }

    @Test
    fun `EPFO passbook email updates the EPF holding by member id`() {
        val h = InvestmentParser.parseEmail(
            "Dear Member, your passbook balance against MH/BAN/0012345/000/0001234 is Rs. 2,56,250/-. " +
                "Contribution of Rs. 3,600/- for due month 092026 has been received.",
            at,
        )!!
        assertEquals(HoldingKind.EPF, h.kind)
        assertEquals("EPF:MHBAN00123450000001234", h.identifier)
        assertEquals(25_625_000L, h.valueMinor)
        assertEquals(360_000L, h.contributionMinor)
    }

    @Test
    fun `NPS emails give the contribution and the holding value`() {
        val c = InvestmentParser.npsContribution(
            "Dear Subscriber, Contribution of Rs.5000.00 for PRAN XXXXXXXX1234 has been credited to your Tier I account on 22-09-2026. -NPS CRA", at,
        )!!
        assertEquals("NPS:1234", c.identifier)
        assertEquals(500_000L, c.amountMinor)
        val v = InvestmentParser.parseEmail("Your NPS a/c PRAN XXXXXXXX1234 holding value as on 23-09-2026 is Rs. 4,56,789.12.", at)!!
        assertEquals(HoldingKind.NPS, v.kind)
        assertEquals("NPS:1234", v.identifier)
        assertEquals(45_678_912L, v.valueMinor)
    }
}
