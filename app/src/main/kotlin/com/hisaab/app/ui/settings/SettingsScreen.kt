package com.hisaab.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.BuildConfig
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.more.MoreScaffold
import com.hisaab.app.ui.more.PrivacyNote
import com.hisaab.app.ui.more.listPadding
import com.hisaab.app.ui.theme.Hx
import com.hisaab.app.update.Release
import com.hisaab.app.update.UpdateState

/**
 * About & updates. Everything else that used to live here has its own screen under More:
 * Data sources, Security & backup, Notifications & alerts, and Customise.
 */
@Composable
fun SettingsRoute(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val update by vm.update.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    MoreScaffold(t("About & updates"), onBack, snackbar = snackbar) { inner ->
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            item("about") {
                HCard {
                    Text("DhanKosh", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Version {version}", "version" to BuildConfig.VERSION_NAME), fontSize = 13.sp, color = Hx.text2)
                        com.hisaab.app.ui.more.WhatsNewButton(Modifier.size(32.dp))
                    }
                }
            }
            item("update") { HCard(title = t("Updates")) { UpdateRow(update, onCheck = vm::checkForUpdate, onInstall = vm::installUpdate) } }
            item("privacy") { PrivacyNote() }
        }
    }
}

@Composable
private fun UpdateRow(state: UpdateState, onCheck: () -> Unit, onInstall: (Release) -> Unit) {
    val version = BuildConfig.VERSION_NAME
    when (state) {
        is UpdateState.Available -> HRow(t("DhanKosh {version} is available", "version" to state.release.version), t("You have {version}", "version" to version)) {
            com.hisaab.app.ui.more.WhatsNewButton(Modifier.size(36.dp))
            Button(onClick = { onInstall(state.release) }) { Text(t("Install")) }
        }
        is UpdateState.Downloading -> Column(Modifier.padding(vertical = 10.dp)) {
            Text(t("Downloading {version}…", "version" to state.release.version), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        else -> HRow(
            t("Check for updates"),
            when (state) {
                UpdateState.Checking -> t("Checking…")
                UpdateState.UpToDate -> t("You have the latest version.")
                is UpdateState.Failed -> state.message
                else -> t("Checked daily")
            },
        ) {
            OutlinedButton(onClick = onCheck, enabled = state != UpdateState.Checking) { Text(t("Check")) }
        }
    }
}
