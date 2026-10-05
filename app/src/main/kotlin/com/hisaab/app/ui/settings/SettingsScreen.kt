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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.hisaab.app.notify.PaymentNotificationListener
import com.hisaab.app.ui.components.Info
import com.hisaab.app.ui.components.InfoButton
import com.hisaab.app.ui.components.rememberSmsPermission
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hisaab.app.ui.format.Periods
import com.hisaab.email.auth.ConnectResult
import com.hisaab.email.sync.GmailSettings
import com.hisaab.email.sync.MailConnection
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onOpenBench: () -> Unit,
    onOpenStatements: () -> Unit,
    onOpenInvestments: () -> Unit,
    contentPadding: PaddingValues,
    onOpenProfile: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val emails by vm.emails.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
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

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text("Settings") },
                navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        val app = s.app
        val g = s.gmail
        Column(Modifier.padding(top = inner.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()).verticalScroll(rememberScrollState())) {
            val lookback = g?.lookbackDays ?: GmailSettings.DEFAULT_LOOKBACK
            Section("History to read")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                GmailSettings.LOOKBACK_CHOICES.forEachIndexed { i, days ->
                    SegmentedButton(selected = lookback == days, onClick = { vm.setLookback(days) },
                        shape = SegmentedButtonDefaults.itemShape(i, GmailSettings.LOOKBACK_CHOICES.size)) { Text("${days}d") }
                }
            }

            Section("SMS")
            if (!sms.granted) {
                ListItem(
                    headlineContent = { Text("SMS access is off", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { if (sms.blocked) Text("Allow it in App info → ⋮ → Allow restricted settings.") },
                    trailingContent = { Button(onClick = sms::request) { Text(if (sms.blocked) "Settings" else "Allow") } },
                )
            }
            SwitchRow("Read bank SMS", "New messages, as they arrive", app?.smsEnabled ?: true, vm::setSmsEnabled)

            Section("Notifications")
            SwitchRow("Transaction alerts", "With quick category buttons", app?.transactionNotifications ?: true, vm::setTransactionNotifications)
            var notifAccess by remember { mutableStateOf(PaymentNotificationListener.hasAccess(context)) }
            LifecycleResumeEffect(Unit) { notifAccess = PaymentNotificationListener.hasAccess(context); onPauseOrDispose { } }
            val openNotifAccess = {
                AppLockGate.skipNextLock()
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            SwitchRow(
                "UPI app payments", "From GPay, PhonePe, Paytm and others with no bank SMS",
                app?.appNotificationsEnabled ?: false,
                { on -> vm.setAppNotifications(on); if (on && !notifAccess) openNotifAccess() },
            )
            if (app?.appNotificationsEnabled == true && !notifAccess) {
                ListItem(
                    headlineContent = { Text("Notification access needed", color = MaterialTheme.colorScheme.error) },
                    trailingContent = { Button(onClick = openNotifAccess) { Text("Allow") } },
                )
            }

            Section("Email")
            if (g == null || !g.connected) {
                ListItem(
                    headlineContent = { Text("Connect email") },
                    supportingContent = { Text("Bank alerts and statements") },
                    leadingContent = { Icon(androidx.compose.material.icons.Icons.Filled.Email, null) },
                    trailingContent = { Button(onClick = { connectingEmail = true }) { Text("Connect") } },
                )
            } else {
                val imap = g.connection == MailConnection.IMAP
                if (g.needsReauth) {
                    ListItem(headlineContent = { Text("Sign in again", color = MaterialTheme.colorScheme.error) },
                        trailingContent = { Button(onClick = { if (imap) connectingEmail = true else connectGoogle() }) { Text("Sign in") } })
                }
                SwitchRow("Read email", g.lastSyncAt?.let { "Last synced ${Periods.dateTime(it)}" } ?: "Not synced yet", g.enabled, vm::setGmailEnabled)
                if (imap) {
                    emails.forEach { address ->
                        ListItem(
                            headlineContent = { Text(address) },
                            leadingContent = { Icon(androidx.compose.material.icons.Icons.Filled.Email, null) },
                            trailingContent = { TextButton(onClick = { vm.removeEmail(address) }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Add another email") },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Filled.Add, null) },
                        modifier = Modifier.clickable { connectingEmail = true },
                    )
                } else {
                    ListItem(headlineContent = { Text(g.accountEmail ?: "Google account") }, leadingContent = { Icon(androidx.compose.material.icons.Icons.Filled.Email, null) })
                }
                ListItem(
                    headlineContent = { Text("Bank senders") },
                    supportingContent = { Text("${g.senders.size} in the filter") },
                    modifier = Modifier.clickable { editingSenders = true },
                    trailingContent = { TextButton(onClick = { editingSenders = true }) { Text("Edit") } },
                )
                ListItem(
                    headlineContent = { Text(if (imap) "Disconnect email" else "Sign out") },
                    trailingContent = { OutlinedButton(onClick = { confirmSignOut = true }) { Text("Disconnect") } },
                )
            }

            Section("Security")
            val canLock = remember {
                BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
            }
            SwitchRow("App lock", if (canLock) "Fingerprint, face or screen lock" else "Set a screen lock on this phone first",
                app?.appLock ?: false, vm::setAppLock, enabled = canLock)

            Section("Appearance")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(selected = (app?.theme ?: ThemeMode.SYSTEM) == mode, onClick = { vm.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }

            Section("Data")
            ListItem(headlineContent = { Text("Export CSV") },
                trailingContent = { OutlinedButton(onClick = { AppLockGate.skipNextLock(); exportLauncher.launch("hisaab-${LocalDate.now()}.csv") }) { Text("Export") } })
            ListItem(headlineContent = { Text("Import CSV") },
                trailingContent = { OutlinedButton(onClick = { AppLockGate.skipNextLock(); importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) }) { Text("Import") } })

            Section("About & updates")
            UpdateRow(update, onCheck = vm::checkForUpdate, onInstall = vm::installUpdate)

            Section("Privacy")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Your data never leaves this phone. No account, no servers, no tracking.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp),
                )
            }

            Text(
                "Hisaab v${com.hisaab.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp),
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
private fun UpdateRow(state: com.hisaab.app.update.UpdateState, onCheck: () -> Unit, onInstall: (com.hisaab.app.update.Release) -> Unit) {
    val version = com.hisaab.app.BuildConfig.VERSION_NAME
    when (state) {
        is com.hisaab.app.update.UpdateState.Available -> ListItem(
            headlineContent = { Text("Hisaab ${state.release.version} is available") },
            supportingContent = { Text("You have $version") },
            trailingContent = { Button(onClick = { onInstall(state.release) }) { Text("Install") } },
        )
        is com.hisaab.app.update.UpdateState.Downloading -> ListItem(
            headlineContent = { Text("Downloading ${state.release.version}…") },
            supportingContent = { androidx.compose.material3.LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) },
        )
        else -> ListItem(
            headlineContent = { Text("Check for updates") },
            supportingContent = {
                Text(
                    when (state) {
                        com.hisaab.app.update.UpdateState.Checking -> "Checking…"
                        com.hisaab.app.update.UpdateState.UpToDate -> "You have the latest version."
                        is com.hisaab.app.update.UpdateState.Failed -> state.message
                        else -> "Checked daily"
                    },
                )
            },
            trailingContent = { OutlinedButton(onClick = onCheck, enabled = state != com.hisaab.app.update.UpdateState.Checking) { Text("Check") } },
        )
    }
}

@Composable
private fun Section(title: String, info: Array<String>? = null) {
    Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 6.dp))
    }
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
