package com.hisaab.shared.insight

import com.hisaab.parser.model.Category
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Income for a twelve-month period worked out from tracked credits, in paise. */
data class TaxIncomeEstimate(
    val salary: Long = 0,
    /** Bank and deposit interest. */
    val interest: Long = 0,
    /** The part of [interest] that is not from a deposit, for 80TTA / 80TTB. */
    val savingsInterest: Long = 0,
    val dividends: Long = 0,
    val rent: Long = 0,
    /** Freelance, business and other credits. */
    val other: Long = 0,
    /** Salary received in the months with data, before any projection. */
    val salarySoFar: Long = 0,
    val salaryMonths: Int = 0,
    /** Months of the period with tracked data. */
    val monthsOfData: Int = 0,
    /** True when the period has fewer than twelve months of data, so figures are projected to a full year. */
    val estimated: Boolean = false,
) {
    /** Interest, dividends, rent and other credits, for the slab-rate "other income" line. */
    val otherIncome: Long get() = interest + dividends + other
    val total: Long get() = salary + interest + dividends + rent + other
}

/**
 * Collects the credits that are income: classified salary, plus income credits split into interest, dividends, rent
 * and other. Own-account transfers, refunds, card-bill, loan and redemption credits carry other categories and are
 * left out, as are credits noted as reimbursements. With less than twelve months of data the year is estimated:
 * salary continues at the latest month's pay, and other income is scaled by the months observed.
 */
object TaxIncome {
    private val INTEREST = Regex("""\binterest\b|\bint\.?\s?(pd|paid|cr|credit)\b|\bint\b""", RegexOption.IGNORE_CASE)
    private val DEPOSIT = Regex("""\bfd\b|fixed deposit|\brd\b|recurring|deposit""", RegexOption.IGNORE_CASE)
    private val DIVIDEND = Regex("""dividend|\bdiv\b""", RegexOption.IGNORE_CASE)
    private val RENT = Regex("""\brent\b""", RegexOption.IGNORE_CASE)
    private val EXCLUDED = Regex("""reimburs|refund|reversal|cashback""", RegexOption.IGNORE_CASE)

    private fun paise(t: TransactionEntity): Long = t.inrMinor ?: if (t.currency == "INR") t.amountMinor else 0L
    private fun text(t: TransactionEntity) = listOfNotNull(t.merchant, t.note, t.upiId).joinToString(" ")

    /**
     * [txs] are all transactions up to [to] (those outside [from]..[to] are ignored, but the earliest one tells how far
     * the data goes back). [today] bounds an unfinished period; the month in progress is not counted as a full month.
     */
    fun estimate(txs: List<TransactionEntity>, from: LocalDate, to: LocalDate, today: LocalDate, zone: ZoneId): TaxIncomeEstimate {
        fun day(t: TransactionEntity) = Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalDate()
        val live = txs.filter { !it.needsReview && it.duplicateOfId == null }
        val firstDay = live.minOfOrNull(::day) ?: return TaxIncomeEstimate()
        val end = minOf(to, today)
        val start = maxOf(from, firstDay)
        if (start > end) return TaxIncomeEstimate()

        var months = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end)).toInt() + 1
        // The month in progress counts only once it is nearly over; a partial first month counts as a month of data.
        if (end == today && end < to && today.dayOfMonth < today.lengthOfMonth() - 2) months -= 1
        months = months.coerceIn(0, 12)
        val estimated = months < 12

        val inPeriod = live.filter { day(it) in start..end && it.type == TransactionType.CREDIT && it.category != Category.TRANSFER }
        val salaryTx = inPeriod.filter { it.category == Category.SALARY && !EXCLUDED.containsMatchIn(text(it)) }
        val byMonth = salaryTx.groupBy { YearMonth.from(day(it)) }.toSortedMap()
        val salarySoFar = salaryTx.sumOf(::paise)
        val latest = byMonth.values.lastOrNull()?.sumOf(::paise) ?: 0L
        val salary = if (estimated && byMonth.isNotEmpty()) salarySoFar + latest * (12 - months).coerceAtLeast(0) else salarySoFar

        var interest = 0L; var deposit = 0L; var dividends = 0L; var rent = 0L; var other = 0L
        inPeriod.filter { it.category == Category.INCOME }.forEach { t ->
            val s = text(t)
            val a = paise(t)
            when {
                EXCLUDED.containsMatchIn(s) -> Unit
                DIVIDEND.containsMatchIn(s) -> dividends += a
                INTEREST.containsMatchIn(s) -> { interest += a; if (DEPOSIT.containsMatchIn(s)) deposit += a }
                RENT.containsMatchIn(s) -> rent += a
                else -> other += a
            }
        }
        fun scale(v: Long) = if (estimated && months > 0) v * 12 / months else v
        return TaxIncomeEstimate(
            salary = salary, interest = scale(interest), savingsInterest = scale(interest - deposit), dividends = scale(dividends),
            rent = scale(rent), other = scale(other), salarySoFar = salarySoFar, salaryMonths = byMonth.size,
            monthsOfData = months, estimated = estimated,
        )
    }
}
