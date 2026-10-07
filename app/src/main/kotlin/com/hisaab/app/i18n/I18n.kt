package com.hisaab.app.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Languages DhanKosh can show. English is built in; the others are downloaded when the user picks them. */
enum class Language(val code: String, val english: String, val native: String) {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "Hindi", "हिन्दी"),
    BENGALI("bn", "Bengali", "বাংলা"),
    TELUGU("te", "Telugu", "తెలుగు"),
    MARATHI("mr", "Marathi", "मराठी"),
    TAMIL("ta", "Tamil", "தமிழ்"),
    GUJARATI("gu", "Gujarati", "ગુજરાતી"),
    KANNADA("kn", "Kannada", "ಕನ್ನಡ");

    companion object {
        fun of(code: String?): Language = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

/**
 * The active translation: English text → translated text. It is Compose state, so changing the language
 * redraws every screen. A plain map lookup per string, so it costs nothing measurable.
 */
object I18n {
    var strings: Map<String, String> by mutableStateOf(emptyMap())
        internal set
    var language: Language by mutableStateOf(Language.ENGLISH)
        internal set
}

/**
 * The text in the user's language. [text] is the English wording, which is also the key; `{name}`
 * placeholders are filled from [args]. Anything not translated stays in English, so nothing is ever blank.
 */
fun t(text: String, vararg args: Pair<String, Any?>): String {
    var s = I18n.strings[text] ?: text
    for ((k, v) in args) s = s.replace("{$k}", v.toString())
    return s
}

private val Context.langStore: DataStore<Preferences> by preferencesDataStore(name = "language")

/** Download state of one language pack. */
data class PackInfo(val language: Language, val downloaded: Boolean, val sizeBytes: Long)

/**
 * Language packs: JSON files published in the app's GitHub repository (lang/<code>.json), downloaded only
 * when the user chooses that language and kept in app storage. Only the pack is fetched; nothing is sent.
 */
@Singleton
class LanguageStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val store = context.langStore
    private val dir: File get() = File(context.filesDir, "lang").apply { mkdirs() }

    val selected: Flow<Language> = store.data.map { Language.of(it[KEY]) }

    fun isDownloaded(l: Language) = l == Language.ENGLISH || File(dir, "${l.code}.json").exists()

    fun packs(): List<PackInfo> = Language.entries.map { l ->
        val f = File(dir, "${l.code}.json")
        PackInfo(l, l == Language.ENGLISH || f.exists(), if (f.exists()) f.length() else 0L)
    }

    /** Loads the saved choice at start-up. Falls back to English if its pack is missing or broken. */
    suspend fun restore() {
        val l = selected.first()
        if (l == Language.ENGLISH) return
        runCatching { apply(l) }.onFailure { I18n.language = Language.ENGLISH; I18n.strings = emptyMap() }
    }

    /** Downloads [l]'s pack (replacing any older copy). Throws on network or format errors. */
    suspend fun download(l: Language) = withContext(Dispatchers.IO) {
        require(l != Language.ENGLISH)
        val c = (URL("$BASE/${l.code}.json").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000
        }
        val body = try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
        val parsed = parse(String(body, Charsets.UTF_8))
        require(parsed.isNotEmpty()) { "Empty pack" }
        val tmp = File(dir, "${l.code}.json.tmp")
        tmp.writeBytes(body)
        tmp.renameTo(File(dir, "${l.code}.json"))
    }

    /** Switches the app to [l] (its pack must be downloaded) and remembers the choice. */
    suspend fun use(l: Language) {
        apply(l)
        store.edit { it[KEY] = l.code }
    }

    /** Deletes a downloaded pack; if it was in use, the app goes back to English. */
    suspend fun delete(l: Language) {
        if (l == Language.ENGLISH) return
        File(dir, "${l.code}.json").delete()
        if (I18n.language == l) use(Language.ENGLISH)
    }

    private suspend fun apply(l: Language) {
        val map = if (l == Language.ENGLISH) emptyMap() else withContext(Dispatchers.IO) { parse(File(dir, "${l.code}.json").readText()) }
        I18n.strings = map
        I18n.language = l
    }

    private fun parse(json: String): Map<String, String> {
        val o = JSONObject(json)
        val s = o.optJSONObject("strings") ?: return emptyMap()
        val out = HashMap<String, String>(s.length() * 2)
        for (k in s.keys()) s.optString(k).takeIf { it.isNotBlank() }?.let { out[k] = it }
        return out
    }

    private companion object {
        const val BASE = "https://raw.githubusercontent.com/DhyanendraSinghSikarwar/hisaab_apk/main/lang"
        val KEY = stringPreferencesKey("language")
    }
}
