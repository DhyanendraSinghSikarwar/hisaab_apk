package com.hisaab.shared.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.hisaab.parser.model.HoldingKind
import kotlinx.coroutines.flow.Flow

/** A statement PDF Hisaab has seen, from email or a file the user picked. Locked ones keep a private copy until unlocked. */
@Entity(tableName = "statements", indices = [Index(value = ["key"], unique = true)])
data class StatementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Email message id + attachment index, or a hash of the file: the same PDF is never read twice. */
    val key: String,
    /** EMAIL or FILE. */
    val source: String,
    val sender: String,
    val subject: String?,
    val fileName: String,
    val receivedAt: Long,
    /** PARSED, LOCKED (needs a password), EMPTY (read, nothing found), UNREADABLE. */
    val status: String,
    val transactionCount: Int,
    val holdingCount: Int,
    /** App-private copy of a LOCKED PDF, deleted once it is unlocked. Never backed up. */
    val filePath: String?,
    val bankName: String?,
    val last4: String?,
    val processedAt: Long,
    /** CREDIT_CARD, BANK, INVESTMENT or OTHER (see StatementKind). Null until the PDF is read. */
    @ColumnInfo(defaultValue = "NULL") val kind: String? = null,
    @ColumnInfo(defaultValue = "NULL") val totalDueMinor: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val minDueMinor: Long? = null,
    /** Payment due date, epoch day. */
    @ColumnInfo(defaultValue = "NULL") val dueEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val creditLimitMinor: Long? = null,
    /** Statement date, epoch day. */
    @ColumnInfo(defaultValue = "NULL") val statementEpochDay: Long? = null,
) {
    companion object {
        const val PARSED = "PARSED"
        const val LOCKED = "LOCKED"
        const val EMPTY = "EMPTY"
        const val UNREADABLE = "UNREADABLE"
    }
}

/** One investment: an EPF account, a fund, a stock, or anything the user adds. */
@Entity(tableName = "holdings", indices = [Index(value = ["identifier"], unique = true)])
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: HoldingKind,
    val name: String,
    /** ISIN, "EPF:<member id>", or "manual:<uuid>". */
    val identifier: String,
    val units: Double?,
    val valueMinor: Long?,
    val investedMinor: Long?,
    val asOf: Long?,
    /** SMS, STATEMENT or MANUAL. */
    val source: String,
    val note: String?,
    val updatedAt: Long,
    /** The value before the latest update, and when it was stated: how fast EPF/NPS grow. */
    @ColumnInfo(defaultValue = "NULL") val previousValueMinor: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val previousAsOf: Long? = null,
)

@Dao
interface StatementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: StatementEntity): Long

    @Query("SELECT * FROM statements WHERE `key` = :key")
    suspend fun byKey(key: String): StatementEntity?

    @Query("SELECT * FROM statements WHERE id = :id")
    suspend fun byId(id: Long): StatementEntity?

    @Query("SELECT * FROM statements WHERE status = 'LOCKED' ORDER BY receivedAt DESC")
    suspend fun locked(): List<StatementEntity>

    @Query("SELECT * FROM statements WHERE status = 'LOCKED' ORDER BY receivedAt DESC")
    fun observeLocked(): Flow<List<StatementEntity>>

    @Query("SELECT * FROM statements WHERE id = :id")
    fun observeById(id: Long): Flow<StatementEntity?>

    @Query("SELECT * FROM statements ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<StatementEntity>>

    @Query("DELETE FROM statements WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HoldingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(h: HoldingEntity): Long

    @Update
    suspend fun update(h: HoldingEntity)

    @Query("SELECT * FROM holdings WHERE identifier = :identifier")
    suspend fun byIdentifier(identifier: String): HoldingEntity?

    @Query("SELECT * FROM holdings WHERE id = :id")
    suspend fun byId(id: Long): HoldingEntity?

    @Query("SELECT * FROM holdings ORDER BY kind, COALESCE(valueMinor, 0) DESC")
    fun observeAll(): Flow<List<HoldingEntity>>

    @Query("SELECT COALESCE(SUM(valueMinor), 0) FROM holdings")
    fun observeTotal(): Flow<Long>

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun delete(id: Long)
}
