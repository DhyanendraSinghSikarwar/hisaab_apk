package com.hisaab.shared

import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.statement.MfOrderParser
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.HoldingEntity
import com.hisaab.shared.repo.HoldingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** A statement's holdings replace the same schemes from an earlier one; schemes of one statement never replace each other. */
class HoldingRecordTest {
    private class FakeDao : HoldingDao {
        val rows = MutableStateFlow<List<HoldingEntity>>(emptyList())
        private var next = 1L
        override suspend fun insert(h: HoldingEntity): Long = next++.also { id -> rows.value = rows.value + h.copy(id = id) }
        override suspend fun update(h: HoldingEntity) { rows.value = rows.value.map { if (it.id == h.id) h else it } }
        override suspend fun byIdentifier(identifier: String) = rows.value.firstOrNull { it.identifier == identifier }
        override suspend fun byId(id: Long) = rows.value.firstOrNull { it.id == id }
        override fun observeAll(): Flow<List<HoldingEntity>> = rows
        override fun observeTotal(): Flow<Long> = rows.map { r -> r.sumOf { it.valueMinor ?: 0L } }
        override suspend fun delete(id: Long) { rows.value = rows.value.filter { it.id != id } }
    }

    private fun snap(name: String, units: Double, invested: Long, value: Long, asOf: Long, kind: HoldingKind = HoldingKind.MUTUAL_FUND) =
        HoldingSnapshot(kind, name, MfOrderParser.identifierFor(name), units, value, invested, asOf)

    @Test
    fun laterStatementReplacesInsteadOfAdding() = runBlocking {
        val dao = FakeDao()
        val repo = HoldingRepository(dao)
        val ppfas = "Parag Parikh Flexi Cap Fund Direct Growth"
        repo.record(listOf(snap(ppfas, 500.0, 3_050_000, 3_800_000, 1_000)), "STATEMENT")
        repo.record(listOf(snap(ppfas, 520.0, 3_150_000, 4_000_000, 2_000)), "STATEMENT")
        val row = dao.rows.value.single()
        assertEquals(520.0, row.units!!, 1e-6)
        assertEquals(4_000_000L, row.valueMinor)
        assertEquals(3_150_000L, row.investedMinor)
        // An older statement arriving late changes nothing.
        repo.record(listOf(snap(ppfas, 100.0, 1, 1, 500)), "STATEMENT")
        assertEquals(4_000_000L, dao.rows.value.single().valueMinor)
    }

    @Test
    fun differentSchemesOfOneStatementAreAllKept() = runBlocking {
        val dao = FakeDao()
        val repo = HoldingRepository(dao)
        val batch = listOf(
            snap("SBI Gold Direct Plan Growth", 2000.0, 2_950_000, 3_280_000, 1_000, HoldingKind.GOLD),
            snap("SBI Multi Asset Allocation Fund Direct Growth", 700.0, 3_550_000, 3_780_000, 1_000),
            snap("SBI Small Cap Fund Direct Growth", 100.0, 1_000_000, 1_100_000, 1_000),
            snap("LIC MF Gold ETF FoF Direct Growth", 1200.0, 1_790_000, 1_968_000, 1_000, HoldingKind.GOLD),
        )
        repo.record(batch, "STATEMENT")
        assertEquals(4, dao.rows.value.size)
        assertEquals(batch.sumOf { it.valueMinor!! }, dao.rows.value.sumOf { it.valueMinor!! })
        // The same statement again, later: still four, values replaced.
        repo.record(batch.map { it.copy(valueMinor = it.valueMinor!! + 100, asOf = 2_000) }, "STATEMENT")
        assertEquals(4, dao.rows.value.size)
        assertEquals(batch.sumOf { it.valueMinor!! } + 400, dao.rows.value.sumOf { it.valueMinor!! })
    }
}
