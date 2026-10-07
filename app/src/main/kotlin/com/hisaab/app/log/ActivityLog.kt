package com.hisaab.app.log

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisaab.email.sync.MailConnection
import com.hisaab.email.sync.SyncActivity
import com.hisaab.email.sync.SyncReport
import com.hisaab.shared.db.ProcessedEmailDao
import com.hisaab.shared.db.StatementEntity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Where a sync run read from. */
enum class ActivitySource(val label: String) {
    SMS_SCAN("SMS scan"),
    SMS_LIVE("Live SMS"),
    GMAIL("Gmail"),
    IMAP("IMAP"),
    STATEMENTS("Statements"),
    NOTIFICATIONS("Notifications"),
}

/**
 * One sync run: when, from where, and counts only. Never holds message text, senders or amounts.
 * Live SMS and notification runs within [ActivityLog.COALESCE_MS] of each other share one entry ([runs]).
 */
data class ActivityEntry(
    val at: Long,
    val source: ActivitySource,
    /** Messages (SMS or notifications) looked at. */
    val read: Int = 0,
    /** Of [read], those from banks or payment apps. */
    val relevant: Int = 0,
    val added: Int = 0,
    val merged: Int = 0,
    val review: Int = 0,
    val skipped: Int = 0,
    val emails: Int = 0,
    val statements: Int = 0,
    val locked: Int = 0,
    val millis: Long = 0,
    /** Error class (e.g. "IOException", "auth"), never its message. */
    val error: String? = null,
    val runs: Int = 1,
) {
    internal fun toJson(): JSONObject = JSONObject().apply {
        put("at", at); put("src", source.name); put("read", read); put("rel", relevant); put("add", added); put("mrg", merged)
        put("rev", review); put("skp", skipped); put("eml", emails); put("st", statements); put("lck", locked); put("ms", millis)
        put("runs", runs); error?.let { put("err", it) }
    }

    internal operator fun plus(o: ActivityEntry) = copy(
        read = read + o.read, relevant = relevant + o.relevant, added = added + o.added, merged = merged + o.merged,
        review = review + o.review, skipped = skipped + o.skipped, millis = millis + o.millis, error = o.error ?: error, runs = runs + o.runs,
    )

    internal companion object {
        fun fromJson(o: JSONObject): ActivityEntry? {
            val source = runCatching { ActivitySource.valueOf(o.getString("src")) }.getOrNull() ?: return null
            return ActivityEntry(
                o.getLong("at"), source, o.optInt("read"), o.optInt("rel"), o.optInt("add"), o.optInt("mrg"), o.optInt("rev"),
                o.optInt("skp"), o.optInt("eml"), o.optInt("st"), o.optInt("lck"), o.optLong("ms"),
                o.optString("err").ifEmpty { null }, o.optInt("runs", 1),
            )
        }
    }
}

private val Context.activityStore: DataStore<Preferences> by preferencesDataStore(name = "activity_log")

/** The on-phone activity log: the newest [MAX_ENTRIES] sync runs, as JSON in DataStore. Never leaves the device. */
@Singleton
class ActivityLog @Inject constructor(
    @ApplicationContext context: Context,
    private val emails: ProcessedEmailDao,
) : SyncActivity {
    private val store = context.activityStore

    /** Newest first. */
    val entries: Flow<List<ActivityEntry>> = store.data.map { decode(it[KEY]) }

    suspend fun record(entry: ActivityEntry) {
        runCatching {
            store.edit { prefs ->
                val list = decode(prefs[KEY]).toMutableList()
                val head = list.firstOrNull()
                val live = entry.source == ActivitySource.SMS_LIVE || entry.source == ActivitySource.NOTIFICATIONS
                if (live && head != null && head.source == entry.source && entry.at - head.at in 0..COALESCE_MS) {
                    list[0] = head + entry
                } else {
                    list.add(0, entry)
                }
                prefs[KEY] = JSONArray().apply { list.take(MAX_ENTRIES).forEach { put(it.toJson()) } }.toString()
            }
        }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    /** An email sync: counts from the report, statements from the rows written since [startedAt]. */
    override suspend fun record(connection: MailConnection, report: SyncReport?, startedAt: Long, error: String?) {
        val source = if (connection == MailConnection.IMAP) ActivitySource.IMAP else ActivitySource.GMAIL
        val statuses = runCatching { emails.statementStatusSince(startedAt) }.getOrDefault(emptyList())
        val ingest = report?.ingest
        record(
            ActivityEntry(
                at = startedAt, source = source,
                added = ingest?.inserted ?: 0, merged = ingest?.merged ?: 0, review = ingest?.flagged ?: 0, skipped = ingest?.skipped ?: 0,
                emails = report?.fetched ?: 0,
                statements = statuses.sumOf { it.count }, locked = statuses.filter { it.status == StatementEntity.LOCKED }.sumOf { it.count },
                millis = System.currentTimeMillis() - startedAt, error = error,
            ),
        )
    }

    private fun decode(raw: String?): List<ActivityEntry> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { ActivityEntry.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val MAX_ENTRIES = 200
        /** Live SMS and notifications this close together are one entry. */
        const val COALESCE_MS = 30 * 60 * 1000L
        private val KEY = stringPreferencesKey("entries")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ActivityLogModule {
    @Binds abstract fun syncActivity(impl: ActivityLog): SyncActivity
}
