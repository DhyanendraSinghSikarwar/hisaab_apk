package com.hisaab.app.ui.accounts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.insight.GapChoice
import com.hisaab.shared.insight.UntrackedGap
import com.hisaab.shared.repo.UntrackedGapFinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gapStore: DataStore<Preferences> by preferencesDataStore(name = "untracked_gaps")

/**
 * Untracked changes ([UntrackedGap]) the user has not dealt with. Dismissed windows, the gaps already notified and the
 * last way each account's gaps were explained are kept here, on the phone only.
 */
@Singleton
class GapTracker @Inject constructor(
    @ApplicationContext context: Context,
    private val finder: UntrackedGapFinder,
    private val transactions: TransactionDao,
) {
    private val store = context.gapStore
    private val dismissedKey = stringSetPreferencesKey("dismissed")
    private val notifiedKey = stringSetPreferencesKey("notified")
    private val choiceKey = stringSetPreferencesKey("choices")

    /** Open gaps, kept up to date as transactions change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val open: Flow<List<UntrackedGap>> = combine(
        transactions.observeSince(System.currentTimeMillis() - WATCH_MS), store.data.map { it[dismissedKey].orEmpty() },
    ) { _, dismissed -> dismissed }.mapLatest { finder.gaps(it) }

    suspend fun openNow(): List<UntrackedGap> = finder.gaps(store.data.first()[dismissedKey].orEmpty())

    suspend fun candidates(g: UntrackedGap) = finder.candidates(g)

    suspend fun dismiss(g: UntrackedGap) { store.edit { it[dismissedKey] = it[dismissedKey].orEmpty() + g.key } }

    /** True once per gap: the caller notifies only then. */
    suspend fun markNotified(g: UntrackedGap): Boolean {
        var first = false
        store.edit { p ->
            val seen = p[notifiedKey].orEmpty()
            if (g.key !in seen) { first = true; p[notifiedKey] = seen + g.key }
        }
        return first
    }

    suspend fun lastChoice(accountId: Long): GapChoice? =
        store.data.first()[choiceKey].orEmpty().firstOrNull { it.startsWith("$accountId=") }?.substringAfter('=')?.let { runCatching { GapChoice.valueOf(it) }.getOrNull() }

    suspend fun rememberChoice(accountId: Long, choice: GapChoice) {
        store.edit { p -> p[choiceKey] = p[choiceKey].orEmpty().filterNot { it.startsWith("$accountId=") }.toSet() + "$accountId=${choice.name}" }
    }

    private companion object {
        const val WATCH_MS = 240L * 24 * 60 * 60 * 1000
    }
}
