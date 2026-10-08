package com.hisaab.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.hisaab.parser.model.AccountKind
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    /** Returns the new row id, or -1 when the unique transactionHash already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: TransactionEntity): Long

    @Update
    suspend fun update(tx: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE referenceNumber = :ref")
    suspend fun findByReference(ref: String): List<TransactionEntity>

    /** Matches the stored hash, or the hash of any message already merged into a transaction. */
    @Query(
        """SELECT * FROM transactions WHERE transactionHash = :hash
           UNION SELECT t.* FROM transactions t JOIN transaction_sources s ON s.transactionId = t.id WHERE s.parsedHash = :hash
           LIMIT 1""",
    )
    suspend fun findByHash(hash: String): TransactionEntity?

    /** Same amount in a time window. Served by the (amountMinor, accountLast4, timestamp) index. */
    @Query("SELECT * FROM transactions WHERE amountMinor = :amountMinor AND timestamp BETWEEN :from AND :to")
    suspend fun findPotentialDuplicates(amountMinor: Long, from: Long, to: Long): List<TransactionEntity>

    /**
     * The transactions list. Every filter is optional (null = any); [limit] grows as the user scrolls,
     * so the UI never loads the whole table.
     */
    @Query(
        """SELECT * FROM transactions
           WHERE (:search IS NULL OR merchant LIKE '%' || :search || '%' OR bankName LIKE '%' || :search || '%'
                  OR note LIKE '%' || :search || '%' OR referenceNumber LIKE '%' || :search || '%'
                  OR CAST(amountMinor / 100 AS TEXT) LIKE :search || '%')
             AND (:accountId IS NULL OR accountId = :accountId OR accountId IN (SELECT id FROM accounts WHERE mergedIntoId = :accountId))
             AND (:category IS NULL OR category = :category)
             AND (:type IS NULL OR type = :type)
             AND (:source IS NULL OR EXISTS (SELECT 1 FROM transaction_sources s WHERE s.transactionId = transactions.id AND s.source = :source))
             AND timestamp BETWEEN :from AND :to
           ORDER BY timestamp DESC
           LIMIT :limit""",
    )
    fun observe(
        search: String?, accountId: Long?, category: String?, type: String?, source: String?,
        from: Long, to: Long, limit: Int,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    /** Every transaction in [from, to], newest first: the screens aggregate these themselves. */
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT COALESCE(SUM(inrMinor), 0) FROM transactions WHERE type IN (:types) AND inrMinor IS NOT NULL AND timestamp BETWEEN :from AND :to")
    fun observeTotal(types: List<String>, from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(inrMinor), 0) FROM transactions WHERE type IN (:types) AND inrMinor IS NOT NULL AND timestamp BETWEEN :from AND :to")
    suspend fun total(types: List<String>, from: Long, to: Long): Long

    @Query(
        """SELECT category, SUM(inrMinor) AS total FROM transactions
           WHERE type IN ('DEBIT', 'INVESTMENT') AND inrMinor IS NOT NULL AND timestamp BETWEEN :from AND :to
           GROUP BY category ORDER BY total DESC""",
    )
    fun observeCategoryTotals(from: Long, to: Long): Flow<List<CategoryTotal>>

    /** Spend per local day. [offsetMillis] is the zone's UTC offset, so days break at local midnight. */
    @Query(
        """SELECT (timestamp + :offsetMillis) / 86400000 AS day, SUM(inrMinor) AS total FROM transactions
           WHERE type IN ('DEBIT', 'INVESTMENT') AND inrMinor IS NOT NULL AND timestamp BETWEEN :from AND :to
           GROUP BY day ORDER BY day""",
    )
    fun observeDailySpend(from: Long, to: Long, offsetMillis: Long): Flow<List<DayTotal>>

    @Query(
        """SELECT strftime('%Y-%m', (timestamp + :offsetMillis) / 1000, 'unixepoch') AS month,
                  SUM(CASE WHEN type IN ('DEBIT', 'INVESTMENT') THEN inrMinor ELSE 0 END) AS spent,
                  SUM(CASE WHEN type = 'CREDIT' THEN inrMinor ELSE 0 END) AS income
           FROM transactions WHERE inrMinor IS NOT NULL AND timestamp BETWEEN :from AND :to
           GROUP BY month ORDER BY month""",
    )
    fun observeMonthly(from: Long, to: Long, offsetMillis: Long): Flow<List<MonthTotal>>

    // Analytics with filters. [scope] is ALL, PERSONAL or BUSINESS (transactions with no account count as
    // personal); [kind] is ALL, ACCOUNT or CARD.

    @Query(
        """SELECT category, SUM(inrMinor) AS total FROM transactions
           WHERE type IN ('DEBIT', 'INVESTMENT') AND inrMinor IS NOT NULL AND timestamp BETWEEN :from AND :to
             AND (:scope = 'ALL'
                  OR (:scope = 'PERSONAL' AND (accountId IS NULL OR accountId IN (SELECT id FROM accounts WHERE usage = 'PERSONAL')))
                  OR (:scope = 'BUSINESS' AND accountId IN (SELECT id FROM accounts WHERE usage = 'BUSINESS')))
             AND (:kind = 'ALL' OR accountKind = :kind)
           GROUP BY category ORDER BY total DESC""",
    )
    fun categoryTotalsFor(from: Long, to: Long, scope: String, kind: String): Flow<List<CategoryTotal>>

    @Query(
        """SELECT strftime('%Y-%m', (timestamp + :offsetMillis) / 1000, 'unixepoch') AS month,
                  SUM(CASE WHEN type IN ('DEBIT', 'INVESTMENT') THEN inrMinor ELSE 0 END) AS spent,
                  SUM(CASE WHEN type = 'CREDIT' THEN inrMinor ELSE 0 END) AS income
           FROM transactions WHERE inrMinor IS NOT NULL AND timestamp BETWEEN :from AND :to
             AND (:scope = 'ALL'
                  OR (:scope = 'PERSONAL' AND (accountId IS NULL OR accountId IN (SELECT id FROM accounts WHERE usage = 'PERSONAL')))
                  OR (:scope = 'BUSINESS' AND accountId IN (SELECT id FROM accounts WHERE usage = 'BUSINESS')))
             AND (:kind = 'ALL' OR accountKind = :kind)
           GROUP BY month ORDER BY month""",
    )
    fun monthlyFor(from: Long, to: Long, offsetMillis: Long, scope: String, kind: String): Flow<List<MonthTotal>>

    @Query(
        """SELECT (timestamp + :offsetMillis) / 86400000 AS day, SUM(inrMinor) AS total FROM transactions
           WHERE type IN ('DEBIT', 'INVESTMENT') AND inrMinor IS NOT NULL AND timestamp BETWEEN :from AND :to
             AND (:scope = 'ALL'
                  OR (:scope = 'PERSONAL' AND (accountId IS NULL OR accountId IN (SELECT id FROM accounts WHERE usage = 'PERSONAL')))
                  OR (:scope = 'BUSINESS' AND accountId IN (SELECT id FROM accounts WHERE usage = 'BUSINESS')))
             AND (:kind = 'ALL' OR accountKind = :kind)
           GROUP BY day ORDER BY day""",
    )
    fun dailyFor(from: Long, to: Long, offsetMillis: Long, scope: String, kind: String): Flow<List<DayTotal>>

    @Query(
        """SELECT COALESCE(merchant, bankName) AS name, SUM(inrMinor) AS total, COUNT(*) AS count FROM transactions
           WHERE type IN ('DEBIT', 'INVESTMENT') AND inrMinor IS NOT NULL AND timestamp BETWEEN :from AND :to
             AND (:scope = 'ALL'
                  OR (:scope = 'PERSONAL' AND (accountId IS NULL OR accountId IN (SELECT id FROM accounts WHERE usage = 'PERSONAL')))
                  OR (:scope = 'BUSINESS' AND accountId IN (SELECT id FROM accounts WHERE usage = 'BUSINESS')))
             AND (:kind = 'ALL' OR accountKind = :kind)
           GROUP BY name ORDER BY total DESC LIMIT :limit""",
    )
    fun topMerchantsFor(from: Long, to: Long, limit: Int, scope: String, kind: String): Flow<List<MerchantTotal>>

    @Query("SELECT MIN(timestamp) FROM transactions")
    suspend fun firstTimestamp(): Long?

    /** Spending and income per month for one account (and the debit cards that draw from it). */
    @Query(
        """SELECT strftime('%Y-%m', (timestamp + :offsetMillis) / 1000, 'unixepoch') AS month,
                  SUM(CASE WHEN type IN ('DEBIT', 'INVESTMENT') THEN inrMinor ELSE 0 END) AS spent,
                  SUM(CASE WHEN type = 'CREDIT' THEN inrMinor ELSE 0 END) AS income
           FROM transactions
           WHERE inrMinor IS NOT NULL AND timestamp BETWEEN :from AND :to
             AND (accountId = :accountId OR accountId IN (SELECT id FROM accounts WHERE linkedAccountId = :accountId OR mergedIntoId = :accountId
                                                        OR linkedAccountId IN (SELECT m.id FROM accounts m WHERE m.mergedIntoId = :accountId)))
           GROUP BY month ORDER BY month""",
    )
    fun monthlyForAccount(accountId: Long, from: Long, to: Long, offsetMillis: Long): Flow<List<MonthTotal>>

    @Query("SELECT MIN(timestamp) FROM transactions WHERE accountId = :accountId OR accountId IN (SELECT id FROM accounts WHERE mergedIntoId = :accountId)")
    suspend fun firstTimestampFor(accountId: Long): Long?

    /** Where the money went, by merchant, biggest first. */
    @Query(
        """SELECT COALESCE(merchant, bankName) AS name, SUM(inrMinor) AS total, COUNT(*) AS count FROM transactions
           WHERE type IN ('DEBIT', 'INVESTMENT') AND inrMinor IS NOT NULL AND timestamp BETWEEN :from AND :to
           GROUP BY name ORDER BY total DESC LIMIT :limit""",
    )
    fun observeTopMerchants(from: Long, to: Long, limit: Int): Flow<List<MerchantTotal>>

    @Query("SELECT COUNT(*) FROM transactions WHERE needsReview = 1")
    fun observeReviewCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY timestamp DESC")
    fun observeNeedsReview(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp")
    suspend fun getAll(): List<TransactionEntity>

    /** Every payment that may be an EMI: category EMI & Loans, or anything booked on a loan account. For the loan tracker. */
    @Query(
        """SELECT * FROM transactions WHERE type != 'CREDIT' AND needsReview = 0
           AND (category = 'EMI_LOAN' OR accountId IN (SELECT id FROM accounts WHERE accountType = 'LOAN'))
           ORDER BY timestamp""",
    )
    fun observeLoanPayments(): Flow<List<TransactionEntity>>

    /** Everything since [from], for planning (recurring payments, insurance, savings, insights). */
    @Query("SELECT * FROM transactions WHERE timestamp >= :from ORDER BY timestamp")
    fun observeSince(from: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from ORDER BY timestamp")
    suspend fun since(from: Long): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeTransactionCount(): Flow<Int>

    @Query("UPDATE transactions SET category = :category, subcategory = NULL, customCategoryId = NULL WHERE id = :id")
    suspend fun setCategory(id: Long, category: String)

    @Query("UPDATE transactions SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    /** The transactions a statement produced, or merged into: its sources are named "stmt:<statement key>:<row>". */
    @Query(
        """SELECT DISTINCT t.* FROM transactions t JOIN transaction_sources s ON s.transactionId = t.id
           WHERE s.source = 'STATEMENT' AND s.sourceMessageId LIKE 'stmt:' || :statementKey || ':%' ORDER BY t.timestamp""",
    )
    fun observeForStatement(statementKey: String): Flow<List<TransactionEntity>>

    /** How many of a statement's rows matched a transaction an SMS or email had already reported. */
    @Query(
        """SELECT COUNT(DISTINCT t.id) FROM transactions t JOIN transaction_sources s ON s.transactionId = t.id
           WHERE s.source = 'STATEMENT' AND s.sourceMessageId LIKE 'stmt:' || :statementKey || ':%'
             AND EXISTS (SELECT 1 FROM transaction_sources o WHERE o.transactionId = t.id AND o.source != 'STATEMENT')""",
    )
    fun observeMatchedForStatement(statementKey: String): Flow<Int>

    /** How often each category was used for this type of transaction since [from], most used first. */
    @Query("SELECT category, COUNT(*) AS count FROM transactions WHERE type = :type AND timestamp >= :from GROUP BY category ORDER BY count DESC")
    suspend fun categoryUsage(type: String, from: Long): List<CategoryCount>

    @Query("UPDATE transactions SET category = :category, subcategory = NULL, customCategoryId = NULL WHERE id IN (:ids)")
    suspend fun setCategoryFor(ids: List<Long>, category: String)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    /** All transactions in id order, a page at a time (for one-off maintenance passes). */
    @Query("SELECT * FROM transactions WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun pageAfter(afterId: Long, limit: Int): List<TransactionEntity>

    /** Every transaction booked on one account, for deleting the account with its history. */
    @Query("SELECT id FROM transactions WHERE accountId = :accountId")
    suspend fun idsForAccount(accountId: Long): List<Long>

    /** Transactions with no account, or whose account no longer exists, in id order a page at a time. */
    @Query("SELECT * FROM transactions WHERE id > :afterId AND (accountId IS NULL OR accountId NOT IN (SELECT id FROM accounts)) ORDER BY id LIMIT :limit")
    suspend fun orphansAfter(afterId: Long, limit: Int): List<TransactionEntity>

    @Query("UPDATE transactions SET accountId = :accountId, accountLast4 = :last4, accountKind = :kind WHERE id = :id")
    suspend fun setAccount(id: Long, accountId: Long, last4: String, kind: AccountKind)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key")
    suspend fun get(key: String): MerchantRuleEntity?

    @androidx.room.Upsert
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules WHERE merchantKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM merchant_rules ORDER BY merchantKey")
    fun observeAll(): Flow<List<MerchantRuleEntity>>

    /** The rules the user added, which are checked before learned ones on every new transaction. */
    @Query("SELECT * FROM merchant_rules WHERE manual = 1")
    suspend fun manualRules(): List<MerchantRuleEntity>

    /**
     * Files every past transaction a rule matches (exactly, by merchant or UPI id, or by [like] = "%text%" when
     * [contains]); transfers are left alone. Returns how many changed.
     */
    @Query(
        """UPDATE transactions SET category = :category, subcategory = :subcategory, customCategoryId = :customCategoryId
           WHERE type != 'TRANSFER' AND (
             (:contains = 0 AND (LOWER(TRIM(merchant)) = :key OR LOWER(TRIM(upiId)) = :key))
             OR (:contains = 1 AND (merchant LIKE :like ESCAPE '\' OR upiId LIKE :like ESCAPE '\')))""",
    )
    suspend fun applyToPast(key: String, like: String, contains: Boolean, category: String, subcategory: String?, customCategoryId: Long?): Int

    /** How many past transactions [applyToPast] would change, for the preview in the rule editor. */
    @Query(
        """SELECT COUNT(*) FROM transactions
           WHERE type != 'TRANSFER' AND (
             (:contains = 0 AND (LOWER(TRIM(merchant)) = :key OR LOWER(TRIM(upiId)) = :key))
             OR (:contains = 1 AND (merchant LIKE :like ESCAPE '\' OR upiId LIKE :like ESCAPE '\')))""",
    )
    fun observeMatchCount(key: String, like: String, contains: Boolean): Flow<Int>

    /** Earlier transactions from the same merchant that were never categorised, so the new rule can fix them too. */
    @Query(
        """UPDATE transactions SET category = :category
           WHERE category = 'OTHER' AND type IN ('DEBIT', 'CREDIT', 'INVESTMENT')
             AND (LOWER(TRIM(merchant)) = :key OR (merchant IS NULL AND LOWER(TRIM(upiId)) = :key))""",
    )
    suspend fun applyToUncategorised(key: String, category: String): Int
}

@Dao
interface DeletedMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<DeletedMessageEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_messages WHERE source = :source AND messageId = :messageId)")
    suspend fun exists(source: String, messageId: String): Boolean
}

@Dao
interface TransactionSourceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(source: TransactionSourceEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM transaction_sources WHERE source = :source AND sourceMessageId = :messageId)")
    suspend fun exists(source: String, messageId: String): Boolean

    @Query("SELECT * FROM transaction_sources WHERE transactionId = :transactionId ORDER BY receivedAt")
    suspend fun forTransaction(transactionId: Long): List<TransactionSourceEntity>

    @Query("SELECT * FROM transaction_sources WHERE transactionId = :transactionId ORDER BY receivedAt")
    fun observeForTransaction(transactionId: Long): Flow<List<TransactionSourceEntity>>

    @Query("SELECT DISTINCT transactionId, source FROM transaction_sources WHERE transactionId IN (:ids)")
    suspend fun sourcesOf(ids: List<Long>): List<SourceOfTransaction>

    @Query("SELECT * FROM transaction_sources WHERE id = :id")
    suspend fun getById(id: Long): TransactionSourceEntity?

    @Query("SELECT transactionId FROM transaction_sources WHERE source = :source AND sourceMessageId = :messageId")
    suspend fun transactionIdFor(source: String, messageId: String): Long?

    @Query("SELECT * FROM transaction_sources WHERE transactionId IN (:ids)")
    suspend fun forTransactions(ids: List<Long>): List<TransactionSourceEntity>

    @Query("UPDATE transaction_sources SET transactionId = :to WHERE transactionId = :from")
    suspend fun reassign(from: Long, to: Long)

    @Query("UPDATE transaction_sources SET transactionId = :to WHERE id = :sourceId")
    suspend fun move(sourceId: Long, to: Long)
}

@Dao
interface ProcessedEmailDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<ProcessedEmailEntity>)

    @Query("SELECT messageId FROM processed_emails WHERE messageId IN (:ids)")
    suspend fun existing(ids: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM processed_emails")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM processed_emails")
    suspend fun clear()

    /** Statements recorded since [since], counted by status (PARSED, LOCKED...). For the activity log. */
    @Query("SELECT status, COUNT(*) AS count FROM statements WHERE processedAt >= :since GROUP BY status")
    suspend fun statementStatusSince(since: Long): List<StatusCount>
}

/** A status and how many rows have it. */
data class StatusCount(val status: String, val count: Int)

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: AccountEntity): Long

    @Query("SELECT * FROM accounts WHERE bankName = :bankName AND last4 = :last4")
    suspend fun find(bankName: String, last4: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    /** Only a newer message may move the balance, so re-reading old SMS never rewinds it. */
    @Query(
        """UPDATE accounts SET latestBalanceMinor = COALESCE(:balance, latestBalanceMinor),
                  availableLimitMinor = COALESCE(:limit, availableLimitMinor), balanceUpdatedAt = :at
           WHERE id = :id AND (balanceUpdatedAt IS NULL OR balanceUpdatedAt <= :at)""",
    )
    suspend fun updateBalance(id: Long, balance: Long?, limit: Long?, at: Long)

    @Query("UPDATE accounts SET nickname = :nickname, colorArgb = :colorArgb WHERE id = :id")
    suspend fun rename(id: Long, nickname: String?, colorArgb: Int?)

    @Query("UPDATE accounts SET accountType = :type, cardNetwork = :network WHERE id = :id")
    suspend fun setType(id: Long, type: AccountType?, network: CardNetwork?)

    @Query("UPDATE accounts SET usage = :usage WHERE id = :id")
    suspend fun setUsage(id: Long, usage: AccountUsage)

    @Query("UPDATE accounts SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    /** Card numbers, for guessing statement passwords like DDMMYYYY + last 4 of the card. */
    @Query("SELECT DISTINCT last4 FROM accounts WHERE kind = 'CARD' AND length(last4) = 4")
    suspend fun cardLast4s(): List<String>

    @Query("UPDATE accounts SET maturityDay = :day, maturityAction = :action WHERE id = :id")
    suspend fun setMaturity(id: Long, day: Long?, action: MaturityAction?)

    @Query("UPDATE accounts SET forexMarkupBps = :bps WHERE id = :id")
    suspend fun setForexMarkup(id: Long, bps: Int?)

    /** Sets, or with nulls clears, a loan's terms. */
    @Query(
        """UPDATE accounts SET loanPrincipalMinor = :principal, loanRateBps = :rateBps, loanTenureMonths = :tenure,
                  loanStartDay = :startDay WHERE id = :id""",
    )
    suspend fun setLoanTerms(id: Long, principal: Long?, rateBps: Int?, tenure: Int?, startDay: Long?)

    /** Deposits paid out on or before [today] (epoch day) leave the lists; renewed ones stay. */
    @Query("UPDATE accounts SET hidden = 1 WHERE hidden = 0 AND maturityAction = 'CREDIT' AND maturityDay <= :today")
    suspend fun closeMatured(today: Long): Int

    /** A card's limit from its statement: the available limit is the limit minus what is due. */
    @Query(
        """UPDATE accounts SET availableLimitMinor = :available, balanceUpdatedAt = :at
           WHERE id = :id AND (balanceUpdatedAt IS NULL OR balanceUpdatedAt <= :at)""",
    )
    suspend fun setLimitFromStatement(id: Long, available: Long, at: Long)

    /** Links a debit card to its bank account, or with null unlinks it. */
    @Query("UPDATE accounts SET linkedAccountId = :accountId WHERE id = :cardId")
    suspend fun link(cardId: Long, accountId: Long?)

    /** Sets, or with nulls clears, the balance the user typed in. */
    @Query("UPDATE accounts SET manualBalanceMinor = :balance, manualBalanceAt = :at WHERE id = :id")
    suspend fun setManualBalance(id: Long, balance: Long?, at: Long?)

    /**
     * Accounts with their spend in [from, to). A bank account also counts the transactions of the debit
     * cards linked to it. changeSinceManual is what the transactions after the user's own balance did to
     * it: credits add, spends subtract, and a card bill payment (TRANSFER) frees up card limit.
     * Transactions still waiting in review are left out.
     */
    @Query(
        """SELECT a.id, a.bankName, a.last4, a.kind, a.nickname, a.colorArgb, a.latestBalanceMinor, a.availableLimitMinor,
                  a.balanceUpdatedAt, a.manualBalanceMinor, a.manualBalanceAt, a.accountType, a.cardNetwork,
                  COALESCE((SELECT p.mergedIntoId FROM accounts p WHERE p.id = a.linkedAccountId), a.linkedAccountId) AS linkedAccountId, a.hidden, a.usage,
                  a.maturityDay, a.maturityAction, a.forexMarkupBps,
                  a.loanPrincipalMinor, a.loanRateBps, a.loanTenureMonths, a.loanStartDay,
                  COALESCE((SELECT SUM(t.inrMinor) FROM transactions t
                            WHERE (t.accountId = a.id OR t.accountId IN (SELECT c.id FROM accounts c WHERE c.linkedAccountId = a.id OR c.mergedIntoId = a.id OR c.linkedAccountId IN (SELECT m.id FROM accounts m WHERE m.mergedIntoId = a.id)))
                            AND t.type IN ('DEBIT', 'INVESTMENT') AND t.timestamp >= :from AND t.timestamp < :to), 0) AS monthSpent,
                  (SELECT COUNT(*) FROM transactions t WHERE t.accountId = a.id OR t.accountId IN (SELECT m.id FROM accounts m WHERE m.mergedIntoId = a.id)) AS transactionCount,
                  COALESCE((SELECT SUM(CASE WHEN t.type = 'CREDIT' THEN t.inrMinor
                                            WHEN t.type IN ('DEBIT', 'INVESTMENT') THEN -t.inrMinor
                                            WHEN t.type = 'TRANSFER' AND a.kind = 'CARD' THEN t.inrMinor
                                            ELSE 0 END)
                            FROM transactions t
                            WHERE (t.accountId = a.id OR t.accountId IN (SELECT c.id FROM accounts c WHERE c.linkedAccountId = a.id OR c.mergedIntoId = a.id OR c.linkedAccountId IN (SELECT m.id FROM accounts m WHERE m.mergedIntoId = a.id)))
                            AND a.manualBalanceAt IS NOT NULL
                            AND t.timestamp > a.manualBalanceAt AND t.needsReview = 0 AND t.inrMinor IS NOT NULL), 0) AS changeSinceManual
           FROM accounts a
           WHERE a.mergedIntoId IS NULL OR a.mergedIntoId NOT IN (SELECT x.id FROM accounts x)
           ORDER BY a.bankName, a.last4""",
    )
    fun observeWithActivity(from: Long, to: Long = Long.MAX_VALUE): Flow<List<AccountWithActivity>>

    /** Every account, merged ones included (for names); pickers leave out those with [AccountEntity.mergedIntoId]. */
    @Query("SELECT * FROM accounts ORDER BY bankName, last4")
    fun observeAll(): Flow<List<AccountEntity>>

    /** Accounts shown together with another one. */
    @Query("SELECT * FROM accounts WHERE mergedIntoId IS NOT NULL ORDER BY bankName, last4")
    fun observeMerged(): Flow<List<AccountEntity>>

    /** Shows [id] together with [into] in every list and total, or with null on its own again. Nothing else changes. */
    @Query("UPDATE accounts SET mergedIntoId = :into WHERE id = :id")
    suspend fun setMergedInto(id: Long, into: Long?)

    /** Accounts shown with [from] are shown with [to] instead. */
    @Query("UPDATE accounts SET mergedIntoId = :to WHERE mergedIntoId = :from")
    suspend fun repointMerged(from: Long, to: Long)

    /** Accounts merged into [id] show on their own again (before [id] is deleted). */
    @Query("UPDATE accounts SET mergedIntoId = NULL WHERE mergedIntoId = :id")
    suspend fun unmergeChildren(id: Long)

    @Query("SELECT * FROM accounts")
    suspend fun all(): List<AccountEntity>

    /** Cards linked to this account, plus recurring payments set on it. */
    @Query("SELECT (SELECT COUNT(*) FROM accounts WHERE linkedAccountId = :id) + (SELECT COUNT(*) FROM recurring WHERE accountId = :id)")
    suspend fun referenceCount(id: Long): Int

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BudgetDao {
    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun delete(category: String)

    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>
}

/** What the transactions did to an account's balance over a span, for checking a stated balance against them. */
data class NetChange(
    /** Credits minus spends (for a card: refunds and payments minus spends), in paise. */
    val net: Long,
    /** Transactions whose effect is not known: a bank-side transfer (in or out?) or a foreign amount with no rupee value. */
    val unclear: Int,
)

@Dao
interface ReconcileDao {
    /**
     * The net effect on account [id] (and the debit cards linked to it) of the transactions after [from] up to and
     * including [to]. Transactions waiting in review are left out.
     */
    @Query(
        """SELECT COALESCE(SUM(CASE WHEN t.type = 'CREDIT' THEN t.inrMinor
                                    WHEN t.type IN ('DEBIT', 'INVESTMENT') THEN -t.inrMinor
                                    WHEN t.type = 'TRANSFER' AND :card THEN t.inrMinor
                                    ELSE 0 END), 0) AS net,
                  COALESCE(SUM(CASE WHEN t.inrMinor IS NULL OR (t.type = 'TRANSFER' AND NOT :card) THEN 1 ELSE 0 END), 0) AS unclear
           FROM transactions t
           WHERE (t.accountId = :id OR t.accountId IN (SELECT c.id FROM accounts c WHERE c.linkedAccountId = :id OR c.mergedIntoId = :id OR c.linkedAccountId IN (SELECT m.id FROM accounts m WHERE m.mergedIntoId = :id)))
             AND t.timestamp > :from AND t.timestamp <= :to AND t.needsReview = 0""",
    )
    suspend fun netChange(id: Long, card: Boolean, from: Long, to: Long): NetChange
}
