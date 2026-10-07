package com.hisaab.app.ui.loans

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

private val Context.dismissStore: DataStore<Preferences> by preferencesDataStore(name = "loan_dismissals")

/** Payee keys (see `Loans.keyOf`) the user marked "Not a loan"; those EMI series are never detected again. On the phone only. */
@Singleton
class LoanDismissals @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.dismissStore
    private val key = stringSetPreferencesKey("payees")

    val payees: Flow<Set<String>> = store.data.map { it[key].orEmpty() }

    suspend fun dismiss(payee: String) { store.edit { it[key] = it[key].orEmpty() + payee } }
}
