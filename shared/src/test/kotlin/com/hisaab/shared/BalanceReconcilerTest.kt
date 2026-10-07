package com.hisaab.shared

import com.hisaab.shared.repo.BalanceReconciler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stated balance against the last one moved on by the transactions in between. */
class BalanceReconcilerTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test
    fun matchingBalanceHasNoGap() {
        // ₹50,000 then ₹1,240 spent and ₹5,000 received: ₹53,760 expected and stated.
        assertNull(BalanceReconciler.gap(5_000_000, now - 3 * day, 376_000, 0, 5_376_000, now - day, now))
    }

    @Test
    fun missingSpendShowsAsANegativeGap() {
        // The bank says ₹52,520: ₹1,240 went out that the app never saw.
        assertEquals(-124_000L, BalanceReconciler.gap(5_000_000, now - 3 * day, 376_000, 0, 5_252_000, now - day, now))
    }

    @Test
    fun missingCreditShowsAsAPositiveGap() {
        assertEquals(1_000_000L, BalanceReconciler.gap(5_000_000, now - 3 * day, 0, 0, 6_000_000, now - day, now))
    }

    @Test
    fun roundingIsNotAGap() {
        assertNull(BalanceReconciler.gap(5_000_000, now - 3 * day, 0, 0, 5_000_090, now - day, now))
    }

    @Test
    fun nothingToSayWithoutANewerFreshBalance() {
        assertNull(BalanceReconciler.gap(null, null, 0, 0, 5_000_000, now, now)) // first balance ever
        assertNull(BalanceReconciler.gap(5_000_000, now - day, 0, 0, 4_000_000, now - 2 * day, now)) // older message
        assertNull(BalanceReconciler.gap(5_000_000, now - 60 * day, 0, 0, 4_000_000, now - 40 * day, now)) // rescan of old SMS
    }

    @Test
    fun transactionsOfUnknownDirectionMakeTheGapUnknowable() {
        assertNull(BalanceReconciler.gap(5_000_000, now - 3 * day, 0, 1, 4_000_000, now - day, now))
    }

    @Test
    fun oncePerAccountPerDay() {
        assertTrue(BalanceReconciler.due(null, 20_000))
        assertFalse(BalanceReconciler.due(20_000, 20_000))
        assertTrue(BalanceReconciler.due(19_999, 20_000))
    }

    @Test
    fun shortAccountLabel() {
        assertEquals("HDFC ••2779", BalanceReconciler.accountLabel("HDFC Bank", "2779", null))
        assertEquals("Salary", BalanceReconciler.accountLabel("HDFC Bank", "2779", "Salary"))
    }
}
