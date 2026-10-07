package com.hisaab.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.BuildConfig
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.theme.Hx
import com.hisaab.app.update.UpdateState
import com.hisaab.app.update.Updater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** One version's user-facing notes. */
data class ChangeEntry(val version: String, val points: List<String>)

/** Notes shipped inside the app, newest first. Strings go through [t] when the list is built. */
fun bundledChangelog(): List<ChangeEntry> = listOf(
    ChangeEntry("2.7.0", listOf(
        t("Sync also re-reads the last 14 days of SMS"),
        t("Separate Credit cards card on Home with the billed amount to pay"),
        t("Choose which money counts in net worth"),
        t("Add and remove loans"),
        t("What's new and a Support page"),
    )),
    ChangeEntry("2.6.0", listOf(
        t("New DhanKosh shield logo and colours, plus a Fold theme"),
        t("Seven language packs, downloaded on demand"),
        t("Activity log, duplicate account merge and bank, type and source filters"),
        t("Loans from NBFC lenders, with their EMIs"),
        t("NPS, Groww and mutual fund holdings with invested value and gains"),
        t("Market news from several sources, newest first"),
    )),
    ChangeEntry("2.4.0", listOf(
        t("Loan tracker with EMIs, payoff and amortisation"),
        t("Profile redesign with photo crop and zoom"),
        t("Hero and Cinema themes, pure-black dark mode"),
        t("Cleaner transaction rows with coloured amounts"),
        t("Smoother animations and haptics"),
    )),
    ChangeEntry("2.2.0", listOf(
        t("Global Personal / Business book across the app"),
        t("Net worth split by account"),
        t("CAS, Excel and CSV statements, and Groww-style fund emails"),
        t("Tax centre what-if, shareable as image or PDF"),
        t("Extra themes and softer animations"),
    )),
)

/** Strips markdown, commit hashes and checksum lines from a GitHub release body; at most [maxLines] lines. */
fun cleanReleaseNotes(body: String, maxLines: Int = 12): List<String> = body.lineSequence()
    .map { it.trim() }
    .filterNot { it.startsWith("sha256:", ignoreCase = true) || it.startsWith("full changelog", ignoreCase = true) }
    .map { line ->
        line.replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
            .replace(Regex("""^#{1,6}\s*"""), "")
            .replace(Regex("""^[-*+]\s+"""), "")
            .replace(Regex("""[*_`]{1,3}"""), "")
            .replace(Regex("""\(?\b[0-9a-f]{7,40}\b\)?"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
    .filter { it.isNotEmpty() }
    .take(maxLines)
    .toList()

@HiltViewModel
class WhatsNewViewModel @Inject constructor(updater: Updater) : ViewModel() {
    val update = updater.state
}

/** An (i) button that opens the notes for the installed version, or for the new one when an update is waiting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewButton(modifier: Modifier = Modifier, vm: WhatsNewViewModel = hiltViewModel()) {
    val update by vm.update.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Outlined.Info, t("What's new"), tint = Hx.text2, modifier = Modifier.size(20.dp))
    }
    if (!open) return

    val installed = BuildConfig.VERSION_NAME
    val bundled = remember { bundledChangelog() }
    val release = (update as? UpdateState.Available)?.release ?: (update as? UpdateState.Downloading)?.release
    val version = release?.version ?: installed
    val points = remember(release) {
        release?.notes?.let { cleanReleaseNotes(it) }?.takeIf { it.isNotEmpty() }
            ?: bundled.firstOrNull { it.version == version }?.points
            ?: emptyList()
    }
    val earlier = remember(version) { bundled.filter { it.version != version }.take(2) }

    ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(t("What's new"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (release != null) t("DhanKosh {version} is available", "version" to version) else t("Version {version}", "version" to version),
                fontSize = 13.sp, color = Hx.text2,
            )
            if (points.isEmpty()) Text(t("Release notes aren't available."), fontSize = 14.sp, color = Hx.text2)
            points.forEach { Bullet(it) }
            earlier.forEach { e ->
                Text(t("Version {version}", "version" to e.version), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Hx.text2, modifier = Modifier.padding(top = 10.dp))
                e.points.forEach { Bullet(it, dim = true) }
            }
        }
    }
}

@Composable
private fun Bullet(text: String, dim: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("•", fontSize = 14.sp, color = Hx.accent)
        Text(text, fontSize = 14.sp, color = if (dim) Hx.text2 else androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
    }
}
