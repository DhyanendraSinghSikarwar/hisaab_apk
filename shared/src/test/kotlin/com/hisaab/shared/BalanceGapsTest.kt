package com.hisaab.shared

import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountEntity
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.BalanceGaps
import com.hisaab.shared.insight.GapCandidates
import com.hisaab.shared.insight.GapChoice
import com.hisaab.shared.insight.GapPrefill
import com.hisaab.shared.insight.UntrackedGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Money that moved with no message: found from stated balances, explained by rows the user adds or assigns. */
class BalanceGapsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day
    private val sbi = acc(1, "SBI", "2971", AccountKind.ACCOUNT)
    private val axis = acc(2, "Axis", "7788", AccountKind.CARD)
    private var nextId = 1L

    private fun acc(id: Long, bank: String, last4: String, kind: AccountKind, manualAt: Long? = null) = AccountEntity(
        id = id, bankName = bank, last4 = last4, kind = kind, createdAt = 0, manualBalanceAt = manualAt,
    )

    private fun rupees(v: Long) = v * 100

    private fun tx(
        type: TransactionType, amount: Long, at: Long, account: Long? = 1, balance: Long? = null, limit: Long? = null,
        merchant: String? = null, kind: AccountKind = AccountKind.ACCOUNT, review: Boolean = false,
    ) = TransactionEntity(
        id = nextId++, amountMinor = rupees(amount), currency = "INR", type = type, bankName = "SBI", accountLast4 = null, accountKind = kind,
        accountId = account, merchant = merchant, upiId = null, referenceNumber = null, channel = Channel.UPI,
        balanceMinor = balance?.let(::rupees), availableLimitMinor = limit?.let(::rupees), timestamp = at, hasExplicitTime = true,
        category = Category.OTHER, transactionHash = "h$nextId", confidence = 1f, needsReview = review, createdAt = at,
        inrMinor = rupees(amount),
    )

    private fun detect(
        vararg txs: TransactionEntity, accounts: List<AccountEntity> = listOf(sbi), dir: Map<Long, Int> = emptyMap(), dismissed: Set<String> = emptySet(),
    ) = BalanceGaps.detect(accounts, txs.toList(), dir, dismissed, now)

    private val t0 = now - 10 * day

    @Test
    fun moneyLeftWithoutAMessage() {
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val b = tx(TransactionType.DEBIT, 100, t0 + day, balance = 45_900) // expected 49,900
        val g = detect(a, b).single()
        assertEquals(-rupees(4_000), g.deltaMinor)
        assertEquals(1L, g.accountId)
        assertEquals(t0, g.fromTime)
        assertEquals(t0 + day, g.toTime)
        assertTrue(g.moneyLeft)
    }

    @Test
    fun matchingBalancesHaveNoGap() {
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val b = tx(TransactionType.CREDIT, 500, t0 + day, balance = 50_500)
        assertTrue(detect(a, b).isEmpty())
    }

    @Test
    fun twoGapsAreTwoCards() {
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val b = tx(TransactionType.DEBIT, 100, t0 + day, balance = 48_900) // 1,000 missing
        val c = tx(TransactionType.CREDIT, 100, t0 + 3 * day, balance = 50_000) // expected 49,000: 1,000 in
        assertEquals(listOf(-rupees(1_000), rupees(1_000)), detect(a, b, c).map { it.deltaMinor }.sorted())
    }

    @Test
    fun aTransferTheUserEnteredExplainsTheGap() {
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val leg = tx(TransactionType.TRANSFER, 4_000, t0 + day - 60_000, merchant = "To Axis ••7788")
        val b = tx(TransactionType.DEBIT, 100, t0 + day, balance = 45_900)
        assertEquals(1, detect(a, b).size)
        assertTrue(detect(a, leg, b, dir = mapOf(leg.id to -1)).isEmpty())
    }

    @Test
    fun aTransferOfUnknownDirectionNeverRaisesAGap() {
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val self = tx(TransactionType.TRANSFER, 4_000, t0 + day - 1000) // paired by the app, direction lost
        val b = tx(TransactionType.DEBIT, 100, t0 + day, balance = 45_900)
        assertTrue(detect(a, self, b).isEmpty())
    }

    @Test
    fun subRupeeNoiseIsIgnored() {
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val b = tx(TransactionType.DEBIT, 100, t0 + day).copy(balanceMinor = rupees(49_900) - 60)
        assertTrue(detect(a, b).isEmpty())
    }

    @Test
    fun smallShareOfABigWindowIsIgnored() {
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 5_000_000)
        val spend = tx(TransactionType.DEBIT, 1_000_000, t0 + day - 1)
        val b = tx(TransactionType.DEBIT, 100, t0 + day, balance = 3_999_797) // ₹103 off on ₹10 lakh moved
        assertTrue(detect(a, spend, b).isEmpty())
    }

    @Test
    fun dismissedGapDoesNotReturn() {
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val b = tx(TransactionType.DEBIT, 100, t0 + day, balance = 45_900)
        val g = detect(a, b).single()
        assertEquals("1|$t0|${t0 + day}", g.key)
        assertTrue(detect(a, b, dismissed = setOf(g.key)).isEmpty())
    }

    @Test
    fun noStatedBalanceNoGap() {
        assertTrue(detect(tx(TransactionType.DEBIT, 100, t0), tx(TransactionType.DEBIT, 200, t0 + day)).isEmpty())
        assertTrue(detect(tx(TransactionType.DEBIT, 100, t0, balance = 50_000)).isEmpty()) // the first balance only
    }

    @Test
    fun oldManualAndFlaggedCases() {
        val a = tx(TransactionType.DEBIT, 100, now - 200 * day, balance = 50_000)
        val b = tx(TransactionType.DEBIT, 100, now - 199 * day, balance = 40_000)
        assertTrue(detect(a, b).isEmpty()) // older than 90 days
        val c = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val d = tx(TransactionType.DEBIT, 100, t0 + day, balance = 45_900)
        assertTrue(detect(c, d, accounts = listOf(acc(1, "SBI", "2971", AccountKind.ACCOUNT, manualAt = t0 + day / 2))).isEmpty())
        // A flagged copy of a debit is not counted.
        val e = tx(TransactionType.DEBIT, 100, t0 + 1, review = true)
        assertEquals(-rupees(4_000), detect(c, e, d).single().deltaMinor)
    }

    @Test
    fun debitCardsBelongToTheirBank() {
        val card = acc(3, "SBI", "1111", AccountKind.CARD).copy(linkedAccountId = 1)
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val b = tx(TransactionType.DEBIT, 100, t0 + day, account = 3, balance = 49_900, kind = AccountKind.CARD)
        assertTrue(detect(a, b, accounts = listOf(sbi, card)).isEmpty())
    }

    @Test
    fun cardLimitRiseWithoutAMessageIsAPaymentThatCameIn() {
        val a = tx(TransactionType.DEBIT, 1_000, t0, account = 2, limit = 100_000, kind = AccountKind.CARD)
        val b = tx(TransactionType.DEBIT, 500, t0 + 2 * day, account = 2, limit = 119_500, kind = AccountKind.CARD) // expected 99,500
        val g = detect(a, b, accounts = listOf(axis)).single()
        assertTrue(g.card)
        assertEquals(rupees(20_000), g.deltaMinor)
    }

    @Test
    fun ownTransferMovesBothBalances() {
        // SBI down 4,000 and the Axis limit up 4,000, from one pair of hand-entered legs.
        val s1 = tx(TransactionType.DEBIT, 100, t0, account = 1, balance = 50_000)
        val s2 = tx(TransactionType.DEBIT, 100, t0 + day, account = 1, balance = 45_900)
        val c1 = tx(TransactionType.DEBIT, 1_000, t0, account = 2, limit = 100_000, kind = AccountKind.CARD)
        val c2 = tx(TransactionType.DEBIT, 500, t0 + day, account = 2, limit = 103_500, kind = AccountKind.CARD)
        val accounts = listOf(sbi, axis)
        assertEquals(2, detect(s1, s2, c1, c2, accounts = accounts).size)
        val out = tx(TransactionType.TRANSFER, 4_000, t0 + day - 60_000, account = 1)
        val into = tx(TransactionType.TRANSFER, 4_000, t0 + day - 60_000, account = 2, kind = AccountKind.CARD)
        assertTrue(detect(s1, s2, c1, c2, out, into, accounts = accounts, dir = mapOf(out.id to -1, into.id to 1)).isEmpty())
    }

    @Test
    fun prefillFollowsTheGap() {
        val out = UntrackedGap(1, t0, t0 + day, -rupees(4_000), 0, 0, card = false)
        val p = GapPrefill.of(out)
        assertEquals(rupees(4_000), p.amountMinor)
        assertTrue(p.outgoing)
        assertEquals(GapChoice.PAYMENT, p.choice)
        assertEquals(listOf(GapChoice.PAYMENT, GapChoice.TRANSFER), p.choices)
        assertTrue(p.time > t0 && p.time <= t0 + day)
        assertEquals(1L, p.fromAccountId)
        assertEquals(GapChoice.TRANSFER, GapPrefill.of(out, GapChoice.TRANSFER).choice)
        val into = UntrackedGap(1, t0, t0 + day, rupees(900), 0, 0, card = false)
        assertEquals(GapChoice.RECEIVED, GapPrefill.of(into, GapChoice.PAYMENT).choice) // a remembered choice that does not apply
        assertEquals(1L, GapPrefill.of(into).toAccountId)
        assertEquals(GapChoice.TRANSFER, GapPrefill.of(UntrackedGap(2, t0, t0 + day, rupees(900), 0, 0, card = true)).choice)
    }

    @Test
    fun externalPaymentFoundInAnAccountLessNotificationRow() {
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val b = tx(TransactionType.DEBIT, 100, t0 + day, balance = 49_650) // 250 missing
        val phonepe = tx(TransactionType.DEBIT, 250, t0 + day / 2, account = null, merchant = "Ravi Kumar")
        val other = tx(TransactionType.DEBIT, 80, t0 + day / 2, account = null, merchant = "Cafe")
        val g = detect(a, b, phonepe, other).single()
        assertEquals(-rupees(250), g.deltaMinor)
        assertEquals(listOf(listOf(phonepe)), GapCandidates.find(g, listOf(phonepe, other)))
        // Assigning it to the account (TransactionRepository.setAccount) explains the gap, counted once.
        assertTrue(detect(a, b, phonepe.copy(accountId = 1), other).isEmpty())
    }

    @Test
    fun twoRowsThatAddUpAreOffered() {
        val g = UntrackedGap(1, t0, t0 + day, -rupees(250), 0, 0, card = false)
        val x = tx(TransactionType.DEBIT, 100, t0 + 1000, account = null)
        val y = tx(TransactionType.DEBIT, 150, t0 + 2000, account = null)
        val credit = tx(TransactionType.CREDIT, 250, t0 + 3000, account = null)
        val outside = tx(TransactionType.DEBIT, 250, t0 - 1000, account = null)
        assertEquals(listOf(listOf(x, y)), GapCandidates.find(g, listOf(x, y, credit, outside)))
        assertTrue(GapCandidates.find(g.copy(deltaMinor = rupees(250)), listOf(x, y)).isEmpty())
    }

    @Test
    fun aTypedPaymentWithAPayeeClearsTheGap() {
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val b = tx(TransactionType.DEBIT, 100, t0 + day, balance = 49_650)
        val p = GapPrefill.of(detect(a, b).single())
        val typed = tx(TransactionType.DEBIT, 250, p.time, merchant = "Ravi Kumar")
        assertTrue(detect(a, typed, b).isEmpty())
    }

    @Test
    fun moneyReceivedClearsAPositiveGap() {
        val a = tx(TransactionType.DEBIT, 100, t0, balance = 50_000)
        val b = tx(TransactionType.DEBIT, 100, t0 + day, balance = 51_400) // 1,500 came in
        val g = detect(a, b).single()
        assertEquals(rupees(1_500), g.deltaMinor)
        val p = GapPrefill.of(g)
        assertEquals(GapChoice.RECEIVED, p.choice)
        assertTrue(detect(a, tx(TransactionType.CREDIT, 1_500, p.time, merchant = "Refund"), b).isEmpty())
    }
}
