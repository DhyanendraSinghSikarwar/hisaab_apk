package com.hisaab.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.listsStore: DataStore<Preferences> by preferencesDataStore(name = "local_lists")

/** Net worth on a day: assets and liabilities, recorded once a day while the app is used. */
data class WorthPoint(val day: LocalDate, val assetsMinor: Long, val liabilitiesMinor: Long) {
    val netMinor: Long get() = assetsMinor - liabilitiesMinor
}

/** Net-worth history, one point a day. On the phone only, as JSON in DataStore. */
@Singleton
class LocalListsStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.listsStore

    val worth: Flow<List<WorthPoint>> = store.data.map { p -> parse(p[WORTH]) { o ->
        WorthPoint(LocalDate.parse(o.getString("day")), o.getLong("a"), o.getLong("l"))
    }.sortedBy { it.day } }

    /** Records today's net worth (replacing any earlier figure for today). Keeps about three years. */
    suspend fun recordWorth(assets: Long, liabilities: Long) {
        val today = LocalDate.now()
        edit(WORTH, { it.getString("day") == today.toString() }) { JSONObject().put("day", today.toString()).put("a", assets).put("l", liabilities) }
        store.edit { p ->
            val arr = JSONArray(p[WORTH] ?: "[]")
            if (arr.length() > 1100) p[WORTH] = JSONArray((arr.length() - 1100 until arr.length()).map { arr.get(it) }).toString()
        }
    }

    private suspend fun edit(key: Preferences.Key<String>, match: (JSONObject) -> Boolean, make: (() -> JSONObject)?) {
        store.edit { p ->
            val arr = JSONArray(p[key] ?: "[]")
            val out = JSONArray()
            var replaced = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (match(o)) { if (make != null && !replaced) { out.put(make()); replaced = true } } else out.put(o)
            }
            if (make != null && !replaced) out.put(make())
            p[key] = out.toString()
        }
    }

    private fun <T> parse(json: String?, read: (JSONObject) -> T): List<T> {
        val arr = runCatching { JSONArray(json ?: "[]") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { runCatching { read(arr.getJSONObject(it)) }.getOrNull() }
    }

    private companion object {
        val WORTH = stringPreferencesKey("worth")
    }
}
