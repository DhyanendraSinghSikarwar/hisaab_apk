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
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.AccountEntity
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.DeletedMessageEntity
import com.hisaab.shared.db.MerchantRuleEntity
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
    /** An email's subject line. */
    val subject: String? = null,
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
    private val deletedDao = db.deletedMessages()
    private val rules = db.merchantRules()

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
        // A category the user chose for this merchant before wins over the parser's guess.
        val tx = MerchantRuleEntity.keyOf(m.parsed.merchant, m.parsed.upiId)
            ?.takeIf { m.parsed.type != TransactionType.TRANSFER }
            ?.let { rules.get(it) }
            ?.let { m.parsed.copy(category = it.category) } ?: m.parsed
        if (sourceDao.exists(m.sourceName, m.sourceMessageId)) return IngestOutcome.ALREADY_PROCESSED
        if (deletedDao.exists(m.sourceName, m.sourceMessageId)) return IngestOutcome.ALREADY_PROCESSED

        val decision = DuplicateMatcher.decide(tx, lookup)
        val accountId = ensureAccount(tx, m.rawText)
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
        if (outcome == IngestOutcome.INSERTED) pairTransfers(txId)
        sourceDao.insert(
            TransactionSourceEntity(
                transactionId = txId, source = m.sourceName, sourceMessageId = m.sourceMessageId, sender = tx.sender,
                parsedHash = tx.transactionHash, rawText = m.rawText?.take(RAW_TEXT_CAP), receivedAt = tx.messageTimestamp,
                subject = m.subject,
            ),
        )
        if (accountId != null) {
            val balance = tx.balanceMinor.takeIf { tx.accountKind == AccountKind.ACCOUNT || tx.isDebitCard }
            if (balance != null || tx.availableLimitMinor != null) accountDao.updateBalance(accountId, balance, tx.availableLimitMinor, tx.transactionTime)
            // A debit card's SMS states its bank account's balance.
            if (tx.isDebitCard && balance != null) {
                accountDao.getById(accountId)?.linkedAccountId?.let { accountDao.updateBalance(it, balance, null, tx.transactionTime) }
            }
        }
        return outcome
    }

    private suspend fun ensureAccount(tx: ParsedTransaction, rawText: String? = null): Long? {
        val last4 = tx.accountLast4 ?: return null
        accountDao.find(tx.bankName, last4)?.let { existing ->
            if (existing.accountType == null && tx.isDebitCard) accountDao.setType(existing.id, AccountType.DEBIT_CARD, existing.cardNetwork)
            return existing.id
        }
        val guess = AccountGuess.of(tx, rawText)
        val id = accountDao.insert(
            AccountEntity(
                bankName = tx.bankName, last4 = last4, kind = tx.accountKind, createdAt = System.currentTimeMillis(),
                accountType = guess.type, cardNetwork = guess.network,
            ),
        )
        return if (id != -1L) id else accountDao.find(tx.bankName, last4)?.id
    }

    /**
     * Money that only moved between the user's own accounts is neither spending nor income:
     *  - a debit on one account and a credit of the same amount on another of theirs within 2 hours (self transfer);
     *  - a bank debit and a card's "payment received" for the same amount within 3 days (credit card bill).
     * Both sides become TRANSFER, so totals don't count the money twice.
     */
    private suspend fun pairTransfers(id: Long) {
        val t = txDao.getById(id) ?: return
        val accountId = t.accountId ?: return
        val hour = 60 * 60 * 1000L
        when {
            t.type == TransactionType.DEBIT || t.type == TransactionType.CREDIT -> {
                val want = if (t.type == TransactionType.DEBIT) TransactionType.CREDIT else TransactionType.DEBIT
                val mate = txDao.findPotentialDuplicates(t.amountMinor, t.timestamp - 2 * hour, t.timestamp + 2 * hour)
                    .firstOrNull { it.type == want && it.accountId != null && it.accountId != accountId && it.accountKind == AccountKind.ACCOUNT && t.accountKind == AccountKind.ACCOUNT }
                if (mate != null) {
                    markTransfer(t, "Self transfer between your accounts")
                    markTransfer(mate, "Self transfer between your accounts")
                    return
                }
                // A bank debit that pays a card bill already recorded on the card side.
                if (t.type == TransactionType.DEBIT && t.accountKind == AccountKind.ACCOUNT) {
                    txDao.findPotentialDuplicates(t.amountMinor, t.timestamp - 72 * hour, t.timestamp + 72 * hour)
                        .firstOrNull { it.type == TransactionType.TRANSFER && it.accountKind == AccountKind.CARD }
                        ?.let { markTransfer(t, "Credit card bill payment") }
                }
            }
            t.type == TransactionType.TRANSFER && t.accountKind == AccountKind.CARD -> {
                txDao.findPotentialDuplicates(t.amountMinor, t.timestamp - 72 * hour, t.timestamp + 72 * hour)
                    .firstOrNull { it.type == TransactionType.DEBIT && it.accountKind == AccountKind.ACCOUNT }
                    ?.let { markTransfer(it, "Credit card bill payment") }
            }
        }
    }

    private suspend fun markTransfer(t: TransactionEntity, note: String) {
        txDao.update(t.copy(type = TransactionType.TRANSFER, category = Category.TRANSFER, note = t.note ?: note))
    }

    /** A transaction typed in, or read from a screenshot. Goes through dedup like any message. */
    suspend fun addManual(tx: ParsedTransaction, rawText: String?): IngestOutcome =
        ingest(IncomingMessage(tx, "${tx.source.name.lowercase()}:${java.util.UUID.randomUUID()}", rawText, tx.source.name))

    /** Deletes transactions, and remembers their messages so a rescan does not add them back. */
    suspend fun deleteTransactions(ids: List<Long>) {
        if (ids.isEmpty()) return
        write {
            val now = System.currentTimeMillis()
            for (chunk in ids.chunked(SQLITE_MAX_ARGS)) {
                deletedDao.insertAll(sourceDao.forTransactions(chunk).map { DeletedMessageEntity(it.source, it.sourceMessageId, now) })
                txDao.deleteAll(chunk)
            }
        }
    }

    /**
     * Sets the category and remembers it for each merchant involved, so their future transactions (and past
     * ones still uncategorised) get it too.
     */
    suspend fun setCategory(ids: List<Long>, category: Category) {
        for (chunk in ids.chunked(SQLITE_MAX_ARGS)) txDao.setCategoryFor(chunk, category.name)
        val now = System.currentTimeMillis()
        val keys = ids.mapNotNull { txDao.getById(it) }.filter { it.type != TransactionType.TRANSFER }
            .mapNotNull { MerchantRuleEntity.keyOf(it.merchant, it.upiId) }.distinct()
        for (k in keys) {
            rules.upsert(MerchantRuleEntity(k, category, now))
            rules.applyToUncategorised(k, category.name)
        }
    }

    /**
     * What a statement says about the account: a card's credit limit and amount due give its available limit;
     * a bank statement's last running balance is the account balance. Only a newer figure replaces an older one.
     */
    suspend fun applyStatementToAccount(
        bankName: String, last4: String, kind: AccountKind, closingBalance: Long?, creditLimit: Long?, totalDue: Long?, at: Long,
        available: Long? = null,
    ) {
        val id = accountDao.find(bankName, last4)?.id
            ?: accountDao.insert(AccountEntity(bankName = bankName, last4 = last4, kind = kind, createdAt = System.currentTimeMillis())).takeIf { it != -1L }
            ?: return
        when (kind) {
            // The available limit printed on the statement beats working it out from the limit and the amount due.
            AccountKind.CARD -> (available ?: creditLimit?.let { (it - (totalDue ?: 0)).coerceAtLeast(0) })
                ?.let { accountDao.setLimitFromStatement(id, it, at) }
            AccountKind.ACCOUNT -> if (closingBalance != null) accountDao.updateBalance(id, closingBalance, null, at)
        }
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
        const val RAW_TEXT_CAP = 20_000
        const val CSV = "CSV"
        const val SQLITE_MAX_ARGS = 900
    }
}
