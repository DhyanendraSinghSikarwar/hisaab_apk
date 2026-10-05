package com.hisaab.email.statement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisaab.email.auth.KeystoreSecret
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.statementPasswords: DataStore<Preferences> by preferencesDataStore(name = "statement_passwords")

/** A saved statement password, shown by its label only. */
data class SavedPassword(val id: String, val label: String)

/**
 * Passwords for statement PDFs (card statements, CAS, bank statements). Each is sealed with a Keystore
 * key; only ciphertext is on disk, and they are never sent anywhere. New statements try every saved one.
 */
@Singleton
class StatementPasswordStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.statementPasswords
    private val secret = KeystoreSecret(ALIAS)

    val saved: Flow<List<SavedPassword>> = store.data.map { p -> p[ENTRIES].orEmpty().mapNotNull(::decode).map { SavedPassword(it.first, it.second) }.sortedBy { it.label } }

    suspend fun add(label: String, password: String) {
        if (password.isEmpty()) return
        val entry = listOf(UUID.randomUUID().toString(), label.trim().ifEmpty { "Statement password" }.replace(SEP, ' '), secret.seal(password))
            .joinToString(SEP.toString())
        store.edit { it[ENTRIES] = it[ENTRIES].orEmpty() + entry }
    }

    suspend fun remove(id: String) = store.edit { p -> p[ENTRIES] = p[ENTRIES].orEmpty().filterNot { decode(it)?.first == id }.toSet() }

    /** Every saved password, decrypted for one attempt at opening a PDF. */
    suspend fun passwords(): List<String> = store.data.first()[ENTRIES].orEmpty().mapNotNull { decode(it)?.third?.let(secret::open) }

    private fun decode(entry: String): Triple<String, String, String>? {
        val parts = entry.split(SEP)
        return if (parts.size == 3) Triple(parts[0], parts[1], parts[2]) else null
    }

    private companion object {
        const val ALIAS = "hisaab.statement.passwords"
        const val SEP = '\u001F'
        val ENTRIES = stringSetPreferencesKey("entries")
    }
}
