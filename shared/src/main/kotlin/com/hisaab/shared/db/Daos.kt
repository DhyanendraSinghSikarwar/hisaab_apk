package com.hisaab.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
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
             AND (:accountId IS NULL OR accountId = :accountId)
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

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE type IN (:types) AND currency = 'INR' AND timestamp BETWEEN :from AND :to")
    fun observeTotal(types: List<String>, from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE type IN (:types) AND currency = 'INR' AND timestamp BETWEEN :from AND :to")
    suspend fun total(types: List<String>, from: Long, to: Long): Long

    @Query(
        """SELECT category, SUM(amountMinor) AS total FROM transactions
           WHERE type IN ('DEBIT', 'INVESTMENT') AND currency = 'INR' AND timestamp BETWEEN :from AND :to
           GROUP BY category ORDER BY total DESC""",
    )
    fun observeCategoryTotals(from: Long, to: Long): Flow<List<CategoryTotal>>

    /** Spend per local day. [offsetMillis] is the zone's UTC offset, so days break at local midnight. */
    @Query(
        """SELECT (timestamp + :offsetMillis) / 86400000 AS day, SUM(amountMinor) AS total FROM transactions
           WHERE type IN ('DEBIT', 'INVESTMENT') AND currency = 'INR' AND timestamp BETWEEN :from AND :to
           GROUP BY day ORDER BY day""",
    )
    fun observeDailySpend(from: Long, to: Long, offsetMillis: Long): Flow<List<DayTotal>>

    @Query(
        """SELECT strftime('%Y-%m', (timestamp + :offsetMillis) / 1000, 'unixepoch') AS month,
                  SUM(CASE WHEN type IN ('DEBIT', 'INVESTMENT') THEN amountMinor ELSE 0 END) AS spent,
                  SUM(CASE WHEN type = 'CREDIT' THEN amountMinor ELSE 0 END) AS income
           FROM transactions WHERE currency = 'INR' AND timestamp BETWEEN :from AND :to
           GROUP BY month ORDER BY month""",
    )
    fun observeMonthly(from: Long, to: Long, offsetMillis: Long): Flow<List<MonthTotal>>

    @Query("SELECT COUNT(*) FROM transactions WHERE needsReview = 1")
    fun observeReviewCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY timestamp DESC")
    fun observeNeedsReview(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    suspend fun setCategory(id: Long, category: String)

    @Query("UPDATE transactions SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)
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
}

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

    @Query(
        """SELECT a.id, a.bankName, a.last4, a.kind, a.nickname, a.colorArgb, a.latestBalanceMinor, a.availableLimitMinor,
                  a.balanceUpdatedAt,
                  COALESCE((SELECT SUM(t.amountMinor) FROM transactions t WHERE t.accountId = a.id
                            AND t.type IN ('DEBIT', 'INVESTMENT') AND t.timestamp >= :monthStart), 0) AS monthSpent,
                  (SELECT COUNT(*) FROM transactions t WHERE t.accountId = a.id) AS transactionCount
           FROM accounts a ORDER BY a.bankName, a.last4""",
    )
    fun observeWithActivity(monthStart: Long): Flow<List<AccountWithActivity>>

    @Query("SELECT * FROM accounts ORDER BY bankName, last4")
    fun observeAll(): Flow<List<AccountEntity>>
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
