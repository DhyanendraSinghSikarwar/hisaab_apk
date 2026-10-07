package com.hisaab.shared.insight

import com.hisaab.parser.model.Category
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/** A loan's terms as the user entered them. [startDay] is the date of the first EMI. */
data class LoanTerms(val principalMinor: Long, val rateBps: Int, val tenureMonths: Int, val startDay: LocalDate?)

/** One EMI of an amortisation schedule: how it splits into interest and principal, and what is left after it. */
data class Instalment(
    val number: Int,
    val due: LocalDate?,
    val emiMinor: Long,
    val interestMinor: Long,
    val principalMinor: Long,
    val balanceAfterMinor: Long,
)

/** Reducing-balance EMI arithmetic (monthly rests), in paise. */
object Amortization {
    private fun monthlyRate(rateBps: Int): Double = rateBps / 10_000.0 / 12.0

    /** The EMI for [principalMinor] over [months] at [rateBps] a year: P·r·(1+r)^n / ((1+r)^n − 1). */
    fun emi(principalMinor: Long, rateBps: Int, months: Int): Long {
        if (principalMinor <= 0 || months <= 0) return 0
        val r = monthlyRate(rateBps)
        if (r == 0.0) return ceil(principalMinor.toDouble() / months).toLong()
        val f = (1 + r).pow(months)
        return (principalMinor * r * f / (f - 1)).roundToLong()
    }

    /**
     * The full schedule. [emiMinor] overrides the computed EMI (the bank's figure, from the debits); the last
     * instalment pays off whatever is left, so the balance always ends at zero.
     */
    fun schedule(terms: LoanTerms, emiMinor: Long? = null): List<Instalment> {
        val emi = emiMinor?.takeIf { it > 0 } ?: emi(terms.principalMinor, terms.rateBps, terms.tenureMonths)
        if (emi <= 0 || terms.principalMinor <= 0) return emptyList()
        val r = monthlyRate(terms.rateBps)
        var balance = terms.principalMinor
        val out = ArrayList<Instalment>()
        var n = 0
        // Never more than the tenure plus a margin: a too-small EMI must not loop for ever.
        val cap = maxOf(terms.tenureMonths, 1) + 600
        while (balance > 0 && n < cap) {
            n++
            val interest = (balance * r).roundToLong()
            val last = n == terms.tenureMonths || emi >= balance + interest
            val pay = if (last) balance + interest else emi
            val principal = pay - interest
            if (principal <= 0 && !last) break
            balance -= principal
            out += Instalment(n, terms.startDay?.let { dueOn(it, n) }, pay, interest, principal, balance.coerceAtLeast(0))
        }
        return out
    }

    /** The [n]th EMI date (1-based), keeping the first EMI's day of month where the month allows. */
    fun dueOn(first: LocalDate, n: Int): LocalDate {
        val ym = YearMonth.from(first).plusMonths((n - 1).toLong())
        return ym.atDay(minOf(first.dayOfMonth, ym.lengthOfMonth()))
    }

    /** EMIs due on or before [today]: the instalments already paid, by the calendar. */
    fun elapsed(first: LocalDate, today: LocalDate, max: Int): Int {
        if (today.isBefore(first)) return 0
        var n = ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(today)).toInt() + 1
        if (dueOn(first, n).isAfter(today)) n--
        return n.coerceIn(0, max)
    }

    /** EMIs still needed to clear [outstandingMinor] at [emiMinor] a month and [rateBps] a year; null if the EMI never clears it. */
    fun remainingEmis(outstandingMinor: Long, emiMinor: Long, rateBps: Int): Int? {
        if (outstandingMinor <= 0) return 0
        if (emiMinor <= 0) return null
        val r = monthlyRate(rateBps)
        if (r == 0.0) return ceil(outstandingMinor.toDouble() / emiMinor).toInt()
        val x = 1 - r * outstandingMinor / emiMinor
        if (x <= 0) return null
        // A last instalment under 1% of an EMI is rounding, not another month.
        return ceil(-ln(x) / ln(1 + r) - 0.01).toInt()
    }
}

/** Where a loan came from: a loan account the user has, or an EMI series found in the payments only. */
data class Loan(
    /** "a<id>" for an account, "e<merchant>" for a detected EMI. */
    val key: String,
    val accountId: Long?,
    val name: String,
    val bankName: String,
    val last4: String?,
    val emiMinor: Long?,
    val emiDay: Int?,
    /** EMIs paid: by the calendar when the terms are set, otherwise the EMI debits found. */
    val paidCount: Int,
    val trackedCount: Int,
    val totalPaidMinor: Long,
    val firstPaid: LocalDate?,
    val lastPaid: LocalDate?,
    val nextDue: LocalDate?,
    /** What is still owed: the bank's figure when a message gave one, else the schedule's. */
    val outstandingMinor: Long?,
    val outstandingFromBank: Boolean,
    val terms: LoanTerms?,
    val totalEmis: Int?,
    val remainingEmis: Int?,
    val payoffDate: LocalDate?,
    val principalPaidMinor: Long?,
    val interestPaidMinor: Long?,
    val totalInterestMinor: Long?,
    /** The next instalments (up to 12) when the terms are set. */
    val upcoming: List<Instalment>,
    /** The EMI and other payments found, newest first. */
    val payments: List<TransactionEntity>,
    /** The account the EMI is debited from, when the debits show it. */
    val payingAccountId: Long?,
) {
    val detected: Boolean get() = accountId == null
    val closed: Boolean get() = (outstandingMinor == 0L) || (remainingEmis == 0 && terms != null)

    /** Share repaid, 0..1: by principal when the terms are set, else by EMIs. Null when unknown. */
    val progress: Float?
        get() = when {
            terms != null && terms.principalMinor > 0 && outstandingMinor != null ->
                (1f - outstandingMinor.toFloat() / terms.principalMinor).coerceIn(0f, 1f)
            totalEmis != null && totalEmis > 0 -> (paidCount.toFloat() / totalEmis).coerceIn(0f, 1f)
            remainingEmis != null && paidCount + remainingEmis > 0 -> paidCount.toFloat() / (paidCount + remainingEmis)
            else -> null
        }
}

/** Builds [Loan]s from loan accounts and EMI payments. Pure, so it is tested on the JVM. */
object Loans {
    private fun date(t: TransactionEntity, zone: ZoneId): LocalDate = Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalDate()

    private fun isPayment(t: TransactionEntity) = !t.needsReview && t.type != TransactionType.CREDIT && t.currency == "INR"

    /** The merchant key a detected EMI series is grouped by. */
    fun keyOf(t: TransactionEntity): String? = (t.merchant ?: t.upiId)?.trim()?.lowercase()?.takeIf { it.length >= 2 }

    /**
     * Every loan account, plus EMI series (category EMI & Loans, two or more months, paid in the last 75 days)
     * not already claimed by an account. A payment belongs to a loan account when it was booked on it, or when
     * it is an EMI whose merchant names the account (its number, nickname or lender).
     */
    fun build(accounts: List<AccountWithActivity>, payments: List<TransactionEntity>, today: LocalDate, zone: ZoneId): List<Loan> {
        val loanAccs = accounts.filter { it.isLoan && !it.hidden }
        val pays = payments.filter(::isPayment)
        val loanIds = loanAccs.map { it.id }.toSet()
        val claimed = HashSet<Long>()
        val loans = loanAccs.map { a ->
            val mine = pays.filter { t ->
                t.accountId == a.id || (t.category == Category.EMI_LOAN && t.accountId !in loanIds && names(t, a))
            }
            claimed += mine.map { it.id }
            val terms = termsOf(a)
            val outstanding = a.currentBalanceMinor?.let(::abs)
            summarise(
                key = "a${a.id}", accountId = a.id, name = a.nickname?.takeIf { it.isNotBlank() } ?: a.bankName, bank = a.bankName,
                last4 = a.last4, txs = mine, terms = terms, bankOutstanding = outstanding, today = today, zone = zone,
                loanIds = loanIds,
            )
        }
        val detected = pays.filter { it.category == Category.EMI_LOAN && it.id !in claimed && it.accountId !in loanIds }
            .groupBy { keyOf(it) }.filterKeys { it != null }
            .mapNotNull { (k, group) ->
                val months = group.map { YearMonth.from(date(it, zone)) }.distinct()
                val last = group.maxOf { date(it, zone) }
                if (months.size < 2 || last.isBefore(today.minusDays(75))) return@mapNotNull null
                val name = group.maxBy { it.timestamp }.let { it.merchant ?: it.upiId ?: "EMI" }
                summarise(
                    key = "e$k", accountId = null, name = name, bank = name, last4 = null, txs = group, terms = null,
                    bankOutstanding = null, today = today, zone = zone, loanIds = emptySet(),
                )
            }
        return loans.sortedByDescending { it.outstandingMinor ?: 0 } + detected.sortedBy { it.nextDue }
    }

    private fun names(t: TransactionEntity, a: AccountWithActivity): Boolean {
        val text = listOfNotNull(t.merchant, t.upiId, t.note).joinToString(" ").lowercase()
        if (text.isBlank()) return false
        return (a.last4.length >= 4 && text.contains(a.last4)) ||
            listOfNotNull(a.nickname, a.bankName).any { n -> n.isNotBlank() && t.merchant?.trim().equals(n.trim(), ignoreCase = true) }
    }

    fun termsOf(a: AccountWithActivity): LoanTerms? {
        val p = a.loanPrincipalMinor ?: return null
        val n = a.loanTenureMonths ?: return null
        if (p <= 0 || n <= 0) return null
        return LoanTerms(p, a.loanRateBps ?: 0, n, a.loanStartDay?.let(LocalDate::ofEpochDay))
    }

    /** One EMI per month: copies of the same payment a few days apart (SMS on the loan and on the bank account) count once. */
    private fun distinct(txs: List<TransactionEntity>, zone: ZoneId): List<TransactionEntity> {
        val out = ArrayList<TransactionEntity>()
        for (t in txs.sortedBy { it.timestamp }) {
            val dup = out.any { o -> o.amountMinor == t.amountMinor && abs(ChronoUnit.DAYS.between(date(o, zone), date(t, zone))) <= 3 }
            if (!dup) out += t
        }
        return out
    }

    internal fun summarise(
        key: String, accountId: Long?, name: String, bank: String, last4: String?, txs: List<TransactionEntity>,
        terms: LoanTerms?, bankOutstanding: Long?, today: LocalDate, zone: ZoneId, loanIds: Set<Long>,
    ): Loan {
        val all = distinct(txs, zone)
        val median = all.map { it.amountMinor }.sorted().getOrNull(all.size / 2)
        val series = if (median == null) emptyList() else all.filter { abs(it.amountMinor - median) <= median / 5 }
        val computedEmi = terms?.let { Amortization.emi(it.principalMinor, it.rateBps, it.tenureMonths) }?.takeIf { it > 0 }
        val emi = series.lastOrNull()?.amountMinor ?: computedEmi
        val days = series.map { date(it, zone).dayOfMonth }
        val emiDay = days.sorted().getOrNull(days.size / 2) ?: terms?.startDay?.dayOfMonth
        val lastPaid = all.lastOrNull()?.let { date(it, zone) }
        val firstPaid = all.firstOrNull()?.let { date(it, zone) }

        val schedule = terms?.let { Amortization.schedule(it, emi) }.orEmpty()
        val elapsed = terms?.startDay?.let { Amortization.elapsed(it, today, schedule.size) }
        val paidCount = elapsed ?: series.size
        val scheduledOutstanding = if (terms != null && elapsed != null) {
            if (elapsed == 0) terms.principalMinor else schedule.getOrNull(elapsed - 1)?.balanceAfterMinor
        } else null
        val outstanding = bankOutstanding ?: scheduledOutstanding
        val remaining = when {
            terms != null && bankOutstanding == null && elapsed != null -> schedule.size - elapsed
            outstanding != null && emi != null -> Amortization.remainingEmis(outstanding, emi, terms?.rateBps ?: 0)
            terms != null -> (schedule.size - series.size).coerceAtLeast(0)
            else -> null
        }

        val nextDue = emiDay?.let { d ->
            fun on(ym: YearMonth) = ym.atDay(minOf(d, ym.lengthOfMonth()))
            val thisMonth = YearMonth.from(today)
            val paidThisMonth = lastPaid != null && YearMonth.from(lastPaid) == thisMonth
            val candidate = if (!paidThisMonth && !on(thisMonth).isBefore(today)) on(thisMonth) else on(thisMonth.plusMonths(1))
            terms?.startDay?.takeIf { it.isAfter(candidate) } ?: candidate
        }?.takeIf { remaining != 0 && outstanding != 0L }
        val payoff = when {
            remaining == null -> null
            remaining == 0 -> lastPaid
            nextDue != null -> YearMonth.from(nextDue).plusMonths((remaining - 1).toLong()).let { it.atDay(minOf(emiDay ?: 1, it.lengthOfMonth())) }
            else -> null
        }

        val done = schedule.take(elapsed ?: 0)
        val upcomingStart = elapsed ?: 0
        return Loan(
            key = key, accountId = accountId, name = name, bankName = bank, last4 = last4,
            emiMinor = emi, emiDay = emiDay, paidCount = paidCount, trackedCount = series.size,
            totalPaidMinor = if (elapsed != null && emi != null) maxOf(done.sumOf { it.emiMinor }, all.sumOf { it.amountMinor }) else all.sumOf { it.amountMinor },
            firstPaid = firstPaid, lastPaid = lastPaid, nextDue = nextDue,
            outstandingMinor = outstanding, outstandingFromBank = bankOutstanding != null,
            terms = terms, totalEmis = schedule.size.takeIf { terms != null },
            remainingEmis = remaining, payoffDate = payoff,
            principalPaidMinor = terms?.let { t -> outstanding?.let { (t.principalMinor - it).coerceAtLeast(0) } },
            interestPaidMinor = if (elapsed != null) done.sumOf { it.interestMinor } else null,
            totalInterestMinor = schedule.takeIf { it.isNotEmpty() }?.sumOf { it.interestMinor },
            upcoming = schedule.drop(upcomingStart).take(12),
            payments = all.sortedByDescending { it.timestamp },
            payingAccountId = all.lastOrNull { it.accountId != null && it.accountId !in loanIds }?.accountId,
        )
    }
}
