package com.hisaab.shared.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

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
        const val VERSION = 1
    }
}

/**
 * Schema migrations. Version 1 is the first released schema, so the list is empty.
 * Each schema change bumps [HisaabDatabase.VERSION], adds a Migration here (or an AutoMigration
 * on @Database), and exports the new JSON under shared/schemas, which MigrationTest checks.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
