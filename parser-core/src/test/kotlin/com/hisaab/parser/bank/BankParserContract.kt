package com.hisaab.parser.bank

import com.hisaab.parser.BankParser
import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.corpus.Sample
import com.hisaab.parser.extract.Money
import com.hisaab.parser.model.Source
import com.hisaab.parser.registry.ParserRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertAll
import java.time.Instant

/** Every bank parser test runs the same checks over its own corpus. */
abstract class BankParserContract {
    abstract val parser: BankParser
    abstract val samples: List<Sample>

    private val registry = ParserRegistry.default()

    @TestFactory
    fun corpus(): List<DynamicTest> = samples.map { s -> dynamicTest(s.label) { verify(parser, s) } }

    @Test
    fun `corpus has at least 10 sms and 5 email transactions plus otp promo and failed negatives`() {
        val positives = samples.filter { it.expect != null }
        assertTrue(positives.count { it.source == Source.SMS } >= 10, "need >= 10 SMS samples")
        assertTrue(positives.count { it.source == Source.EMAIL } >= 5, "need >= 5 email samples")
        val negatives = samples.filter { it.expect == null }.map { it.body.lowercase() }
        assertTrue(negatives.any { "otp" in it || "one time password" in it }, "need an OTP negative")
        assertTrue(negatives.any { "apply now" in it || "pre-approved" in it || "eligible" in it }, "need a promo negative")
        assertTrue(negatives.any { "declined" in it || "failed" in it || "unsuccessful" in it }, "need a failed-transaction negative")
    }

    @Test
    fun `registry routes every sample sender to this parser`() {
        for (s in samples) assertEquals(parser.javaClass, registry.resolve(s.sender)?.javaClass, s.sender)
    }

    companion object {
        fun verify(parser: BankParser, s: Sample) {
            val tx = parser.parse(s.body, s.sender, Fixture.RECEIVED_AT, s.source)
            val e = s.expect
            if (e == null) {
                assertNull(tx, "expected rejection, parsed $tx")
                return
            }
            assertNotNull(tx, "expected a transaction, got null")
            tx!!
            assertAll(
                { assertEquals(Money.parse(e.amount)!!.minor, tx.amountMinor, "amount") },
                { assertEquals(e.currency, tx.currency, "currency") },
                { assertEquals(e.type, tx.type, "type") },
                { assertEquals(e.last4, tx.accountLast4, "accountLast4") },
                { assertEquals(e.ref, tx.referenceNumber, "referenceNumber") },
                { if (e.merchant != null) assertEquals(e.merchant, tx.merchant, "merchant") },
                { if (e.balance != null) assertEquals(Money.parse(e.balance)!!.minor, tx.balanceMinor, "balance") },
                { if (e.kind != null) assertEquals(e.kind, tx.accountKind, "accountKind") },
                {
                    if (e.date != null) {
                        val d = Instant.ofEpochMilli(tx.transactionTime).atZone(Fixture.IST).toLocalDate().toString()
                        assertEquals(e.date, d, "date")
                    }
                },
            )
        }
    }
}
