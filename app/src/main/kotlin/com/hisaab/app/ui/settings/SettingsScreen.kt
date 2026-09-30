package com.hisaab.app.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.security.AppLockGate
import com.hisaab.app.settings.ThemeMode
import com.hisaab.app.ui.components.rememberSmsPermission
import com.hisaab.app.ui.format.Periods
import com.hisaab.email.auth.ConnectResult
import com.hisaab.email.sync.GmailSettings
import com.hisaab.email.sync.MailConnection
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(onOpenBench: () -> Unit, contentPadding: PaddingValues, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var pendingEmail by remember { mutableStateOf<String?>(null) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        scope.launch { vm.completeConsent(activity, result.data, pendingEmail) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    var editingSenders by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var connectingEmail by remember { mutableStateOf(false) }
    val sms = rememberSmsPermission(onGranted = vm::rescanSms)
    val connectGoogle: () -> Unit = {
        scope.launch {
            AppLockGate.skipNextLock()
            (vm.connect(activity) as? ConnectResult.NeedsConsent)?.let { r ->
                pendingEmail = r.email
                AppLockGate.skipNextLock()
                consent.launch(IntentSenderRequest.Builder(r.intent.intentSender).build())
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        val app = s.app
        val g = s.gmail
        Column(Modifier.padding(top = inner.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()).verticalScroll(rememberScrollState())) {
            val lookback = g?.lookbackDays ?: GmailSettings.DEFAULT_LOOKBACK
            Section("Fetch history")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("How far back to read SMS and email", style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    GmailSettings.LOOKBACK_CHOICES.forEachIndexed { i, days ->
                        SegmentedButton(selected = lookback == days, onClick = { vm.setLookback(days) },
                            shape = SegmentedButtonDefaults.itemShape(i, GmailSettings.LOOKBACK_CHOICES.size)) { Text("${days}d") }
                    }
                }
                Text("Changing it rescans the SMS inbox and re-syncs email for the new period. Transactions already found stay.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }

            Section("SMS")
            if (!sms.granted) {
                ListItem(
                    headlineContent = { Text("SMS access is off", color = MaterialTheme.colorScheme.error) },
                    supportingContent = {
                        Text(if (sms.blocked) "Allow SMS in App settings. If it is greyed out, use the menu in App info: Allow restricted settings."
                        else "Needed to read bank alerts from your inbox.")
                    },
                    trailingContent = { Button(onClick = sms::request) { Text(if (sms.blocked) "Settings" else "Allow") } },
                )
            }
            SwitchRow("Read bank SMS", "Real-time for new messages, plus inbox scans", app?.smsEnabled ?: true, vm::setSmsEnabled)
            ListItem(
                headlineContent = { Text(if (s.smsScanning) "Scanning inbox…" else "Rescan last $lookback days") },
                supportingContent = { Text(app?.lastSmsResult ?: "Not scanned yet") },
                trailingContent = { OutlinedButton(onClick = vm::rescanSms, enabled = !s.smsScanning && sms.granted) { Text("Rescan") } },
            )

            Section("Email")
            if (g == null || !g.connected) {
                ListItem(
                    headlineContent = { Text("Connect email") },
                    supportingContent = { Text("Enter your email, sign in, and confirm the code we mail you. Bank alerts are then read on this phone only.") },
                    trailingContent = { Button(onClick = { connectingEmail = true }) { Text("Connect") } },
                )
            } else {
                val imap = g.connection == MailConnection.IMAP
                if (g.needsReauth) {
                    ListItem(headlineContent = { Text("Sign in again", color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text(if (imap) "Your email provider refused the saved app password." else "Google needs you to approve Gmail access again.") },
                        trailingContent = { Button(onClick = { if (imap) connectingEmail = true else connectGoogle() }) { Text("Sign in") } })
                }
                SwitchRow("Sync email", (g.accountEmail ?: "Connected") + if (imap) " · email sign-in" else " · Google sign-in", g.enabled, vm::setGmailEnabled)
                ListItem(
                    headlineContent = { Text(if (s.gmailSyncing) "Syncing…" else "Sync now") },
                    supportingContent = { Text((g.lastResult ?: "Not synced yet") + (g.lastSyncAt?.let { "\nLast: " + Periods.dateTime(it) } ?: "") + "\n${s.processedEmails} emails processed") },
                    trailingContent = { OutlinedButton(onClick = vm::syncGmailNow, enabled = g.enabled && !s.gmailSyncing) { Text("Sync") } },
                )
                ListItem(
                    headlineContent = { Text("Bank senders") },
                    supportingContent = { Text("${g.senders.size} addresses and domains in the email filter") },
                    modifier = Modifier.clickable { editingSenders = true },
                    trailingContent = { TextButton(onClick = { editingSenders = true }) { Text("Edit") } },
                )
                SwitchRow("Read PDF statements", "Unlocked PDF attachments go through the same parser", g.readPdfStatements, vm::setReadPdf)
                ListItem(
                    headlineContent = { Text(if (imap) "Disconnect email" else "Sign out and wipe tokens") },
                    supportingContent = {
                        Text(if (imap) "Deletes the saved app password and its key. Transactions stay."
                        else "Revokes access and deletes the encrypted token and its key. Transactions stay.")
                    },
                    trailingContent = { OutlinedButton(onClick = { confirmSignOut = true }) { Text("Sign out") } },
                )
            }

            Section("Security")
            val canLock = remember {
                BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
            }
            SwitchRow("App lock", if (canLock) "Fingerprint, face, or screen lock when opening Hisaab" else "Set a screen lock on this phone first",
                app?.appLock ?: false, vm::setAppLock, enabled = canLock)

            Section("Appearance")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(selected = (app?.theme ?: ThemeMode.SYSTEM) == mode, onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            }

            Section("Data")
            ListItem(headlineContent = { Text("Export CSV") }, supportingContent = { Text("All transactions, readable by any spreadsheet") },
                trailingContent = { OutlinedButton(onClick = { AppLockGate.skipNextLock(); exportLauncher.launch("hisaab-${LocalDate.now()}.csv") }) { Text("Export") } })
            ListItem(headlineContent = { Text("Import CSV") }, supportingContent = { Text("A Hisaab export; rows already present are skipped") },
                trailingContent = { OutlinedButton(onClick = { AppLockGate.skipNextLock(); importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) }) { Text("Import") } })

            Section("Tools")
            ListItem(headlineContent = { Text("Test the parser") }, supportingContent = { Text("Paste any bank SMS or email and see what is extracted") },
                leadingContent = { Icon(Icons.Filled.Science, null) }, modifier = Modifier.clickable(onClick = onOpenBench))

            Section("Privacy")
            Text(
                "Everything stays on this phone: no account, no server, no analytics. The internet permission is used only to talk to " +
                    "your email provider when you connect it: to read bank alerts, and once to mail you a verification code. " +
                    "Backups of the app's data are disabled.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (editingSenders && s.gmail != null) {
        SendersDialog(s.gmail!!.senders, onDismiss = { editingSenders = false }, onSave = { vm.setSenders(it); editingSenders = false },
            onReset = { vm.resetSenders(); editingSenders = false })
    }
    if (connectingEmail) {
        EmailConnectDialog(onDismiss = { connectingEmail = false }, onUseGoogle = { connectingEmail = false; connectGoogle() })
    }
    if (confirmSignOut) {
        AlertDialog(onDismissRequest = { confirmSignOut = false }, title = { Text("Disconnect email?") },
            text = { Text("Hisaab will stop reading email and delete the stored sign-in. Transactions already found are kept.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.disconnectGmail() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } })
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
    )
}

@Composable
private fun SendersDialog(current: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit, onReset: () -> Unit) {
    var text by remember { mutableStateOf(current.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bank senders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("One address or domain per line. A domain matches every address at it.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.lines().map { it.trim() }.filter { it.isNotEmpty() }) }) { Text("Save") } },
        dismissButton = { Row { TextButton(onClick = onReset) { Text("Defaults") }; TextButton(onClick = onDismiss) { Text("Cancel") } } },
    )
}
