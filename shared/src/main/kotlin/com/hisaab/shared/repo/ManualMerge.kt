package com.hisaab.shared.repo

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.hisaab.parser.extract.GenericPhrases
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.HisaabDatabase
import com.hisaab.shared.db.TransactionEntity
import javax.inject.Inject

/** What a manual merge did, enough to put things back: the rows as they were and which messages moved. */
data class MergeResult(
    val keptId: Long,
    val keptBefore: TransactionEntity,
    val removed: List<RemovedTransaction>,
)

/** A transaction folded into another, and the ids of the source messages that moved with it. */
data class RemovedTransaction(val entity: TransactionEntity, val sourceIds: List<Long>)

/**
 * The user's own merge of transactions the dedup layer missed. The best record is kept, the others'
 * messages are re-linked to it (they stay visible and can be split out again), and the others are deleted
 * without being added to the deleted-messages list. All in one transaction.
 */
class ManualMerge @Inject constructor(private val db: HisaabDatabase) {
    private val txDao = db.transactions()
    private val sourceDao = db.sources()

    /** Merges [ids] (at least two) into the best of them. Null when fewer than two exist. */
    suspend fun merge(ids: Collection<Long>): MergeResult? {
        var result: MergeResult? = null
        db.useWriterConnection {
            it.immediateTransaction {
                val rows = ids.distinct().mapNotNull { id -> txDao.getById(id) }
                if (rows.size < 2) return@immediateTransaction
                val ranked = rows.sortedWith(BEST)
                val keep = ranked.first()
                val others = ranked.drop(1)
                var merged = keep
                for (o in others) merged = fold(merged, o)
                if (merged.duplicateOfId in others.map { o -> o.id }) merged = merged.copy(needsReview = false, duplicateOfId = null, reviewReason = null)
                val removed = others.map { o ->
                    val sourceIds = sourceDao.forTransaction(o.id).map { s -> s.id }
                    sourceDao.reassign(o.id, keep.id)
                    txDao.delete(o.id)
                    RemovedTransaction(o, sourceIds)
                }
                txDao.update(merged)
                result = MergeResult(keep.id, keep, removed)
            }
        }
        return result
    }

    /** Undoes [merge]: restores the kept row and re-creates the others with their messages. Returns true when done. */
    suspend fun undo(result: MergeResult): Boolean {
        var ok = false
        db.useWriterConnection {
            it.immediateTransaction {
                if (txDao.getById(result.keptId) == null) return@immediateTransaction
                txDao.update(result.keptBefore)
                for (r in result.removed) {
                    val id = txDao.insert(r.entity.copy(id = 0))
                    if (id == -1L) continue
                    r.sourceIds.forEach { sid -> sourceDao.move(sid, id) }
                }
                ok = true
            }
        }
        return ok
    }

    /** Fills the gaps of [a] from [b]; where only [b] knows the paise, an explicit time or a category, it wins. */
    private fun fold(a: TransactionEntity, b: TransactionEntity): TransactionEntity {
        val base = a.mergedWith(b)
        val paise = a.amountMinor % 100 == 0L && b.amountMinor % 100 != 0L && kotlin.math.abs(a.amountMinor - b.amountMinor) < 100
        val bankFromB = a.accountId == null && b.accountId != null
        return base.copy(
            amountMinor = if (paise) b.amountMinor else a.amountMinor,
            inrMinor = if (paise) b.inrMinor ?: a.inrMinor else a.inrMinor,
            bankName = if (bankFromB) b.bankName else a.bankName,
            accountKind = if (bankFromB) b.accountKind else a.accountKind,
            timestamp = if (!a.hasExplicitTime && b.hasExplicitTime) b.timestamp else a.timestamp,
            hasExplicitTime = a.hasExplicitTime || b.hasExplicitTime,
            category = if (a.category == Category.OTHER && b.category != Category.OTHER) b.category else a.category,
            subcategory = a.subcategory ?: b.subcategory,
            customCategoryId = a.customCategoryId ?: b.customCategoryId,
        )
    }

    private companion object {
        /** Best first: a real merchant, an explicit time, a reference, an account, then the earliest. */
        val BEST: Comparator<TransactionEntity> = compareByDescending<TransactionEntity> { it.merchant != null && !GenericPhrases.isGeneric(it.merchant) }
            .thenByDescending { it.hasExplicitTime }
            .thenByDescending { it.referenceNumber != null }
            .thenByDescending { it.accountId != null || it.accountLast4 != null }
            .thenBy { it.createdAt }
            .thenBy { it.id }
    }
}
