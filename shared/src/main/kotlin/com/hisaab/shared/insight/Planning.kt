package com.hisaab.shared.insight

import com.hisaab.parser.model.Category
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.HoldingEntity
import com.hisaab.shared.db.TransactionEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/*
 * Planning from the transactions already on the phone: what repeats every month, which insurance
 * policies are being paid, how much was saved, and a few plain-language observations. Pure functions,
 * so they are tested on the JVM and cheap to run on every refresh.
 */

/** A payment that happens every month on about the same day: rent, an EMI, a SIP, a subscription, a premium. */
data class Recurring(
    val name: String,
    val amountMinor: Long,
    val dayOfMonth: Int,
    val category: Category,
    val accountId: Long?,
    val lastPaid: LocalDate?,
    val nextDue: LocalDate,
    val occurrences: Int,
    /** Set for one the user added (its row id); null for one found in the transactions. */
    val manualId: Long? = null,
    /** Money coming in, such as a salary. Only user-added items can be income. */
    val income: Boolean = false,
    val yearly: Boolean = false,
)

enum class InsuranceKind(val label: String) { HEALTH("Health"), LIFE("Life / term"), MOTOR("Car / bike"), OTHER("General") }

/** An insurance policy recognised from its premium payments. */
data class Policy(
    val insurer: String,
    val kind: InsuranceKind,
    val premiumMinor: Long,
    val monthly: Boolean,
    val lastPaid: LocalDate,
    val nextDue: LocalDate,
    val accountId: Long?,
)

data class MonthSavings(
    val month: YearMonth,
    val incomeMinor: Long,
    val spentMinor: Long,
    val investedMinor: Long,
) {
    /** What stayed with you: income minus spending. SIPs and other investments count as saved, not spent. */
    val savedMinor: Long get() = incomeMinor - spentMinor
    val rate: Double get() = if (incomeMinor > 0) savedMinor.toDouble() / incomeMinor else 0.0
}

data class SavingsOutlook(
    val lastMonth: MonthSavings?,
    /** Average saved per month over the last (up to three) complete months, before PF/NPS. */
    val averageMonthlyMinor: Long,
    /** EPF and NPS growth per month, from the change in their balances; 0 when unknown. */
    val pfNpsMonthlyMinor: Long,
) {
    val yearlyEstimateMinor: Long get() = (averageMonthlyMinor + pfNpsMonthlyMinor) * 12
}

enum class Tone { GOOD, WARN, INFO }

data class Insight(val title: String, val detail: String, val tone: Tone)

object Planning {
    private val SPEND = setOf(TransactionType.DEBIT)

    private fun date(t: TransactionEntity, zone: ZoneId): LocalDate = Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalDate()
    private fun key(t: TransactionEntity): String? = (t.merchant ?: t.upiId)?.trim()?.lowercase()?.takeIf { it.length >= 2 }

    /**
     * Monthly repeats: the same merchant on two or more different months in the last ~4 months, at a
     * similar amount (within 20%) and on a similar day (within 6 days), last paid in the past 45 days.
     */
    fun recurring(txs: List<TransactionEntity>, today: LocalDate, zone: ZoneId): List<Recurring> =
        txs.asSequence()
            .filter { (it.type == TransactionType.DEBIT || it.type == TransactionType.INVESTMENT) && !it.needsReview && it.currency == "INR" }
            .filter { date(it, zone) >= today.minusDays(130) }
            .groupBy { key(it) }
            .filterKeys { it != null }
            .mapNotNull { (_, group) ->
                val median = group.map { it.amountMinor }.sorted()[group.size / 2]
                val similar = group.filter { abs(it.amountMinor - median) <= median / 5 }.sortedBy { it.timestamp }
                val months = similar.map { YearMonth.from(date(it, zone)) }.distinct()
                if (months.size < 2) return@mapNotNull null
                val days = similar.map { date(it, zone).dayOfMonth }
                if (days.max() - days.min() > 6) return@mapNotNull null
                val last = similar.last()
                val lastDate = date(last, zone)
                if (lastDate < today.minusDays(45)) return@mapNotNull null
                val day = days.sorted()[days.size / 2]
                val nextMonth = YearMonth.from(lastDate).plusMonths(1)
                Recurring(
                    name = last.merchant ?: last.upiId ?: "Payment", amountMinor = last.amountMinor, dayOfMonth = day, category = last.category,
                    accountId = last.accountId, lastPaid = lastDate, nextDue = nextMonth.atDay(minOf(day, nextMonth.lengthOfMonth())),
                    occurrences = similar.size,
                )
            }
            .sortedBy { it.nextDue }
            .toList()

    /**
     * The user's own recurring items, with their next date, merged with the detected ones. An item the user
     * added replaces a detected one with the same name.
     */
    fun withManual(detected: List<Recurring>, manual: List<com.hisaab.shared.db.RecurringEntity>, today: LocalDate): List<Recurring> {
        val mine = manual.map { m ->
            val yearly = m.frequency == com.hisaab.shared.db.RecurringEntity.YEARLY
            val next = if (yearly) {
                val month = (m.month ?: today.monthValue).coerceIn(1, 12)
                fun inYear(y: Int) = YearMonth.of(y, month).let { it.atDay(minOf(m.dayOfMonth, it.lengthOfMonth())) }
                inYear(today.year).takeIf { it >= today } ?: inYear(today.year + 1)
            } else {
                fun inMonth(ym: YearMonth) = ym.atDay(minOf(m.dayOfMonth, ym.lengthOfMonth()))
                inMonth(YearMonth.from(today)).takeIf { it >= today } ?: inMonth(YearMonth.from(today).plusMonths(1))
            }
            Recurring(m.name, m.amountMinor, m.dayOfMonth, m.category, m.accountId, null, next, 0, manualId = m.id, income = m.income, yearly = yearly)
        }
        val names = mine.map { it.name.lowercase() }.toSet()
        return (mine + detected.filter { it.name.lowercase() !in names }).sortedBy { it.nextDue }
    }

    private val HEALTH = Regex("""health|mediclaim|star\s|niva|bupa|care\s|cigna|aditya birla health""", RegexOption.IGNORE_CASE)
    private val LIFE = Regex("""\blife\b|term|\blic\b|pru|aia|max life""", RegexOption.IGNORE_CASE)
    private val MOTOR = Regex("""motor|car\b|bike|two.?wheeler|vehicle|\bauto\b""", RegexOption.IGNORE_CASE)

    fun kindOf(text: String): InsuranceKind = when {
        HEALTH.containsMatchIn(text) -> InsuranceKind.HEALTH
        LIFE.containsMatchIn(text) -> InsuranceKind.LIFE
        MOTOR.containsMatchIn(text) -> InsuranceKind.MOTOR
        else -> InsuranceKind.OTHER
    }

    /** Insurance premiums grouped by insurer: monthly when paid in consecutive months, otherwise yearly. */
    fun policies(txs: List<TransactionEntity>, today: LocalDate, zone: ZoneId): List<Policy> =
        txs.filter { it.category == Category.INSURANCE && it.type != TransactionType.CREDIT && it.type != TransactionType.TRANSFER }
            .groupBy { (it.merchant ?: "Insurance").trim() }
            .map { (insurer, payments) ->
                val sorted = payments.sortedBy { it.timestamp }
                val last = sorted.last()
                val lastDate = date(last, zone)
                val months = sorted.map { YearMonth.from(date(it, zone)) }.distinct()
                val monthly = months.size >= 2 && months.zipWithNext().any { (a, b) -> ChronoUnit.MONTHS.between(a, b) == 1L }
                Policy(
                    insurer = insurer, kind = kindOf("$insurer ${last.note.orEmpty()}"), premiumMinor = last.amountMinor, monthly = monthly,
                    lastPaid = lastDate, nextDue = if (monthly) lastDate.plusMonths(1) else lastDate.plusYears(1), accountId = last.accountId,
                )
            }
            .sortedBy { it.nextDue }

    fun month(txs: List<TransactionEntity>, month: YearMonth, zone: ZoneId): MonthSavings {
        val inMonth = txs.filter { YearMonth.from(date(it, zone)) == month && !it.needsReview && it.currency == "INR" }
        val refunds = inMonth.filter { it.type == TransactionType.CREDIT && it.category == Category.REFUND }.sumOf { it.amountMinor }
        return MonthSavings(
            month = month,
            incomeMinor = inMonth.filter { it.type == TransactionType.CREDIT && it.category != Category.REFUND }.sumOf { it.amountMinor },
            spentMinor = (inMonth.filter { it.type in SPEND }.sumOf { it.amountMinor } - refunds).coerceAtLeast(0),
            investedMinor = inMonth.filter { it.type == TransactionType.INVESTMENT }.sumOf { it.amountMinor },
        )
    }

    /** Last month's savings and, from the last three complete months plus PF/NPS growth, a yearly estimate. */
    fun savings(txs: List<TransactionEntity>, holdings: List<HoldingEntity>, today: LocalDate, zone: ZoneId): SavingsOutlook {
        val thisMonth = YearMonth.from(today)
        val months = (1..3).map { thisMonth.minusMonths(it.toLong()) }.map { month(txs, it, zone) }
            .filter { it.incomeMinor > 0 || it.spentMinor > 0 }
        val pfNps = holdings.filter { it.kind == HoldingKind.EPF || it.kind == HoldingKind.NPS }.sumOf { h ->
            val prev = h.previousValueMinor; val prevAt = h.previousAsOf; val now = h.valueMinor; val at = h.asOf
            if (prev == null || prevAt == null || now == null || at == null || at <= prevAt) 0L
            else {
                val monthsBetween = ((at - prevAt) / (30L * 24 * 60 * 60 * 1000)).coerceAtLeast(1)
                ((now - prev) / monthsBetween).coerceAtLeast(0)
            }
        }
        return SavingsOutlook(
            lastMonth = months.firstOrNull { it.month == thisMonth.minusMonths(1) },
            averageMonthlyMinor = if (months.isEmpty()) 0 else months.sumOf { it.savedMinor } / months.size,
            pfNpsMonthlyMinor = pfNps,
        )
    }

    /** A handful of plain observations, most useful first. Never more than [max]. */
    fun insights(txs: List<TransactionEntity>, recurring: List<Recurring>, today: LocalDate, zone: ZoneId, max: Int = 5): List<Insight> {
        val out = ArrayList<Insight>()
        val thisMonth = YearMonth.from(today)
        val spend = txs.filter { it.type == TransactionType.DEBIT && !it.needsReview && it.currency == "INR" }
        fun inMonth(m: YearMonth, upToDay: Int = 31) = spend.filter { val d = date(it, zone); YearMonth.from(d) == m && d.dayOfMonth <= upToDay }

        // Pace against the same point last month.
        val nowSoFar = inMonth(thisMonth, today.dayOfMonth).sumOf { it.amountMinor }
        val lastSoFar = inMonth(thisMonth.minusMonths(1), today.dayOfMonth).sumOf { it.amountMinor }
        if (lastSoFar > 0 && nowSoFar > 0) {
            val pct = ((nowSoFar - lastSoFar) * 100 / lastSoFar).toInt()
            if (abs(pct) >= 10) out += Insight(
                if (pct > 0) "Spending is $pct% ahead of last month" else "Spending is ${-pct}% below last month",
                "By day ${today.dayOfMonth} you've spent ${rupees(nowSoFar)}, against ${rupees(lastSoFar)} by this day last month.",
                if (pct > 0) Tone.WARN else Tone.GOOD,
            )
        }

        // The category that moved most against its three-month average.
        val prev3 = (1..3).map { thisMonth.minusMonths(it.toLong()) }
        val byCat = Category.entries.mapNotNull { c ->
            val avg = prev3.sumOf { m -> inMonth(m).filter { it.category == c }.sumOf { it.amountMinor } } / 3
            val now = inMonth(thisMonth).filter { it.category == c }.sumOf { it.amountMinor }
            if (avg <= 0 && now <= 0) null else Triple(c, now, avg)
        }
        byCat.filter { it.third > 0 }.maxByOrNull { it.second - it.third }?.let { (c, now, avg) ->
            if (now - avg >= 50_000) out += Insight(
                "${c.label} is above your usual",
                "${rupees(now)} so far this month against a 3-month average of ${rupees(avg)}.", Tone.WARN,
            )
        }

        // What repeats every month.
        if (recurring.isNotEmpty()) {
            val subs = recurring.filter { it.category == Category.SUBSCRIPTIONS || it.category == Category.ENTERTAINMENT || it.category == Category.BILLS }
            val total = recurring.sumOf { it.amountMinor }
            out += Insight(
                "${recurring.size} regular payments, ${rupees(total)} a month",
                if (subs.isNotEmpty()) "Including ${subs.size} subscription${if (subs.size > 1) "s" else ""} and bills worth ${rupees(subs.sumOf { it.amountMinor })}."
                else "Rent, EMIs, SIPs and premiums that come every month.",
                Tone.INFO,
            )
        }

        // The single biggest expense this month.
        inMonth(thisMonth).maxByOrNull { it.amountMinor }?.let { t ->
            out += Insight("Biggest expense: ${rupees(t.amountMinor)}", "${t.merchant ?: t.category.label} on ${date(t, zone).dayOfMonth} ${thisMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}.", Tone.INFO)
        }

        // Weekend share.
        val month = inMonth(thisMonth)
        val total = month.sumOf { it.amountMinor }
        if (total > 0) {
            val weekend = month.filter { date(it, zone).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.sumOf { it.amountMinor }
            val pct = (weekend * 100 / total).toInt()
            if (pct >= 45) out += Insight("$pct% of spending is on weekends", "Weekends are 2 days in 7, but most of the money goes then.", Tone.INFO)
        }
        return out.take(max)
    }

    private fun rupees(minor: Long): String {
        val r = minor / 100
        val s = r.toString()
        if (s.length <= 3) return "₹$s"
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "₹$rest,$last3"
    }
}
