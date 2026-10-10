package com.hisaab.shared.repo

import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.statement.CardDue
import com.hisaab.shared.db.CardFlow
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.StatementEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A credit card's money position. With a usable statement: [billedMinor] (what is left of its total due), [minDueMinor],
 * [dueDay] and [unbilledMinor] (spends since). Without one: [outstandingMinor], worked out from the limit and the available
 * limit, else from the spends since the last bill payment.
 */
data class CardPosition(
    val billedMinor: Long? = null,
    val minDueMinor: Long? = null,
    val dueDay: LocalDate? = null,
    val statementDay: LocalDate? = null,
    val limitMinor: Long? = null,
    val unbilledMinor: Long? = null,
    val outstandingMinor: Long? = null,
    /** True when no statement was seen: [billedMinor] and [dueDay] were worked out from the usual statement day. */
    val estimated: Boolean = false,
) {
    /** What the card owes now: the billed amount, else the outstanding figure. */
    val owedMinor: Long get() = billedMinor?.takeIf { it > 0 } ?: outstandingMinor?.takeIf { it > 0 } ?: 0L
    val known: Boolean get() = billedMinor != null || unbilledMinor != null || outstandingMinor != null
}

/** Computes [CardPosition]s and records card due messages as statement rows. */
object CardDues {
    /** [StatementEntity.kind] of a payment the issuer acknowledged; its amount is in `debitsMinor`, its day in `statementEpochDay`. */
    const val KIND_PAYMENT = "CARD_PAYMENT"
    const val KIND_DUE = "CREDIT_CARD"
    private const val STALE_DAYS = 35L
    const val WINDOW_DAYS = 60L

    private fun ms(day: LocalDate, zone: ZoneId) = day.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Typical days from statement date to payment due date, when only one of them is known. */
    const val DUE_GAP_DAYS = 19L

    private fun clampDay(ym: YearMonth, dom: Int) = ym.atDay(minOf(dom, ym.lengthOfMonth()))

    /** The latest statement date on or before [today] for a card whose statements fall on day [dom] of the month. */
    internal fun latestStatement(dom: Int, today: LocalDate): LocalDate {
        val c = clampDay(YearMonth.from(today), dom)
        return if (c.isAfter(today)) clampDay(YearMonth.from(today).minusMonths(1), dom) else c
    }

    private fun net(flow: List<CardFlow>, from: Long, to: Long = Long.MAX_VALUE): Long = flow.filter { it.timestamp in from until to }.sumOf {
        when (it.type) {
            TransactionType.DEBIT -> it.amountMinor
            TransactionType.CREDIT -> -it.amountMinor
            else -> 0L
        }
    }.coerceAtLeast(0L)

    /**
     * [rows]: every statement row of the card (any kind); [availableMinor]: its latest available limit; [flow]: its
     * movements over the last [WINDOW_DAYS] days.
     */
    fun position(rows: List<StatementEntity>, availableMinor: Long?, flow: List<CardFlow>, today: LocalDate, zone: ZoneId): CardPosition {
        val newest = compareBy<StatementEntity>({ it.statementEpochDay ?: 0L }, { it.processedAt })
        val bills = rows.filter { it.kind != KIND_PAYMENT }
        val bill = bills.filter { it.totalDueMinor != null }.maxWithOrNull(newest)
        val limit = bills.filter { it.creditLimitMinor != null }.maxWithOrNull(newest)?.creditLimitMinor
        val dueDay = bill?.dueEpochDay?.let(LocalDate::ofEpochDay)
        val gap = bill?.statementEpochDay?.let { s -> bill.dueEpochDay?.let { (it - s).coerceIn(10L, 30L) } } ?: DUE_GAP_DAYS
        // The statement date of the bill, or one worked out from its due date.
        val statementDay = bill?.statementEpochDay?.let(LocalDate::ofEpochDay) ?: dueDay?.minusDays(gap)
        fun paidSince(day: LocalDate): Long {
            val tx = flow.filter { it.type == TransactionType.TRANSFER && it.timestamp >= ms(day, zone) }.sumOf { it.amountMinor }
            val ack = rows.filter { it.kind == KIND_PAYMENT && (it.statementEpochDay ?: 0L) >= day.toEpochDay() }.sumOf { it.debitsMinor ?: 0L }
            return maxOf(tx, ack)
        }
        val stale = dueDay != null && dueDay.isBefore(today.minusDays(STALE_DAYS))
        // The usual statement day: from the newest row that carries a statement or due date.
        val ref = bills.filter { it.statementEpochDay != null || it.dueEpochDay != null }.maxWithOrNull(newest)
        val refStmt = ref?.statementEpochDay?.let(LocalDate::ofEpochDay) ?: ref?.dueEpochDay?.let { LocalDate.ofEpochDay(it).minusDays(gap) }
        val expected = refStmt?.let { latestStatement(it.dayOfMonth, today) }
        val covered = bill != null && !stale && (expected == null || statementDay == null || !statementDay.isBefore(expected))
        if (covered && bill != null) {
            val billed = ((bill.totalDueMinor ?: 0L) - (statementDay?.let(::paidSince) ?: maxOf(
                flow.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amountMinor },
                rows.filter { it.kind == KIND_PAYMENT }.sumOf { it.debitsMinor ?: 0L },
            ))).coerceAtLeast(0L)
            val unbilled = statementDay?.let { net(flow, ms(it.plusDays(1), zone)) }
            return CardPosition(billed, bill.minDueMinor.takeIf { billed > 0 }, dueDay, statementDay, limit, unbilled, null)
        }
        if (expected != null && refStmt != null) {
            // No statement seen for the latest cycle: freeze the spends up to its date as an estimated bill.
            val prev = clampDay(YearMonth.from(expected).minusMonths(1), refStmt.dayOfMonth)
            val carried = bill != null && !stale && statementDay != null && !statementDay.isBefore(prev.minusDays(3))
            val base = if (carried) statementDay!! else prev
            val gross = (if (carried) bill!!.totalDueMinor ?: 0L else 0L) + net(flow, ms(base.plusDays(1), zone), ms(expected.plusDays(1), zone)) - paidSince(base)
            if (gross > 0 || carried) {
                return CardPosition(gross.coerceAtLeast(0L), null, expected.plusDays(gap), expected, limit, net(flow, ms(expected.plusDays(1), zone)), null, estimated = true)
            }
        }
        val outstanding = when {
            limit != null && availableMinor != null && limit >= availableMinor -> limit - availableMinor
            flow.any { it.type == TransactionType.DEBIT } -> {
                val since = flow.filter { it.type == TransactionType.TRANSFER }.maxOfOrNull { it.timestamp } ?: 0L
                net(flow, since)
            }
            else -> null
        }
        return CardPosition(limitMinor = limit, outstandingMinor = outstanding)
    }

    /** The spend and payment keys of a due message, one row per statement or payment so re-reading a message adds nothing. */
    internal fun keyOf(d: CardDue, day: Long) =
        if (d.paymentMinor != null) "cardpay:${d.bank}:${d.last4}:$day:${d.paymentMinor}" else "carddue:${d.bank}:${d.last4}:$day"
}

/** Stores [CardDue]s from SMS and email where the card screens read them (see [CardDues.position]). */
@Singleton
class CardDueRecorder @Inject constructor(private val statements: StatementDao) {
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    /** Returns true when something new was stored. */
    suspend fun apply(d: CardDue, source: String): Boolean {
        val sent = Instant.ofEpochMilli(d.at).atZone(zone).toLocalDate().toEpochDay()
        val day = if (d.paymentMinor != null) sent else d.statementEpochDay ?: sent
        val key = CardDues.keyOf(d, day)
        val existing = statements.byKey(key)
        if (existing != null && (d.paymentMinor != null || existing.totalDueMinor != null || d.totalDueMinor == null)) return false
        statements.upsert(
            StatementEntity(
                id = existing?.id ?: 0, key = key, source = source, sender = d.bank, subject = null, fileName = "", receivedAt = d.at,
                status = StatementEntity.PARSED, transactionCount = 0, holdingCount = 0, filePath = null, bankName = d.bank, last4 = d.last4,
                processedAt = System.currentTimeMillis(),
                kind = if (d.paymentMinor != null) CardDues.KIND_PAYMENT else CardDues.KIND_DUE,
                totalDueMinor = d.totalDueMinor, minDueMinor = d.minDueMinor, dueEpochDay = d.dueEpochDay,
                statementEpochDay = day, debitsMinor = d.paymentMinor,
            ),
        )
        return true
    }
}
