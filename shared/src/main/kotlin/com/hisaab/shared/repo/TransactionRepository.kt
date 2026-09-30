package com.hisaab.shared.repo

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.hisaab.parser.dedup.DedupDecision
import com.hisaab.parser.dedup.DedupLookup
import com.hisaab.parser.dedup.DuplicateMatcher
import com.hisaab.parser.dedup.StoredTransaction
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.db.AccountEntity
import com.hisaab.shared.db.HisaabDatabase
import com.hisaab.shared.db.ProcessedEmailEntity
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.db.TransactionSourceEntity
import javax.inject.Inject
import javax.inject.Singleton

/** A parsed message on its way into the database. [sourceMessageId] is unique per source. */
data class IncomingMessage(
    val parsed: ParsedTransaction,
    val sourceMessageId: String,
    val rawText: String?,
    /** SMS, EMAIL or CSV. Defaults to the parser's source. */
    val sourceName: String = parsed.source.name,
)

enum class IngestOutcome { INSERTED, MERGED, FLAGGED_FOR_REVIEW, ALREADY_PROCESSED }

data class IngestReport(val inserted: Int, val merged: Int, val flagged: Int, val skipped: Int) {
    val total get() = inserted + merged + flagged + skipped
    operator fun plus(o: IngestReport) = IngestReport(inserted + o.inserted, merged + o.merged, flagged + o.flagged, skipped + o.skipped)

    companion object {
        val EMPTY = IngestReport(0, 0, 0, 0)
    }
}

/**
 * The single write path for transactions. Every batch runs in one immediate (write) transaction:
 * dedup lookups, the insert or merge, source bookkeeping, and account balances commit together,
 * so two workers ingesting the same payment at once can never both insert it.
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val db: HisaabDatabase,
    private val registry: ParserRegistry,
) {
    private val txDao = db.transactions()
    private val sourceDao = db.sources()
    private val accountDao = db.accounts()
    private val processedDao = db.processedEmails()

    private val lookup = object : DedupLookup {
        override suspend fun byReference(referenceNumber: String) = stored(txDao.findByReference(referenceNumber))
        override suspend fun byHash(transactionHash: String) = txDao.findByHash(transactionHash)?.let { stored(listOf(it)).first() }
        override suspend fun potentialDuplicates(amountMinor: Long, from: Long, to: Long) = stored(txDao.findPotentialDuplicates(amountMinor, from, to))
    }

    private suspend fun stored(rows: List<TransactionEntity>): List<StoredTransaction> {
        if (rows.isEmpty()) return emptyList()
        val sources = sourceDao.sourcesOf(rows.map { it.id }).groupBy({ it.transactionId }, { it.source })
        return rows.map {
            StoredTransaction(it.id, it.amountMinor, it.type, it.accountLast4, it.merchant, it.referenceNumber, it.timestamp, sources[it.id].orEmpty().toSet())
        }
    }

    suspend fun ingest(message: IncomingMessage): IngestOutcome {
        var outcome = IngestOutcome.ALREADY_PROCESSED
        write { outcome = ingestInTransaction(message, System.currentTimeMillis()) }
        return outcome
    }

    /** Ingests a batch in one database transaction. [processedEmails] are recorded in the same transaction. */
    suspend fun ingestBatch(messages: List<IncomingMessage>, processedEmails: List<ProcessedEmailEntity> = emptyList()): IngestReport {
        if (messages.isEmpty() && processedEmails.isEmpty()) return IngestReport.EMPTY
        var report = IngestReport.EMPTY
        write {
            val now = System.currentTimeMillis()
            var inserted = 0; var merged = 0; var flagged = 0; var skipped = 0
            for (m in messages) {
                when (ingestInTransaction(m, now)) {
                    IngestOutcome.INSERTED -> inserted++
                    IngestOutcome.MERGED -> merged++
                    IngestOutcome.FLAGGED_FOR_REVIEW -> flagged++
                    IngestOutcome.ALREADY_PROCESSED -> skipped++
                }
            }
            if (processedEmails.isNotEmpty()) processedDao.insertAll(processedEmails)
            report = IngestReport(inserted, merged, flagged, skipped)
        }
        return report
    }

    private suspend fun ingestInTransaction(m: IncomingMessage, now: Long): IngestOutcome {
        val tx = m.parsed
        if (sourceDao.exists(m.sourceName, m.sourceMessageId)) return IngestOutcome.ALREADY_PROCESSED

        val decision = DuplicateMatcher.decide(tx, lookup)
        val accountId = ensureAccount(tx)
        val txId: Long
        val outcome: IngestOutcome
        when (decision) {
            is DedupDecision.Duplicate -> {
                val existing = txDao.getById(decision.existingId)!!
                txDao.update(existing.mergedWith(tx).copy(accountId = existing.accountId ?: accountId))
                txId = existing.id
                outcome = IngestOutcome.MERGED
            }
            is DedupDecision.PossibleDuplicate -> {
                // The flagged copy needs its own unique hash until the user decides.
                val entity = tx.toEntity(accountId, now, hash = "${tx.transactionHash}#${m.sourceName}:${m.sourceMessageId}")
                    .copy(needsReview = true, duplicateOfId = decision.existingId, reviewReason = decision.reason)
                txId = txDao.insert(entity)
                outcome = IngestOutcome.FLAGGED_FOR_REVIEW
            }
            DedupDecision.New -> {
                val id = txDao.insert(tx.toEntity(accountId, now))
                if (id == -1L) {
                    // Unique hash hit: the matcher and the index disagree only under a race. Merge.
                    val existing = txDao.findByHash(tx.transactionHash)!!
                    txDao.update(existing.mergedWith(tx))
                    txId = existing.id
                    outcome = IngestOutcome.MERGED
                } else {
                    txId = id
                    outcome = IngestOutcome.INSERTED
                }
            }
        }
        sourceDao.insert(
            TransactionSourceEntity(
                transactionId = txId, source = m.sourceName, sourceMessageId = m.sourceMessageId, sender = tx.sender,
                parsedHash = tx.transactionHash, rawText = m.rawText?.take(RAW_TEXT_CAP), receivedAt = tx.messageTimestamp,
            ),
        )
        if (accountId != null) {
            val balance = tx.balanceMinor.takeIf { tx.accountKind == AccountKind.ACCOUNT }
            if (balance != null || tx.availableLimitMinor != null) accountDao.updateBalance(accountId, balance, tx.availableLimitMinor, tx.transactionTime)
        }
        return outcome
    }

    private suspend fun ensureAccount(tx: ParsedTransaction): Long? {
        val last4 = tx.accountLast4 ?: return null
        accountDao.find(tx.bankName, last4)?.let { return it.id }
        val id = accountDao.insert(AccountEntity(bankName = tx.bankName, last4 = last4, kind = tx.accountKind, createdAt = System.currentTimeMillis()))
        return if (id != -1L) id else accountDao.find(tx.bankName, last4)?.id
    }

    // Review actions.

    /** The user confirms the flagged transaction duplicates its candidate: fold it in and delete it. */
    suspend fun mergeFlagged(flaggedId: Long) = write {
        val flagged = txDao.getById(flaggedId) ?: return@write
        val target = flagged.duplicateOfId?.let { txDao.getById(it) }
        if (target == null) {
            txDao.update(flagged.copy(needsReview = false, duplicateOfId = null, reviewReason = null))
            return@write
        }
        txDao.update(target.mergedWith(flagged))
        sourceDao.reassign(flaggedId, target.id)
        txDao.delete(flaggedId)
    }

    /** The user says they are two different transactions. */
    suspend fun keepSeparate(flaggedId: Long) = write {
        val flagged = txDao.getById(flaggedId) ?: return@write
        txDao.update(flagged.copy(needsReview = false, duplicateOfId = null, reviewReason = null))
    }

    /**
     * Undoes a wrong merge: re-parses one of the transaction's source messages into its own transaction.
     * Returns the new transaction id, or null when the source cannot be re-parsed.
     */
    suspend fun split(transactionId: Long, sourceId: Long): Long? {
        var result: Long? = null
        write {
            val src = sourceDao.getById(sourceId)?.takeIf { it.transactionId == transactionId } ?: return@write
            if (sourceDao.forTransaction(transactionId).size < 2) return@write
            val source = Source.entries.firstOrNull { it.name == src.source } ?: return@write
            val parsed = src.rawText?.let { registry.parse(it, src.sender, src.receivedAt, source) } ?: return@write
            val id = txDao.insert(parsed.toEntity(ensureAccount(parsed), System.currentTimeMillis(), hash = "${parsed.transactionHash}#split:${src.id}"))
            if (id == -1L) return@write
            sourceDao.move(src.id, id)
            result = id
        }
        return result
    }

    /**
     * Restores rows from a CSV export. A row whose hash already exists is skipped, so importing the
     * same file twice, or over the data it came from, adds nothing.
     */
    suspend fun importTransactions(rows: List<ParsedTransaction>): IngestReport {
        var inserted = 0
        var skipped = 0
        write {
            val now = System.currentTimeMillis()
            for (tx in rows) {
                if (txDao.findByHash(tx.transactionHash) != null || sourceDao.exists(CSV, tx.transactionHash)) { skipped++; continue }
                val id = txDao.insert(tx.toEntity(ensureAccount(tx), now))
                if (id == -1L) { skipped++; continue }
                sourceDao.insert(TransactionSourceEntity(transactionId = id, source = CSV, sourceMessageId = tx.transactionHash, sender = tx.sender, parsedHash = tx.transactionHash, rawText = null, receivedAt = now))
                inserted++
            }
        }
        return IngestReport(inserted, 0, 0, skipped)
    }

    suspend fun recordProcessedEmails(rows: List<ProcessedEmailEntity>) {
        if (rows.isNotEmpty()) processedDao.insertAll(rows)
    }

    suspend fun alreadyProcessedEmails(ids: List<String>): Set<String> =
        ids.chunked(SQLITE_MAX_ARGS).flatMap { processedDao.existing(it) }.toSet()

    private suspend fun write(block: suspend () -> Unit) {
        db.useWriterConnection { it.immediateTransaction { block() } }
    }

    private companion object {
        const val RAW_TEXT_CAP = 4000
        const val CSV = "CSV"
        const val SQLITE_MAX_ARGS = 900
    }
}
