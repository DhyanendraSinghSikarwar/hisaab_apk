package com.hisaab.shared.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        TransactionEntity::class,
        TransactionSourceEntity::class,
        ProcessedEmailEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
    ],
    version = HisaabDatabase.VERSION,
    exportSchema = true,
)
abstract class HisaabDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun sources(): TransactionSourceDao
    abstract fun processedEmails(): ProcessedEmailDao
    abstract fun accounts(): AccountDao
    abstract fun budgets(): BudgetDao

    companion object {
        const val NAME = "hisaab.db"
        const val VERSION = 2
    }
}

/**
 * Schema migrations. Each schema change bumps [HisaabDatabase.VERSION], adds a Migration here, and
 * exports the new JSON under shared/schemas, which MigrationTest checks.
 */
object Migrations {
    /** 1 -> 2: an optional balance the user sets on an account. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE accounts ADD COLUMN manualBalanceMinor INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE accounts ADD COLUMN manualBalanceAt INTEGER DEFAULT NULL")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
