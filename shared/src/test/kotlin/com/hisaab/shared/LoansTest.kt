package com.hisaab.shared

import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.Amortization
import com.hisaab.shared.insight.LoanTerms
import com.hisaab.shared.insight.Loans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LoansTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private var n = 0L

    @Test
    fun `emi matches the reducing-balance formula`() {
        // ₹10,00,000 at 8.5% for 20 years: ₹8,678.23.
        assertEquals(867_823L, Amortization.emi(100_000_000, 850, 240))
        // ₹1,00,000 at 12% for a year: ₹8,884.88.
        assertEquals(888_488L, Amortization.emi(10_000_000, 1200, 12))
        // No interest: the principal split evenly.
        assertEquals(1_000_000L, Amortization.emi(12_000_000, 0, 12))
    }

    @Test
    fun `schedule splits interest and principal and ends at zero`() {
        val terms = LoanTerms(100_000_000, 850, 240, LocalDate.of(2025, 1, 5))
        val s = Amortization.schedule(terms)
        assertEquals(240, s.size)
        assertEquals(708_333L, s.first().interestMinor)
        assertEquals(159_490L, s.first().principalMinor)
        assertEquals(0L, s.last().balanceAfterMinor)
        assertEquals(100_000_000L, s.sumOf { it.principalMinor })
        assertEquals(108_277_663L, s.sumOf { it.interestMinor })
        assertEquals(LocalDate.of(2025, 2, 5), s[1].due)
        // Interest falls and principal rises every month.
        assertTrue(s.zipWithNext().all { (a, b) -> b.interestMinor <= a.interestMinor })
    }

    @Test
    fun `elapsed EMIs and remaining EMIs agree with the schedule`() {
        val first = LocalDate.of(2025, 1, 31)
        assertEquals(0, Amortization.elapsed(first, LocalDate.of(2025, 1, 30), 240))
        assertEquals(1, Amortization.elapsed(first, LocalDate.of(2025, 1, 31), 240))
        // February has no 31st: the EMI falls on the 28th.
        assertEquals(2, Amortization.elapsed(first, LocalDate.of(2025, 2, 28), 240))
        assertEquals(LocalDate.of(2025, 2, 28), Amortization.dueOn(first, 2))

        val s = Amortization.schedule(LoanTerms(100_000_000, 850, 240, first))
        val after60 = s[59].balanceAfterMinor
        assertEquals(180, Amortization.remainingEmis(after60, 867_823, 850))
        assertEquals(0, Amortization.remainingEmis(0, 867_823, 850))
        assertNull(Amortization.remainingEmis(100_000_000, 100_000, 850)) // EMI below the interest never clears it
    }

    @Test
    fun `a loan account gathers its EMIs and works out what is left`() {
        val today = LocalDate.of(2026, 10, 20)
        val loan = AccountWithActivity(
            id = 7, bankName = "HDFC Bank", last4 = "4321", kind = AccountKind.ACCOUNT, nickname = "Home loan", colorArgb = null,
            latestBalanceMinor = null, availableLimitMinor = null, balanceUpdatedAt = null, monthSpent = 0, transactionCount = 3,
            accountType = AccountType.LOAN, loanPrincipalMinor = 100_000_000, loanRateBps = 850, loanTenureMonths = 240,
            loanStartDay = LocalDate.of(2025, 1, 5).toEpochDay(),
        )
        val txs = listOf(
            tx(LocalDate.of(2026, 8, 5), 867_823, accountId = 7),
            tx(LocalDate.of(2026, 9, 5), 867_823, accountId = 7),
            // The same EMI seen again on the savings account: counted once.
            tx(LocalDate.of(2026, 9, 6), 867_823, accountId = 1, merchant = "HDFC Bank", category = Category.EMI_LOAN),
            tx(LocalDate.of(2026, 10, 5), 867_823, accountId = 1, merchant = "Loan 4321", category = Category.EMI_LOAN),
            // A car loan with no account: found as a detected EMI.
            tx(LocalDate.of(2026, 9, 10), 1_200_000, accountId = 1, merchant = "Bajaj Finance", category = Category.EMI_LOAN),
            tx(LocalDate.of(2026, 10, 10), 1_200_000, accountId = 1, merchant = "Bajaj Finance", category = Category.EMI_LOAN),
        )
        val loans = Loans.build(listOf(loan), txs, today, zone)
        assertEquals(2, loans.size)
        val home = loans.first { it.accountId == 7L }
        assertEquals(867_823L, home.emiMinor)
        assertEquals(5, home.emiDay)
        assertEquals(3, home.trackedCount)
        assertEquals(22, home.paidCount) // Jan 2025 .. Oct 2026
        assertEquals(240, home.totalEmis)
        assertEquals(218, home.remainingEmis)
        assertEquals(LocalDate.of(2026, 11, 5), home.nextDue)
        assertEquals(LocalDate.of(2044, 12, 5), home.payoffDate)
        assertEquals(1L, home.payingAccountId)
        assertTrue(home.interestPaidMinor!! > home.principalPaidMinor!!)

        val car = loans.first { it.detected }
        assertEquals("Bajaj Finance", car.name)
        assertEquals(1_200_000L, car.emiMinor)
        assertEquals(LocalDate.of(2026, 11, 10), car.nextDue)
        assertNull(car.remainingEmis)
    }

    private fun tx(date: LocalDate, amount: Long, accountId: Long, merchant: String? = null, category: Category = Category.EMI_LOAN) =
        TransactionEntity(
            id = ++n, amountMinor = amount, currency = "INR", type = TransactionType.DEBIT, bankName = "HDFC Bank", accountLast4 = "1234",
            accountKind = AccountKind.ACCOUNT, accountId = accountId, merchant = merchant, upiId = null, referenceNumber = null, channel = Channel.OTHER,
            balanceMinor = null, availableLimitMinor = null, timestamp = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
            hasExplicitTime = true, category = category, transactionHash = "h$n", confidence = 1f, createdAt = 0,
        )
}
