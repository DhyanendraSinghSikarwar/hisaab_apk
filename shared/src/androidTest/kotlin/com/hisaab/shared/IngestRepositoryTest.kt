package com.hisaab.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hisaab.parser.model.Source
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.db.HisaabDatabase
import com.hisaab.shared.repo.IncomingMessage
import com.hisaab.shared.repo.IngestOutcome
import com.hisaab.shared.repo.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The dedup scenarios against real Room: SMS + email for one payment must become one row with two sources. */
@RunWith(AndroidJUnit4::class)
class IngestRepositoryTest {
    private lateinit var db: HisaabDatabase
    private lateinit var repo: TransactionRepository
    private val registry = ParserRegistry.default()
    private val t0 = 1_790_000_000_000L
    private val minute = 60_000L

    @Before fun open() { db = inMemoryDb(); repo = TransactionRepository(db, registry) }
    @After fun close() { db.close() }

    private val smsBody = "Rs.450.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA swiggy@icici. UPI Ref 526812345678. Avl bal:INR 9,550.00"
    private val mailNoRef = "Dear Customer,\nRs.450.00 has been debited from account **1234 to VPA swiggy@icici SWIGGY on 25-09-26."
    private val mailWithRef = "$mailNoRef\nYour UPI transaction reference number is 526812345678."

    private fun sms(body: String, at: Long, id: String) =
        IncomingMessage(registry.parse(body, "VM-HDFCBK", at, Source.SMS)!!, id, body)
    private fun mail(body: String, at: Long, id: String) =
        IncomingMessage(registry.parse(body, "alerts@hdfcbank.net", at, Source.EMAIL)!!, id, body)

    @Test
    fun smsThenEmailIsOneRecordWithBothSources() = runTest {
        assertEquals(IngestOutcome.INSERTED, repo.ingest(sms(smsBody, t0, "sms-1")))
        assertEquals(IngestOutcome.MERGED, repo.ingest(mail(mailWithRef, t0 + 2 * minute, "gm-1")))
        val all = db.transactions().getAll()
        assertEquals(1, all.size)
        assertEquals(setOf("SMS", "EMAIL"), db.sources().forTransaction(all[0].id).map { it.source }.toSet())
        assertEquals(955000L, all[0].balanceMinor)
    }

    @Test
    fun emailThenSmsIsOneRecord() = runTest {
        repo.ingest(mail(mailWithRef, t0, "gm-1"))
        assertEquals(IngestOutcome.MERGED, repo.ingest(sms(smsBody, t0 + minute, "sms-1")))
        val tx = db.transactions().getAll().single()
        assertEquals(955000L, tx.balanceMinor) // filled from the SMS
        assertEquals("Swiggy", tx.merchant)
    }

    @Test
    fun delayedEmailWithoutReferenceMerges() = runTest {
        repo.ingest(sms(smsBody, t0, "sms-1"))
        assertEquals(IngestOutcome.MERGED, repo.ingest(mail(mailNoRef, t0 + 25 * minute, "gm-1")))
        assertEquals(1, db.transactions().count())
    }

    @Test
    fun sameMessageTwiceIsSkipped() = runTest {
        repo.ingest(sms(smsBody, t0, "sms-1"))
        assertEquals(IngestOutcome.ALREADY_PROCESSED, repo.ingest(sms(smsBody, t0, "sms-1")))
        assertEquals(1, db.sources().forTransaction(db.transactions().getAll()[0].id).size)
    }

    @Test
    fun lowConfidenceMatchGoesToReviewAndCanBeMergedOrKept() = runTest {
        val a = "Rs.120.00 spent on HDFC Bank Card x5678 at STARBUCKS on 2026-09-25:10:00:00."
        val b = "Rs.120.00 spent on HDFC Bank Card x5678 at STARBUCKS on 2026-09-25:10:04:00."
        repo.ingest(sms(a, t0, "s1"))
        assertEquals(IngestOutcome.FLAGGED_FOR_REVIEW, repo.ingest(sms(b, t0 + 4 * minute, "s2")))
        assertEquals(1, db.transactions().observeReviewCount().first())

        val flagged = db.transactions().observeNeedsReview().first().single()
        repo.mergeFlagged(flagged.id)
        assertEquals(1, db.transactions().count())
        assertEquals(0, db.transactions().observeReviewCount().first())
        assertEquals(2, db.sources().forTransaction(db.transactions().getAll()[0].id).size)
    }

    @Test
    fun keepSeparateClearsTheFlag() = runTest {
        repo.ingest(sms("Rs.120.00 spent on HDFC Bank Card x5678 at STARBUCKS on 2026-09-25:10:00:00.", t0, "s1"))
        repo.ingest(sms("Rs.120.00 spent on HDFC Bank Card x5678 at STARBUCKS on 2026-09-25:10:04:00.", t0, "s2"))
        repo.keepSeparate(db.transactions().observeNeedsReview().first().single().id)
        assertEquals(2, db.transactions().count())
        assertEquals(0, db.transactions().observeReviewCount().first())
    }

    @Test
    fun splitUndoesAMerge() = runTest {
        repo.ingest(sms(smsBody, t0, "sms-1"))
        repo.ingest(mail(mailWithRef, t0 + minute, "gm-1"))
        val tx = db.transactions().getAll().single()
        val emailSource = db.sources().forTransaction(tx.id).first { it.source == "EMAIL" }
        val newId = repo.split(tx.id, emailSource.id)
        assertNotNull(newId)
        assertEquals(2, db.transactions().count())
        assertEquals(1, db.sources().forTransaction(tx.id).size)
    }

    @Test
    fun accountsAreCreatedAndBalanceOnlyMovesForward() = runTest {
        repo.ingest(sms("INR 100.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA a@ybl. UPI Ref 526800000001. Avl bal:INR 5,000.00", t0, "a"))
        repo.ingest(sms("INR 100.00 debited from HDFC Bank A/c XX1234 on 20-09-26 to VPA b@ybl. UPI Ref 526800000002. Avl bal:INR 9,000.00", t0, "b"))
        val account = db.accounts().observeAll().first().single()
        assertEquals("1234", account.last4)
        assertEquals(500000L, account.latestBalanceMinor) // the older message did not rewind it
    }

    @Test
    fun batchOfCombinedSmsAndEmailHasZeroDuplicates() = runTest {
        val batch = (0 until 60).flatMap { i ->
            val amount = 100 + i
            val ref = "5268%08d".format(i)
            val at = t0 - i * 60 * minute
            val s = sms("Rs.$amount.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA shop$i@ybl. UPI Ref $ref", at, "sms-$i")
            val m = mail("Dear Customer,\nRs.$amount.00 has been debited from account **1234 to VPA shop$i@ybl SHOP on 25-09-26." +
                (if (i % 2 == 0) "\nYour UPI transaction reference number is $ref." else ""), at + (i % 5) * 5 * minute, "gm-$i")
            if (i % 2 == 0) listOf(m, s) else listOf(s, m)
        }
        val report = repo.ingestBatch(batch)
        assertEquals(60, report.inserted)
        assertEquals(60, report.merged)
        assertEquals(60, db.transactions().count())
        assertTrue(db.transactions().getAll().all { db.sources().forTransaction(it.id).size == 2 })
    }
}
