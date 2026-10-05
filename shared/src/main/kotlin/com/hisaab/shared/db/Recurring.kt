package com.hisaab.shared.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.hisaab.parser.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * A regular payment or income the user adds by hand: a subscription, rent, an EMI, a salary. It plans
 * and reminds; it does not create transactions (the real payment still arrives by SMS or email, so it is
 * never counted twice).
 */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountMinor: Long,
    /** Money coming in (salary, rent received) rather than going out. */
    val income: Boolean,
    /** MONTHLY or YEARLY. */
    val frequency: String,
    val dayOfMonth: Int,
    /** For YEARLY: 1..12. */
    val month: Int?,
    val category: Category,
    val accountId: Long?,
    val createdAt: Long,
) {
    companion object {
        const val MONTHLY = "MONTHLY"
        const val YEARLY = "YEARLY"
    }
}

@Dao
interface RecurringDao {
    @Upsert
    suspend fun upsert(r: RecurringEntity): Long

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM recurring ORDER BY dayOfMonth")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring")
    suspend fun all(): List<RecurringEntity>
}
