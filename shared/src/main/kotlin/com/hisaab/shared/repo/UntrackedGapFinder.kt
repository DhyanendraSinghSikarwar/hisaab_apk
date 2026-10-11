package com.hisaab.shared.repo

import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.HisaabDatabase
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.db.TransferLegs
import com.hisaab.shared.insight.BalanceGaps
import com.hisaab.shared.insight.GapCandidates
import com.hisaab.shared.insight.UntrackedGap
import javax.inject.Inject
import javax.inject.Singleton

/** Loads what [BalanceGaps] needs from the database. Read only; dismissals are the app's (DataStore). */
@Singleton
class UntrackedGapFinder @Inject constructor(private val db: HisaabDatabase) {
    private val day = 24L * 60 * 60 * 1000

    /** Open gaps, newest first, leaving out the keys in [dismissed]. */
    suspend fun gaps(dismissed: Set<String>, now: Long = System.currentTimeMillis()): List<UntrackedGap> {
        val txs = db.transactions().since(now - HISTORY_DAYS * day)
        val sources = db.sources()
        // A card bill paid from an account (noted when it was recognised) left that account.
        val billOut = txs.filter { it.type == TransactionType.TRANSFER && it.note?.startsWith("Credit card bill") == true }.associate { it.id to -1 }
        val dir = billOut + sources.idsWithPrefix(TransferLegs.OUT_PREFIX).associateWith { -1 } + sources.idsWithPrefix(TransferLegs.IN_PREFIX).associateWith { 1 }
        return BalanceGaps.detect(db.accounts().all(), txs, dir, dismissed, now).sortedByDescending { it.toTime }
    }

    /** Account-less rows that could be [gap]'s missing money (see [GapCandidates]). */
    suspend fun candidates(gap: UntrackedGap): List<List<TransactionEntity>> =
        GapCandidates.find(gap, db.transactions().since(gap.fromTime).filter { it.accountId == null })

    private companion object {
        /** Gaps are looked for over 90 days, plus the balance each starts from. */
        const val HISTORY_DAYS = 240L
    }
}
