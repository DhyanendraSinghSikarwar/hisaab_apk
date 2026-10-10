package com.hisaab.app

import com.hisaab.app.notify.CardDueReminderPlan
import com.hisaab.app.notify.DueStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CardDueReminderPlanTest {
    private val due = LocalDate.of(2026, 10, 20)
    private fun next(daysToDue: Long, sent: Set<DueStage> = emptySet(), paid: Boolean = false, snoozed: LocalDate? = null, d: LocalDate = due) =
        CardDueReminderPlan.next(d.minusDays(daysToDue), d, sent, paid, snoozed)

    @Test
    fun `stages fire at 5, 2, 0 and -1 days`() {
        assertNull(next(6))
        assertEquals(DueStage.DAYS_5, next(5))
        assertEquals(DueStage.DAYS_2, next(2, setOf(DueStage.DAYS_5)))
        assertEquals(DueStage.DUE_DAY, next(0, setOf(DueStage.DAYS_5, DueStage.DAYS_2)))
        assertEquals(DueStage.OVERDUE, next(-1, setOf(DueStage.DAYS_5, DueStage.DAYS_2, DueStage.DUE_DAY)))
    }

    @Test
    fun `a stage is never repeated`() {
        assertNull(next(5, setOf(DueStage.DAYS_5)))
        assertNull(next(4, setOf(DueStage.DAYS_5)))
        assertNull(next(3, setOf(DueStage.DAYS_5)))
        assertNull(next(-1, setOf(DueStage.OVERDUE)))
    }

    @Test
    fun `a card first seen late gets only the most urgent stage`() {
        assertEquals(DueStage.DAYS_2, next(1))
        assertEquals(DueStage.DUE_DAY, next(0))
        assertEquals(DueStage.OVERDUE, next(-3))
    }

    @Test
    fun `a paid bill gets nothing`() {
        for (d in listOf(5L, 2L, 0L, -1L)) assertNull(next(d, paid = true))
    }

    @Test
    fun `a snoozed bill waits until the snooze ends`() {
        val today = due.minusDays(2)
        assertNull(CardDueReminderPlan.next(today, due, setOf(DueStage.DAYS_5), false, today.plusDays(1)))
        assertEquals(DueStage.DAYS_2, CardDueReminderPlan.next(today.plusDays(1), due, setOf(DueStage.DAYS_5), false, today.plusDays(1)))
    }

    @Test
    fun `a new due date starts fresh`() {
        val newDue = due.plusMonths(1)
        val sentForOld = setOf(DueStage.DAYS_5, DueStage.DAYS_2, DueStage.DUE_DAY, DueStage.OVERDUE)
        assertNull(next(5, sentForOld))
        // The store keys sent stages by due date, so the new bill is looked up with an empty set (and is not paid).
        assertEquals(DueStage.DAYS_5, next(5, emptySet(), paid = false, d = newDue))
    }

    @Test
    fun `reminders stop a week after the due date`() {
        assertEquals(DueStage.OVERDUE, next(-7))
        assertNull(next(-8))
    }
}
