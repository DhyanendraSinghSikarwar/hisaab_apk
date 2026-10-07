package com.hisaab.app.di

import com.hisaab.app.ApplicationScope
import com.hisaab.app.notify.StatementNotifications
import com.hisaab.app.sms.SmsInboxSource
import com.hisaab.email.statement.LockedStatementNotifier
import com.hisaab.app.sms.TelephonySmsInboxSource
import com.hisaab.app.widget.WidgetUpdater
import com.hisaab.shared.repo.TransactionsChangedNotifier
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds abstract fun notifier(impl: com.hisaab.app.notify.TransactionsChangedHub): TransactionsChangedNotifier
    @Binds abstract fun inbox(impl: TelephonySmsInboxSource): SmsInboxSource
    @Binds abstract fun lockedStatements(impl: StatementNotifications): LockedStatementNotifier
    @Binds abstract fun balanceUpdates(impl: com.hisaab.app.notify.BalanceNotifications): com.hisaab.shared.repo.BalanceUpdateNotifier
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
