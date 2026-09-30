package com.hisaab.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hisaab.parser.model.Source
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.db.HisaabDatabase
import com.hisaab.shared.repo.toEntity
import androidx.room.useReaderConnection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {
    private lateinit var db: HisaabDatabase
    private val registry = ParserRegistry.default()
    private val t0 = 1_790_000_000_000L

    @Before fun open() { db = inMemoryDb() }
    @After fun close() { db.close() }

    private fun parse(body: String, at: Long = t0) = registry.parse(body, "VM-HDFCBK", at, Source.SMS)!!

    @Test
    fun uniqueHashIgnoresTheSecondInsert() = runTest {
        val tx = parse("Rs.250.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA a@ybl. UPI Ref 526812345678")
        val first = db.transactions().insert(tx.toEntity(null, t0))
        val second = db.transactions().insert(tx.toEntity(null, t0))
        assertNotEquals(-1L, first)
        assertEquals(-1L, second)
        assertEquals(1, db.transactions().count())
    }

    @Test
    fun findPotentialDuplicatesUsesAmountAndWindow() = runTest {
        val dao = db.transactions()
        dao.insert(parse("Rs.120.00 spent on HDFC Bank Card x5678 at STARBUCKS on 2026-09-25:10:00:00.").toEntity(null, t0))
        dao.insert(parse("Rs.121.00 spent on HDFC Bank Card x5678 at STARBUCKS on 2026-09-25:10:01:00.").toEntity(null, t0))
        val base = dao.getAll().first().timestamp
        assertEquals(1, dao.findPotentialDuplicates(12000, base - 60_000, base + 60_000).size)
        assertEquals(0, dao.findPotentialDuplicates(12000, base + 60_000, base + 120_000).size)
    }

    @Test
    fun theDuplicateLookupUsesTheCompositeIndex() = runTest {
        // EXPLAIN QUERY PLAN proves the fuzzy lookup never falls back to a full table scan.
        val plan = StringBuilder()
        db.useReaderConnection { conn ->
            conn.usePrepared("EXPLAIN QUERY PLAN SELECT * FROM transactions WHERE amountMinor = 100 AND timestamp BETWEEN 0 AND 1") { st ->
                while (st.step()) plan.append(st.getText(3)).append('\n')
            }
        }
        assert(plan.contains("index_transactions_amountMinor_accountLast4_timestamp")) { plan.toString() }
    }

    @Test
    fun totalsAndCategoriesAggregate() = runTest {
        val dao = db.transactions()
        dao.insert(parse("Rs.250.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA swiggy@icici. UPI Ref 526800000001").toEntity(null, t0))
        dao.insert(parse("Rs.100.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA uber@icici. UPI Ref 526800000002").toEntity(null, t0))
        dao.insert(parse("Rs.5000.00 credited to HDFC Bank A/c XX1234 on 25-09-26 from VPA boss@okaxis (UPI 526800000003)").toEntity(null, t0))
        assertEquals(35000L, dao.observeTotal(listOf("DEBIT", "INVESTMENT"), 0, Long.MAX_VALUE).first())
        assertEquals(500000L, dao.observeTotal(listOf("CREDIT"), 0, Long.MAX_VALUE).first())
        val cats = dao.observeCategoryTotals(0, Long.MAX_VALUE).first().associate { it.category.name to it.total }
        assertEquals(mapOf("FOOD" to 25000L, "TRANSPORT" to 10000L), cats)
    }

    @Test
    fun filtersBySearchAndSource() = runTest {
        val dao = db.transactions()
        val id = dao.insert(parse("Rs.250.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA swiggy@icici. UPI Ref 526800000001").toEntity(null, t0))
        db.sources().insert(com.hisaab.shared.db.TransactionSourceEntity(transactionId = id, source = "SMS", sourceMessageId = "m1", sender = "x", parsedHash = "h", rawText = null, receivedAt = t0))
        assertEquals(1, dao.observe("swig", null, null, null, "SMS", 0, Long.MAX_VALUE, 50).first().size)
        assertEquals(0, dao.observe("swig", null, null, null, "EMAIL", 0, Long.MAX_VALUE, 50).first().size)
        assertEquals(0, dao.observe("zomato", null, null, null, null, 0, Long.MAX_VALUE, 50).first().size)
        assertNull(dao.getById(999))
    }
}
