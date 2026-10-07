package com.hisaab.shared.repo

import kotlin.math.abs

/**
 * A balance a bank, card issuer or statement stated that the transactions DhanKosh holds do not add up to. The new
 * balance has been saved; [unexplainedMinor] is how far off the transactions were (positive: more money than they
 * explain), which usually means transactions the app never saw.
 */
data class BalanceGap(
    /** What changed, for the notification: "HDFC ••2779 balance", "Parag Parikh Flexi Cap invested". */
    val label: String,
    /** Stable per account or holding: one notification a day each. */
    val key: String,
    val unexplainedMinor: Long,
    /** When the stated balance was true. */
    val at: Long,
)

/** Told when stated balances did not match the transactions (see [BalanceGap]). Bound in :app to a short notification. */
fun interface BalanceUpdateNotifier {
    suspend fun onBalancesUpdated(gaps: List<BalanceGap>)

    companion object {
        val NONE = BalanceUpdateNotifier { }
    }
}

/**
 * Compares a stated balance with the one the app expects: the last stated balance moved on by every transaction since.
 * Only a fresh statement of balance counts (a rescan of old messages says nothing new), and only a gap above the
 * rounding of balances in messages.
 */
object BalanceReconciler {
    /** Balances in messages are often rounded to the rupee. */
    const val TOLERANCE_MINOR = 100L

    /** A balance stated longer ago than this (a rescan, an old statement) is applied but not reported. */
    const val FRESH_MS = 15L * 24 * 60 * 60 * 1000

    /**
     * The unexplained part of [stated] (true at [statedAt]), given the [previous] balance stated at [previousAt] and the
     * [netSince] change of the transactions in between; null when there is nothing to report. [unclear] counts the
     * transactions in between whose direction or rupee value is not known (a self transfer, an unpriced foreign spend):
     * with any of those the gap cannot be told apart from them.
     */
    fun gap(previous: Long?, previousAt: Long?, netSince: Long, unclear: Int, stated: Long, statedAt: Long, now: Long): Long? {
        if (previous == null || previousAt == null || statedAt <= previousAt) return null
        if (unclear > 0 || now - statedAt > FRESH_MS) return null
        val g = stated - (previous + netSince)
        return g.takeIf { abs(it) > TOLERANCE_MINOR }
    }

    /** True when [key] was not reported yet today ([today] and [lastDay] are epoch days). */
    fun due(lastDay: Long?, today: Long): Boolean = lastDay == null || lastDay < today

    /** "HDFC ••2779": the bank's name without " Bank", or the user's nickname, and the last digits. */
    fun accountLabel(bankName: String, last4: String, nickname: String?): String =
        nickname?.takeIf { it.isNotBlank() } ?: "${bankName.removeSuffix(" Bank").trim()} ••$last4"
}
