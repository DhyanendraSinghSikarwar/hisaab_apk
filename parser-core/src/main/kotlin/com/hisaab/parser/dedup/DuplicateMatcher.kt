package com.hisaab.parser.dedup

import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.TransactionType
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

    suspend fun decide(tx: ParsedTransaction, lookup: DedupLookup): DedupDecision {
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
            tx.amountMinor, tx.transactionTime - REVIEW_WINDOW_MILLIS, tx.transactionTime + REVIEW_WINDOW_MILLIS,
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

            when {
                close && crossSource && (sameAccount || sameMerchant) -> return DedupDecision.Duplicate(c.id, MatchReason.FUZZY)
                review != null -> Unit
                close && (sameAccount || sameMerchant) -> review = c to "Same amount and ${if (sameAccount) "account" else "merchant"} within 30 minutes"
                close && crossSource -> review = c to "Same amount from SMS and email within 30 minutes"
                crossSource && sameAccount -> review = c to "Same amount and account on the same day, from SMS and email"
            }
        }
        return review?.let { DedupDecision.PossibleDuplicate(it.first.id, it.second) } ?: DedupDecision.New
    }

    /** "123" (ICICI prints 3 digits) matches "0123". */
    fun last4Match(a: String, b: String): Boolean = a == b || a.endsWith(b) || b.endsWith(a)
}
