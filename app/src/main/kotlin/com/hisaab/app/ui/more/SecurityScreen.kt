package com.hisaab.app.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.security.AppLockGate
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.settings.SettingsViewModel
import com.hisaab.app.ui.theme.Hx
import java.time.LocalDate

/** App lock, hiding amounts, and CSV backup. Nothing else. */
@Composable
fun SecurityRoute(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    val canLock = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    MoreScaffold("Security & backup", onBack, snackbar = snackbar) { inner ->
        val app = s.app ?: return@MoreScaffold
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            item("privacy") {
                HCard {
                    SettingSwitch(
                        "App lock", if (canLock) "Fingerprint, face or screen lock" else "Set a screen lock on this phone first",
                        app.appLock, vm::setAppLock, enabled = canLock,
                    )
                    SettingSwitch("Hide amounts", "Mask balances and totals when the app opens", app.hideAmounts, vm::setHideAmounts)
                }
            }
            item("backup") {
                HCard(title = "Backup") {
                    HRow("Export CSV", "Every transaction, to a file you choose") {
                        OutlinedButton(onClick = { AppLockGate.skipNextLock(); exportLauncher.launch("hisaab-${LocalDate.now()}.csv") }) { Text("Export") }
                    }
                    HRow("Import CSV", "Rows already present are skipped") {
                        OutlinedButton(onClick = {
                            AppLockGate.skipNextLock()
                            importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                        }) { Text("Import") }
                    }
                }
            }
            item("note") { PrivacyNote() }
        }
    }
}

/** "Your data never leaves this phone." with a lock. */
@Composable
internal fun PrivacyNote(modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Lock, null, tint = Hx.accent, modifier = Modifier.size(16.dp))
        Text(
            "Your data never leaves this phone. No servers, no tracking.",
            fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(start = 8.dp),
        )
    }
}
