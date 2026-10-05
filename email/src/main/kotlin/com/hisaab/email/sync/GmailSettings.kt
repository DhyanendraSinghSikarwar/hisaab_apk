package com.hisaab.email.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisaab.parser.registry.ParserRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** How the inbox is reached: Google sign-in (Gmail API) or an email address and app password (IMAP). */
enum class MailConnection { NONE, GOOGLE, IMAP }

data class GmailSettings(
    val enabled: Boolean,
    val accountEmail: String?,
    /** How many days back SMS inbox scans and email syncs read. */
    val lookbackDays: Int,
    /** Sender addresses or domains; the Gmail query's `from:` list. */
    val senders: List<String>,
    val readPdfStatements: Boolean,
    val historyId: String?,
    val lastSyncAt: Long?,
    val needsReauth: Boolean,
    val lastResult: String?,
    val connection: MailConnection = MailConnection.NONE,
    /** IMAP only: the start time of the last successful sync; null means the next sync is a full one. */
    val imapSyncedAt: Long? = null,
) {
    val connected: Boolean get() = connection != MailConnection.NONE

    companion object {
        val LOOKBACK_CHOICES = listOf(7, 30, 90, 180, 365)
        const val DEFAULT_LOOKBACK = 90
    }
}

/** Sync position and preferences. The sync engine reads it and writes its checkpoint back. */
interface SyncStateStore {
    suspend fun read(): GmailSettings
    suspend fun saveCheckpoint(historyId: String, at: Long, result: String)
    suspend fun setNeedsReauth(value: Boolean)
}

private val Context.gmailStore: DataStore<Preferences> by preferencesDataStore(name = "gmail_settings")

@Singleton
class GmailSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
    registry: ParserRegistry,
) : SyncStateStore {
    private val store = context.gmailStore
    private val defaultSenders = registry.defaultEmailSenders

    val settings: Flow<GmailSettings> = store.data.map { p ->
        GmailSettings(
            enabled = p[ENABLED] ?: false,
            accountEmail = p[ACCOUNT],
            lookbackDays = p[LOOKBACK] ?: GmailSettings.DEFAULT_LOOKBACK,
            senders = p[SENDERS]?.sorted() ?: defaultSenders,
            readPdfStatements = p[READ_PDF] ?: true,
            historyId = p[HISTORY_ID],
            lastSyncAt = p[LAST_SYNC],
            needsReauth = p[NEEDS_REAUTH] ?: false,
            lastResult = p[LAST_RESULT],
            // Installs from before IMAP support have no CONNECTION key; an account there came from Google sign-in.
            connection = p[CONNECTION]?.let { runCatching { MailConnection.valueOf(it) }.getOrNull() }
                ?: if (p[ACCOUNT] != null || p[ENABLED] == true) MailConnection.GOOGLE else MailConnection.NONE,
            imapSyncedAt = p[IMAP_SYNCED_AT],
        )
    }

    override suspend fun read(): GmailSettings = settings.first()

    override suspend fun saveCheckpoint(historyId: String, at: Long, result: String) {
        store.edit { it[HISTORY_ID] = historyId; it[LAST_SYNC] = at; it[LAST_RESULT] = result; it[NEEDS_REAUTH] = false }
    }

    suspend fun saveImapSync(email: String, at: Long, result: String) {
        store.edit {
            it[imapKey(email)] = at; it[LAST_SYNC] = at; it[LAST_RESULT] = result; it[NEEDS_REAUTH] = false
        }
    }

    /** Where the last sync of this address got to; null means its next sync reads the whole look-back window. */
    suspend fun imapSyncedAt(email: String): Long? = store.data.first()[imapKey(email)]

    private fun imapKey(email: String) = longPreferencesKey("imap_synced_at:${email.lowercase()}")

    override suspend fun setNeedsReauth(value: Boolean) {
        store.edit { it[NEEDS_REAUTH] = value }
    }

    suspend fun connected(email: String?, connection: MailConnection = MailConnection.GOOGLE) = store.edit {
        it[ENABLED] = true
        if (email != null) it[ACCOUNT] = email
        it[CONNECTION] = connection.name
        it[NEEDS_REAUTH] = false
        it.remove(IMAP_SYNCED_AT)
    }

    suspend fun setEnabled(value: Boolean) = store.edit { it[ENABLED] = value }

    /** A longer look-back needs the older mail, so the next sync starts over from a full sync. */
    suspend fun setLookbackDays(days: Int) = store.edit {
        val previous = it[LOOKBACK] ?: GmailSettings.DEFAULT_LOOKBACK
        it[LOOKBACK] = days
        if (days > previous) forgetPosition(it)
    }

    suspend fun setSenders(senders: List<String>) = store.edit {
        it[SENDERS] = senders.map { s -> s.trim().lowercase() }.filter { s -> s.isNotEmpty() }.toSet()
        forgetPosition(it) // new senders: look back over their older mail too
    }

    suspend fun resetSenders() = store.edit { it.remove(SENDERS); forgetPosition(it) }

    suspend fun setReadPdf(value: Boolean) = store.edit { it[READ_PDF] = value }

    /** The next sync reads the whole look-back window again (used by "Re-read email for statements"). */
    suspend fun forgetSyncPosition() = store.edit { forgetPosition(it) }

    suspend fun forgetSyncPosition(email: String) = store.edit { it.remove(imapKey(email)) }

    suspend fun lastResult(result: String) = store.edit { it[LAST_RESULT] = result }

    /** Sign-out: forget the account and the sync position. Parsed transactions are kept. */
    suspend fun clearAccount() = store.edit {
        it[ENABLED] = false
        it.remove(ACCOUNT); it.remove(HISTORY_ID); it.remove(LAST_SYNC); it.remove(NEEDS_REAUTH); it.remove(LAST_RESULT)
        it.remove(IMAP_SYNCED_AT)
        it[CONNECTION] = MailConnection.NONE.name
    }

    private fun forgetPosition(p: androidx.datastore.preferences.core.MutablePreferences) {
        p.remove(HISTORY_ID)
        p.remove(IMAP_SYNCED_AT)
        p.asMap().keys.filter { it.name.startsWith("imap_synced_at:") }.forEach { p.remove(it) }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val ACCOUNT = stringPreferencesKey("account")
        val LOOKBACK = intPreferencesKey("lookback_days")
        val SENDERS = stringSetPreferencesKey("senders")
        val READ_PDF = booleanPreferencesKey("read_pdf")
        val HISTORY_ID = stringPreferencesKey("history_id")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val NEEDS_REAUTH = booleanPreferencesKey("needs_reauth")
        val LAST_RESULT = stringPreferencesKey("last_result")
        val CONNECTION = stringPreferencesKey("connection")
        val IMAP_SYNCED_AT = longPreferencesKey("imap_synced_at")
    }
}
