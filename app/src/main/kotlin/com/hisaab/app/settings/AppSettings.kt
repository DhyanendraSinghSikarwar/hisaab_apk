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
    /** Read payment-app notifications for UPI payments that get no bank SMS. Off until the user opts in. */
    val appNotificationsEnabled: Boolean = false,
    /** Asked once for permission to post "statement needs a password" notifications. */
    val notificationsAsked: Boolean = false,
    /** Budget alert at this share of the limit (and again at 100%). */
    val budgetAlertPercent: Int = 90,
    /** Alerts already sent, so none repeats: "budget:FOOD:2026-10:90", "bill:Netflix:2026-11-05". */
    val alertedKeys: Set<String> = emptySet(),
    /** A notification for each new transaction, with category buttons. */
    val transactionNotifications: Boolean = true,
    /** Amounts shown as ₹•••• everywhere, for using the app in public. */
    val hideAmounts: Boolean = false,
    /** Look for a newer version on GitHub, at most once a day. */
    val checkUpdates: Boolean = true,
    val lastUpdateCheck: Long? = null,
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
            appNotificationsEnabled = p[APP_NOTIFICATIONS] ?: false,
            notificationsAsked = p[NOTIFICATIONS_ASKED] ?: false,
            budgetAlertPercent = p[BUDGET_ALERT] ?: 90,
            alertedKeys = p[ALERTED].orEmpty(),
            transactionNotifications = p[TX_NOTIFICATIONS] ?: true,
            hideAmounts = p[HIDE_AMOUNTS] ?: false,
            checkUpdates = p[CHECK_UPDATES] ?: true,
            lastUpdateCheck = p[LAST_UPDATE_CHECK],
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
    suspend fun setAppNotificationsEnabled(value: Boolean) = store.edit { it[APP_NOTIFICATIONS] = value }
    suspend fun setNotificationsAsked() = store.edit { it[NOTIFICATIONS_ASKED] = true }
    suspend fun setBudgetAlertPercent(value: Int) = store.edit { it[BUDGET_ALERT] = value }
    suspend fun setTransactionNotifications(value: Boolean) = store.edit { it[TX_NOTIFICATIONS] = value }
    suspend fun setHideAmounts(value: Boolean) = store.edit { it[HIDE_AMOUNTS] = value }
    suspend fun setCheckUpdates(value: Boolean) = store.edit { it[CHECK_UPDATES] = value }
    suspend fun setLastUpdateCheck(at: Long) = store.edit { it[LAST_UPDATE_CHECK] = at }

    /** Remembers an alert as sent. Keys older than the last few months are dropped so the set stays small. */
    suspend fun addAlerted(key: String) = store.edit {
        val keep = it[ALERTED].orEmpty().toMutableList().apply { add(key) }.takeLast(300).toSet()
        it[ALERTED] = keep
    }

    private companion object {
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val THEME = stringPreferencesKey("theme")
        val SMS_ENABLED = booleanPreferencesKey("sms_enabled")
        val SMS_CURSOR = longPreferencesKey("sms_cursor")
        val LAST_SMS_SCAN = longPreferencesKey("last_sms_scan")
        val LAST_SMS_RESULT = stringPreferencesKey("last_sms_result")
        val SMS_PROMPT_DISMISSED = booleanPreferencesKey("sms_prompt_dismissed")
        val APP_NOTIFICATIONS = booleanPreferencesKey("app_notifications")
        val NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")
        val BUDGET_ALERT = androidx.datastore.preferences.core.intPreferencesKey("budget_alert_percent")
        val ALERTED = androidx.datastore.preferences.core.stringSetPreferencesKey("alerted_keys")
        val TX_NOTIFICATIONS = booleanPreferencesKey("transaction_notifications")
        val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
        val CHECK_UPDATES = booleanPreferencesKey("check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }
}
