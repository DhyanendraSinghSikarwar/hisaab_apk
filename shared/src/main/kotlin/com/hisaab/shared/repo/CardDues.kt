package com.hisaab.shared.repo

import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.statement.BankKeys
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
    /** Which figures come from a manual [CardOverride]. */
    val billedEdited: Boolean = false,
    val minDueEdited: Boolean = false,
    val dueEdited: Boolean = false,
    val unbilledEdited: Boolean = false,
    val outstandingEdited: Boolean = false,
    /** The app-calculated position when an override is applied, for "Calculated ₹x" notes. */
    val calculated: CardPosition? = null,
    /** Where the billed figures came from: SMS, EMAIL, STATEMENT (a file) or MANUAL (an override); null when worked out. */
    val source: String? = null,
    /** When that source arrived (epoch ms): the message or statement time, or the moment the figures were edited. */
    val sourceAtMs: Long? = null,
) {
    val edited: Boolean get() = billedEdited || minDueEdited || dueEdited || unbilledEdited || outstandingEdited

    /** Everything owed now: billed plus unbilled when either is known, else the outstanding figure. */
    val totalMinor: Long? get() = if (billedMinor != null || unbilledMinor != null) (billedMinor ?: 0L) + (unbilledMinor ?: 0L) else outstandingMinor
    /** What the card owes now: the billed amount, else the outstanding figure. */
    val owedMinor: Long get() = billedMinor?.takeIf { it > 0 } ?: outstandingMinor?.takeIf { it > 0 } ?: 0L
    val known: Boolean get() = billedMinor != null || unbilledMinor != null || outstandingMinor != null

    /** Limit left: the card's own available figure, except that a manual total owed is taken off the limit. */
    fun limitLeft(limit: Long?, available: Long?): Long? =
        if ((billedEdited || unbilledEdited || outstandingEdited) && limit != null) totalMinor?.let { (limit - it).coerceAtLeast(0L) } ?: available else available
}

/**
 * Figures the user set by hand for one card; a null field is not overridden. Kept as a statement row of kind
 * [CardDues.KIND_OVERRIDE] (billed in `totalDueMinor`, unbilled in `closingMinor`, [setAtEpochMs] in `processedAt`), so it stays on the phone.
 */
data class CardOverride(
    val cardId: Long,
    val billedMinor: Long? = null,
    val minDueMinor: Long? = null,
    val dueEpochDay: Long? = null,
    val unbilledMinor: Long? = null,
    val setAtEpochMs: Long,
    /** Total owed when no statement is known; replaces the computed outstanding (stored in `openingMinor`). */
    val outstandingMinor: Long? = null,
) {
    val isEmpty: Boolean get() = billedMinor == null && minDueMinor == null && dueEpochDay == null && unbilledMinor == null && outstandingMinor == null
}

/** Computes [CardPosition]s and records card due messages as statement rows. */
object CardDues {
    /** A manual [CardOverride] row. */
    const val KIND_OVERRIDE = "CARD_OVERRIDE"

    fun overrideKey(bank: String?, last4: String?) = "cardovr:${bank?.lowercase().orEmpty()}:$last4"

    /** The override held by [rows], if any. [cardId] is carried through unchanged. */
    fun overrideOf(rows: List<StatementEntity>, cardId: Long = 0): CardOverride? =
        rows.filter { it.kind == KIND_OVERRIDE }.maxByOrNull { it.processedAt }?.let {
            CardOverride(cardId, it.totalDueMinor, it.minDueMinor, it.dueEpochDay, it.closingMinor, it.processedAt, it.openingMinor).takeIf { o -> !o.isEmpty }
        }

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
        // A locked statement (password not yet entered) or an unreadable one says nothing about the card.
        val real = rows.filter { it.kind != KIND_OVERRIDE && usable(it) }
        val o = overrideOf(rows) ?: return basePosition(real, availableMinor, flow, today, zone)
        // A statement or due message that ARRIVED after the edit replaces the manual figures, whatever date it is for;
        // one that arrived before the edit never beats it.
        val fresh = real.filter { carriesBill(it) && arrivalOf(it) > o.setAtEpochMs }
        if (fresh.isNotEmpty()) {
            val kept = real.filter { !carriesBill(it) || it in fresh }
            val b = basePosition(kept, availableMinor, flow, today, zone, trust = true)
            return if (b.limitMinor != null) b else b.copy(limitMinor = basePosition(real, availableMinor, flow, today, zone).limitMinor)
        }
        val base = basePosition(real, availableMinor, flow, today, zone)
        val setDay = Instant.ofEpochMilli(o.setAtEpochMs).atZone(zone).toLocalDate().toEpochDay()
        val paid = maxOf(
            flow.filter { it.type == TransactionType.TRANSFER && it.timestamp >= o.setAtEpochMs }.sumOf { it.amountMinor },
            real.filter { it.kind == KIND_PAYMENT && (it.statementEpochDay ?: 0L) >= setDay }.sumOf { it.debitsMinor ?: 0L },
        )
        val billed = o.billedMinor?.let { it - paid }
        // Paid off: the manual figures are done with.
        if (billed != null && billed <= 0L) return base
        val outstanding = o.outstandingMinor?.let { it - paid }
        if (outstanding != null && outstanding <= 0L) return base
        val spent = net(flow, o.setAtEpochMs)
        return base.copy(
            billedMinor = billed ?: base.billedMinor,
            minDueMinor = if (o.billedMinor != null) o.minDueMinor else o.minDueMinor ?: base.minDueMinor,
            dueDay = o.dueEpochDay?.let(LocalDate::ofEpochDay) ?: base.dueDay,
            unbilledMinor = o.unbilledMinor?.let { it + spent } ?: base.unbilledMinor,
            estimated = if (o.billedMinor != null) false else base.estimated,
            billedEdited = o.billedMinor != null, minDueEdited = o.minDueMinor != null, dueEdited = o.dueEpochDay != null,
            unbilledEdited = o.unbilledMinor != null, calculated = base,
            outstandingMinor = outstanding?.let { it + spent } ?: base.outstandingMinor, outstandingEdited = outstanding != null,
            source = "MANUAL", sourceAtMs = o.setAtEpochMs,
        )
    }

    /** When a row reached the phone: the message or mail time, else the time it was processed. */
    fun arrivalOf(r: StatementEntity): Long = if (r.receivedAt > 0) r.receivedAt else r.processedAt

    private fun usable(r: StatementEntity) =
        r.status != StatementEntity.LOCKED && r.status != StatementEntity.UNREADABLE && r.kind != "BANK" && r.kind != "INVESTMENT"

    /** A real statement or due message: carries a total or a due date (not a payment, override or limit-only row). */
    private fun carriesBill(r: StatementEntity) =
        r.kind != KIND_PAYMENT && r.kind != KIND_OVERRIDE && (r.totalDueMinor != null || r.dueEpochDay != null)

    /** A card to match statement rows against: its bank and last four digits. */
    data class CardRef(val bank: String?, val last4: String?)

    private val BANK_NOISE = Regex("""\b(?:bank|ltd|limited|card|cards|credit|india)\b""")
    private val NON_ALNUM = Regex("""[^a-z0-9]""")

    private fun bankKey(name: String): String = BankKeys.of(name) ?: name.lowercase().replace(BANK_NOISE, "").replace(NON_ALNUM, "")

    /** Whether two bank names are the issuer ("Axis", "AXIS BANK", "HDFC Bank Ltd", "SBI Card"); false when either is blank. */
    fun bankMatches(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val x = bankKey(a)
        val y = bankKey(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || (minOf(x.length, y.length) >= 3 && (x.startsWith(y) || y.startsWith(x)))
    }

    /**
     * The rows of [all] that belong to the credit card [bank] ••[last4]: rows with its last four (and the same issuer, or
     * none named), else, when it is the only card of its issuer in [cards], rows of that issuer with no last four or with
     * one no known card has (a re-issued or supplementary card, a bill that names the card only by product).
     */
    fun rowsFor(all: List<StatementEntity>, bank: String?, last4: String?, cards: List<CardRef> = emptyList()): List<StatementEntity> {
        val sameBank = cards.filter { bankMatches(it.bank, bank) }
        val sole = sameBank.size <= 1
        return all.filter { r ->
            val rl = r.last4?.takeIf { it.isNotBlank() }
            when {
                rl != null && rl == last4 -> r.bankName == null || bankMatches(r.bankName, bank)
                !sole || !bankMatches(r.bankName, bank) -> false
                rl == null -> true
                else -> sameBank.none { it.last4 == rl }
            }
        }
    }

    /** [trust]: the newest bill is taken as it is, even when its statement date is older than the usual cycle (it arrived after a manual edit). */
    private fun basePosition(rows: List<StatementEntity>, availableMinor: Long?, flow: List<CardFlow>, today: LocalDate, zone: ZoneId, trust: Boolean = false): CardPosition {
        val newest = compareBy<StatementEntity>({ it.statementEpochDay ?: 0L }, { arrivalOf(it) }, { it.processedAt })
        // A bill without a statement date takes one from its due date, else from the day it arrived, so it is ranked fairly.
        val bills = rows.filter { it.kind != KIND_PAYMENT }.map {
            if (carriesBill(it) && it.statementEpochDay == null) {
                it.copy(statementEpochDay = it.dueEpochDay?.minus(DUE_GAP_DAYS) ?: Instant.ofEpochMilli(arrivalOf(it)).atZone(zone).toLocalDate().toEpochDay())
            } else it
        }
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
        val covered = bill != null && !stale && (trust || expected == null || statementDay == null || !statementDay.isBefore(expected))
        if (covered && bill != null) {
            val billed = ((bill.totalDueMinor ?: 0L) - (statementDay?.let(::paidSince) ?: maxOf(
                flow.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amountMinor },
                rows.filter { it.kind == KIND_PAYMENT }.sumOf { it.debitsMinor ?: 0L },
            ))).coerceAtLeast(0L)
            val unbilled = statementDay?.let { net(flow, ms(it.plusDays(1), zone)) }
            return CardPosition(
                billed, bill.minDueMinor.takeIf { billed > 0 }, dueDay, statementDay, limit, unbilled, null,
                source = if (bill.kind == KIND_DUE) bill.source else "STATEMENT", sourceAtMs = arrivalOf(bill),
            )
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
        if (d.paymentMinor != null) "cardpay:${d.bank}:${d.last4}:$day:${d.paymentMinor}"
        else "carddue:${d.bank}:${d.last4.ifEmpty { "any" + d.totalDueMinor }}:$day"
}

/** Saves and clears a card's manual [CardOverride] (a statement row of kind [CardDues.KIND_OVERRIDE]). */
@Singleton
class CardOverrideStore @Inject constructor(private val statements: StatementDao) {
    /** Stores [o] for the card [bank] ••[last4]; an empty override clears it. */
    suspend fun save(bank: String?, last4: String, o: CardOverride, now: Long = System.currentTimeMillis()) {
        if (o.isEmpty) return clear(bank, last4)
        val key = CardDues.overrideKey(bank, last4)
        val existing = statements.byKey(key)
        statements.upsert(
            StatementEntity(
                id = existing?.id ?: 0, key = key, source = "MANUAL", sender = bank.orEmpty(), subject = null, fileName = "", receivedAt = now,
                status = StatementEntity.PARSED, transactionCount = 0, holdingCount = 0, filePath = null, bankName = bank, last4 = last4,
                processedAt = now, kind = CardDues.KIND_OVERRIDE, totalDueMinor = o.billedMinor, minDueMinor = o.minDueMinor,
                dueEpochDay = o.dueEpochDay, closingMinor = o.unbilledMinor, openingMinor = o.outstandingMinor,
            ),
        )
    }

    suspend fun clear(bank: String?, last4: String) {
        statements.byKey(CardDues.overrideKey(bank, last4))?.let { statements.delete(it.id) }
    }
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
                status = StatementEntity.PARSED, transactionCount = 0, holdingCount = 0, filePath = null, bankName = d.bank, last4 = d.last4.ifBlank { null },
                processedAt = System.currentTimeMillis(),
                kind = if (d.paymentMinor != null) CardDues.KIND_PAYMENT else CardDues.KIND_DUE,
                totalDueMinor = d.totalDueMinor, minDueMinor = d.minDueMinor, dueEpochDay = d.dueEpochDay,
                statementEpochDay = day, debitsMinor = d.paymentMinor,
            ),
        )
        return true
    }
}
