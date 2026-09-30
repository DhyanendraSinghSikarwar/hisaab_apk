package com.hisaab.shared

import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountBalances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBalancesTest {
    private val account = AccountKind.ACCOUNT

    @Test
    fun noBalanceAtAllIsNull() {
        assertNull(AccountBalances.current(account, null, null, null, null, null, 0))
    }

    @Test
    fun bankBalanceIsUsedWhenTheUserSetNone() {
        assertEquals(50_000L, AccountBalances.current(account, 50_000, null, 1_000, null, null, 0))
    }

    @Test
    fun userBalanceIsCarriedForwardByLaterTransactions() {
        // Set ₹1,000 at t=2000; since then ₹300 spent and ₹100 received.
        assertEquals(80_000L, AccountBalances.current(account, 50_000, null, 1_000, 100_000, 2_000, -20_000))
        assertTrue(AccountBalances.usesManual(account, 50_000, null, 1_000, 100_000, 2_000))
    }

    @Test
    fun aNewerBankBalanceWinsOverTheUserBalance() {
        assertEquals(70_000L, AccountBalances.current(account, 70_000, null, 3_000, 100_000, 2_000, -20_000))
        assertFalse(AccountBalances.usesManual(account, 70_000, null, 3_000, 100_000, 2_000))
    }

    @Test
    fun cardsUseTheAvailableLimit() {
        assertEquals(40_000L, AccountBalances.current(AccountKind.CARD, 99, 40_000, 1_000, null, null, 0))
        assertEquals(90_000L, AccountBalances.current(AccountKind.CARD, null, 40_000, 1_000, 100_000, 2_000, -10_000))
    }
}
