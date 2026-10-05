package com.hisaab.shared.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.BudgetDao
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.HisaabDatabase
import com.hisaab.shared.db.Migrations
import com.hisaab.shared.db.ProcessedEmailDao
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionSourceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SharedModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HisaabDatabase =
        Room.databaseBuilder(context, HisaabDatabase::class.java, HisaabDatabase.NAME)
            // The bundled SQLite is the same fast, recent build on every Android version.
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides fun transactionDao(db: HisaabDatabase): TransactionDao = db.transactions()
    @Provides fun sourceDao(db: HisaabDatabase): TransactionSourceDao = db.sources()
    @Provides fun processedEmailDao(db: HisaabDatabase): ProcessedEmailDao = db.processedEmails()
    @Provides fun accountDao(db: HisaabDatabase): AccountDao = db.accounts()
    @Provides fun budgetDao(db: HisaabDatabase): BudgetDao = db.budgets()
    @Provides fun statementDao(db: HisaabDatabase): StatementDao = db.statements()
    @Provides fun holdingDao(db: HisaabDatabase): HoldingDao = db.holdings()
    @Provides fun merchantRuleDao(db: HisaabDatabase): com.hisaab.shared.db.MerchantRuleDao = db.merchantRules()
    @Provides fun recurringDao(db: HisaabDatabase): com.hisaab.shared.db.RecurringDao = db.recurring()
    @Provides fun categoryDao(db: HisaabDatabase): com.hisaab.shared.db.CategoryDao = db.categories()

    @Provides
    @Singleton
    fun parserRegistry(): ParserRegistry = ParserRegistry.default()
}
