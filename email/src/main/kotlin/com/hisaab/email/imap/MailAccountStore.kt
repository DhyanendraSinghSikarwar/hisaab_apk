package com.hisaab.email.imap

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisaab.email.auth.KeystoreSecret
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mailAccountStore: DataStore<Preferences> by preferencesDataStore(name = "mail_account")

/**
 * The verified email address and its app password. The password is sealed with a Keystore key
 * (see [KeystoreSecret]); only ciphertext reaches the disk, and sign-out deletes the key.
 */
@Singleton
class MailAccountStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.mailAccountStore
    private val secret = KeystoreSecret(ALIAS)

    suspend fun save(email: String, password: String) {
        val sealed = secret.seal(password)
        store.edit {
            it[EMAIL] = email
            it[PASSWORD] = sealed
        }
    }

    /** The saved login, or null when there is none or its key was reset. */
    suspend fun login(): MailLogin? {
        val p = store.data.first()
        val email = p[EMAIL] ?: return null
        val password = p[PASSWORD]?.let(secret::open) ?: return null
        return MailLogin(email, password, MailServers.forAddress(email))
    }

    suspend fun wipe() {
        store.edit { it.clear() }
        secret.deleteKey()
    }

    private companion object {
        const val ALIAS = "hisaab.mail.password"
        val EMAIL = stringPreferencesKey("email")
        val PASSWORD = stringPreferencesKey("password")
    }
}
