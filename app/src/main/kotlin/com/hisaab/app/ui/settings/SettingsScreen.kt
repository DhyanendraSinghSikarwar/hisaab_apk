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

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text("Settings") }) }, snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        val app = s.app
        val g = s.gmail
        Column(Modifier.padding(top = inner.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()).verticalScroll(rememberScrollState())) {
            val lookback = g?.lookbackDays ?: GmailSettings.DEFAULT_LOOKBACK
            Section("Fetch history", Info.FETCH_HISTORY)
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

            Section("SMS", Info.SMS)
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

            Section("Notifications")
            SwitchRow(
                "New transaction alerts",
                "Each new transaction with its category. Expand the notification to pick a different one without opening the app; Hisaab remembers it for that merchant.",
                app?.transactionNotifications ?: true, vm::setTransactionNotifications,
            )

            Section("UPI & payment apps", Info.PAYMENT_APPS)
            var notifAccess by remember { mutableStateOf(PaymentNotificationListener.hasAccess(context)) }
            LifecycleResumeEffect(Unit) { notifAccess = PaymentNotificationListener.hasAccess(context); onPauseOrDispose { } }
            val openNotifAccess = {
                AppLockGate.skipNextLock()
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            SwitchRow(
                "Read payment app notifications",
                "For UPI payments your bank doesn't send an SMS for. Only GPay, PhonePe, Paytm, BHIM, CRED, Amazon Pay and bank apps " +
                    "are read, and only completed payments are kept. A later SMS or email for the same payment is merged, not counted twice.",
                app?.appNotificationsEnabled ?: false,
                { on -> vm.setAppNotifications(on); if (on && !notifAccess) openNotifAccess() },
            )
            if (app?.appNotificationsEnabled == true && !notifAccess) {
                ListItem(
                    headlineContent = { Text("Notification access needed", color = MaterialTheme.colorScheme.error) },
                    supportingContent = {
                        Text("Turn on Hisaab under Notification access. If it is greyed out, open App info for Hisaab, tap ⋮ and choose Allow restricted settings first.")
                    },
                    trailingContent = { Button(onClick = openNotifAccess) { Text("Allow") } },
                )
            }

            Section("Email", Info.EMAIL)
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
                SwitchRow(
                    "Sync email",
                    if (imap) "${emails.size} address${if (emails.size == 1) "" else "es"} · email sign-in" else (g.accountEmail ?: "Connected") + " · Google sign-in",
                    g.enabled, vm::setGmailEnabled,
                )
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
                        supportingContent = { Text("Statements and alerts from every connected inbox are read together.") },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Filled.Add, null) },
                        modifier = Modifier.clickable { connectingEmail = true },
                    )
                }
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
                SwitchRow(
                    "Read statement PDFs",
                    "Card, bank, CAS and broker statements attached to these emails. Password-protected ones use your saved passwords, " +
                        "or ask you once.",
                    g.readPdfStatements, vm::setReadPdf,
                )
                ListItem(
                    headlineContent = { Text("Re-read email for statements") },
                    supportingContent = { Text("Goes through the last ${g.lookbackDays} days of email again, including archived mail, and reads any statements in it. Nothing is added twice.") },
                    trailingContent = { OutlinedButton(onClick = vm::rereadEmail, enabled = g.enabled && !s.gmailSyncing) { Text("Re-read") } },
                )
                ListItem(
                    headlineContent = { Text(if (imap) "Disconnect email" else "Sign out and wipe tokens") },
                    supportingContent = {
                        Text(if (imap) "Deletes the saved app password and its key. Transactions stay."
                        else "Revokes access and deletes the encrypted token and its key. Transactions stay.")
                    },
                    trailingContent = { OutlinedButton(onClick = { confirmSignOut = true }) { Text("Sign out") } },
                )
            }

            Section("Statements & investments", Info.STATEMENTS)
            ListItem(
                headlineContent = { Text("Statements & passwords") },
                supportingContent = { Text("Card, bank and CAS statement PDFs from email or your phone. Save PDF passwords here.") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null) }, modifier = Modifier.clickable(onClick = onOpenStatements),
            )
            ListItem(
                headlineContent = { Text("Investments") },
                supportingContent = { Text("EPF from EPFO SMS, mutual funds and shares from statements, and anything you add.") },
                leadingContent = { Icon(Icons.Filled.PieChart, null) }, modifier = Modifier.clickable(onClick = onOpenInvestments),
            )

            Section("Security", Info.APP_LOCK)
            val canLock = remember {
                BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
            }
            SwitchRow("App lock", if (canLock) "Fingerprint, face, or screen lock when opening Hisaab" else "Set a screen lock on this phone first",
                app?.appLock ?: false, vm::setAppLock, enabled = canLock)

            Section("Privacy screen")
            SwitchRow("Hide amounts", "Shows ₹•••• instead of figures everywhere. Also on the eye button on Home.",
                app?.hideAmounts ?: false, vm::setHideAmounts)

            Section("Appearance")
            ListItem(
                headlineContent = { Text("Profile") },
                supportingContent = { Text(app?.displayName?.let { "$it · name, photo and contact" } ?: "Add your name and photo") },
                leadingContent = { com.hisaab.app.ui.profile.ProfileAvatar(app?.displayName ?: "You", app?.profile?.photoPath, 40.dp) },
                modifier = Modifier.clickable(onClick = onOpenProfile),
            )
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

            Section("About & updates")
            UpdateRow(update, onCheck = vm::checkForUpdate, onInstall = vm::installUpdate)
            SwitchRow("Check for updates", "Once a day, asks GitHub whether a newer Hisaab exists. Nothing about you is sent.",
                app?.checkUpdates ?: true, vm::setCheckUpdates)

            Section("Tools")
            ListItem(headlineContent = { Text("Test the parser") }, supportingContent = { Text("Paste any bank SMS or email and see what is extracted") },
                leadingContent = { Icon(Icons.Filled.Science, null) }, modifier = Modifier.clickable(onClick = onOpenBench))

            Section("Privacy")
            Text(
                "Everything stays on this phone: no account, no server, no analytics, no cloud backup. Transactions, messages, " +
                    "statements, passwords and investments are stored only in this app's private storage; passwords are encrypted with " +
                    "a key that never leaves the phone. The internet is used only to talk to your own email provider after you connect " +
                    "it: to download bank emails and statements, and once to mail yourself a verification code. Once a day it also asks GitHub " +
                    "whether a newer Hisaab exists (switch off under About & updates). Nothing about you or your money is ever uploaded. " +
                    "Screenshots and PDFs are read on the phone, and the text reader's usage reporting to Google is switched off.",
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
private fun UpdateRow(state: com.hisaab.app.update.UpdateState, onCheck: () -> Unit, onInstall: (com.hisaab.app.update.Release) -> Unit) {
    val version = com.hisaab.app.BuildConfig.VERSION_NAME
    when (state) {
        is com.hisaab.app.update.UpdateState.Available -> ListItem(
            headlineContent = { Text("Hisaab ${state.release.version} is available") },
            supportingContent = { Text("You have $version. " + state.release.notes.lineSequence().take(4).joinToString("\n")) },
            trailingContent = { Button(onClick = { onInstall(state.release) }) { Text("Install") } },
        )
        is com.hisaab.app.update.UpdateState.Downloading -> ListItem(
            headlineContent = { Text("Downloading ${state.release.version}…") },
            supportingContent = { androidx.compose.material3.LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) },
        )
        else -> ListItem(
            headlineContent = { Text("Hisaab $version") },
            supportingContent = {
                Text(
                    when (state) {
                        com.hisaab.app.update.UpdateState.Checking -> "Checking…"
                        com.hisaab.app.update.UpdateState.UpToDate -> "You have the latest version."
                        is com.hisaab.app.update.UpdateState.Failed -> state.message
                        else -> "Updates come from the project's GitHub releases."
                    },
                )
            },
            trailingContent = { OutlinedButton(onClick = onCheck, enabled = state != com.hisaab.app.update.UpdateState.Checking) { Text("Check") } },
        )
    }
}

@Composable
private fun Section(title: String, info: Array<String>? = null) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Row(Modifier.padding(start = 16.dp, top = 10.dp, end = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = if (info == null) 6.dp else 0.dp))
        if (info != null) InfoButton(title, *info)
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
