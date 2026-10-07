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
        DeletedMessageEntity::class,
        StatementEntity::class,
        HoldingEntity::class,
        MerchantRuleEntity::class,
        RecurringEntity::class,
        CustomCategoryEntity::class,
        CustomSubcategoryEntity::class,
        ForexRateEntity::class,
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
    abstract fun deletedMessages(): DeletedMessageDao
    abstract fun statements(): StatementDao
    abstract fun holdings(): HoldingDao
    abstract fun merchantRules(): MerchantRuleDao
    abstract fun recurring(): RecurringDao
    abstract fun categories(): CategoryDao
    abstract fun forex(): ForexDao
    abstract fun reconcile(): ReconcileDao

    companion object {
        const val NAME = "hisaab.db"
        const val VERSION = 14
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

    /** 2 -> 3: account type, card network and debit-card links; tombstones for deleted transactions. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE accounts ADD COLUMN accountType TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE accounts ADD COLUMN cardNetwork TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE accounts ADD COLUMN linkedAccountId INTEGER DEFAULT NULL")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `deleted_messages` (`source` TEXT NOT NULL, `messageId` TEXT NOT NULL, " +
                    "`deletedAt` INTEGER NOT NULL, PRIMARY KEY(`source`, `messageId`))",
            )
        }
    }

    /** 3 -> 4: statement PDFs and investment holdings. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `statements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `key` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL, `sender` TEXT NOT NULL, `subject` TEXT, `fileName` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, `transactionCount` INTEGER NOT NULL, `holdingCount` INTEGER NOT NULL, `filePath` TEXT, " +
                    "`bankName` TEXT, `last4` TEXT, `processedAt` INTEGER NOT NULL)",
            )
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_statements_key` ON `statements` (`key`)")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `holdings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `identifier` TEXT NOT NULL, `units` REAL, `valueMinor` INTEGER, `investedMinor` INTEGER, " +
                    "`asOf` INTEGER, `source` TEXT NOT NULL, `note` TEXT, `updatedAt` INTEGER NOT NULL)",
            )
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_holdings_identifier` ON `holdings` (`identifier`)")
        }
    }

    /** 4 -> 5: statement type and its summary figures (dues, due date, limit). */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(connection: SQLiteConnection) {
            for (col in listOf("kind TEXT", "totalDueMinor INTEGER", "minDueMinor INTEGER", "dueEpochDay INTEGER", "creditLimitMinor INTEGER", "statementEpochDay INTEGER")) {
                connection.execSQL("ALTER TABLE statements ADD COLUMN $col DEFAULT NULL")
            }
        }
    }

    /** 5 -> 6: a holding's previous value, to measure EPF/NPS growth. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE holdings ADD COLUMN previousValueMinor INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE holdings ADD COLUMN previousAsOf INTEGER DEFAULT NULL")
        }
    }

    /** 6 -> 7: hidden accounts, and remembered merchant categories. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE accounts ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `merchant_rules` (`merchantKey` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`merchantKey`))",
            )
        }
    }

    /** 7 -> 8: recurring payments and income added by the user. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`amountMinor` INTEGER NOT NULL, `income` INTEGER NOT NULL, `frequency` TEXT NOT NULL, `dayOfMonth` INTEGER NOT NULL, " +
                    "`month` INTEGER, `category` TEXT NOT NULL, `accountId` INTEGER, `createdAt` INTEGER NOT NULL)",
            )
        }
    }

    /** 8 -> 9: personal or business accounts. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE accounts ADD COLUMN usage TEXT NOT NULL DEFAULT 'PERSONAL'")
        }
    }

    /** 9 -> 10: email subjects, statement totals, sub-categories and categories of the user's own. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE transaction_sources ADD COLUMN subject TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE transactions ADD COLUMN subcategory TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE transactions ADD COLUMN customCategoryId INTEGER DEFAULT NULL")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `custom_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`icon` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `custom_subcategories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `parent` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `icon` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            )
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_subcategories_parent_name` ON `custom_subcategories` (`parent`, `name`)")
            for (c in listOf("openingMinor", "closingMinor", "debitsMinor", "creditsMinor", "availableMinor")) {
                connection.execSQL("ALTER TABLE statements ADD COLUMN $c INTEGER DEFAULT NULL")
            }
        }
    }

    /** 10 -> 11: the email text that came with a statement. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE statements ADD COLUMN emailText TEXT DEFAULT NULL")
        }
    }

    /** 11 -> 12: rupee value of every transaction (forex), FD/RD maturity, card forex markup. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE transactions ADD COLUMN inrMinor INTEGER DEFAULT NULL")
            connection.execSQL("UPDATE transactions SET inrMinor = amountMinor WHERE currency = 'INR'")
            connection.execSQL("ALTER TABLE accounts ADD COLUMN maturityDay INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE accounts ADD COLUMN maturityAction TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE accounts ADD COLUMN forexMarkupBps INTEGER DEFAULT NULL")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `forex_rates` (`currency` TEXT NOT NULL, `day` INTEGER NOT NULL, " +
                    "`inrPerUnit` REAL NOT NULL, PRIMARY KEY(`currency`, `day`))",
            )
            ForexSql.install(connection)
        }
    }

    /** 12 -> 13: rules the user adds: "contains" matching, a sub-category or own category, and a manual flag. */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE merchant_rules ADD COLUMN matchType TEXT NOT NULL DEFAULT 'EXACT'")
            connection.execSQL("ALTER TABLE merchant_rules ADD COLUMN subcategory TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE merchant_rules ADD COLUMN customCategoryId INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE merchant_rules ADD COLUMN manual INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** 13 -> 14: a loan's terms (amount, rate, tenure, first EMI) for its payoff schedule. */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE accounts ADD COLUMN loanPrincipalMinor INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE accounts ADD COLUMN loanRateBps INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE accounts ADD COLUMN loanTenureMonths INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE accounts ADD COLUMN loanStartDay INTEGER DEFAULT NULL")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
}
