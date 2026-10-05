package com.hisaab.shared

import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.HoldingEntity
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.InsuranceKind
import com.hisaab.shared.insight.Planning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class PlanningTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 20)
    private var n = 0L

    private fun tx(date: LocalDate, rupees: Long, merchant: String?, type: TransactionType = TransactionType.DEBIT, category: Category = Category.OTHER) =
        TransactionEntity(
            id = ++n, amountMinor = rupees * 100, currency = "INR", type = type, bankName = "HDFC Bank", accountLast4 = "1234",
            accountKind = AccountKind.ACCOUNT, accountId = 1, merchant = merchant, upiId = null, referenceNumber = null, channel = Channel.UPI,
            balanceMinor = null, availableLimitMinor = null, timestamp = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
            hasExplicitTime = true, category = category, transactionHash = "h$n", confidence = 1f, createdAt = 0,
        )

    @Test
    fun `monthly payments on a similar day are recurring, one-offs are not`() {
        val txs = listOf(
            tx(LocalDate.of(2026, 8, 5), 649, "Netflix", category = Category.SUBSCRIPTIONS),
            tx(LocalDate.of(2026, 9, 5), 649, "Netflix", category = Category.SUBSCRIPTIONS),
            tx(LocalDate.of(2026, 10, 6), 649, "Netflix", category = Category.SUBSCRIPTIONS),
            tx(LocalDate.of(2026, 9, 1), 25000, "Landlord", category = Category.RENT),
            tx(LocalDate.of(2026, 10, 2), 25000, "Landlord", category = Category.RENT),
            tx(LocalDate.of(2026, 10, 10), 3200, "Myntra", category = Category.SHOPPING),
        )
        val r = Planning.recurring(txs, today, zone)
        assertEquals(setOf("Netflix", "Landlord"), r.map { it.name }.toSet())
        val netflix = r.first { it.name == "Netflix" }
        assertEquals(LocalDate.of(2026, 11, 5), netflix.nextDue)
        assertEquals(64900L, netflix.amountMinor)
    }

    @Test
    fun `insurance premiums become policies with a renewal date and a kind`() {
        val txs = listOf(
            tx(LocalDate.of(2026, 3, 15), 18500, "Star Health", category = Category.INSURANCE),
            tx(LocalDate.of(2026, 9, 10), 1500, "HDFC Life", category = Category.INSURANCE),
            tx(LocalDate.of(2026, 10, 10), 1500, "HDFC Life", category = Category.INSURANCE),
        )
        val p = Planning.policies(txs, today, zone)
        val health = p.first { it.insurer == "Star Health" }
        assertEquals(InsuranceKind.HEALTH, health.kind)
        assertEquals(LocalDate.of(2027, 3, 15), health.nextDue)
        val life = p.first { it.insurer == "HDFC Life" }
        assertEquals(InsuranceKind.LIFE, life.kind)
        assertTrue(life.monthly)
    }

    @Test
    fun `savings count investments as saved and estimate a year including PF growth`() {
        val sep = YearMonth.of(2026, 9)
        val txs = listOf(
            tx(sep.atDay(1), 100000, "Employer", TransactionType.CREDIT, Category.SALARY),
            tx(sep.atDay(5), 40000, "Rent", category = Category.RENT),
            tx(sep.atDay(7), 10000, "Zerodha", TransactionType.INVESTMENT, Category.INVESTMENT),
            tx(sep.atDay(9), 5000, "Myntra", category = Category.SHOPPING),
            tx(sep.atDay(12), 1000, "Myntra", TransactionType.CREDIT, Category.REFUND),
        )
        val day = 24 * 60 * 60 * 1000L
        val pf = HoldingEntity(kind = HoldingKind.EPF, name = "EPF", identifier = "EPF:1", units = null, valueMinor = 50_00_000_00,
            investedMinor = null, asOf = 60 * day, source = "SMS", note = null, updatedAt = 0, previousValueMinor = 49_60_000_00, previousAsOf = 0)
        val o = Planning.savings(txs, listOf(pf), today, zone)
        val last = o.lastMonth!!
        assertEquals(100000_00L, last.incomeMinor)
        assertEquals(44000_00L, last.spentMinor)          // rent + shopping - refund; the SIP is not spending
        assertEquals(56000_00L, last.savedMinor)          // includes the ₹10,000 SIP
        assertEquals(10000_00L, last.investedMinor)
        assertEquals(20000_00L, o.pfNpsMonthlyMinor)      // ₹40,000 over two months
        assertEquals((56000_00L + 20000_00L) * 12, o.yearlyEstimateMinor)
    }

    @Test
    fun `insights note when spending runs ahead of last month`() {
        val txs = listOf(
            tx(LocalDate.of(2026, 9, 10), 2000, "Swiggy", category = Category.FOOD),
            tx(LocalDate.of(2026, 10, 10), 6000, "Swiggy", category = Category.FOOD),
        )
        val i = Planning.insights(txs, emptyList(), today, zone)
        assertTrue(i.any { it.title.startsWith("Spending is 200% ahead") })
    }
}
