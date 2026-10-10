package com.hisaab.shared

import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountWithActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBalanceTest {
    private fun acc(kind: AccountKind, balance: Long?, limit: Long?, change: Long) = AccountWithActivity(
        id = 1, bankName = "Axis Bank", last4 = "0775", kind = kind, nickname = null, colorArgb = null,
        latestBalanceMinor = balance, availableLimitMinor = limit, balanceUpdatedAt = 1000, monthSpent = 0, transactionCount = 1,
        changeSinceReported = change,
    )

    @Test
    fun `bank balance moves on by later transactions and is marked estimated`() {
        val a = acc(AccountKind.ACCOUNT, 100000, null, -30000)
        assertEquals(70000L, a.currentBalanceMinor)
        assertTrue(a.balanceIsEstimated)
        val stated = acc(AccountKind.ACCOUNT, 100000, null, 0)
        assertEquals(100000L, stated.currentBalanceMinor)
        assertFalse(stated.balanceIsEstimated)
    }

    @Test
    fun `card limit left drops by a spend without a limit figure and rises on a refund`() {
        assertEquals(55322536L - 121200L, acc(AccountKind.CARD, null, 55322536, -121200).currentBalanceMinor)
        assertEquals(55322536L + 10000L, acc(AccountKind.CARD, null, 55322536, 10000).currentBalanceMinor)
        assertTrue(acc(AccountKind.CARD, null, 55322536, -121200).balanceIsEstimated)
    }

    @Test
    fun `no stated figure - nothing is invented`() {
        assertEquals(null, acc(AccountKind.ACCOUNT, null, null, -500).currentBalanceMinor)
    }
}
