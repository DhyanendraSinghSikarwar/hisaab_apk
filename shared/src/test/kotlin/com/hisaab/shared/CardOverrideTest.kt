package com.hisaab.shared

import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.CardFlow
import com.hisaab.shared.db.StatementEntity
import com.hisaab.shared.repo.CardDues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CardOverrideTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 25)
    private val stmt = LocalDate.of(2026, 9, 20)
    private val setDay = LocalDate.of(2026, 9, 22)
    private val setAt = setDay.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun ms(day: LocalDate) = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun base(kind: String, key: String, total: Long? = null, due: LocalDate? = null, day: LocalDate? = null, paid: Long? = null, at: Long = 0) = StatementEntity(
        key = key, source = "SMS", sender = "Axis Bank", subject = null, fileName = "", receivedAt = at, status = StatementEntity.PARSED,
        transactionCount = 0, holdingCount = 0, filePath = null, bankName = "Axis Bank", last4 = "0775", processedAt = 1, kind = kind,
        totalDueMinor = total, dueEpochDay = due?.toEpochDay(), statementEpochDay = day?.toEpochDay(), debitsMinor = paid,
    )

    private val bill = base(CardDues.KIND_DUE, "carddue:a", 1_000_000, LocalDate.of(2026, 10, 9), stmt)

    private fun override(billed: Long? = null, min: Long? = null, due: LocalDate? = null, unbilled: Long? = null) = StatementEntity(
        key = "cardovr:axis bank:0775", source = "MANUAL", sender = "Axis Bank", subject = null, fileName = "", receivedAt = setAt,
        status = StatementEntity.PARSED, transactionCount = 0, holdingCount = 0, filePath = null, bankName = "Axis Bank", last4 = "0775",
        processedAt = setAt, kind = CardDues.KIND_OVERRIDE, totalDueMinor = billed, minDueMinor = min, dueEpochDay = due?.toEpochDay(), closingMinor = unbilled,
    )

    private fun pos(rows: List<StatementEntity>, flow: List<CardFlow> = emptyList()) = CardDues.position(rows, null, flow, today, zone)

    @Test
    fun `override wins over the calculated figures`() {
        val p = pos(listOf(bill, override(billed = 750_000, min = 40_000, due = LocalDate.of(2026, 10, 12))))
        assertEquals(750_000L, p.billedMinor)
        assertEquals(40_000L, p.minDueMinor)
        assertEquals(LocalDate.of(2026, 10, 12), p.dueDay)
        assertTrue(p.billedEdited && p.minDueEdited && p.dueEdited)
        assertEquals(1_000_000L, p.calculated?.billedMinor)
    }

    @Test
    fun `fields left blank keep the calculated value`() {
        val p = pos(listOf(bill, override(unbilled = 421_000)))
        assertEquals(1_000_000L, p.billedMinor)
        assertFalse(p.billedEdited)
        assertTrue(p.unbilledEdited)
    }

    @Test
    fun `a newer statement drops the override`() {
        val newer = base(CardDues.KIND_DUE, "carddue:b", 900_000, LocalDate.of(2026, 10, 28), LocalDate.of(2026, 9, 24), at = setAt + 3_600_000)
        val p = pos(listOf(bill, newer, override(billed = 750_000)))
        assertEquals(900_000L, p.billedMinor)
        assertFalse(p.edited)
    }

    @Test
    fun `an older or same-day statement keeps the override`() {
        val sameDay = base(CardDues.KIND_DUE, "carddue:c", 900_000, LocalDate.of(2026, 10, 14), setDay, at = setAt - 3_600_000)
        assertEquals(750_000L, pos(listOf(bill, sameDay, override(billed = 750_000))).billedMinor)
    }

    @Test
    fun `a payment covering the billed override clears it`() {
        val pay = base(CardDues.KIND_PAYMENT, "cardpay:a", day = LocalDate.of(2026, 9, 24), paid = 750_000)
        val p = pos(listOf(bill, pay, override(billed = 750_000)))
        assertFalse(p.billedEdited)
        assertEquals(250_000L, p.billedMinor)
    }

    @Test
    fun `a part payment reduces the billed override`() {
        val flow = listOf(CardFlow(ms(LocalDate.of(2026, 9, 24)), TransactionType.TRANSFER, 200_000))
        val p = pos(listOf(bill, override(billed = 750_000)), flow)
        assertEquals(550_000L, p.billedMinor)
        assertTrue(p.billedEdited)
    }

    @Test
    fun `unbilled override is a baseline plus later spends only`() {
        val flow = listOf(
            CardFlow(ms(LocalDate.of(2026, 9, 21)), TransactionType.DEBIT, 99_900),
            CardFlow(ms(LocalDate.of(2026, 9, 24)), TransactionType.DEBIT, 30_000),
        )
        val p = pos(listOf(bill, override(unbilled = 421_000)), flow)
        assertEquals(451_000L, p.unbilledMinor)
        assertTrue(p.unbilledEdited)
    }

    private fun overrideWith(billed: Long? = null, unbilled: Long? = null, outstanding: Long? = null) =
        override(billed = billed, unbilled = unbilled).copy(openingMinor = outstanding)

    @Test
    fun `card with no statement - override is the only row`() {
        val p = pos(listOf(override(billed = 500_000, unbilled = 120_000)))
        assertEquals(500_000L, p.billedMinor)
        assertEquals(120_000L, p.unbilledMinor)
        assertEquals(620_000L, p.totalMinor)
        assertTrue(p.billedEdited && p.unbilledEdited)
    }

    @Test
    fun `total-only edit replaces outstanding and adds later spends`() {
        val flow = listOf(CardFlow(ms(LocalDate.of(2026, 9, 24)), TransactionType.DEBIT, 30_000))
        val p = pos(listOf(overrideWith(outstanding = 800_000)), flow)
        assertEquals(830_000L, p.outstandingMinor)
        assertEquals(830_000L, p.totalMinor)
        assertTrue(p.outstandingEdited)
    }

    @Test
    fun `billed and unbilled edit changes the total`() {
        val p = pos(listOf(bill, override(billed = 700_000, unbilled = 100_000)))
        assertEquals(800_000L, p.totalMinor)
    }

    @Test
    fun `outstanding override clears on full payment and on a newer statement`() {
        val pay = base(CardDues.KIND_PAYMENT, "cardpay:z", day = LocalDate.of(2026, 9, 24), paid = 800_000)
        assertFalse(pos(listOf(pay, overrideWith(outstanding = 800_000))).outstandingEdited)
        val newer = base(CardDues.KIND_DUE, "carddue:n", 900_000, LocalDate.of(2026, 10, 28), LocalDate.of(2026, 9, 24), at = setAt + 3_600_000)
        assertFalse(pos(listOf(newer, overrideWith(outstanding = 800_000))).edited)
    }

    @Test
    fun `without the override row the calculated position returns`() {
        val flow = listOf(CardFlow(ms(LocalDate.of(2026, 9, 24)), TransactionType.DEBIT, 30_000))
        val p = pos(listOf(bill), flow)
        assertEquals(1_000_000L, p.billedMinor)
        assertEquals(30_000L, p.unbilledMinor)
        assertNull(p.calculated)
        assertFalse(p.edited)
    }
}
