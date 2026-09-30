package com.hisaab.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val appLock: Boolean,
    val theme: ThemeMode,
    val smsEnabled: Boolean,
    val lastSmsScanAt: Long?,
    val lastSmsResult: String?,
    /** The user tapped "Not now" on the Home SMS permission card. */
    val smsPromptDismissed: Boolean = false,
)

private val Context.appStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class AppSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.appStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            appLock = p[APP_LOCK] ?: false,
            theme = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            smsEnabled = p[SMS_ENABLED] ?: true,
            lastSmsScanAt = p[LAST_SMS_SCAN],
            lastSmsResult = p[LAST_SMS_RESULT],
            smsPromptDismissed = p[SMS_PROMPT_DISMISSED] ?: false,
        )
    }

    /** Receive time of the newest SMS already scanned; the next incremental scan starts after it. */
    suspend fun smsCursor(): Long = store.data.first()[SMS_CURSOR] ?: 0L

    suspend fun smsScanned(cursor: Long, result: String) = store.edit {
        it[SMS_CURSOR] = maxOf(cursor, it[SMS_CURSOR] ?: 0L)
        it[LAST_SMS_SCAN] = System.currentTimeMillis()
        it[LAST_SMS_RESULT] = result
    }

    suspend fun resetSmsCursor() = store.edit { it.remove(SMS_CURSOR) }
    suspend fun setAppLock(value: Boolean) = store.edit { it[APP_LOCK] = value }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setSmsEnabled(value: Boolean) = store.edit { it[SMS_ENABLED] = value }
    suspend fun setSmsPromptDismissed(value: Boolean) = store.edit { it[SMS_PROMPT_DISMISSED] = value }

    private companion object {
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val THEME = stringPreferencesKey("theme")
        val SMS_ENABLED = booleanPreferencesKey("sms_enabled")
        val SMS_CURSOR = longPreferencesKey("sms_cursor")
        val LAST_SMS_SCAN = longPreferencesKey("last_sms_scan")
        val LAST_SMS_RESULT = stringPreferencesKey("last_sms_result")
        val SMS_PROMPT_DISMISSED = booleanPreferencesKey("sms_prompt_dismissed")
    }
}
