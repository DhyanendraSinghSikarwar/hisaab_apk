package com.hisaab.parser.registry

import com.hisaab.parser.bank.HdfcBankParser
import com.hisaab.parser.bank.SbiParser
import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ParserRegistryTest {
    private val registry = ParserRegistry.default()

    @Test
    fun `sms headers resolve regardless of operator prefix and suffix`() {
        for (s in listOf("VM-HDFCBK", "AD-HDFCBK-S", "JD-HDFCBK-T", "HDFCBK", "vm-hdfcbk")) {
            assertEquals(HdfcBankParser::class.java, registry.resolve(s)?.javaClass, s)
        }
    }

    @Test
    fun `email senders resolve by address, domain, or parent domain`() {
        assertEquals(HdfcBankParser::class.java, registry.resolve("HDFC Bank <alerts@hdfcbank.net>")?.javaClass)
        assertEquals(HdfcBankParser::class.java, registry.resolve("alerts@hdfcbank.bank.in")?.javaClass)
        assertEquals(SbiParser::class.java, registry.resolve("donotreply.sbiatm@alerts.sbi.co.in")?.javaClass)
        assertEquals(SbiParser::class.java, registry.resolve("x@mail.sbi.co.in")?.javaClass)
    }

    @Test
    fun `unknown senders are filtered out before parsing`() {
        assertFalse(registry.isKnownSender("VM-AMAZON"))
        assertFalse(registry.isKnownSender("+919876543210"))
        assertFalse(registry.isKnownSender("offers@shopping.com"))
        assertTrue(registry.isKnownSender("AX-ICICIB"))
    }

    @Test
    fun `the generic parser takes bank-looking senders nobody claims`() {
        val tx = registry.parse("INR 500.00 debited from A/c XX4321 on 24-09-26 to SWIGGY. UPI Ref 526812340000", "VM-ZZXBNK", Fixture.RECEIVED_AT, Source.SMS)
        assertNotNull(tx)
        assertEquals("ZZXBNK", tx!!.bankName)
        assertEquals("4321", tx.accountLast4)
        assertEquals("Swiggy", tx.merchant)
    }

    @Test
    fun `the generic parser ignores senders that are not banks`() {
        assertNull(registry.parse("INR 500.00 debited from A/c XX4321 on 24-09-26", "VM-SWIGGY", Fixture.RECEIVED_AT, Source.SMS))
    }

    @Test
    fun `default email whitelist covers every bank`() {
        val senders = registry.defaultEmailSenders
        for (d in listOf("hdfcbank.net", "icicibank.com", "sbi.co.in", "axisbank.com", "kotak.com", "idfcfirstbank.com", "yesbank.in", "bankofbaroda.co.in", "pnb.co.in", "aubank.in")) {
            assertTrue(d in senders, d)
        }
    }

    @Test
    fun `two parsers may not claim the same key`() {
        assertThrows<IllegalArgumentException> { ParserRegistry(listOf(HdfcBankParser(), HdfcBankParserClone())) }
    }

    private class HdfcBankParserClone : com.hisaab.parser.bank.BaseBankParser(com.hisaab.parser.ParserConfig()) {
        override val bankName = "Clone"
        override val smsHeaders = setOf("HDFCBK")
        override val emailDomains = emptySet<String>()
    }

    @Test
    fun `sender keys`() {
        assertEquals(listOf("HDFCBK"), SenderKeys.candidates("VM-HDFCBK-S"))
        assertEquals(listOf("a@alerts.sbi.co.in", "alerts.sbi.co.in", "sbi.co.in", "co.in"), SenderKeys.candidates("SBI <a@alerts.sbi.co.in>"))
        assertEquals(emptyList<String>(), SenderKeys.candidates("  "))
    }
}
