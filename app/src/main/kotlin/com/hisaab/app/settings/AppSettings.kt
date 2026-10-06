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

/** The colour palette the whole app is drawn in. Colours live in ui/theme; this is only the choice. */
enum class ThemePalette(val label: String) {
    CLASSIC("Classic"), EMERALD("Emerald"), GRAPHITE("Graphite"), INDIGO("Indigo Night"), SAFFRON("Saffron"), OCEAN("Ocean"),
}

/** Who uses this copy of Hisaab. Stays on the phone; nothing here is sent anywhere. */
data class Profile(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val occupation: String? = null,
    /** App-private copy of the chosen photo. */
    val photoPath: String? = null,
)

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
    /** Amounts shown as ₹•••• everywhere until the user taps the eye. On by default. */
    val hideAmounts: Boolean = true,
    /** The user's name, shown at the top of Home. Part of the profile. */
    val displayName: String? = null,
    val profile: Profile = Profile(),
    /** The user tapped "Later" on the first-run profile prompt. */
    val profilePromptDismissed: Boolean = false,
    /** Look for a newer version on GitHub, at most once a day. */
    val checkUpdates: Boolean = true,
    val lastUpdateCheck: Long? = null,
    val palette: ThemePalette = ThemePalette.CLASSIC,
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
            hideAmounts = p[HIDE_AMOUNTS] ?: true,
            checkUpdates = p[CHECK_UPDATES] ?: true,
            displayName = p[DISPLAY_NAME],
            profile = Profile(p[DISPLAY_NAME], p[PROFILE_EMAIL], p[PROFILE_PHONE], p[PROFILE_OCCUPATION], p[PROFILE_PHOTO]),
            profilePromptDismissed = p[PROFILE_PROMPT_DISMISSED] ?: false,
            lastUpdateCheck = p[LAST_UPDATE_CHECK],
            palette = p[THEME_PALETTE]?.let { runCatching { ThemePalette.valueOf(it) }.getOrNull() } ?: ThemePalette.CLASSIC,
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
    suspend fun setPalette(palette: ThemePalette) = store.edit { it[THEME_PALETTE] = palette.name }
    suspend fun setSmsEnabled(value: Boolean) = store.edit { it[SMS_ENABLED] = value }
    suspend fun setSmsPromptDismissed(value: Boolean) = store.edit { it[SMS_PROMPT_DISMISSED] = value }
    suspend fun setAppNotificationsEnabled(value: Boolean) = store.edit { it[APP_NOTIFICATIONS] = value }
    suspend fun setNotificationsAsked() = store.edit { it[NOTIFICATIONS_ASKED] = true }
    suspend fun setBudgetAlertPercent(value: Int) = store.edit { it[BUDGET_ALERT] = value }
    suspend fun setTransactionNotifications(value: Boolean) = store.edit { it[TX_NOTIFICATIONS] = value }
    suspend fun setHideAmounts(value: Boolean) = store.edit { it[HIDE_AMOUNTS] = value }
    suspend fun setCheckUpdates(value: Boolean) = store.edit { it[CHECK_UPDATES] = value }
    suspend fun setDisplayName(value: String) = store.edit { if (value.isBlank()) it.remove(DISPLAY_NAME) else it[DISPLAY_NAME] = value.trim() }
    suspend fun saveProfile(name: String, email: String, phone: String, occupation: String) = store.edit {
        fun put(key: androidx.datastore.preferences.core.Preferences.Key<String>, v: String) { if (v.isBlank()) it.remove(key) else it[key] = v.trim() }
        put(DISPLAY_NAME, name); put(PROFILE_EMAIL, email); put(PROFILE_PHONE, phone); put(PROFILE_OCCUPATION, occupation)
    }
    suspend fun setProfilePhoto(path: String?) = store.edit { if (path == null) it.remove(PROFILE_PHOTO) else it[PROFILE_PHOTO] = path }
    suspend fun dismissProfilePrompt() = store.edit { it[PROFILE_PROMPT_DISMISSED] = true }
    suspend fun setLastUpdateCheck(at: Long) = store.edit { it[LAST_UPDATE_CHECK] = at }

    /** Remembers an alert as sent. Keys older than the last few months are dropped so the set stays small. */
    suspend fun addAlerted(key: String) = store.edit {
        val keep = it[ALERTED].orEmpty().toMutableList().apply { add(key) }.takeLast(300).toSet()
        it[ALERTED] = keep
    }

    private companion object {
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val THEME = stringPreferencesKey("theme")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
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
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PROFILE_EMAIL = stringPreferencesKey("profile_email")
        val PROFILE_PHONE = stringPreferencesKey("profile_phone")
        val PROFILE_OCCUPATION = stringPreferencesKey("profile_occupation")
        val PROFILE_PHOTO = stringPreferencesKey("profile_photo")
        val PROFILE_PROMPT_DISMISSED = booleanPreferencesKey("profile_prompt_dismissed")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }
}
