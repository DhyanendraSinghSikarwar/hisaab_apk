package com.hisaab.shared.insight

import com.hisaab.parser.model.Category
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.TransactionEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Spend of one account or card in the month; [accountId] null collects transactions without an account. */
data class AccountSpend(val accountId: Long?, val amount: Long)

/**
 * "Spent this month": the calendar month up to [MonthSpend.compute]'s `today`, against the same days of the month before.
 * Cumulative lists hold one running total per day (index 0 = day 1); [thisCum] stops at today, [lastCum] covers the whole month.
 */
data class MonthSpendResult(
    val spent: Long,
    val lastMonthSpent: Long,
    /** Last month's spend over the same number of days as elapsed this month. */
    val lastSamePeriod: Long,
    val income: Long,
    val saved: Long,
    val thisCum: List<Long>,
    val lastCum: List<Long>,
    val byAccount: List<AccountSpend>,
) {
    /** Change against [lastSamePeriod] in percent (negative = less); null when last month had nothing to compare. */
    val changePercent: Int? get() = if (lastSamePeriod > 0) ((spent - lastSamePeriod) * 100 / lastSamePeriod).toInt() else null
}

/**
 * The month's spend, defined as the Analysis tab defines it: DEBIT transactions that are not transfers (own-account moves and
 * credit-card bill payments are marked TRANSFER) and not investments. Card spends count; the bill payment that settles them does not.
 * Refunds net against spend instead of counting as income.
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

    fun compute(txs: List<TransactionEntity>, today: LocalDate, zone: ZoneId): MonthSpendResult {
        val month = YearMonth.from(today)
        val last = month.minusMonths(1)
        fun day(t: TransactionEntity) = java.time.Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalDate()
        val thisTx = txs.filter { YearMonth.from(day(it)) == month && !day(it).isAfter(today) }
        val lastTx = txs.filter { YearMonth.from(day(it)) == last }

        fun cumulative(list: List<TransactionEntity>, days: Int): List<Long> {
            val by = list.groupBy { day(it).dayOfMonth }.mapValues { (_, l) -> l.sumOf(::net) }
            var run = 0L
            return (1..days).map { d -> run += by[d] ?: 0L; run }
        }
        val thisCum = cumulative(thisTx, today.dayOfMonth)
        val lastCum = cumulative(lastTx, last.lengthOfMonth())
        val elapsed = minOf(today.dayOfMonth, last.lengthOfMonth())
        val spent = thisCum.lastOrNull() ?: 0L
        val income = thisTx.filter(::isIncome).sumOf(::rupees)
        val byAccount = thisTx.filter { net(it) != 0L }.groupBy { it.accountId }
            .map { (id, l) -> AccountSpend(id, l.sumOf(::net)) }.filter { it.amount != 0L }.sortedByDescending { it.amount }
        return MonthSpendResult(
            spent = spent, lastMonthSpent = lastCum.lastOrNull() ?: 0L, lastSamePeriod = lastCum.getOrElse(elapsed - 1) { 0L },
            income = income, saved = income - spent, thisCum = thisCum, lastCum = lastCum, byAccount = byAccount,
        )
    }
}
