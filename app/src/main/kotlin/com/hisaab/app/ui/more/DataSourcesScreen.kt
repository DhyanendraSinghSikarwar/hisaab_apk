package com.hisaab.app.ui.more

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.notify.PaymentNotificationListener
import com.hisaab.app.security.AppLockGate
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.sms.SmsScanScheduler
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.components.Segmented
import com.hisaab.app.ui.components.rememberSmsPermission
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.settings.EmailConnectDialog
import com.hisaab.app.ui.settings.SettingsViewModel
import com.hisaab.app.ui.theme.Hx
import com.hisaab.email.auth.ConnectResult
import com.hisaab.email.imap.MailAccountStore
import com.hisaab.email.sync.GmailSettings
import com.hisaab.email.sync.GmailSettingsStore
import com.hisaab.email.sync.MailConnection
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.StatementEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SourcesState(
    val smsEnabled: Boolean = true,
    val lastSmsScanAt: Long? = null,
    val lastSmsResult: String? = null,
    val appNotifications: Boolean = false,
    val mail: GmailSettings? = null,
    val imapEmails: List<String> = emptyList(),
    val statements: Int = 0,
    val lockedStatements: Int = 0,
    val lastStatementAt: Long? = null,
    val loaded: Boolean = false,
)

/** What each source is doing, for the status lines. The controls themselves go through [SettingsViewModel]. */
@HiltViewModel
class DataSourcesViewModel @Inject constructor(
    app: AppSettingsStore,
    gmail: GmailSettingsStore,
    mailAccounts: MailAccountStore,
    statements: StatementDao,
) : ViewModel() {
    val state: StateFlow<SourcesState> = combine(app.settings, gmail.settings, mailAccounts.emails, statements.observeAll()) { a, g, imap, st ->
        SourcesState(
            smsEnabled = a.smsEnabled, lastSmsScanAt = a.lastSmsScanAt, lastSmsResult = a.lastSmsResult,
            appNotifications = a.appNotificationsEnabled, mail = g, imapEmails = imap,
            statements = st.size, lockedStatements = st.count { it.status == StatementEntity.LOCKED },
            lastStatementAt = st.maxOfOrNull { it.receivedAt }, loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourcesState())
}

/** "just now", "5 min ago", "3 h ago", "2 days ago", or the date. */
internal fun ago(at: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - at) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        m < 7 * 24 * 60 -> "${m / (24 * 60)} day${if (m / (24 * 60) == 1L) "" else "s"} ago"
        else -> Periods.localDate(at).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

/** Where the connected mailbox comes from, for the Email row. Null when no mailbox is connected. */
internal fun mailboxLabel(g: GmailSettings?, imap: List<String>): String? = when (g?.connection) {
    MailConnection.GOOGLE -> g.accountEmail ?: "Google account"
    MailConnection.IMAP -> imap.firstOrNull()?.let { if (imap.size > 1) "$it +${imap.size - 1}" else it } ?: g.accountEmail ?: "Mailbox"
    else -> null
}

/** Every source Artha reads, and every control for them: SMS, email, payment-app notifications, history window. */
@Composable
fun DataSourcesRoute(
    onBack: () -> Unit,
    onOpenStatements: () -> Unit,
    vm: DataSourcesViewModel = hiltViewModel(),
    settings: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val emails by settings.emails.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { settings.messages.collect { snackbar.showSnackbar(it) } }

    val sms = rememberSmsPermission(onGranted = settings::rescanSms)
    var notifAccess by remember { mutableStateOf(PaymentNotificationListener.hasAccess(context)) }
    LifecycleResumeEffect(Unit) { notifAccess = PaymentNotificationListener.hasAccess(context); onPauseOrDispose { } }
    val openNotifAccess = {
        AppLockGate.skipNextLock()
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // Email: Google sign-in with its consent step, or a mailbox with an app password (EmailConnectDialog).
    var pendingEmail by remember { mutableStateOf<String?>(null) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        scope.launch { settings.completeConsent(activity, result.data, pendingEmail) }
    }
    var editingSenders by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var connectingEmail by remember { mutableStateOf(false) }
    val connectGoogle: () -> Unit = {
        scope.launch {
            AppLockGate.skipNextLock()
            (settings.connect(activity) as? ConnectResult.NeedsConsent)?.let { r ->
                pendingEmail = r.email
                AppLockGate.skipNextLock()
                consent.launch(IntentSenderRequest.Builder(r.intent.intentSender).build())
            }
        }
    }

    MoreScaffold("Data sources", onBack, snackbar = snackbar) { inner ->
        if (!s.loaded) return@MoreScaffold
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            item("intro") {
                Text(
                    "Everything Artha knows comes from these sources, and it all stays on this phone.",
                    style = MaterialTheme.typography.bodyMedium, color = Hx.text2, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item("history") {
                val lookback = s.mail?.lookbackDays ?: GmailSettings.DEFAULT_LOOKBACK
                val choices = GmailSettings.LOOKBACK_CHOICES
                HCard(title = "History to read") {
                    Segmented(
                        choices.map { "${it}d" }, choices.indexOf(lookback).coerceAtLeast(0),
                        onSelect = { settings.setLookback(choices[it]) },
                    )
                    HelpText("How far back SMS and email are read. Changing it reads that period again.", Modifier.padding(top = 8.dp))
                }
            }
            item("sms") {
                val on = sms.granted && s.smsEnabled
                SourceCard(
                    icon = Icons.Filled.Sms, color = Hx.palette[0], title = "Bank SMS",
                    status = when {
                        !sms.granted -> "No permission" to Hx.neg
                        !s.smsEnabled -> "Paused" to Hx.warn
                        else -> "On" to Hx.pos
                    },
                    detail = buildString {
                        append(s.lastSmsScanAt?.let { "Last read ${ago(it)}" } ?: "Not read yet")
                        s.lastSmsResult?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    },
                    toggle = if (sms.granted) s.smsEnabled else null, onToggle = settings::setSmsEnabled,
                ) {
                    if (!sms.granted) {
                        Button(onClick = sms::request) { Text(if (sms.blocked) "Open app settings" else "Allow SMS access") }
                        if (sms.blocked) HelpText("Then App info › ⋮ › Allow restricted settings.", Modifier.padding(top = 4.dp))
                    } else if (on) {
                        OutlinedButton(onClick = {
                            SmsScanScheduler.scan(context)
                            Toast.makeText(context, "Reading new messages…", Toast.LENGTH_SHORT).show()
                        }) { Text("Read new messages now") }
                    }
                }
            }
            item("email") {
                val box = mailboxLabel(s.mail, s.imapEmails)
                val g = s.mail
                val connected = g != null && g.connected
                val imap = g?.connection == MailConnection.IMAP
                SourceCard(
                    icon = Icons.Filled.Email, color = Hx.palette[3], title = "Email",
                    status = when {
                        !connected -> "Not connected" to Hx.text2
                        g?.needsReauth == true -> "Sign in again" to Hx.neg
                        g?.enabled == false -> "Paused" to Hx.warn
                        else -> "Connected" to Hx.pos
                    },
                    detail = if (!connected) "Bank alerts and statements from your mailbox."
                    else buildString {
                        append(box ?: "Mailbox")
                        val last = if (imap) g?.imapSyncedAt ?: g?.lastSyncAt else g?.lastSyncAt
                        append(last?.let { " · synced ${ago(it)}" } ?: " · not synced yet")
                    },
                    toggle = if (connected) g!!.enabled else null, onToggle = settings::setGmailEnabled,
                ) {
                    if (!connected) {
                        Button(onClick = { connectingEmail = true }) { Text("Connect email") }
                        return@SourceCard
                    }
                    if (g!!.needsReauth) {
                        Button(onClick = { if (imap) connectingEmail = true else connectGoogle() }) { Text("Sign in again") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Hx.border)
                    if (imap) {
                        emails.forEach { address ->
                            HRow(address, null) {
                                TextButton(onClick = { settings.removeEmail(address) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        HRow("Add another email", null, leading = { Icon(Icons.Filled.Add, null, tint = Hx.accent) }, onClick = { connectingEmail = true })
                    } else {
                        HRow(g.accountEmail ?: "Google account", "Google account")
                    }
                    HRow("Bank senders", "${g.senders.size} in the filter", onClick = { editingSenders = true }) {
                        TextButton(onClick = { editingSenders = true }) { Text("Edit") }
                    }
                    HRow(if (imap) "Disconnect email" else "Sign out", "Transactions already found are kept") {
                        OutlinedButton(onClick = { confirmDisconnect = true }) { Text("Disconnect") }
                    }
                }
            }
            item("notif") {
                val on = s.appNotifications && notifAccess
                SourceCard(
                    icon = Icons.Filled.NotificationsActive, color = Hx.palette[2], title = "Payment-app notifications",
                    status = when {
                        on -> "On" to Hx.pos
                        s.appNotifications -> "Needs access" to Hx.warn
                        else -> "Off" to Hx.text2
                    },
                    detail = "UPI payments from GPay, PhonePe, Paytm and others that send no bank SMS.",
                    toggle = s.appNotifications,
                    onToggle = { v -> settings.setAppNotifications(v); if (v && !notifAccess) openNotifAccess() },
                ) {
                    if (s.appNotifications && !notifAccess) OutlinedButton(onClick = openNotifAccess) { Text("Allow notification access") }
                }
            }
        }
    }

    val g = s.mail
    if (editingSenders && g != null) {
        SendersDialog(g.senders, onDismiss = { editingSenders = false }, onSave = { settings.setSenders(it); editingSenders = false },
            onReset = { settings.resetSenders(); editingSenders = false })
    }
    if (connectingEmail) {
        EmailConnectDialog(onDismiss = { connectingEmail = false }, onUseGoogle = { connectingEmail = false; connectGoogle() })
    }
    if (confirmDisconnect) {
        AlertDialog(onDismissRequest = { confirmDisconnect = false }, title = { Text("Disconnect email?") },
            text = { Text("Artha will stop reading email and delete the stored sign-in. Transactions already found are kept.") },
            confirmButton = { TextButton(onClick = { confirmDisconnect = false; settings.disconnectGmail() }) { Text("Disconnect") } },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } })
    }
}

@Composable
private fun SourceCard(
    icon: ImageVector,
    color: Color,
    title: String,
    status: Pair<String, Color>,
    detail: String,
    toggle: Boolean? = null,
    onToggle: (Boolean) -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    HCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(status.second))
                    Spacer(Modifier.width(6.dp))
                    Text(status.first, fontSize = 12.sp, color = status.second, fontWeight = FontWeight.Medium)
                }
            }
            if (toggle != null) Switch(checked = toggle, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(8.dp))
        Text(detail, fontSize = 13.sp, color = Hx.text2)
        Column(Modifier.padding(top = 4.dp)) { actions() }
    }
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
