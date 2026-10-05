package com.hisaab.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hisaab.app.BuildConfig
import com.hisaab.app.settings.AppSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** A newer release on GitHub. [sha256] comes from the release notes the build pipeline writes. */
data class Release(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long, val sha256: String?)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Updates from the project's public GitHub Releases. The check sends nothing but a plain request for the
 * latest release; no data about you or your money is included. The downloaded APK is checked against the
 * SHA-256 in the release notes, and Android itself refuses any APK not signed with the Hisaab key.
 */
@Singleton
class Updater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettingsStore,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** At most once a day, and only if the user hasn't switched update checks off. */
    suspend fun checkIfDue() {
        val s = settings.settings.first()
        if (!s.checkUpdates) return
        if (System.currentTimeMillis() - (s.lastUpdateCheck ?: 0) < DAY_MS) return
        check()
    }

    suspend fun check() {
        _state.value = UpdateState.Checking
        _state.value = try {
            val r = withContext(Dispatchers.IO) { latest() }
            settings.setLastUpdateCheck(System.currentTimeMillis())
            if (r != null && isNewer(r.version, BuildConfig.VERSION_NAME)) UpdateState.Available(r) else UpdateState.UpToDate
        } catch (e: Exception) {
            UpdateState.Failed("Couldn't check for updates: ${e.message ?: "no connection"}")
        }
    }

    /** Downloads the APK, verifies it, and opens Android's installer. */
    suspend fun downloadAndInstall(release: Release) {
        _state.value = UpdateState.Downloading(release, 0f)
        try {
            val file = withContext(Dispatchers.IO) { download(release) }
            if (release.sha256 != null && !sha256(file).equals(release.sha256, ignoreCase = true)) {
                file.delete()
                _state.value = UpdateState.Failed("The download didn't match its checksum, so it wasn't installed. Try again.")
                return
            }
            _state.value = UpdateState.Available(release)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Download failed: ${e.message ?: "no connection"}")
        }
    }

    private fun latest(): Release? {
        val c = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode !in 200..299) throw IllegalStateException("GitHub answered ${c.responseCode}")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
            val notes = json.optString("body")
            return Release(
                version = json.getString("tag_name").removePrefix("v"),
                notes = notes.lineSequence().filterNot { it.startsWith("sha256:") }.joinToString("\n").trim(),
                apkUrl = apk.getString("browser_download_url"), sizeBytes = apk.optLong("size"),
                sha256 = SHA_LINE.find(notes)?.groupValues?.get(1),
            )
        } finally {
            c.disconnect()
        }
    }

    private fun download(release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "Hisaab-${release.version}.apk")
        var url = URL(release.apkUrl)
        var c = url.openConnection() as HttpURLConnection
        // GitHub serves assets through a redirect to its CDN.
        repeat(5) {
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            if (c.responseCode in 300..399) { url = URL(c.getHeaderField("Location")); c.disconnect(); c = url.openConnection() as HttpURLConnection } else return@repeat
        }
        c.inputStream.use { input ->
            out.outputStream().use { output ->
                val total = c.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    output.write(buf, 0, n); done += n
                    if (total > 0) _state.value = UpdateState.Downloading(release, done.toFloat() / total)
                }
            }
        }
        c.disconnect()
        return out
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val REPO = "DhyanendraSinghSikarwar/hisaab_apk"
        private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private val SHA_LINE = Regex("""sha256:\s*([0-9a-fA-F]{64})""")

        /** "1.10.0" is newer than "1.9.2". Non-numeric parts count as 0. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
