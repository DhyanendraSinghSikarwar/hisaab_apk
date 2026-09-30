package com.hisaab.parser.dedup

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source
import com.hisaab.parser.registry.ParserRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** The dedup scenarios from the spec, run through the real parser and an in-memory store. */
class DuplicateMatcherTest {
    private val registry = ParserRegistry.default()
    private val minute = 60_000L
    private val t0 = Fixture.RECEIVED_AT

    /** Mimics the Room layer: insert on New, attach a source on Duplicate. */
    private class Store : DedupLookup {
        val rows = mutableListOf<StoredTransaction>()
        val hashes = mutableMapOf<String, Long>()
        var nextId = 1L

        fun apply(tx: ParsedTransaction, decision: DedupDecision): Long = when (decision) {
            is DedupDecision.Duplicate -> {
                val i = rows.indexOfFirst { it.id == decision.existingId }
                rows[i] = rows[i].copy(
                    sources = rows[i].sources + tx.source.name,
                    referenceNumber = rows[i].referenceNumber ?: tx.referenceNumber,
                    merchant = rows[i].merchant ?: tx.merchant,
                )
                hashes[tx.transactionHash] = decision.existingId
                decision.existingId
            }
            else -> {
                val id = nextId++
                rows += StoredTransaction(id, tx.amountMinor, tx.type, tx.accountLast4, tx.merchant, tx.referenceNumber, tx.transactionTime, setOf(tx.source.name))
                hashes[tx.transactionHash] = id
                id
            }
        }

        override suspend fun byReference(referenceNumber: String) = rows.filter { it.referenceNumber == referenceNumber }
        override suspend fun byHash(transactionHash: String) = hashes[transactionHash]?.let { id -> rows.first { it.id == id } }
        override suspend fun potentialDuplicates(amountMinor: Long, from: Long, to: Long) =
            rows.filter { it.amountMinor == amountMinor && it.transactionTime in from..to }
    }

    private fun sms(body: String, at: Long = t0, sender: String = "VM-HDFCBK") = registry.parse(body, sender, at, Source.SMS)!!
    private fun mail(body: String, at: Long = t0, sender: String = "alerts@hdfcbank.net") = registry.parse(body, sender, at, Source.EMAIL)!!

    private fun ingest(store: Store, vararg txs: ParsedTransaction): List<DedupDecision> = runBlocking {
        txs.map { tx -> DuplicateMatcher.decide(tx, store).also { store.apply(tx, it) } }
    }

    // One UPI payment, reported by SMS (with reference) and by email (without it).
    private val smsWithRef = "Rs.450.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA swiggy@icici. UPI Ref 526812345678. Avl bal:INR 9,550.00"
    private val emailNoRef = "Dear Customer,\nRs.450.00 has been debited from account **1234 to VPA swiggy@icici SWIGGY on 25-09-26."
    private val emailWithRef = "Dear Customer,\nRs.450.00 has been debited from account **1234 to VPA swiggy@icici SWIGGY on 25-09-26.\nYour UPI transaction reference number is 526812345678."

    @Test
    fun `sms then email for the same transaction is one record`() {
        val store = Store()
        val d = ingest(store, sms(smsWithRef), mail(emailWithRef, t0 + 2 * minute))
        assertEquals(DedupDecision.New, d[0])
        assertInstanceOf(DedupDecision.Duplicate::class.java, d[1])
        assertEquals(1, store.rows.size)
        assertEquals(setOf("SMS", "EMAIL"), store.rows.single().sources)
    }

    @Test
    fun `email then sms for the same transaction is one record`() {
        val store = Store()
        val d = ingest(store, mail(emailWithRef), sms(smsWithRef, t0 + minute))
        assertEquals(MatchReason.REFERENCE, (d[1] as DedupDecision.Duplicate).reason)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `email delayed by 25 minutes still merges`() {
        val store = Store()
        val d = ingest(store, sms(smsWithRef), mail(emailNoRef, t0 + 25 * minute))
        assertEquals(DedupDecision.Duplicate(1, MatchReason.FUZZY), d[1])
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `missing reference on both sides merges by account, amount and time`() {
        val store = Store()
        val cardSms = "Rs.1500.00 spent on HDFC Bank Card x5678 at AMAZON PAY INDIA on 2026-09-24:18:22:10."
        val cardMail = "Dear Card Member,\nThank you for using your HDFC Bank Credit Card ending 5678 for Rs 1,500.00 at AMAZON PAY INDIA on 24-09-2026 18:22:10."
        val d = ingest(store, sms(cardSms), mail(cardMail, t0 + 3 * minute))
        assertInstanceOf(DedupDecision.Duplicate::class.java, d[1])
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `email hours late on the same account goes to review instead of duplicating silently`() {
        val store = Store()
        val d = ingest(store, sms(smsWithRef), mail(emailNoRef, t0 + 3 * 60 * minute))
        assertEquals(DedupDecision.PossibleDuplicate::class, d[1]::class)
    }

    @Test
    fun `two real purchases of the same amount with different references stay separate`() {
        val store = Store()
        val a = sms("Rs.20.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA chai@ybl. UPI Ref 526800000001")
        val b = sms("Rs.20.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA chai@ybl. UPI Ref 526800000002", t0 + 5 * minute)
        val d = ingest(store, a, b)
        assertEquals(listOf<DedupDecision>(DedupDecision.New, DedupDecision.New), d)
    }

    @Test
    fun `same amount on different accounts stays separate`() {
        val store = Store()
        val a = sms("Rs.500.00 spent on HDFC Bank Card x5678 at UBER on 2026-09-25:10:00:00.")
        val b = mail("Dear Customer,\nRs.500.00 has been debited from account **1234 to VPA x@ybl RAHUL on 25-09-26.", t0 + minute)
        val d = ingest(store, a, b)
        assertEquals(DedupDecision.New, d[1])
    }

    @Test
    fun `two sms without references for the same amount and card go to review`() {
        val store = Store()
        val a = sms("Rs.120.00 spent on HDFC Bank Card x5678 at STARBUCKS on 2026-09-25:10:00:00.")
        val b = sms("Rs.120.00 spent on HDFC Bank Card x5678 at STARBUCKS on 2026-09-25:10:04:00.", t0 + 4 * minute)
        val d = ingest(store, a, b)
        assertEquals(DedupDecision.PossibleDuplicate::class, d[1]::class)
        assertEquals(2, store.rows.size)
    }

    @Test
    fun `debit and credit of the same amount are never duplicates`() {
        val store = Store()
        val a = sms("Rs.500.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA x@ybl. UPI Ref 526800000011")
        val b = sms("Rs.500.00 credited to HDFC Bank A/c XX1234 on 25-09-26 from VPA x@ybl. UPI Ref 526800000012", t0 + minute)
        assertEquals(DedupDecision.New, ingest(store, a, b)[1])
    }

    @Test
    fun `combined sms and email corpus produces zero duplicates`() {
        // Every transaction arrives twice, once per source, in shuffled order with a small delay.
        val store = Store()
        val pairs = (0 until 40).map { i ->
            val amount = 100 + i * 7
            val ref = "5268%08d".format(i)
            val at = t0 - i * 90 * minute
            val s = sms("Rs.$amount.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA shop$i@ybl. UPI Ref $ref", at)
            val m = mail(
                "Dear Customer,\nRs.$amount.00 has been debited from account **1234 to VPA shop$i@ybl SHOP on 25-09-26." +
                    (if (i % 2 == 0) "\nYour UPI transaction reference number is $ref." else ""),
                at + (i % 5) * 5 * minute,
            )
            if (i % 3 == 0) listOf(m, s) else listOf(s, m)
        }.flatten()
        ingest(store, *pairs.toTypedArray())
        assertEquals(40, store.rows.size)
        assertEquals(40, store.rows.count { it.sources == setOf("SMS", "EMAIL") })
    }
}
