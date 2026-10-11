package com.hisaab.shared.insight

import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountEntity
import com.hisaab.shared.db.TransactionEntity
import kotlin.math.abs

/**
 * Money that moved on an account or card without any message the app saw: between two stated balances
 * (or available limits) the tracked transactions do not add up. [deltaMinor] is negative when money left, positive
 * when money came in; [card] gaps are changes of a credit card's available limit.
 */
data class UntrackedGap(
    val accountId: Long,
    val fromTime: Long,
    val toTime: Long,
    val deltaMinor: Long,
    val balanceBefore: Long,
    val balanceAfter: Long,
    val card: Boolean,
) {
    /** What the user dismissed or converted: one window of one account. */
    val key: String get() = keyOf(accountId, fromTime, toTime)

    val moneyLeft: Boolean get() = deltaMinor < 0

    companion object {
        fun keyOf(accountId: Long, fromTime: Long, toTime: Long) = "$accountId|$fromTime|$toTime"
    }
}

/**
 * Finds [UntrackedGap]s. For consecutive stated balances B1 (at t1) and B2 (at t2) of one account (with its debit cards
 * and merged accounts), the expected B2 is B1 plus every tracked transaction in (t1, t2], the one stating B2 included.
 * Pure: the caller loads the rows.
 */
object BalanceGaps {
    /** A gap must be at least this much (a rupee) ... */
    const val MIN_GAP_MINOR = 100L

    /** ... and at least this share of the money involved, in basis points (0.5%). */
    const val MIN_SHARE_BPS = 50L

    /** Balances stated longer ago than this are history, not news. */
    const val MAX_AGE_MS = 90L * 24 * 60 * 60 * 1000

    private const val MAX_UNKNOWN_TRANSFERS = 6

    /**
     * [transferDir]: for TRANSFER rows on a bank account, +1 when money came in, -1 when it left (known for transfers
     * the user entered); a transfer missing here has an unknown direction and every way it can read is tried.
     */
    fun detect(
        accounts: List<AccountEntity>,
        txs: List<TransactionEntity>,
        transferDir: Map<Long, Int>,
        dismissed: Set<String>,
        now: Long,
    ): List<UntrackedGap> {
        val usable = txs.filter { !it.needsReview && it.duplicateOfId == null && it.accountId != null }
        val byAccount = usable.groupBy { it.accountId!! }
        val out = ArrayList<UntrackedGap>()
        for (a in accounts) {
            if (a.mergedIntoId != null || a.hidden || a.linkedAccountId != null) continue
            if (a.accountType?.liquid == false) continue
            val card = a.kind == AccountKind.CARD
            val members = memberIds(a, accounts)
            val rows = members.flatMap { byAccount[it].orEmpty() }.sortedWith(compareBy({ it.timestamp }, { it.id }))
            val stated = rows.filter { (if (card) it.availableLimitMinor else it.balanceMinor) != null }
            for (i in 1 until stated.size) {
                val s1 = stated[i - 1]
                val s2 = stated[i]
                val t1 = s1.timestamp
                val t2 = s2.timestamp
                if (t2 <= t1 || now - t2 > MAX_AGE_MS) continue
                if (a.manualBalanceAt != null && a.manualBalanceAt > t1 && a.manualBalanceAt <= t2) continue
                val b1 = (if (card) s1.availableLimitMinor else s1.balanceMinor)!!
                val b2 = (if (card) s2.availableLimitMinor else s2.balanceMinor)!!
                val gap = gapIn(rows.filter { it.timestamp > t1 && it.timestamp <= t2 }, b1, b2, card, transferDir) ?: continue
                val g = UntrackedGap(a.id, t1, t2, gap, b1, b2, card)
                if (g.key !in dismissed) out += g
            }
        }
        return out
    }

    /** The account [a] and everything whose transactions belong to it: debit cards, merged accounts, and their cards. */
    fun memberIds(a: AccountEntity, all: List<AccountEntity>): Set<Long> {
        val merged = all.filter { it.mergedIntoId == a.id }.map { it.id }.toSet()
        val children = all.filter { it.linkedAccountId == a.id || it.mergedIntoId == a.id || (it.linkedAccountId != null && it.linkedAccountId in merged) }
        return setOf(a.id) + children.map { it.id }
    }

    /** B2 minus the expected B2, or null when they agree within the tolerance or the window cannot be judged. */
    private fun gapIn(window: List<TransactionEntity>, b1: Long, b2: Long, card: Boolean, dir: Map<Long, Int>): Long? {
        var known = 0L
        var sumAbs = 0L
        val unknown = ArrayList<Long>()
        for (t in window) {
            val amt = t.inrMinor ?: return null
            sumAbs += amt
            when (t.type) {
                TransactionType.CREDIT -> known += amt
                TransactionType.DEBIT, TransactionType.INVESTMENT -> known -= amt
                TransactionType.TRANSFER -> when {
                    card -> known += amt
                    dir[t.id] != null -> known += dir[t.id]!! * amt
                    else -> unknown += amt
                }
            }
        }
        if (unknown.size > MAX_UNKNOWN_TRANSFERS) return null
        val base = b2 - (b1 + known)
        // Every direction an unknown transfer can have: if any reading explains the balance, there is no gap.
        var sums = listOf(0L)
        for (u in unknown) sums = sums.flatMap { listOf(it + u, it - u) }
        val gaps = sums.map { base - it }
        if (gaps.any { !significant(it, sumAbs) }) return null
        if (unknown.isNotEmpty()) return null
        return gaps.single()
    }

    private fun significant(gap: Long, windowAmounts: Long): Boolean {
        val involved = abs(gap) + windowAmounts
        return abs(gap) >= maxOf(MIN_GAP_MINOR, involved * MIN_SHARE_BPS / 10_000)
    }
}

/** A way to explain a gap. */
enum class GapChoice { PAYMENT, TRANSFER, RECEIVED }

/** What the Add transaction screen starts with for a gap. */
data class GapPrefill(
    val accountId: Long,
    val amountMinor: Long,
    val outgoing: Boolean,
    /** A day inside the window: the stated-balance message's. */
    val time: Long,
    val fromTime: Long,
    val toTime: Long,
    val choices: List<GapChoice>,
    val choice: GapChoice,
    /** Transfer sides: the gap's account on the side the money moved from or to. */
    val fromAccountId: Long?,
    val toAccountId: Long?,
) {
    companion object {
        /** [last] is the choice the user made last time for this account, used when it applies. */
        fun of(g: UntrackedGap, last: GapChoice? = null): GapPrefill {
            val out = g.moneyLeft
            // A spend on a credit card is a payment only; money that came in may be a transfer from another account.
            val choices = when {
                out && g.card -> listOf(GapChoice.PAYMENT)
                out -> listOf(GapChoice.PAYMENT, GapChoice.TRANSFER)
                else -> listOf(GapChoice.RECEIVED, GapChoice.TRANSFER)
            }
            // A card limit that rose is most often a bill paid from a bank account.
            val default = if (g.card && !out) GapChoice.TRANSFER else choices.first()
            val time = (g.toTime - 60_000L).takeIf { it > g.fromTime } ?: g.toTime
            return GapPrefill(
                g.accountId, abs(g.deltaMinor), out, time, g.fromTime, g.toTime, choices, last?.takeIf { it in choices } ?: default,
                fromAccountId = if (out) g.accountId else null, toAccountId = if (out) null else g.accountId,
            )
        }
    }
}

/** Existing rows that may be the missing money: account-less transactions in the window with a matching amount. */
object GapCandidates {
    private const val MAX_ROWS = 14

    /** Up to three suggestions, best first: one row of the amount, else two or three rows that add up to it. */
    fun find(g: UntrackedGap, rows: List<TransactionEntity>): List<List<TransactionEntity>> {
        val want = abs(g.deltaMinor)
        val pool = rows.filter {
            it.accountId == null && !it.needsReview && it.duplicateOfId == null && it.currency == "INR" &&
                it.timestamp > g.fromTime && it.timestamp <= g.toTime && when (it.type) {
                    TransactionType.CREDIT -> !g.moneyLeft
                    TransactionType.DEBIT, TransactionType.INVESTMENT -> g.moneyLeft
                    TransactionType.TRANSFER -> false
                }
        }.sortedBy { it.timestamp }.take(MAX_ROWS)
        fun hit(sum: Long) = abs(sum - want) < BalanceGaps.MIN_GAP_MINOR
        val found = ArrayList<List<TransactionEntity>>()
        pool.filter { hit(it.amountMinor) }.forEach { found += listOf(it) }
        for (i in pool.indices) for (j in i + 1 until pool.size) {
            if (hit(pool[i].amountMinor + pool[j].amountMinor)) found += listOf(pool[i], pool[j])
        }
        for (i in pool.indices) for (j in i + 1 until pool.size) for (k in j + 1 until pool.size) {
            if (hit(pool[i].amountMinor + pool[j].amountMinor + pool[k].amountMinor)) found += listOf(pool[i], pool[j], pool[k])
        }
        return found.take(3)
    }
}
