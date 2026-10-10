package com.hisaab.shared

import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.db.CardFlow
import com.hisaab.shared.db.StatementEntity
import com.hisaab.shared.repo.CardDues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CardDuesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 25)
    private val registry = ParserRegistry.default()

    private fun ms(day: LocalDate, hour: Int = 12) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun row(kind: String, key: String, total: Long? = null, min: Long? = null, due: LocalDate? = null, stmt: LocalDate? = null, limit: Long? = null, paid: Long? = null) =
        StatementEntity(
            key = key, source = "SMS", sender = "HDFC Bank", subject = null, fileName = "", receivedAt = 0, status = StatementEntity.PARSED,
            transactionCount = 0, holdingCount = 0, filePath = null, bankName = "HDFC Bank", last4 = "5521", processedAt = 1,
            kind = kind, totalDueMinor = total, minDueMinor = min, dueEpochDay = due?.toEpochDay(), creditLimitMinor = limit,
            statementEpochDay = stmt?.toEpochDay(), debitsMinor = paid,
        )

    private fun flowOf(body: String, sender: String, day: LocalDate): Pair<CardFlow, Long?> {
        val tx = registry.parse(body, sender, ms(day), Source.SMS)
        assertNotNull(body, tx)
        return CardFlow(tx!!.transactionTime, tx.type, tx.amountMinor) to tx.availableLimitMinor
    }

    @Test
    fun `statement known - billed, min due, due date and spends since`() {
        val stmt = LocalDate.of(2026, 9, 20)
        val rows = listOf(row("CREDIT_CARD", "carddue:HDFC Bank:5521:${stmt.toEpochDay()}", 1234500, 62000, LocalDate.of(2026, 10, 21), stmt, 20000000))
        val spend = flowOf("Rs.349.00 spent via HDFC Bank Card xx5521 at UBER INDIA on 2026-09-22:22:01:45 Avl Lmt: Rs 1,23,456.00", "VM-HDFCBK", LocalDate.of(2026, 9, 22))
        val older = flowOf("Rs.899.00 spent via HDFC Bank Card xx5521 at ZOMATO on 2026-09-10:20:01:45 Avl Lmt: Rs 1,24,000.00", "VM-HDFCBK", LocalDate.of(2026, 9, 10))
        val p = CardDues.position(rows, 12345600, listOf(older.first, spend.first), today, zone)
        assertEquals(1234500L, p.billedMinor)
        assertEquals(62000L, p.minDueMinor)
        assertEquals(LocalDate.of(2026, 10, 21), p.dueDay)
        assertEquals(34900L, p.unbilledMinor)
        assertNull(p.outstandingMinor)
        assertEquals(1234500L, p.owedMinor)
    }

    @Test
    fun `a bill payment after the statement clears the billed amount`() {
        val stmt = LocalDate.of(2026, 9, 20)
        val rows = listOf(row("CREDIT_CARD", "a", 1234500, 62000, LocalDate.of(2026, 10, 21), stmt))
        val paid = CardFlow(ms(LocalDate.of(2026, 9, 23)), TransactionType.TRANSFER, 1234500)
        val p = CardDues.position(rows, null, listOf(paid), today, zone)
        assertEquals(0L, p.billedMinor)
        assertNull(p.minDueMinor)
        assertEquals(0L, p.owedMinor)
        // The issuer's acknowledgement counts the same, and the two are never added.
        val ack = row(CardDues.KIND_PAYMENT, "cardpay", stmt = LocalDate.of(2026, 9, 23), paid = 500000)
        val partial = CardDues.position(rows + ack, null, emptyList(), today, zone)
        assertEquals(734500L, partial.billedMinor)
    }

    @Test
    fun `a bill long past due is ignored`() {
        val rows = listOf(row("CREDIT_CARD", "old", 500000, 25000, LocalDate.of(2026, 7, 5), LocalDate.of(2026, 6, 20)))
        val p = CardDues.position(rows, null, emptyList(), today, zone)
        assertNull(p.billedMinor)
        assertNull(p.outstandingMinor)
    }

    @Test
    fun `no statement - outstanding is limit minus available`() {
        val rows = listOf(row("CREDIT_CARD", "lim", limit = 20000000))
        val spend = flowOf("Rs.349.00 spent via HDFC Bank Card xx5521 at UBER INDIA on 2026-09-22:22:01:45 Avl Lmt: Rs 1,23,456.00", "VM-HDFCBK", LocalDate.of(2026, 9, 22))
        assertEquals(12345600L, spend.second)
        val p = CardDues.position(rows, spend.second, listOf(spend.first), today, zone)
        assertNull(p.billedMinor)
        assertEquals(20000000L - 12345600L, p.outstandingMinor)
        assertEquals(7654400L, p.owedMinor)
    }

    @Test
    fun `no statement and no limit - spends since the last bill payment`() {
        val before = flowOf("Rs.1,799.00 spent on your SBI Credit Card ending with 3456 at FLIPKART on 05/09/26. Trxn. not done by you? Report at https://sbicard.com/Dispute", "AD-SBICRD", LocalDate.of(2026, 9, 5)).first
        val payment = CardFlow(ms(LocalDate.of(2026, 9, 12)), TransactionType.TRANSFER, 179900)
        val a = flowOf("Rs.349.00 spent via HDFC Bank Card xx5521 at UBER INDIA on 2026-09-20:22:01:45 Avl Lmt: Rs 1,23,456.00", "VM-HDFCBK", LocalDate.of(2026, 9, 20)).first
        val b = flowOf("Rs.1,799.00 spent on your SBI Credit Card ending with 3456 at FLIPKART on 24/09/26. Trxn. not done by you? Report at https://sbicard.com/Dispute", "AD-SBICRD", LocalDate.of(2026, 9, 24)).first
        val refund = CardFlow(ms(LocalDate.of(2026, 9, 24), 15), TransactionType.CREDIT, 10000)
        val p = CardDues.position(emptyList(), null, listOf(before, payment, a, b, refund), today, zone)
        assertNull(p.billedMinor)
        assertEquals(34900L + 179900L - 10000L, p.outstandingMinor)
    }

    private fun flow(day: LocalDate, type: TransactionType, amount: Long) = CardFlow(ms(day), type, amount)

    @Test
    fun `an Axis card spend is parsed and counts as unbilled and outstanding at once`() {
        val sms = "Spent INR 1212\nAxis Bank Card no. XX0775\n09-10-26 17:20:55 IST\nFERNANDEZ F\nAvl Limit: INR 553225.36"
        val spend = flowOf(sms, "AX-AXISBK-S", LocalDate.of(2026, 10, 9))
        assertEquals(55322536L, spend.second)
        assertEquals(121200L, spend.first.amountMinor)
        val now = LocalDate.of(2026, 10, 10)
        val withStmt = listOf(row("CREDIT_CARD", "ax", 500000, 25000, LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 5), 60000000))
        val p = CardDues.position(withStmt, spend.second, listOf(spend.first), now, zone)
        assertEquals(500000L, p.billedMinor)
        assertEquals(121200L, p.unbilledMinor)
        val noStmt = CardDues.position(listOf(row("CREDIT_CARD", "lim", limit = 60000000)), spend.second, listOf(spend.first), now, zone)
        assertEquals(60000000L - 55322536L, noStmt.outstandingMinor)
        // Without a limit figure: spends since the last payment.
        assertEquals(121200L, CardDues.position(emptyList(), null, listOf(spend.first), now, zone).outstandingMinor)
    }

    @Test
    fun `statement then spend, refund and payment`() {
        val stmt = LocalDate.of(2026, 9, 20)
        val rows = listOf(row("CREDIT_CARD", "s", 800000, 40000, LocalDate.of(2026, 10, 9), stmt))
        val spend = flow(LocalDate.of(2026, 9, 22), TransactionType.DEBIT, 50000)
        val refund = flow(LocalDate.of(2026, 9, 23), TransactionType.CREDIT, 10000)
        val before = flow(LocalDate.of(2026, 9, 10), TransactionType.DEBIT, 99999)
        val p = CardDues.position(rows, null, listOf(before, spend, refund), today, zone)
        assertEquals(800000L, p.billedMinor)
        assertEquals(LocalDate.of(2026, 10, 9), p.dueDay)
        assertEquals(40000L, p.unbilledMinor)
        assertEquals(false, p.estimated)
        val paid = CardDues.position(rows, null, listOf(spend, refund, flow(LocalDate.of(2026, 9, 24), TransactionType.TRANSFER, 800000)), today, zone)
        assertEquals(0L, paid.billedMinor)
        assertEquals(0L, paid.owedMinor)
        assertEquals(40000L, paid.unbilledMinor)
    }

    @Test
    fun `two cards stay independent`() {
        val a = CardDues.position(emptyList(), null, listOf(flow(LocalDate.of(2026, 9, 22), TransactionType.DEBIT, 100)), today, zone)
        val b = CardDues.position(emptyList(), null, listOf(flow(LocalDate.of(2026, 9, 22), TransactionType.DEBIT, 900)), today, zone)
        assertEquals(100L, a.outstandingMinor)
        assertEquals(900L, b.outstandingMinor)
    }

    @Test
    fun `no statement seen for the latest cycle - billed is rolled over as an estimate`() {
        val rows = listOf(row("CREDIT_CARD", "old", 1000000, 50000, LocalDate.of(2026, 9, 8), LocalDate.of(2026, 8, 20)))
        val f = listOf(
            flow(LocalDate.of(2026, 8, 25), TransactionType.DEBIT, 20000),
            flow(LocalDate.of(2026, 9, 5), TransactionType.TRANSFER, 1000000),
            flow(LocalDate.of(2026, 9, 10), TransactionType.DEBIT, 30000),
            flow(LocalDate.of(2026, 9, 22), TransactionType.DEBIT, 5000),
        )
        val p = CardDues.position(rows, null, f, today, zone)
        assertEquals(true, p.estimated)
        assertEquals(50000L, p.billedMinor)
        assertEquals(LocalDate.of(2026, 9, 20), p.statementDay)
        assertEquals(LocalDate.of(2026, 10, 9), p.dueDay)
        assertEquals(5000L, p.unbilledMinor)
        // A real statement for that cycle replaces the estimate.
        val real = rows + row("CREDIT_CARD", "new", 52000, 2600, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 9, 20))
        val q = CardDues.position(real, null, f, today, zone)
        assertEquals(false, q.estimated)
        assertEquals(52000L, q.billedMinor)
    }

    @Test
    fun `no activity - nothing owed is known`() {
        val p = CardDues.position(emptyList(), 5000000, emptyList(), today, zone)
        assertNull(p.outstandingMinor)
        assertEquals(0L, p.owedMinor)
    }
}
