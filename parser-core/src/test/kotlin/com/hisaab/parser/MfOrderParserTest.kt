package com.hisaab.parser

import com.hisaab.parser.statement.MfOrderParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MfOrderParserTest {
    private val parser = MfOrderParser()
    private val received = LocalDateTime.of(2026, 10, 6, 10, 15).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

    @Test
    fun `Groww SIP instalment email gives scheme, amount, units, NAV and date`() {
        val body = """
            Hi Dhyanendra,
            Your SIP instalment of ₹5,000 for Parag Parikh Flexi Cap Fund Direct Growth has been processed successfully.
            Amount
            ₹5,000.00
            Units allotted
            61.234
            NAV
            ₹81.6543
            NAV date
            05 Oct 2026
            Folio number
            12345678/90
            Happy investing!
            Team Groww
        """.trimIndent()
        val o = parser.parse(body, "Your SIP instalment for Parag Parikh Flexi Cap Fund Direct Growth is successful", "Groww <noreply@groww.in>", received)
        assertNotNull(o)
        o!!
        assertEquals("Groww", o.platform)
        assertEquals("Parag Parikh Flexi Cap Fund Direct Growth", o.schemeName)
        assertEquals(500_000L, o.amountMinor)
        assertEquals(61.234, o.units!!, 0.0001)
        assertEquals(81.6543, o.nav!!, 0.00001)
        assertEquals(LocalDate.of(2026, 10, 5), o.date)
        assertEquals("12345678/90", o.folio)
        assertTrue(o.isSip)
        // Same fund as the CAS names it, so both update one holding.
        assertEquals(MfOrderParser.identifierFor("Parag Parikh Flexi Cap Fund - Direct Plan - Growth"), o.identifier)
    }

    @Test
    fun `Zerodha Coin allotment email with labelled fields`() {
        val body = """
            Hi,
            Units have been allotted for your mutual fund order.
            Fund: Axis Bluechip Fund - Direct Growth
            Folio: 91012345678
            Amount: ₹2,000.00
            Units: 45.123
            NAV: 44.3234
            Allotment date: 2026-10-05
            Team Coin by Zerodha
        """.trimIndent()
        val o = parser.parse(body, "Mutual fund units allotted", "Coin by Zerodha <no-reply@zerodha.net>", received)!!
        assertEquals("Zerodha Coin", o.platform)
        assertEquals("Axis Bluechip Fund - Direct Growth", o.schemeName)
        assertEquals(200_000L, o.amountMinor)
        assertEquals(45.123, o.units!!, 0.0001)
        assertEquals(44.3234, o.nav!!, 0.00001)
        assertEquals(LocalDate.of(2026, 10, 5), o.date)
        assertEquals("91012345678", o.folio)
    }

    @Test
    fun `KFintech confirmation sentence`() {
        val body = "Dear Investor, We are pleased to inform you that your SIP purchase of Rs. 3,000.00 in Mirae Asset Large Cap Fund - Direct Plan - Growth " +
            "under folio 79912345678 has been processed at NAV of Rs. 110.4521 on 06/10/2026 and 27.161 units have been allotted."
        val o = parser.parse(body, "Transaction confirmation - Mirae Asset Mutual Fund", "KFintech <noreply@kfintech.com>", received)!!
        assertEquals("KFintech", o.platform)
        assertEquals("Mirae Asset Large Cap Fund - Direct Plan - Growth", o.schemeName)
        assertEquals(300_000L, o.amountMinor)
        assertEquals(27.161, o.units!!, 0.0001)
        assertEquals(110.4521, o.nav!!, 0.00001)
        assertEquals(LocalDate.of(2026, 10, 6), o.date)
    }

    @Test
    fun `order placed before units are allotted still gives the amount`() {
        val body = "Your order of ₹10,000 in HDFC Index Fund Nifty 50 Plan Direct Growth was placed successfully. Units will be allotted in 2-3 working days."
        val o = parser.parse(body, "Order successful", "Groww <noreply@groww.in>", received)!!
        assertEquals("HDFC Index Fund Nifty 50 Plan Direct Growth", o.schemeName)
        assertEquals(1_000_000L, o.amountMinor)
        assertNull(o.units)
        assertEquals(LocalDate.of(2026, 10, 6), o.date)
    }

    @Test
    fun `reminders, failures and redemptions are not purchases`() {
        assertNull(parser.parse("Your SIP of ₹5,000 for Parag Parikh Flexi Cap Fund Direct Growth is due on 10 Oct 2026. Keep sufficient balance.",
            "SIP reminder", "Groww <noreply@groww.in>", received))
        assertNull(parser.parse("Your SIP instalment of ₹5,000 for Parag Parikh Flexi Cap Fund Direct Growth failed due to insufficient balance.",
            "SIP failed", "Groww <noreply@groww.in>", received))
        assertNull(parser.parse("Your redemption of 10.000 units of Axis Bluechip Fund - Direct Growth has been processed. Amount ₹520.00 will be credited.",
            "Redemption processed", "Coin by Zerodha <no-reply@zerodha.net>", received))
    }

    @Test
    fun `scheme names normalise across apps and statements`() {
        assertEquals("MF:parag parikh flexi cap", MfOrderParser.identifierFor("Parag Parikh Flexi Cap Fund - Direct Plan - Growth"))
        assertEquals("MF:parag parikh flexi cap", MfOrderParser.identifierFor("PARAG PARIKH FLEXI CAP FUND DIRECT GROWTH"))
        assertEquals("MF:hdfc index nifty 50", MfOrderParser.identifierFor("HDFC Index Fund-NIFTY 50 Plan-Direct Plan"))
    }
}
