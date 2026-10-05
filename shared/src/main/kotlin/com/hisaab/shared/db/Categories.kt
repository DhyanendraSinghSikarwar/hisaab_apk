package com.hisaab.shared.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A category the user made. Its transactions keep the built-in category OTHER and point here. */
@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** A key from the app's icon library. */
    val icon: String,
    val colorArgb: Int,
    val createdAt: Long,
)

/**
 * A sub-category the user made, under a built-in category ([parent] is its enum name, "FOOD") or under one
 * of their own ([parent] is "custom:<id>").
 */
@Entity(tableName = "custom_subcategories", indices = [Index(value = ["parent", "name"], unique = true)])
data class CustomSubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parent: String,
    val name: String,
    val icon: String,
    val createdAt: Long,
) {
    companion object {
        fun parentOf(customCategoryId: Long) = "custom:$customCategoryId"
    }
}

data class CustomCategoryTotal(val customCategoryId: Long, val total: Long)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY name COLLATE NOCASE")
    fun observeCustom(): Flow<List<CustomCategoryEntity>>

    @Insert
    suspend fun insertCustom(c: CustomCategoryEntity): Long

    /** Deleting a category of the user's own leaves its transactions in Other. */
    @Query("UPDATE transactions SET customCategoryId = NULL, subcategory = NULL WHERE customCategoryId = :id")
    suspend fun releaseCustom(id: Long)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteCustom(id: Long)

    @Query("DELETE FROM custom_subcategories WHERE parent = :parent")
    suspend fun deleteSubsOf(parent: String)

    @Query("SELECT * FROM custom_subcategories ORDER BY name COLLATE NOCASE")
    fun observeSubs(): Flow<List<CustomSubcategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSub(s: CustomSubcategoryEntity): Long

    @Query("DELETE FROM custom_subcategories WHERE id = :id")
    suspend fun deleteSub(id: Long)

    /** Spent per category of the user's own, so Home can show each one apart from Other. */
    @Query(
        """SELECT customCategoryId, SUM(amountMinor) AS total FROM transactions
           WHERE customCategoryId IS NOT NULL AND type IN ('DEBIT', 'INVESTMENT') AND currency = 'INR' AND timestamp BETWEEN :from AND :to
           GROUP BY customCategoryId ORDER BY total DESC""",
    )
    fun observeCustomTotals(from: Long, to: Long): Flow<List<CustomCategoryTotal>>

    /** The spending in one category for a period, for the sub-category drill-down. */
    @Query(
        """SELECT * FROM transactions
           WHERE type IN ('DEBIT', 'INVESTMENT') AND currency = 'INR' AND timestamp BETWEEN :from AND :to
             AND ((:customId IS NULL AND category = :category AND customCategoryId IS NULL) OR customCategoryId = :customId)
           ORDER BY timestamp DESC""",
    )
    fun observeSpendIn(category: String, customId: Long?, from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET subcategory = :subcategory WHERE id IN (:ids)")
    suspend fun setSubcategory(ids: List<Long>, subcategory: String?)

    /** Moves transactions into one of the user's own categories (built-in category OTHER). */
    @Query("UPDATE transactions SET category = 'OTHER', customCategoryId = :customId, subcategory = NULL WHERE id IN (:ids)")
    suspend fun setCustomCategory(ids: List<Long>, customId: Long)
}
