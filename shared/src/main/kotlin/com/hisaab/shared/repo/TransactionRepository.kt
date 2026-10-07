package com.hisaab.shared.repo

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.hisaab.parser.bank.Lenders
import com.hisaab.parser.bank.LoanStatus
import com.hisaab.parser.bank.DepositInfo
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
import com.hisaab.parser.model.Channel
import com.hisaab.shared.db.AccountEntity
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.DeletedMessageEntity
import com.hisaab.shared.db.MerchantRuleEntity
import com.hisaab.shared.db.HisaabDatabase
import com.hisaab.shared.db.ProcessedEmailEntity
import com.hisaab.shared.db.RuleMatch
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

/** What [TransactionRepository.reparseStored] did: fixes found, transactions changed, accounts deleted. */
data class ReparseReport(val found: Int, val changed: Int, val accountsDeleted: Int)

private class ReparseFix(
    val id: Long,
    val oldLast4: String?,
    val oldType: TransactionType,
    /** The reading whose account to move to (its last4 may be null: no account), or null to keep the account. */
    val account: ParsedTransaction?,
    /** The reading whose type and category to take, or null to keep them. */
    val typed: ParsedTransaction?,
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
    private val balanceNotifier: BalanceUpdateNotifier = BalanceUpdateNotifier.NONE,
) {
    private val reconcileDao = db.reconcile()
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
        val gaps = ArrayList<BalanceGap>()
        write { outcome = ingestInTransaction(message, System.currentTimeMillis(), rules.manualRules(), gaps) }
        report(gaps)
        return outcome
    }

    /** Ingests a batch in one database transaction. [processedEmails] are recorded in the same transaction. */
    suspend fun ingestBatch(messages: List<IncomingMessage>, processedEmails: List<ProcessedEmailEntity> = emptyList()): IngestReport {
        if (messages.isEmpty() && processedEmails.isEmpty()) return IngestReport.EMPTY
        var report = IngestReport.EMPTY
        val gaps = ArrayList<BalanceGap>()
        write {
            gaps.clear()
            val now = System.currentTimeMillis()
            var inserted = 0; var merged = 0; var flagged = 0; var skipped = 0
            val manual = if (messages.isEmpty()) emptyList() else rules.manualRules()
            for (m in messages) {
                when (ingestInTransaction(m, now, manual, gaps)) {
                    IngestOutcome.INSERTED -> inserted++
                    IngestOutcome.MERGED -> merged++
                    IngestOutcome.FLAGGED_FOR_REVIEW -> flagged++
                    IngestOutcome.ALREADY_PROCESSED -> skipped++
                }
            }
            if (processedEmails.isNotEmpty()) processedDao.insertAll(processedEmails)
            report = IngestReport(inserted, merged, flagged, skipped)
        }
        report(gaps)
        return report
    }

    /** Tells the user about balances the transactions did not explain: the latest gap per account. */
    private suspend fun report(gaps: List<BalanceGap>) {
        if (gaps.isEmpty()) return
        runCatching { balanceNotifier.onBalancesUpdated(gaps.associateBy { it.key }.values.toList()) }
    }

    /**
     * The gap between [stated] (an account's balance, or with [card] a card's available limit, true at [at]) and the last
     * stated figure moved on by the transactions since; null when they agree, or nothing can be said. Deposits and loans
     * are left out: interest moves them without a transaction.
     */
    private suspend fun gapFor(accountId: Long, stated: Long, at: Long, card: Boolean): BalanceGap? {
        val a = accountDao.getById(accountId) ?: return null
        if (a.accountType?.liquid == false) return null
        val previous = if (card) a.availableLimitMinor else a.latestBalanceMinor
        val previousAt = a.balanceUpdatedAt ?: return null
        // A balance the user set since then is the better base; leave it alone.
        if (a.manualBalanceAt != null && a.manualBalanceAt >= previousAt) return null
        if (previous == null || at <= previousAt) return null
        val change = reconcileDao.netChange(accountId, card, previousAt, at)
        val gap = BalanceReconciler.gap(previous, previousAt, change.net, change.unclear, stated, at, System.currentTimeMillis()) ?: return null
        val what = if (card) "limit" else "balance"
        return BalanceGap("${BalanceReconciler.accountLabel(a.bankName, a.last4, a.nickname)} $what", "account:${a.id}", gap, at)
    }

    /**
     * The rule for a new transaction, or null: the user's own rules ([manual], exact before "contains", the
     * longest "contains" first) win over one learned from a recategorisation. Transfers are never re-filed.
     */
    private suspend fun ruleFor(p: ParsedTransaction, manual: List<MerchantRuleEntity>): MerchantRuleEntity? {
        if (p.type == TransactionType.TRANSFER) return null
        return MerchantRuleEntity.pick(manual, p.merchant, p.upiId)
            ?: MerchantRuleEntity.keyOf(p.merchant, p.upiId)?.let { rules.get(it) }?.takeIf { !it.manual }
    }

    private suspend fun ingestInTransaction(
        m: IncomingMessage, now: Long, manual: List<MerchantRuleEntity>, gaps: MutableList<BalanceGap> = ArrayList(),
    ): IngestOutcome {
        // A category the user set for this merchant wins over the parser's guess.
        val rule = ruleFor(m.parsed, manual)
        val tx = rule?.let { m.parsed.copy(category = it.category) } ?: m.parsed
        fun TransactionEntity.filed() = if (rule == null) this else copy(subcategory = rule.subcategory, customCategoryId = rule.customCategoryId)
        if (sourceDao.exists(m.sourceName, m.sourceMessageId)) return IngestOutcome.ALREADY_PROCESSED
        if (deletedDao.exists(m.sourceName, m.sourceMessageId)) return IngestOutcome.ALREADY_PROCESSED

        val decision = DuplicateMatcher.decide(tx, lookup)
        val accountId = ensureAccount(tx, m.rawText)
        val txId: Long
        val outcome: IngestOutcome
        when (decision) {
            is DedupDecision.Duplicate -> {
                val existing = txDao.getById(decision.existingId)!!
                val merged = existing.mergedWith(tx).copy(accountId = existing.accountId ?: accountId)
                // The rule's sub-category follows only when the record still has no placement of the user's own.
                val keepsOwn = existing.subcategory != null || existing.customCategoryId != null || merged.category != tx.category
                txDao.update(if (keepsOwn) merged else merged.filed())
                txId = existing.id
                outcome = IngestOutcome.MERGED
            }
            is DedupDecision.PossibleDuplicate -> {
                // The flagged copy needs its own unique hash until the user decides.
                val entity = tx.toEntity(accountId, now, hash = "${tx.transactionHash}#${m.sourceName}:${m.sourceMessageId}")
                    .copy(needsReview = true, duplicateOfId = decision.existingId, reviewReason = decision.reason).filed()
                txId = txDao.insert(entity)
                outcome = IngestOutcome.FLAGGED_FOR_REVIEW
            }
            DedupDecision.New -> {
                val id = txDao.insert(tx.toEntity(accountId, now).filed())
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
            // A stated balance or limit is checked against the transactions before it replaces the old one.
            when {
                tx.isDebitCard -> null
                tx.accountKind == AccountKind.CARD -> tx.availableLimitMinor?.let { gapFor(accountId, it, tx.transactionTime, card = true) }
                else -> balance?.let { gapFor(accountId, it, tx.transactionTime, card = false) }
            }?.let(gaps::add)
            if (balance != null || tx.availableLimitMinor != null) accountDao.updateBalance(accountId, balance, tx.availableLimitMinor, tx.transactionTime)
            // A debit card's SMS states its bank account's balance.
            if (tx.isDebitCard && balance != null) {
                accountDao.getById(accountId)?.linkedAccountId?.let {
                    gapFor(it, balance, tx.transactionTime, card = false)?.let(gaps::add)
                    accountDao.updateBalance(it, balance, null, tx.transactionTime)
                }
            }
        }
        return outcome
    }

    /**
     * Last 4 digits of the user's own mobile numbers. A bank message can carry the mobile masked like an
     * account ("XXXXXX6810"); no account is ever created with these digits.
     */
    @Volatile var phoneLast4s: Set<String> = emptySet()

    private suspend fun ensureAccount(tx: ParsedTransaction, rawText: String? = null): Long? {
        val last4 = tx.accountLast4?.takeIf { it !in phoneLast4s } ?: return null
        accountDao.find(tx.bankName, last4)?.let { existing ->
            if (existing.accountType == null && tx.isDebitCard) accountDao.setType(existing.id, AccountType.DEBIT_CARD, existing.cardNetwork)
            if (existing.accountType == null && Lenders.isLender(tx.bankName)) accountDao.setType(existing.id, AccountType.LOAN, null)
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
                if (t.type == TransactionType.DEBIT) pairEmi(t, accountId)
            }
            t.type == TransactionType.TRANSFER && t.accountKind == AccountKind.CARD -> {
                txDao.findPotentialDuplicates(t.amountMinor, t.timestamp - 72 * hour, t.timestamp + 72 * hour)
                    .firstOrNull { it.type == TransactionType.DEBIT && it.accountKind == AccountKind.ACCOUNT }
                    ?.let { markTransfer(it, "Credit card bill payment") }
            }
        }
    }

    /**
     * One EMI seen twice: the lender's receipt on the loan account and the bank's debit (same amount, within 3 days).
     * The bank debit stays the spend; the loan side becomes a transfer, so the EMI is counted once.
     */
    private suspend fun pairEmi(t: TransactionEntity, accountId: Long) {
        val day = 24 * 60 * 60 * 1000L
        val onLoan = accountDao.getById(accountId)?.accountType == AccountType.LOAN
        if (onLoan && t.category != Category.EMI_LOAN) return
        val mate = txDao.findPotentialDuplicates(t.amountMinor, t.timestamp - 3 * day, t.timestamp + 3 * day).firstOrNull { o ->
            val other = o.accountId?.takeIf { it != accountId } ?: return@firstOrNull false
            if (o.id == t.id || o.type != TransactionType.DEBIT) return@firstOrNull false
            val otherOnLoan = accountDao.getById(other)?.accountType == AccountType.LOAN
            val (loanSide, bankSide) = if (onLoan) t to o else o to t
            otherOnLoan != onLoan && loanSide.category == Category.EMI_LOAN &&
                (bankSide.category == Category.EMI_LOAN || bankSide.channel == Channel.AUTO_DEBIT)
        } ?: return
        markTransfer(if (onLoan) t else mate, "EMI also debited from your bank account")
    }

    /**
     * A lender's message that is not a payment: makes sure the loan account exists, records the principal of a
     * disbursal (unless the user set one) and the outstanding amount as the account's balance.
     */
    suspend fun applyLoanStatus(s: LoanStatus) = write {
        if (s.last4 in phoneLast4s) return@write
        s.deposit?.let { applyDeposit(s, it); return@write }
        val existing = accountDao.find(s.lender, s.last4)
        val id = existing?.id ?: accountDao.insert(
            AccountEntity(
                bankName = s.lender, last4 = s.last4, kind = AccountKind.ACCOUNT, createdAt = System.currentTimeMillis(),
                accountType = AccountType.LOAN,
            ),
        ).takeIf { it != -1L } ?: return@write
        if (existing != null && existing.accountType == null) accountDao.setType(id, AccountType.LOAN, null)
        if (s.principalMinor != null && existing?.loanPrincipalMinor == null) {
            accountDao.setLoanTerms(id, s.principalMinor, existing?.loanRateBps, existing?.loanTenureMonths, existing?.loanStartDay)
        }
        s.outstandingMinor?.let { accountDao.updateBalance(id, it, null, s.at) }
    }

    /**
     * A bank's FD, RD or PPF message or advice: the deposit account exists with its type, balance, maturity day and what
     * happens then, so the Maturity calendar and Debt/Retirement totals are right. A deposit paid out or closed leaves
     * the lists. An older message never undoes what a newer one said.
     */
    private suspend fun applyDeposit(s: LoanStatus, d: DepositInfo) {
        val type = DepositTerms.typeOf(d.kind)
        val existing = accountDao.find(s.lender, s.last4)
        val id = existing?.id ?: accountDao.insert(
            AccountEntity(bankName = s.lender, last4 = s.last4, kind = AccountKind.ACCOUNT, createdAt = System.currentTimeMillis(), accountType = type),
        ).takeIf { it != -1L } ?: return
        if (existing != null && (existing.accountType == null || existing.accountType == AccountType.SAVINGS)) accountDao.setType(id, type, existing.cardNetwork)
        val lastAt = existing?.balanceUpdatedAt
        val newest = lastAt == null || s.at >= lastAt
        val terms = DepositTerms.of(d, s.at, existing?.maturityDay, existing?.maturityAction, newest)
        if (terms != null) accountDao.setMaturity(id, terms.first, terms.second)
        if (d.closed && newest) accountDao.setHidden(id, true)
        s.outstandingMinor?.let { accountDao.updateBalance(id, it, null, s.at) }
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
     * ones still uncategorised) get it too. A rule the user wrote is never replaced by a learned one.
     */
    suspend fun setCategory(ids: List<Long>, category: Category) {
        for (chunk in ids.chunked(SQLITE_MAX_ARGS)) txDao.setCategoryFor(chunk, category.name)
        val now = System.currentTimeMillis()
        val keys = ids.mapNotNull { txDao.getById(it) }.filter { it.type != TransactionType.TRANSFER }
            .mapNotNull { MerchantRuleEntity.keyOf(it.merchant, it.upiId) }.distinct()
        for (k in keys) {
            if (rules.get(k)?.manual == true) continue
            rules.upsert(MerchantRuleEntity(k, category, now))
            rules.applyToUncategorised(k, category.name)
        }
    }

    /**
     * Saves a rule the user wrote (replacing [previousKey] when its text changed) and, when [applyToPast], files
     * every earlier transaction it matches. Returns how many past transactions changed.
     */
    suspend fun saveRule(rule: MerchantRuleEntity, previousKey: String?, applyToPast: Boolean): Int {
        var changed = 0
        write {
            if (previousKey != null && previousKey != rule.merchantKey) rules.delete(previousKey)
            rules.upsert(rule.copy(manual = true, updatedAt = System.currentTimeMillis()))
            if (applyToPast) changed = applyRule(rule)
        }
        return changed
    }

    /** Files the past transactions [rule] matches under its category and sub-category. Returns the count. */
    suspend fun applyRule(rule: MerchantRuleEntity): Int = rules.applyToPast(
        key = rule.merchantKey, like = MerchantRuleEntity.likeOf(rule.merchantKey),
        contains = rule.matchType == RuleMatch.CONTAINS,
        category = rule.category.name, subcategory = rule.subcategory, customCategoryId = rule.customCategoryId,
    )

    /**
     * What a statement says about the account: a card's credit limit and amount due give its available limit;
     * a bank statement's last running balance is the account balance. Only a newer figure replaces an older one.
     * With [reconcile], a figure the transactions do not explain is reported (see [BalanceUpdateNotifier]); a statement
     * whose own rows are still to be stored passes false, since those rows fill the gap.
     */
    suspend fun applyStatementToAccount(
        bankName: String, last4: String, kind: AccountKind, closingBalance: Long?, creditLimit: Long?, totalDue: Long?, at: Long,
        available: Long? = null, reconcile: Boolean = false,
    ) {
        val id = accountDao.find(bankName, last4)?.id
            ?: accountDao.insert(AccountEntity(bankName = bankName, last4 = last4, kind = kind, createdAt = System.currentTimeMillis())).takeIf { it != -1L }
            ?: return
        var gap: BalanceGap? = null
        when (kind) {
            // The available limit printed on the statement beats working it out from the limit and the amount due.
            AccountKind.CARD -> (available ?: creditLimit?.let { (it - (totalDue ?: 0)).coerceAtLeast(0) })?.let {
                if (reconcile) gap = gapFor(id, it, at, card = true)
                accountDao.setLimitFromStatement(id, it, at)
            }
            AccountKind.ACCOUNT -> if (closingBalance != null) {
                if (reconcile) gap = gapFor(id, closingBalance, at, card = false)
                accountDao.updateBalance(id, closingBalance, null, at)
            }
        }
        report(listOfNotNull(gap))
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

    /**
     * One-off repair after parser fixes: re-parses every stored SMS/email transaction from its source text and,
     * where the new reading differs, moves it to the right account (a masked mobile number or the payee's account
     * was taken as the user's) and turns a "credited to beneficiary" CREDIT into the DEBIT it is. A category the
     * user chose is kept; only the parser's income guess is replaced. Accounts left empty by the move, and an
     * account numbered like the user's mobile ([mobileLast4]) beside a real one at the same bank, are deleted
     * when the user never touched them. Parsing happens outside the write transaction; all writes in one.
     */
    suspend fun reparseStored(mobileLast4: String?): ReparseReport {
        val fixes = ArrayList<ReparseFix>()
        var after = 0L
        while (true) {
            val page = txDao.pageAfter(after, REPARSE_PAGE)
            if (page.isEmpty()) break
            after = page.last().id
            val sources = sourceDao.forTransactions(page.map { it.id }).groupBy { it.transactionId }
            for (t in page) reparseFix(t, sources[t.id].orEmpty())?.let(fixes::add)
        }
        var changed = 0
        var deleted = 0
        write {
            val manual = rules.manualRules()
            val emptied = HashSet<Long>()
            for (f in fixes) {
                val t = txDao.getById(f.id) ?: continue
                // Changed since it was read: leave it.
                if (t.accountLast4 != f.oldLast4 || t.type != f.oldType) continue
                var next = t
                f.account?.let { p ->
                    val newId = ensureAccount(p)
                    t.accountId?.let(emptied::add)
                    next = next.copy(accountLast4 = p.accountLast4, accountKind = p.accountKind, accountId = newId)
                    if (newId != null) {
                        val balance = p.balanceMinor.takeIf { p.accountKind == AccountKind.ACCOUNT || p.isDebitCard }
                        if (balance != null || p.availableLimitMinor != null) accountDao.updateBalance(newId, balance, p.availableLimitMinor, p.transactionTime)
                    }
                }
                f.typed?.let { p ->
                    next = next.copy(type = p.type)
                    val ownCategory = t.subcategory != null || t.customCategoryId != null ||
                        (t.category != Category.INCOME && t.category != Category.SALARY)
                    if (!ownCategory) next = next.copy(category = ruleFor(p, manual)?.category ?: p.category)
                }
                if (next != t) { txDao.update(next); changed++ }
            }

            val accounts = accountDao.all()
            val phoneLike = if (mobileLast4 == null) emptyList() else accounts.filter { it.last4 == mobileLast4 }
            val gone = HashSet<Long>()
            for (id in emptied + phoneLike.map { it.id }) {
                val a = accountDao.getById(id) ?: continue
                if (!a.untouched() || accountDao.referenceCount(a.id) > 0) continue
                val siblings = accounts.filter {
                    it.id != a.id && it.id !in gone && it.bankName == a.bankName && it.kind == a.kind && !it.hidden && it.last4 != mobileLast4
                }
                var count = txDao.countForAccount(a.id)
                if (a.last4 == mobileLast4 && count > 0 && siblings.size == 1) {
                    // Numbered like the user's phone, at a bank where the user has one real account: it is that account.
                    txDao.moveAccount(a.id, siblings.single().id, siblings.single().last4)
                    count = 0
                }
                val removable = count == 0 && (a.id in emptied || (a.last4 == mobileLast4 && siblings.isNotEmpty()))
                if (removable) { accountDao.delete(a.id); gone += a.id; deleted++ }
            }
        }
        return ReparseReport(fixes.size, changed, deleted)
    }

    /** What re-parsing [t]'s own SMS/email text says should change, or null when nothing should. */
    private fun reparseFix(t: TransactionEntity, sources: List<TransactionSourceEntity>): ReparseFix? {
        val parses = sources.mapNotNull { s ->
            val raw = s.rawText ?: return@mapNotNull null
            // "#..." ids are derived rows (an MF order read from an email), not a plain parse of the text.
            if ('#' in s.sourceMessageId) return@mapNotNull null
            val source = when (s.source) { "SMS" -> Source.SMS; "EMAIL" -> Source.EMAIL; else -> return@mapNotNull null }
            runCatching { registry.parse(raw, s.sender, s.receivedAt, source) }.getOrNull()
                ?.takeIf { it.amountMinor == t.amountMinor && it.bankName == t.bankName }
        }
        if (parses.isEmpty()) return null
        // The account is wrong only when no message of the transaction still reads it.
        val account = if (t.accountLast4 != null && parses.none { it.accountLast4 == t.accountLast4 }) {
            parses.firstOrNull { it.accountLast4 != null } ?: parses.first()
        } else null
        // Only the beneficiary mistake is undone: a CREDIT that every message now reads as money out.
        val typed = if (t.type == TransactionType.CREDIT && parses.none { it.type == TransactionType.CREDIT }) {
            parses.firstOrNull { it.type == TransactionType.DEBIT || it.type == TransactionType.INVESTMENT }
        } else null
        if (account == null && typed == null) return null
        return ReparseFix(t.id, t.accountLast4, t.type, account, typed)
    }

    /**
     * Removes accounts numbered like the user's own mobile: their transactions move to the bank's only other
     * account or card, or stay without an account. Cheap; safe to run at every start.
     */
    suspend fun removePhoneAccounts(phones: Set<String>): Int {
        if (phones.isEmpty()) return 0
        var removed = 0
        write {
            val all = accountDao.all()
            for (a in all.filter { it.last4 in phones }) {
                val siblings = all.filter { it.id != a.id && it.bankName == a.bankName && it.last4 !in phones && !it.hidden }
                if (siblings.size == 1) txDao.moveAccount(a.id, siblings.single().id, siblings.single().last4) else txDao.detachAccount(a.id)
                accountDao.delete(a.id)
                removed++
            }
        }
        return removed
    }

    private fun AccountEntity.untouched() = nickname == null && colorArgb == null && manualBalanceMinor == null &&
        !hidden && usage == com.hisaab.shared.db.AccountUsage.PERSONAL && maturityDay == null && forexMarkupBps == null && linkedAccountId == null

    private suspend fun write(block: suspend () -> Unit) {
        db.useWriterConnection { it.immediateTransaction { block() } }
    }

    private companion object {
        const val RAW_TEXT_CAP = 20_000
        const val CSV = "CSV"
        const val SQLITE_MAX_ARGS = 900
        const val REPARSE_PAGE = 400
    }
}
