package com.hisaab.email.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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

    data class Token(val value: String, val expiresAt: Long)

    suspend fun save(token: String, expiresAt: Long) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        store.edit {
            it[TOKEN] = Base64.encodeToString(sealed, Base64.NO_WRAP)
            it[EXPIRES] = expiresAt
        }
    }

    suspend fun read(): Token? {
        val prefs = store.data.first()
        val sealed = prefs[TOKEN]?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            }
            Token(String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8), prefs[EXPIRES] ?: 0L)
        } catch (_: java.security.GeneralSecurityException) {
            null // key was reset (e.g. device lock removed): treat as signed out
        }
    }

    /** Deletes the ciphertext and the Keystore key itself. */
    suspend fun wipe() {
        store.edit { it.clear() }
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "hisaab.gmail.token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        val TOKEN = stringPreferencesKey("token")
        val EXPIRES = longPreferencesKey("expires_at")
    }
}
