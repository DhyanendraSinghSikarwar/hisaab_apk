package com.hisaab.shared.insight

import com.hisaab.parser.model.Category
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.TransactionEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Spend of one account or card in the period; [accountId] null collects transactions without an account. */
data class AccountSpend(val accountId: Long?, val amount: Long)

/**
 * "Spent this month" for any period: the days of the period up to today, against the previous period.
 * Cumulative lists hold one running total per day (index 0 = first day of the period); [thisCum] stops at today for a running
 * period, [lastCum] covers the whole previous period. Without a previous period both [previousSpent] and [lastCum] are empty/zero.
 */
data class MonthSpendResult(
    val spent: Long,
    /** The whole previous period's spend. */
    val previousSpent: Long,
    /** The previous period's spend over the same number of days as have elapsed in this one. */
    val previousSamePeriod: Long,
    val income: Long,
    val saved: Long,
    val thisCum: List<Long>,
    val lastCum: List<Long>,
    val byAccount: List<AccountSpend>,
    /** Days in the whole period, for scaling a chart; the cumulative lists are indexed from the first day. */
    val days: Int = thisCum.size,
    val hasPrevious: Boolean = true,
) {
    /** Change against [previousSamePeriod] in percent (negative = less); null when there is nothing to compare. */
    val changePercent: Int? get() = if (hasPrevious && previousSamePeriod > 0) ((spent - previousSamePeriod) * 100 / previousSamePeriod).toInt() else null
}

/**
 * A period's spend, defined as the Analysis tab defines it: DEBIT transactions that are not transfers (own-account moves and
 * credit-card bill payments are marked TRANSFER) and not investments. Card spends count; the bill payment that settles them does not.
 * Refunds net against spend instead of counting as income. Callers filter by book first.
 */
object MonthSpend {
    private fun rupees(t: TransactionEntity): Long = t.inrMinor ?: if (t.currency == "INR") t.amountMinor else 0L

    private fun isSpend(t: TransactionEntity) = t.type == TransactionType.DEBIT && t.category != Category.TRANSFER
    private fun isRefund(t: TransactionEntity) = t.type == TransactionType.CREDIT && t.category == Category.REFUND
    private fun isIncome(t: TransactionEntity) = t.type == TransactionType.CREDIT && t.category != Category.TRANSFER && t.category != Category.REFUND

    /** Signed contribution to spend: spends add, refunds subtract, everything else 0. */
    fun net(t: TransactionEntity): Long = when {
        isSpend(t) -> rupees(t)
        isRefund(t) -> -rupees(t)
        else -> 0L
    }

    /** The calendar month holding [today], against the month before. */
    fun compute(txs: List<TransactionEntity>, today: LocalDate, zone: ZoneId): MonthSpendResult {
        val m = YearMonth.from(today)
        val p = m.minusMonths(1)
        return compute(txs, m.atDay(1), m.atEndOfMonth(), p.atDay(1), p.atEndOfMonth(), today, zone)
    }

    /**
     * Spend over [start]..[end] (inclusive) up to [today], compared with [prevStart]..[prevEnd] (null = no comparison).
     * A period still running is compared over the same elapsed days; a finished one over the whole previous period.
     */
    fun compute(
        txs: List<TransactionEntity>, start: LocalDate, end: LocalDate, prevStart: LocalDate?, prevEnd: LocalDate?,
        today: LocalDate, zone: ZoneId,
    ): MonthSpendResult {
        fun day(t: TransactionEntity) = java.time.Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalDate()
        val last = minOf(end, today)
        val thisTx = txs.filter { val d = day(it); !d.isBefore(start) && !d.isAfter(last) }

        fun cumulative(list: List<TransactionEntity>, from: LocalDate, to: LocalDate): List<Long> {
            val n = (ChronoUnit.DAYS.between(from, to) + 1).toInt().coerceAtLeast(0)
            val by = list.groupBy { ChronoUnit.DAYS.between(from, day(it)).toInt() }.mapValues { (_, l) -> l.sumOf(::net) }
            var run = 0L
            return (0 until n).map { i -> run += by[i] ?: 0L; run }
        }
        val thisCum = cumulative(thisTx, start, last)
        val hasPrev = prevStart != null && prevEnd != null
        val lastCum = if (hasPrev) {
            cumulative(txs.filter { val d = day(it); !d.isBefore(prevStart) && !d.isAfter(prevEnd) }, prevStart!!, prevEnd!!)
        } else emptyList()
        val elapsed = minOf(thisCum.size, lastCum.size)
        val spent = thisCum.lastOrNull() ?: 0L
        val income = thisTx.filter(::isIncome).sumOf(::rupees)
        val byAccount = thisTx.filter { net(it) != 0L }.groupBy { it.accountId }
            .map { (id, l) -> AccountSpend(id, l.sumOf(::net)) }.filter { it.amount != 0L }.sortedByDescending { it.amount }
        return MonthSpendResult(
            spent = spent, previousSpent = lastCum.lastOrNull() ?: 0L,
            previousSamePeriod = if (elapsed > 0) lastCum[elapsed - 1] else 0L,
            income = income, saved = income - spent, thisCum = thisCum, lastCum = lastCum, byAccount = byAccount,
            days = (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1), hasPrevious = hasPrev,
        )
    }
}
