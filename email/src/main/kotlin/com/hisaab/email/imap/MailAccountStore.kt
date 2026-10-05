package com.hisaab.email.imap

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisaab.email.auth.KeystoreSecret
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mailAccountStore: DataStore<Preferences> by preferencesDataStore(name = "mail_account")

/**
 * Every connected email address and its app password. Passwords are sealed with a Keystore key
 * (see [KeystoreSecret]); only ciphertext reaches the disk. Removing the last address deletes the key.
 */
@Singleton
class MailAccountStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.mailAccountStore
    private val secret = KeystoreSecret(ALIAS)

    /** Connected addresses, in the order they were added. */
    val emails: Flow<List<String>> = store.data.map { p -> entries(p).map { it.first } }

    /** Adds an address, or replaces its password if it is already connected. */
    suspend fun save(email: String, password: String) {
        val sealed = secret.seal(password)
        store.edit { p ->
            val kept = entries(p).filterNot { it.first.equals(email, ignoreCase = true) }
            p[ACCOUNTS] = (kept + (email to sealed)).map { "${it.first}$SEP${it.second}" }.toSet()
            p.remove(EMAIL); p.remove(PASSWORD)
        }
    }

    /** Every saved login whose password can still be opened. */
    suspend fun logins(): List<MailLogin> = entries(store.data.first()).mapNotNull { (email, sealed) ->
        secret.open(sealed)?.let { MailLogin(email, it, MailServers.forAddress(email)) }
    }

    suspend fun remove(email: String) {
        store.edit { p ->
            val kept = entries(p).filterNot { it.first.equals(email, ignoreCase = true) }
            p[ACCOUNTS] = kept.map { "${it.first}$SEP${it.second}" }.toSet()
            p.remove(EMAIL); p.remove(PASSWORD)
        }
        if (entries(store.data.first()).isEmpty()) secret.deleteKey()
    }

    suspend fun wipe() {
        store.edit { it.clear() }
        secret.deleteKey()
    }

    // Before multiple addresses, one login was kept under EMAIL/PASSWORD; it is read as the first account.
    private fun entries(p: Preferences): List<Pair<String, String>> {
        val list = p[ACCOUNTS].orEmpty().mapNotNull { e -> e.split(SEP).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMutableList()
        val legacyEmail = p[EMAIL]; val legacyPassword = p[PASSWORD]
        if (legacyEmail != null && legacyPassword != null && list.none { it.first.equals(legacyEmail, ignoreCase = true) }) list.add(0, legacyEmail to legacyPassword)
        return list
    }

    private companion object {
        const val ALIAS = "hisaab.mail.password"
        const val SEP = "\u001F"
        val ACCOUNTS = stringSetPreferencesKey("accounts")
        val EMAIL = stringPreferencesKey("email")
        val PASSWORD = stringPreferencesKey("password")
    }
}
