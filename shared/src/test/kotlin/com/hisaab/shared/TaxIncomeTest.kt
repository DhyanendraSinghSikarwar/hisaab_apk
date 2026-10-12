package com.hisaab.shared

import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.TaxIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaxIncomeTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 11)
    private var n = 0

    private fun credit(day: LocalDate, rupees: Long, category: Category, merchant: String = "Acme Payroll", type: TransactionType = TransactionType.CREDIT) =
        TransactionEntity(
            amountMinor = rupees * 100, currency = "INR", type = type, bankName = "Test Bank", accountLast4 = "1234", accountKind = AccountKind.ACCOUNT,
            accountId = 1, merchant = merchant, upiId = null, referenceNumber = null, channel = Channel.OTHER, balanceMinor = null,
            availableLimitMinor = null, timestamp = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), hasExplicitTime = true,
            category = category, transactionHash = "h${n++}", confidence = 1f, createdAt = 0,
        )

    private fun salaries(from: LocalDate, count: Int, rupees: Long) = (0 until count).map { credit(from.plusMonths(it.toLong()).withDayOfMonth(1), rupees, Category.SALARY) }

    @Test fun partYearIsProjectedAtLatestSalary() {
        val txs = salaries(LocalDate.of(2026, 4, 1), 6, 100_000) +
            credit(LocalDate.of(2026, 6, 30), 3_000, Category.INCOME, "Interest credit") +
            credit(LocalDate.of(2026, 7, 2), 500, Category.INCOME, "Dividend Acme")
        val e = TaxIncome.estimate(txs, LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), today, zone)
        assertTrue(e.estimated)
        assertEquals(6, e.monthsOfData)
        assertEquals(600_000L * 100, e.salarySoFar)
        assertEquals(1_200_000L * 100, e.salary)
        assertEquals(6_000L * 100, e.interest)
        assertEquals(1_000L * 100, e.dividends)
    }

    @Test fun fullYearNeedsNoEstimate() {
        val txs = salaries(LocalDate.of(2025, 4, 1), 12, 50_000) + credit(LocalDate.of(2025, 5, 1), 2_000, Category.INCOME, "FD interest")
        val e = TaxIncome.estimate(txs, LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31), today, zone)
        assertFalse(e.estimated)
        assertEquals(12, e.monthsOfData)
        assertEquals(600_000L * 100, e.salary)
        assertEquals(2_000L * 100, e.interest)
        assertEquals(0L, e.savingsInterest)
    }

    @Test fun refundsReimbursementsAndTransfersAreLeftOut() {
        val txs = salaries(LocalDate.of(2025, 4, 1), 12, 10_000) + listOf(
            credit(LocalDate.of(2025, 6, 1), 9_000, Category.INCOME, "Reimbursement travel"),
            credit(LocalDate.of(2025, 6, 2), 9_000, Category.REFUND, "Shop refund"),
            credit(LocalDate.of(2025, 6, 3), 9_000, Category.TRANSFER, "Own account", TransactionType.TRANSFER),
            credit(LocalDate.of(2025, 6, 4), 4_000, Category.INCOME, "Freelance design"),
            credit(LocalDate.of(2025, 6, 5), 15_000, Category.INCOME, "Rent from tenant"),
        )
        val e = TaxIncome.estimate(txs, LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31), today, zone)
        assertEquals(4_000L * 100, e.other)
        assertEquals(15_000L * 100, e.rent)
    }

    @Test fun noDataGivesNothing() {
        val e = TaxIncome.estimate(emptyList(), LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), today, zone)
        assertEquals(0L, e.total)
        assertEquals(0, e.monthsOfData)
    }
}
