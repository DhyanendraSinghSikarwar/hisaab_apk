package com.hisaab.parser.dedup

import com.hisaab.parser.extract.GenericPhrases
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** What the dedup layer knows about a stored transaction. [sources] holds the source names ("SMS", "EMAIL", ...) already attached. */
data class StoredTransaction(
    val id: Long,
    val amountMinor: Long,
    val type: TransactionType,
    val accountLast4: String?,
    val merchant: String?,
    val referenceNumber: String?,
    val transactionTime: Long,
    val sources: Set<String>,
)

enum class MatchReason { REFERENCE, HASH, FUZZY }

sealed interface DedupDecision {
    /** Same transaction: merge the incoming message into [existingId]. */
    data class Duplicate(val existingId: Long, val reason: MatchReason) : DedupDecision

    /** Probably the same transaction but not certain: store it flagged for the user to merge or keep. */
    data class PossibleDuplicate(val existingId: Long, val reason: String) : DedupDecision

    data object New : DedupDecision
}

/** The storage queries the matcher needs. The Room implementation lives in :shared. */
interface DedupLookup {
    suspend fun byReference(referenceNumber: String): List<StoredTransaction>
    suspend fun byHash(transactionHash: String): StoredTransaction?
    /** Transactions of exactly [amountMinor] with a time in [from, to]. */
    suspend fun potentialDuplicates(amountMinor: Long, from: Long, to: Long): List<StoredTransaction>

    /**
     * Transactions with an amount in [minMinor]..[maxMinor] and a time in [from, to]. Used to match a
     * rounded payment-app amount; override with a range query, the default asks once per paisa value.
     */
    suspend fun potentialDuplicatesInRange(minMinor: Long, maxMinor: Long, from: Long, to: Long): List<StoredTransaction> =
        (minMinor..maxMinor).flatMap { potentialDuplicates(it, from, to) }
}

/**
 * Decides whether a parsed message duplicates a stored transaction, in the order the spec fixes:
 * 1. same reference number (and amount, direction);
 * 2. same transactionHash (from the same source without a reference, only a possible duplicate);
 * 3. fuzzy: same amount and direction, and same account or same merchant, within ±30 minutes.
 *
 * Fuzzy matches merge automatically only across sources (an SMS and an email). Two SMS for the same
 * amount on the same card within half an hour can be two real purchases, so those go to review.
 * A cross-source match on the same account later the same day is also sent to review, which covers
 * a bank email that arrives hours late.
 */
object DuplicateMatcher {
    const val WINDOW_MILLIS: Long = 30 * 60 * 1000L
    const val REVIEW_WINDOW_MILLIS: Long = 12 * 60 * 60 * 1000L
    const val STATEMENT_WINDOW_MILLIS: Long = 36 * 60 * 60 * 1000L
    private val LOOSE_SOURCES = setOf("APP", "SCREENSHOT", "MANUAL")
    private const val STATEMENT = "STATEMENT"

    const val ROUNDED_WINDOW_MILLIS: Long = 4 * 60 * 60 * 1000L

    suspend fun decide(tx: ParsedTransaction, lookup: DedupLookup, zone: ZoneId = ZoneId.systemDefault()): DedupDecision {
        tx.referenceNumber?.let { ref ->
            lookup.byReference(ref).firstOrNull { it.amountMinor == tx.amountMinor && it.type.direction == tx.type.direction }
                ?.let { return DedupDecision.Duplicate(it.id, MatchReason.REFERENCE) }
        }
        lookup.byHash(tx.transactionHash)?.let { h ->
            // Without a reference the hash is only amount + account + day, so two purchases of the same
            // amount on one card in a day collide. Across sources that is the same transaction; from the
            // same source it may be two, so the user decides. (Re-reading one message never gets here:
            // the source message id is checked first.)
            if (tx.referenceNumber == null && tx.source.name in h.sources) {
                return DedupDecision.PossibleDuplicate(h.id, "Same amount, account and day, no reference number")
            }
            return DedupDecision.Duplicate(h.id, MatchReason.HASH)
        }

        val candidates = lookup.potentialDuplicates(
            tx.amountMinor, tx.transactionTime - STATEMENT_WINDOW_MILLIS, tx.transactionTime + STATEMENT_WINDOW_MILLIS,
        ).filter { it.type.direction == tx.type.direction }
            .sortedBy { abs(it.transactionTime - tx.transactionTime) }

        var review: Pair<StoredTransaction, String>? = null
        for (c in candidates) {
            // Two different references are two different transactions, however alike they look.
            if (c.referenceNumber != null && tx.referenceNumber != null && c.referenceNumber != tx.referenceNumber) continue
            val bothHaveAccount = c.accountLast4 != null && tx.accountLast4 != null
            val sameAccount = bothHaveAccount && last4Match(c.accountLast4!!, tx.accountLast4!!)
            if (bothHaveAccount && !sameAccount) continue

            val sameMerchant = c.merchant != null && tx.merchant != null && c.merchant.equals(tx.merchant, ignoreCase = true)
            val crossSource = tx.source.name !in c.sources
            val close = abs(c.transactionTime - tx.transactionTime) <= WINDOW_MILLIS

            // A payment-app notification, a screenshot or a typed entry rarely names the account; when it meets
            // a bank message for the same amount within the window, it is the same payment.
            val loose = tx.source.name in LOOSE_SOURCES || c.sources.any { it in LOOSE_SOURCES }
            val accountless = c.accountLast4 == null || tx.accountLast4 == null
            val gap = abs(c.transactionTime - tx.transactionTime)

            // A statement row has only a date (stored at noon), so it matches the SMS or email for the same
            // amount and account within a day and a half either side.
            val statement = tx.source.name == STATEMENT || STATEMENT in c.sources
            if (statement) {
                if (crossSource && gap <= STATEMENT_WINDOW_MILLIS && (sameAccount || accountless)) return DedupDecision.Duplicate(c.id, MatchReason.FUZZY)
                continue
            }
            if (gap > REVIEW_WINDOW_MILLIS) continue

            when {
                close && crossSource && (sameAccount || sameMerchant) -> return DedupDecision.Duplicate(c.id, MatchReason.FUZZY)
                close && crossSource && loose && accountless -> return DedupDecision.Duplicate(c.id, MatchReason.FUZZY)
                review != null -> Unit
                close && (sameAccount || sameMerchant) -> review = c to "Same amount and ${if (sameAccount) "account" else "merchant"} within 30 minutes"
                close && crossSource -> review = c to "Same amount from SMS and email within 30 minutes"
                crossSource && sameAccount -> review = c to "Same amount and account on the same day, from SMS and email"
            }
        }
        roundedAppMatch(tx, lookup, zone)?.let { return DedupDecision.Duplicate(it.id, MatchReason.FUZZY) }
        return review?.let { DedupDecision.PossibleDuplicate(it.first.id, it.second) } ?: DedupDecision.New
    }

    /**
     * A payment-app notification often shows a rounded amount ("₹144") where the bank message has paise
     * ("144.93"), and carries no reliable time. It matches a bank/email/statement record of the same
     * direction when the amounts agree after dropping paise, it is the same day (or within 4 hours),
     * and the merchants do not conflict. Nothing else is loosened.
     */
    private suspend fun roundedAppMatch(tx: ParsedTransaction, lookup: DedupLookup, zone: ZoneId): StoredTransaction? {
        val incomingApp = tx.source.name == "APP"
        val whole = tx.amountMinor % 100 == 0L
        val range = when {
            incomingApp && whole -> (tx.amountMinor + 1)..(tx.amountMinor + 99)
            !incomingApp && !whole -> (tx.amountMinor - tx.amountMinor % 100).let { it..it }
            else -> return null
        }
        val from = tx.transactionTime - STATEMENT_WINDOW_MILLIS
        val to = tx.transactionTime + STATEMENT_WINDOW_MILLIS
        val day = Instant.ofEpochMilli(tx.transactionTime).atZone(zone).toLocalDate()
        return lookup.potentialDuplicatesInRange(range.first, range.last, from, to)
            .filter { it.type.direction == tx.type.direction && tx.source.name !in it.sources }
            .filter { if (incomingApp) it.sources.any { s -> s != "APP" } else it.sources.all { s -> s == "APP" } }
            .filter { c ->
                val sameDay = Instant.ofEpochMilli(c.transactionTime).atZone(zone).toLocalDate() == day
                sameDay || abs(c.transactionTime - tx.transactionTime) <= ROUNDED_WINDOW_MILLIS
            }
            .filter { c ->
                (c.accountLast4 == null || tx.accountLast4 == null || last4Match(c.accountLast4, tx.accountLast4!!)) &&
                    (c.referenceNumber == null || tx.referenceNumber == null || c.referenceNumber == tx.referenceNumber) &&
                    merchantsAgree(c.merchant, tx.merchant)
            }
            .minByOrNull { abs(it.transactionTime - tx.transactionTime) }
    }

    /** True unless both name a real merchant and the names differ (one containing the other counts as the same). */
    private fun merchantsAgree(a: String?, b: String?): Boolean {
        if (a == null || b == null || GenericPhrases.isGeneric(a) || GenericPhrases.isGeneric(b)) return true
        val x = a.lowercase().filter { it.isLetterOrDigit() }
        val y = b.lowercase().filter { it.isLetterOrDigit() }
        return x.isEmpty() || y.isEmpty() || x.contains(y) || y.contains(x)
    }

    /** "123" (ICICI prints 3 digits) matches "0123". */
    fun last4Match(a: String, b: String): Boolean = a == b || a.endsWith(b) || b.endsWith(a)
}
