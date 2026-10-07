package com.hisaab.shared

import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.statement.MfOrderParser
import com.hisaab.shared.repo.HoldingMatch
import com.hisaab.shared.repo.HoldingMatch.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One investment from a CAS, a Groww table and SIP emails is kept once; different investments never merge. */
class HoldingMatchTest {
    private fun mf(id: String, name: String) = Key(HoldingKind.MUTUAL_FUND, id, name)
    private fun stock(id: String, name: String) = Key(HoldingKind.STOCK, id, name)

    @Test
    fun casIsinAndGrowwTableAndOrderEmailsAreOneFund() {
        val cas = mf("INF879O01027", "PPFAS Mutual Fund - Parag Parikh Flexi Cap Fund - Direct Plan Growth")
        val groww = mf(MfOrderParser.identifierFor("Parag Parikh Flexi Cap Fund Direct Growth"), "Parag Parikh Flexi Cap Fund Direct Growth")
        val sip = mf(MfOrderParser.identifierFor("Parag Parikh Flexi Cap Fund - Direct Plan - Growth"), "Parag Parikh Flexi Cap Fund - Direct Plan - Growth")
        assertTrue(HoldingMatch.same(cas, groww))
        assertTrue(HoldingMatch.same(groww, sip))
        assertEquals("INF879O01027", HoldingMatch.preferredId(groww.identifier, cas.identifier))
    }

    @Test
    fun directAndRegularPlansAreDifferentFunds() {
        assertFalse(HoldingMatch.same(mf("MF:axis small cap", "Axis Small Cap Fund Direct Growth"), mf("MF:axis small cap regular", "Axis Small Cap Fund Regular Growth")))
        assertFalse(HoldingMatch.same(mf("MF:a", "Axis Small Cap Fund Direct Growth"), mf("MF:b", "Axis Small Cap Fund Direct IDCW")))
    }

    @Test
    fun similarIndexFundsAreDifferent() {
        assertFalse(HoldingMatch.sameName("SBI Nifty 50 Index Fund Direct Growth", "SBI Nifty Next 50 Index Fund Direct Growth"))
        assertTrue(HoldingMatch.sameName("HDFC Index Fund Nifty 50 Direct Plan", "HDFC Nifty 50 Index Fund - Direct Plan"))
    }

    @Test
    fun twoIsinsAreNeverMerged() {
        assertFalse(HoldingMatch.same(mf("INF879O01027", "Parag Parikh Flexi Cap"), mf("INF879O01035", "Parag Parikh Flexi Cap")))
    }

    @Test
    fun demoStockAndBrokerTableAreOneStock() {
        val cdsl = stock("INE002A01018", "RELIANCE INDUSTRIES LTD")
        val groww = stock("STOCK:RELIANCE INDUSTRIES", "Reliance Industries")
        assertTrue(HoldingMatch.same(cdsl, groww))
        assertFalse(HoldingMatch.same(stock("STOCK:TATA MOTORS", "Tata Motors"), stock("STOCK:TATA MOTORS DVR", "Tata Motors DVR")))
        // A fund and a stock of the same house are different things.
        assertFalse(HoldingMatch.same(mf("MF:hdfc bank", "HDFC Bank"), stock("INE040A01034", "HDFC Bank Limited")))
    }

    @Test
    fun usStocksNeverMatchIndianOnes() {
        assertFalse(HoldingMatch.same(stock("US:INFY", "Infosys"), stock("STOCK:INFOSYS", "Infosys")))
    }

    @Test
    fun epfWithoutMemberIdIsTheMemberAccount() {
        val sms = Key(HoldingKind.EPF, "EPF:MH/BAN/0012345/000/0001234", "EPF ••1234")
        val passbook = Key(HoldingKind.EPF, "EPF:MHBAN00123450000001234", "EPF ••1234")
        val bare = Key(HoldingKind.EPF, "EPF:default", "EPF")
        assertTrue(HoldingMatch.same(sms, passbook))
        assertTrue(HoldingMatch.same(bare, passbook))
        assertFalse(HoldingMatch.same(passbook, Key(HoldingKind.EPF, "EPF:DLCPM00123450000005678", "EPF ••5678")))
        assertEquals("EPF:MHBAN00123450000001234", HoldingMatch.preferredId("EPF:default", sms.identifier))
    }

    @Test
    fun npsTiersStaySeparate() {
        assertFalse(HoldingMatch.same(Key(HoldingKind.NPS, "NPS:1234", "NPS ••1234"), Key(HoldingKind.NPS, "NPS:1234:T2", "NPS ••1234 Tier II")))
    }

    @Test
    fun duplicatesAreGroupedAndAggregatesGiveWay() {
        val rows = listOf(
            mf("INF879O01027", "Parag Parikh Flexi Cap Fund - Direct Plan Growth"),
            mf("MF:parag parikh flexi cap", "Parag Parikh Flexi Cap Fund Direct Growth"),
            mf("MF:axis small cap", "Axis Small Cap Fund Direct Growth"),
            mf("AGG:INDMONEY:MF", "Mutual funds (INDmoney)"),
            Key(HoldingKind.STOCK, "AGG:INDMONEY:US", "US stocks (INDmoney)"),
        )
        val groups = HoldingMatch.duplicates(rows) { it }
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().size)
        assertEquals(setOf("MF"), HoldingMatch.aggregatesCovered(rows))
        assertTrue("US" in HoldingMatch.aggregatesCovered(listOf(stock("US:AAPL", "AAPL Apple Inc"))))
    }
}
