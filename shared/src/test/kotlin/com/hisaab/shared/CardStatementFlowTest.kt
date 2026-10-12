package com.hisaab.shared

import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.CardFlow
import com.hisaab.shared.db.StatementEntity
import com.hisaab.shared.repo.CardDues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** From statement rows to what a card shows, and how a manual edit and a later statement settle which figures win. */
class CardStatementFlowTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 11)

    private fun ms(day: LocalDate, hour: Int = 12) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    private fun row(
        bank: String? = "Axis Bank", last4: String? = "0775", kind: String = "CREDIT_CARD", total: Long? = null, min: Long? = null,
        due: LocalDate? = null, stmt: LocalDate? = null, arrived: Long = 0, status: String = StatementEntity.PARSED, key: String = "k${kotlin.random.Random.nextInt()}",
        source: String = "EMAIL", limit: Long? = null,
    ) = StatementEntity(
        key = key, source = source, sender = bank.orEmpty(), subject = null, fileName = if (kind == CardDues.KIND_DUE) "" else "stmt.pdf", receivedAt = arrived,
        status = status, transactionCount = 0, holdingCount = 0, filePath = null, bankName = bank, last4 = last4, processedAt = arrived + 5_000, kind = kind,
        totalDueMinor = total, minDueMinor = min, dueEpochDay = due?.toEpochDay(), statementEpochDay = stmt?.toEpochDay(), creditLimitMinor = limit,
    )

    private fun edit(setAt: Long, billed: Long? = null, min: Long? = null, due: LocalDate? = null, unbilled: Long? = null, bank: String = "Axis Bank", last4: String = "0775") =
        StatementEntity(
            key = CardDues.overrideKey(bank, last4), source = "MANUAL", sender = bank, subject = null, fileName = "", receivedAt = setAt,
            status = StatementEntity.PARSED, transactionCount = 0, holdingCount = 0, filePath = null, bankName = bank, last4 = last4, processedAt = setAt,
            kind = CardDues.KIND_OVERRIDE, totalDueMinor = billed, minDueMinor = min, dueEpochDay = due?.toEpochDay(), closingMinor = unbilled,
        )

    private fun pos(rows: List<StatementEntity>, flow: List<CardFlow> = emptyList()) = CardDues.position(rows, null, flow, today, zone)

    // ---- A: from statement mail to the card -------------------------------------------------------------------------------

    @Test
    fun `bank names of one issuer match however they are written`() {
        assertTrue(CardDues.bankMatches("Axis", "Axis Bank"))
        assertTrue(CardDues.bankMatches("AXIS BANK", "Axis Bank"))
        assertTrue(CardDues.bankMatches("HDFC Bank Ltd", "HDFC Bank"))
        assertTrue(CardDues.bankMatches("SBI Card", "SBI"))
        assertTrue(CardDues.bankMatches("ICICI", "ICICI Bank"))
        assertFalse(CardDues.bankMatches("Axis Bank", "HDFC Bank"))
        assertFalse(CardDues.bankMatches(null, "HDFC Bank"))
    }

    @Test
    fun `a statement mail with the bank spelled differently still reaches the card`() {
        val mail = row(bank = "AXIS", total = 1_234_500, min = 62_000, due = d(10, 21), stmt = d(10, 2), arrived = ms(d(10, 3)))
        val mine = CardDues.rowsFor(listOf(mail), "Axis Bank", "0775", listOf(CardDues.CardRef("Axis Bank", "0775")))
        val p = pos(mine)
        assertEquals(1_234_500L, p.billedMinor)
        assertEquals(62_000L, p.minDueMinor)
        assertEquals(d(10, 21), p.dueDay)
        assertEquals("EMAIL", p.source)
    }

    @Test
    fun `a bill with no last four goes to the only card of that bank, not to one of two`() {
        val mail = row(bank = "HDFC Bank", last4 = null, total = 800_000, due = d(10, 21), stmt = d(10, 2), arrived = ms(d(10, 3)))
        val one = listOf(CardDues.CardRef("HDFC Bank", "1111"))
        assertEquals(1, CardDues.rowsFor(listOf(mail), "HDFC Bank", "1111", one).size)
        val two = one + CardDues.CardRef("HDFC Bank", "2222")
        assertTrue(CardDues.rowsFor(listOf(mail), "HDFC Bank", "1111", two).isEmpty())
    }

    @Test
    fun `a bill whose last four no known card has attaches to the sole card of the bank`() {
        val mail = row(bank = "HDFC Bank", last4 = "9999", total = 800_000, due = d(10, 21), stmt = d(10, 2), arrived = ms(d(10, 3)))
        assertEquals(1, CardDues.rowsFor(listOf(mail), "HDFC Bank", "1111", listOf(CardDues.CardRef("HDFC Bank", "1111"))).size)
        // The same last four on another bank's card is not this card's.
        assertTrue(CardDues.rowsFor(listOf(mail), "Axis Bank", "9999", listOf(CardDues.CardRef("Axis Bank", "9999"))).isEmpty())
    }

    @Test
    fun `a locked statement says nothing and does not hide the card's other rows`() {
        val locked = row(total = 999_999, due = d(10, 25), stmt = d(10, 9), arrived = ms(d(10, 9)), status = StatementEntity.LOCKED)
        val real = row(total = 400_000, due = d(10, 21), stmt = d(10, 2), arrived = ms(d(10, 3)))
        assertEquals(400_000L, pos(listOf(locked, real)).billedMinor)
    }

    @Test
    fun `without a statement date a later arrival ranks newer`() {
        val older = row(total = 300_000, due = null, arrived = ms(d(10, 3)))
        val newer = row(total = 450_000, due = null, arrived = ms(d(10, 8)))
        assertEquals(450_000L, pos(listOf(older, newer)).billedMinor)
    }

    @Test
    fun `source and date of the figures are reported`() {
        val sms = row(kind = CardDues.KIND_DUE, source = "SMS", total = 500_000, due = d(10, 21), stmt = d(10, 2), arrived = ms(d(10, 2)))
        val p = pos(listOf(sms))
        assertEquals("SMS", p.source)
        assertEquals(d(10, 2), p.statementDay)
        assertEquals(ms(d(10, 2)), p.sourceAtMs)
    }

    // ---- B: a manual edit against a later statement -----------------------------------------------------------------------

    private val setAt = ms(d(10, 10), 10)
    private val earlier = row(total = 500_000, min = 25_000, due = d(10, 21), stmt = d(10, 2), arrived = ms(d(10, 3)))

    @Test
    fun `statement then edit - the edit wins`() {
        val p = pos(listOf(earlier, edit(setAt, billed = 750_000, min = 40_000, due = d(10, 24))))
        assertEquals(750_000L, p.billedMinor)
        assertEquals(d(10, 24), p.dueDay)
        assertTrue(p.billedEdited)
        assertEquals("MANUAL", p.source)
        assertEquals(setAt, p.sourceAtMs)
    }

    @Test
    fun `edit then an older statement that arrived before it - the edit stays`() {
        val old = row(total = 100_000, due = d(9, 21), stmt = d(9, 2), arrived = ms(d(10, 9)))
        assertEquals(750_000L, pos(listOf(earlier, old, edit(setAt, billed = 750_000))).billedMinor)
    }

    @Test
    fun `edit then a statement that arrives later with an older statement date - the statement wins entirely`() {
        val late = row(total = 640_000, min = 32_000, due = d(10, 25), stmt = d(9, 30), arrived = ms(d(10, 11), 8), key = "late")
        val p = pos(listOf(earlier, late, edit(setAt, billed = 750_000, min = 40_000, due = d(10, 24), unbilled = 12_000)))
        assertEquals(640_000L, p.billedMinor)
        assertEquals(32_000L, p.minDueMinor)
        assertEquals(d(10, 25), p.dueDay)
        assertFalse(p.edited)
        assertNull(p.calculated)
    }

    @Test
    fun `edit then an SMS due - the SMS wins`() {
        val sms = row(kind = CardDues.KIND_DUE, source = "SMS", total = 520_000, min = 26_000, due = d(10, 28), stmt = d(10, 2), arrived = ms(d(10, 10), 18))
        val p = pos(listOf(earlier, sms, edit(setAt, billed = 750_000)))
        assertEquals(520_000L, p.billedMinor)
        assertEquals("SMS", p.source)
        assertFalse(p.edited)
    }

    @Test
    fun `a new statement resets the unbilled baseline to spends after its date`() {
        val flow = listOf(CardFlow(ms(d(10, 5)), TransactionType.DEBIT, 20_000), CardFlow(ms(d(10, 11)), TransactionType.DEBIT, 5_000))
        val late = row(total = 640_000, due = d(10, 25), stmt = d(10, 4), arrived = ms(d(10, 11), 8), key = "late2")
        val p = pos(listOf(earlier, late, edit(setAt, unbilled = 90_000)), flow)
        assertEquals(25_000L, p.unbilledMinor)
        assertFalse(p.unbilledEdited)
    }

    @Test
    fun `two cards keep separate edits`() {
        val a = pos(listOf(earlier, edit(setAt, billed = 750_000)))
        val otherRows = listOf(row(last4 = "1111", total = 300_000, due = d(10, 21), stmt = d(10, 2), arrived = ms(d(10, 3))))
        val b = pos(otherRows)
        assertEquals(750_000L, a.billedMinor)
        assertEquals(300_000L, b.billedMinor)
        assertFalse(b.edited)
    }

    @Test
    fun `a payment after the edit clears it`() {
        val pay = row(kind = CardDues.KIND_PAYMENT, key = "pay", stmt = d(10, 10), arrived = ms(d(10, 10), 15)).copy(debitsMinor = 750_000)
        val p = pos(listOf(earlier, pay, edit(setAt, billed = 750_000)))
        assertFalse(p.billedEdited)
    }

    // ---- One source of truth ------------------------------------------------------------------------------------------------

    private fun acc(id: Long, bank: String, last4: String, kind: AccountKind, balance: Long? = null, limitLeft: Long? = null, type: AccountType? = null,
                    hidden: Boolean = false, linked: Long? = null) =
        AccountWithActivity(
            id = id, bankName = bank, last4 = last4, kind = kind, nickname = null, colorArgb = null, latestBalanceMinor = balance, availableLimitMinor = limitLeft,
            balanceUpdatedAt = 1_000L, monthSpent = 0, transactionCount = 0, accountType = type, hidden = hidden, linkedAccountId = linked,
        )

    @Test
    fun `card dues total equals the sum of what each card shows`() {
        val accounts = listOf(
            acc(1, "HDFC Bank", "1001", AccountKind.ACCOUNT, balance = 5_000_00),
            acc(2, "Axis Bank", "2002", AccountKind.ACCOUNT, balance = 2_500_00),
            acc(3, "HDFC Bank", "3003", AccountKind.CARD, type = AccountType.DEBIT_CARD, balance = 5_000_00, linked = 1),
            acc(4, "Axis Bank", "0775", AccountKind.CARD, limitLeft = 1_000_000, type = AccountType.CREDIT_CARD),
            acc(5, "ICICI Bank", "5005", AccountKind.CARD, limitLeft = 300_000, type = AccountType.CREDIT_CARD, hidden = true),
        )
        val statements = listOf(
            row(bank = "Axis", total = 500_000, min = 25_000, due = d(10, 21), stmt = d(10, 2), arrived = ms(d(10, 3))),
            edit(setAt, billed = 650_000),
            row(bank = "ICICI Bank", last4 = "5005", total = 900_000, due = d(10, 21), stmt = d(10, 2), arrived = ms(d(10, 3))),
        )
        val visible = accounts.filter { !it.hidden }
        // Bank accounts: Accounts totals and Net worth both sum currentBalanceMinor of visible bank accounts.
        val banks = visible.filter { it.kind == AccountKind.ACCOUNT }
        assertEquals(7_500_00L, banks.sumOf { it.currentBalanceMinor ?: 0L })
        // The linked debit card is not a bank account: it adds nothing to the totals.
        assertTrue(banks.none { it.isDebitCard })
        val cards = visible.filter { it.kind == AccountKind.CARD && !it.isDebitCard }
        val refs = accounts.filter { it.kind == AccountKind.CARD && !it.isDebitCard }.map { CardDues.CardRef(it.bankName, it.last4) }
        val owed = cards.map { c ->
            CardDues.position(CardDues.rowsFor(statements, c.bankName, c.last4, refs), c.currentBalanceMinor, emptyList(), today, zone).owedMinor
        }
        // The visible card's edit applies; the hidden card is out of every total.
        assertEquals(listOf(650_000L), owed)
        assertNotNull(cards.singleOrNull())
        assertEquals(owed.sum(), 650_000L)
    }
}
