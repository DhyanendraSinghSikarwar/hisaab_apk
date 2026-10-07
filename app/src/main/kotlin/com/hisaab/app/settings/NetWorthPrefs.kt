package com.hisaab.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.netWorthPrefsStore: DataStore<Preferences> by preferencesDataStore(name = "net_worth_prefs")

/** Liabilities that can be left out of net worth. */
enum class LiabilityKind(val label: String) { LOANS("Loans"), CARD_DUES("Credit card dues") }

/**
 * What is left out of net worth: asset classes (by `AssetClass.name`), liability kinds and individual accounts.
 * Everything is counted by default.
 */
data class NetWorthFilter(
    val excludedClasses: Set<String> = emptySet(),
    val excludedLiabilities: Set<LiabilityKind> = emptySet(),
    val excludedAccountIds: Set<Long> = emptySet(),
) {
    val isDefault: Boolean get() = excludedClasses.isEmpty() && excludedLiabilities.isEmpty() && excludedAccountIds.isEmpty()
    fun countsClass(name: String) = name !in excludedClasses
    fun countsLiability(kind: LiabilityKind) = kind !in excludedLiabilities
    fun countsAccount(id: Long) = id !in excludedAccountIds
}

/** The user's net-worth inclusions, kept on the phone in DataStore. */
@Singleton
class NetWorthPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.netWorthPrefsStore

    val filter: Flow<NetWorthFilter> = store.data.map { p ->
        NetWorthFilter(
            excludedClasses = p[CLASSES].orEmpty(),
            excludedLiabilities = p[LIABILITIES].orEmpty().mapNotNull { n -> LiabilityKind.entries.firstOrNull { it.name == n } }.toSet(),
            excludedAccountIds = p[ACCOUNTS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
        )
    }

    suspend fun setClassIncluded(assetClass: String, included: Boolean) = store.edit { p ->
        p[CLASSES] = p[CLASSES].orEmpty().let { if (included) it - assetClass else it + assetClass }
    }.let {}

    suspend fun setLiabilityIncluded(kind: LiabilityKind, included: Boolean) = store.edit { p ->
        p[LIABILITIES] = p[LIABILITIES].orEmpty().let { if (included) it - kind.name else it + kind.name }
    }.let {}

    suspend fun setAccountIncluded(id: Long, included: Boolean) = store.edit { p ->
        p[ACCOUNTS] = p[ACCOUNTS].orEmpty().let { if (included) it - id.toString() else it + id.toString() }
    }.let {}

    /** Counts everything again. */
    suspend fun includeAll() = store.edit { it.remove(CLASSES); it.remove(LIABILITIES); it.remove(ACCOUNTS) }.let {}

    private companion object {
        val CLASSES = stringSetPreferencesKey("excluded_classes")
        val LIABILITIES = stringSetPreferencesKey("excluded_liabilities")
        val ACCOUNTS = stringSetPreferencesKey("excluded_accounts")
    }
}
