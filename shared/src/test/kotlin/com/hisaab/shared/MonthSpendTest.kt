package com.hisaab.shared

import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.MonthSpend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MonthSpendTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 10)
    private var n = 0

    private fun tx(
        day: LocalDate, rupees: Long, type: TransactionType = TransactionType.DEBIT, category: Category = Category.SHOPPING,
        account: Long? = 1, kind: AccountKind = AccountKind.ACCOUNT,
    ) = TransactionEntity(
        amountMinor = rupees * 100, currency = "INR", type = type, bankName = "Test Bank", accountLast4 = "1234", accountKind = kind,
        accountId = account, merchant = "M", upiId = null, referenceNumber = null, channel = Channel.OTHER, balanceMinor = null,
        availableLimitMinor = null, timestamp = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), hasExplicitTime = true,
        category = category, transactionHash = "h${n++}", confidence = 1f, createdAt = 0,
    )

    private val d = { x: Int -> LocalDate.of(2026, 10, x) }
    private val l = { x: Int -> LocalDate.of(2026, 9, x) }

    @Test fun cardSpendCounted() {
        val r = MonthSpend.compute(listOf(tx(d(2), 500, account = 9, kind = AccountKind.CARD), tx(d(3), 200)), today, zone)
        assertEquals(70_000L, r.spent)
    }

    @Test fun billPaymentFromAccountNotCounted() {
        val r = MonthSpend.compute(
            listOf(tx(d(2), 500, account = 9, kind = AccountKind.CARD), tx(d(5), 500, type = TransactionType.TRANSFER, category = Category.TRANSFER)),
            today, zone,
        )
        assertEquals(50_000L, r.spent)
    }

    @Test fun refundNetsAgainstSpend() {
        val r = MonthSpend.compute(listOf(tx(d(2), 500), tx(d(4), 100, type = TransactionType.CREDIT, category = Category.REFUND)), today, zone)
        assertEquals(40_000L, r.spent)
        assertEquals(0L, r.income)
    }

    @Test fun sameDayTransferIgnored() {
        val r = MonthSpend.compute(
            listOf(tx(d(6), 900, category = Category.TRANSFER), tx(d(6), 900, type = TransactionType.CREDIT, category = Category.TRANSFER, account = 2)),
            today, zone,
        )
        assertEquals(0L, r.spent)
        assertEquals(0L, r.income)
    }

    @Test fun comparesEqualElapsedDays() {
        val r = MonthSpend.compute(
            listOf(tx(d(3), 300), tx(l(4), 200), tx(l(10), 100), tx(l(20), 5000)),
            today, zone,
        )
        assertEquals(30_000L, r.spent)
        assertEquals(30_000L, r.previousSamePeriod)
        assertEquals(530_000L, r.previousSpent)
        assertEquals(0, r.changePercent)
        assertEquals(10, r.thisCum.size)
        assertEquals(30, r.lastCum.size)
        assertNull(MonthSpend.compute(listOf(tx(d(3), 300)), today, zone).changePercent)
    }

    private val all = listOf(tx(d(3), 300), tx(l(4), 200), tx(l(20), 100), tx(LocalDate.of(2026, 8, 15), 50))

    @Test fun lastMonthSelectedComparesWholePreviousMonth() {
        val r = MonthSpend.compute(all, l(1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), today, zone)
        assertEquals(30_000L, r.spent)
        assertEquals(30, r.thisCum.size)
        assertEquals(5_000L, r.previousSpent)
        assertEquals(5_000L, r.previousSamePeriod)
        assertEquals(500, r.changePercent)
    }

    @Test fun customRangeComparesEqualLengthBefore() {
        val r = MonthSpend.compute(all, d(1), d(10), l(21), l(30), today, zone)
        assertEquals(30_000L, r.spent)
        assertEquals(0L, r.previousSpent)
        assertNull(r.changePercent)
        val r2 = MonthSpend.compute(all, l(1), l(10), LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 31), today, zone)
        assertEquals(20_000L, r2.spent)
        assertEquals(10, r2.thisCum.size)
        assertEquals(10, r2.lastCum.size)
    }

    @Test fun futureDaysOfRunningPeriodIgnored() {
        val r = MonthSpend.compute(all + tx(d(20), 999), d(1), d(31), l(1), l(30), today, zone)
        assertEquals(30_000L, r.spent)
        assertEquals(10, r.thisCum.size)
        assertEquals(31, r.days)
    }

    @Test fun noComparisonWhenNoPreviousPeriod() {
        val r = MonthSpend.compute(all, LocalDate.of(2026, 1, 1), today, null, null, today, zone)
        assertEquals(65_000L, r.spent)
        assertEquals(false, r.hasPrevious)
        assertNull(r.changePercent)
        assertEquals(0L, r.previousSpent)
        assertEquals(emptyList<Long>(), r.lastCum)
    }

    /** The Book rule (usage only; no account = personal) is applied by the caller; the breakdown then covers only in-book spend. */
    @Test fun bookFilteredBreakdownSumsToTotal() {
        val business = setOf(7L)
        val txs = listOf(tx(d(1), 100, account = 1), tx(d(2), 400, account = 7), tx(d(3), 50, account = null))
        fun inBook(t: TransactionEntity, personal: Boolean?) = when (personal) {
            null -> true
            true -> t.accountId == null || t.accountId !in business
            false -> t.accountId != null && t.accountId in business
        }
        val personal = MonthSpend.compute(txs.filter { inBook(it, true) }, today, zone)
        val biz = MonthSpend.compute(txs.filter { inBook(it, false) }, today, zone)
        val everything = MonthSpend.compute(txs.filter { inBook(it, null) }, today, zone)
        assertEquals(15_000L, personal.spent)
        assertEquals(40_000L, biz.spent)
        assertEquals(55_000L, everything.spent)
        for (r in listOf(personal, biz, everything)) assertEquals(r.spent, r.byAccount.sumOf { it.amount })
        assertEquals(listOf<Long?>(1L, null), personal.byAccount.map { it.accountId })
        assertEquals(listOf<Long?>(7L), biz.byAccount.map { it.accountId })
    }

    @Test fun breakdownSumsToTotalAndIncomeSaved() {
        val r = MonthSpend.compute(
            listOf(
                tx(d(1), 100, account = 1), tx(d(2), 300, account = 2, kind = AccountKind.CARD), tx(d(3), 50, account = null),
                tx(d(4), 1000, type = TransactionType.CREDIT, category = Category.SALARY),
            ),
            today, zone,
        )
        assertEquals(r.spent, r.byAccount.sumOf { it.amount })
        assertEquals(2L, r.byAccount.first().accountId)
        assertEquals(100_000L - 45_000L, r.saved)
    }
}
