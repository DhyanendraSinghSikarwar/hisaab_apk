package com.hisaab.shared.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

/** Rupees for one unit of [currency] on a day the user set. Spends abroad use the rate of their own day. */
@Entity(tableName = "forex_rates", primaryKeys = ["currency", "day"])
data class ForexRateEntity(
    val currency: String,
    /** Epoch day (India time). */
    val day: Long,
    val inrPerUnit: Double,
)

data class CurrencyCount(val currency: String, val count: Int, val unpriced: Int)

@Dao
interface ForexDao {
    @Query("SELECT * FROM forex_rates ORDER BY currency, day DESC")
    fun observeRates(): Flow<List<ForexRateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: ForexRateEntity)

    @Query("DELETE FROM forex_rates WHERE currency = :currency AND day = :day")
    suspend fun delete(currency: String, day: Long)

    /** Foreign currencies in the transactions, and how many still have no rupee value. */
    @Query(
        """SELECT currency, COUNT(*) AS count, SUM(CASE WHEN inrMinor IS NULL THEN 1 ELSE 0 END) AS unpriced
           FROM transactions WHERE currency != 'INR' GROUP BY currency ORDER BY count DESC""",
    )
    fun observeForeign(): Flow<List<CurrencyCount>>

    /** Works out the rupee value of every foreign transaction again, after a rate or a card's markup changed. */
    @Query("UPDATE transactions SET inrMinor = ${ForexSql.INR_OF_ROW} WHERE currency != 'INR'")
    suspend fun recompute()
}

/**
 * `transactions.inrMinor` is the amount in paise: the amount itself for rupees; for other currencies the
 * amount times the rate of the nearest day the user set, plus the card's forex markup. Null while no rate
 * is known, so the spend stays out of totals instead of being counted as rupees. Triggers keep it current.
 */
object ForexSql {
    private const val IST_OFFSET_MILLIS = 19_800_000L

    private fun inr(row: String) =
        """CASE WHEN $row.currency = 'INR' THEN $row.amountMinor ELSE (
             SELECT CAST(ROUND($row.amountMinor * r.inrPerUnit *
                    (1 + COALESCE((SELECT a.forexMarkupBps FROM accounts a WHERE a.id = $row.accountId), 0) / 10000.0)) AS INTEGER)
             FROM forex_rates r WHERE r.currency = $row.currency
               AND ABS(r.day - ($row.timestamp + $IST_OFFSET_MILLIS) / 86400000) = (
                   SELECT MIN(ABS(r2.day - ($row.timestamp + $IST_OFFSET_MILLIS) / 86400000)) FROM forex_rates r2 WHERE r2.currency = $row.currency)
             LIMIT 1) END"""

    // The same, for an UPDATE over every row (a const, so Room can check it).
    const val INR_OF_ROW =
        """CASE WHEN currency = 'INR' THEN amountMinor ELSE (
             SELECT CAST(ROUND(transactions.amountMinor * r.inrPerUnit *
                    (1 + COALESCE((SELECT a.forexMarkupBps FROM accounts a WHERE a.id = transactions.accountId), 0) / 10000.0)) AS INTEGER)
             FROM forex_rates r WHERE r.currency = transactions.currency
               AND ABS(r.day - (transactions.timestamp + 19800000) / 86400000) = (
                   SELECT MIN(ABS(r2.day - (transactions.timestamp + 19800000) / 86400000)) FROM forex_rates r2 WHERE r2.currency = transactions.currency)
             LIMIT 1) END"""

    /** Creates the triggers. Safe to run on every open. */
    fun install(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TRIGGER IF NOT EXISTS transactions_inr_insert AFTER INSERT ON transactions
               BEGIN UPDATE transactions SET inrMinor = ${inr("NEW")} WHERE id = NEW.id; END""",
        )
        connection.execSQL(
            """CREATE TRIGGER IF NOT EXISTS transactions_inr_update AFTER UPDATE OF amountMinor, currency, accountId, timestamp ON transactions
               BEGIN UPDATE transactions SET inrMinor = ${inr("NEW")} WHERE id = NEW.id; END""",
        )
    }
}
