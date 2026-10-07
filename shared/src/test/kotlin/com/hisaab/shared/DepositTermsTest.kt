package com.hisaab.shared

import com.hisaab.parser.bank.DepositAction
import com.hisaab.parser.bank.DepositInfo
import com.hisaab.parser.bank.DepositKind
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.MaturityAction
import com.hisaab.shared.repo.DepositTerms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** How deposit messages set the maturity calendar: day and renew/pay-out, without older messages undoing newer ones. */
class DepositTermsTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(ist).toInstant().toEpochMilli()

    @Test
    fun kindsMapToAccountTypes() {
        assertEquals(AccountType.FD, DepositTerms.typeOf(DepositKind.FD))
        assertEquals(AccountType.RD, DepositTerms.typeOf(DepositKind.RD))
        assertEquals(AccountType.PPF, DepositTerms.typeOf(DepositKind.PPF))
    }

    @Test
    fun bookingSetsMaturityAndAction() {
        val d = DepositInfo(DepositKind.FD, maturity = LocalDate.of(2027, 9, 24), action = DepositAction.RENEW_ALL)
        assertEquals(LocalDate.of(2027, 9, 24).toEpochDay() to MaturityAction.RENEW_ALL, DepositTerms.of(d, at(LocalDate.of(2026, 9, 24)), null, null, newest = true))
    }

    @Test
    fun renewalMovesTheDayAndKeepsTheUsersAction() {
        val d = DepositInfo(DepositKind.FD, maturity = LocalDate.of(2028, 9, 24))
        val old = LocalDate.of(2027, 9, 24).toEpochDay()
        assertEquals(LocalDate.of(2028, 9, 24).toEpochDay() to MaturityAction.RENEW_PRINCIPAL,
            DepositTerms.of(d, at(LocalDate.of(2027, 9, 24)), old, MaturityAction.RENEW_PRINCIPAL, newest = true))
    }

    @Test
    fun paidOutDepositMaturesOnItsDate() {
        val d = DepositInfo(DepositKind.FD, maturity = LocalDate.of(2026, 9, 24), closed = true)
        assertEquals(LocalDate.of(2026, 9, 24).toEpochDay() to MaturityAction.CREDIT, DepositTerms.of(d, at(LocalDate.of(2026, 9, 24)), null, null, newest = true))
    }

    @Test
    fun olderMessagesDoNotUndoNewerOnes() {
        val known = LocalDate.of(2028, 1, 1).toEpochDay()
        assertNull(DepositTerms.of(DepositInfo(DepositKind.FD, closed = true), at(LocalDate.of(2026, 1, 1)), known, MaturityAction.RENEW_ALL, newest = false))
        assertNull(DepositTerms.of(DepositInfo(DepositKind.FD, maturity = LocalDate.of(2027, 1, 1)), at(LocalDate.of(2026, 1, 1)), known, null, newest = false))
        // ... but fill a maturity day nobody stated yet.
        assertEquals(LocalDate.of(2027, 1, 1).toEpochDay() to null,
            DepositTerms.of(DepositInfo(DepositKind.FD, maturity = LocalDate.of(2027, 1, 1)), at(LocalDate.of(2026, 1, 1)), null, null, newest = false))
    }

    @Test
    fun aMessageWithoutTermsChangesNothing() {
        assertNull(DepositTerms.of(DepositInfo(DepositKind.PPF), at(LocalDate.of(2026, 9, 1)), null, null, newest = true))
    }
}
