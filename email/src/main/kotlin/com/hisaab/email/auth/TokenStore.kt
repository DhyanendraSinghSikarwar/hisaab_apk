package com.hisaab.email.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenStore: DataStore<Preferences> by preferencesDataStore(name = "gmail_tokens")

/**
 * OAuth access token at rest, encrypted with an AES-256-GCM key that lives in the Android Keystore
 * and never leaves it. Only ciphertext is written to disk.
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.tokenStore
    private val secret = KeystoreSecret(ALIAS)

    data class Token(val value: String, val expiresAt: Long)

    suspend fun save(token: String, expiresAt: Long) {
        val sealed = secret.seal(token)
        store.edit {
            it[TOKEN] = sealed
            it[EXPIRES] = expiresAt
        }
    }

    suspend fun read(): Token? {
        val prefs = store.data.first()
        val value = prefs[TOKEN]?.let(secret::open) ?: return null // key reset: treat as signed out
        return Token(value, prefs[EXPIRES] ?: 0L)
    }

    /** Deletes the ciphertext and the Keystore key itself. */
    suspend fun wipe() {
        store.edit { it.clear() }
        secret.deleteKey()
    }

    private companion object {
        const val ALIAS = "hisaab.gmail.token"
        val TOKEN = stringPreferencesKey("token")
        val EXPIRES = longPreferencesKey("expires_at")
    }
}
