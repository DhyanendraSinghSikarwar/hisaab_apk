package com.hisaab.shared.repo

/** Told after new transactions land, so the app can refresh its home-screen widget. Bound in :app. */
fun interface TransactionsChangedNotifier {
    suspend fun onTransactionsChanged()
}
