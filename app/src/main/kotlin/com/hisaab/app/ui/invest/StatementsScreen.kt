package com.hisaab.app.ui.invest

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.security.AppLockGate
import androidx.compose.foundation.clickable
import com.hisaab.app.ui.components.Info
import com.hisaab.app.ui.components.InfoButton
import com.hisaab.app.ui.format.Periods
import com.hisaab.email.statement.SavedPassword
import com.hisaab.email.statement.StatementPasswordStore
import com.hisaab.email.statement.StatementProcessor
import com.hisaab.email.statement.UnlockResult
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.StatementEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class StatementsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    statements: StatementDao,
    private val passwords: StatementPasswordStore,
    private val processor: StatementProcessor,
) : ViewModel() {
    val statements = statements.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val saved = passwords.saved.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val busy = MutableStateFlow(false)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private fun work(block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true
        try { block() } catch (e: Exception) { _messages.trySend("Something went wrong: ${e.message}") } finally { busy.value = false }
    }

    fun import(uri: Uri) = work {
        val (bytes, name) = withContext(Dispatchers.IO) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "statement.pdf"
            context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } to name
        }
        val s = processor.importFile(bytes, name)
        _messages.trySend(describe(s))
    }

    fun unlock(s: StatementEntity, password: String, remember: Boolean) = work {
        when (val r = processor.unlock(s.id, password, remember, label = s.bankName ?: s.sender.substringBefore('<').trim().ifEmpty { s.fileName })) {
            is UnlockResult.Done -> _messages.trySend(describe(r.statement))
            UnlockResult.WrongPassword -> _messages.trySend("That password didn't open ${s.fileName}.")
            UnlockResult.Missing -> _messages.trySend("The PDF is no longer on the phone. Import it again.")
        }
    }

    fun addPassword(label: String, password: String) = work {
        passwords.add(label, password)
        val opened = processor.retryLocked()
        _messages.trySend(if (opened > 0) "Password saved. It opened $opened locked statement${if (opened > 1) "s" else ""}." else "Password saved.")
    }

    fun removePassword(p: SavedPassword) = viewModelScope.launch { passwords.remove(p.id) }

    /** Tries every saved password on every locked statement again. */
    fun retryAll() = work {
        val opened = processor.retryLocked()
        _messages.trySend(if (opened > 0) "Opened $opened statement${if (opened > 1) "s" else ""} with your saved passwords." else "No saved password opens the remaining statements.")
    }
    fun delete(s: StatementEntity) = viewModelScope.launch { processor.delete(s.id) }

    private fun describe(s: StatementEntity?): String = when (s?.status) {
        StatementEntity.PARSED -> "Read ${s.fileName}: " + listOfNotNull(
            s.transactionCount.takeIf { it > 0 }?.let { "$it transactions" }, s.holdingCount.takeIf { it > 0 }?.let { "$it holdings" },
        ).joinToString(" and ")
        StatementEntity.LOCKED -> "${s.fileName} needs a password. Tap Unlock."
        StatementEntity.EMPTY -> "Read ${s.fileName}, but found no transactions or holdings in it."
        StatementEntity.UNREADABLE -> "Couldn't read ${s.fileName}. It may be a scanned image."
        else -> "Already imported."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementsRoute(onBack: () -> Unit, onOpenStatement: (Long) -> Unit, unlockId: Long? = null, vm: StatementsViewModel = hiltViewModel()) {
    val statements by vm.statements.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var unlocking by remember { mutableStateOf<StatementEntity?>(null) }
    // Opened from the "statement needs a password" notification: go straight to its password box.
    var deepLinkHandled by remember { mutableStateOf(false) }
    LaunchedEffect(unlockId, statements) {
        if (!deepLinkHandled && unlockId != null) {
            statements.firstOrNull { it.id == unlockId && it.status == StatementEntity.LOCKED }?.let { unlocking = it; deepLinkHandled = true }
        }
    }
    var addingPassword by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, 
        topBar = {
            TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), 
                title = { Text("Statements") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { InfoButton("Statements", *Info.STATEMENTS) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Credit card, bank and investment statements (CAS from CAMS, KFintech, NSDL or CDSL, and broker statements). " +
                        "PDFs attached to bank emails are read automatically when email is connected. Everything is read on this phone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Button(onClick = { AppLockGate.skipNextLock(); picker.launch(arrayOf("application/pdf")) }, Modifier.fillMaxWidth(), enabled = !busy) {
                    Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Import a statement PDF")
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            item {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Saved passwords", style = MaterialTheme.typography.titleMedium)
                    InfoButton("Statement passwords", *Info.STATEMENT_PASSWORDS)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { addingPassword = true }) { Text("Add") }
                }
                Text(
                    "Tried automatically on every new statement. Kept encrypted on this phone. Common formats: card statements often use the " +
                        "first 4 letters of your name + DDMM of birth (e.g. RAHU0105); CAS uses your PAN in capitals; bank statements often use " +
                        "customer ID or date of birth.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (saved.isEmpty()) item { Text("None yet.", style = MaterialTheme.typography.bodyMedium) }
            if (saved.isNotEmpty() && statements.any { it.status == StatementEntity.LOCKED }) {
                item { OutlinedButton(onClick = vm::retryAll, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Try all passwords on locked statements") } }
            }
            items(saved, key = { it.id }) { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Key, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(p.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("••••••", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { vm.removePassword(p) }) { Icon(Icons.Filled.RemoveCircleOutline, "Remove") }
                }
            }

            if (statements.isEmpty()) {
                item { Text("Statements", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
                item { Text("No statements yet.", style = MaterialTheme.typography.bodyMedium) }
            }
            // Password-needed first, then one section per kind of statement.
            val groups = statements.groupBy { if (it.status == StatementEntity.LOCKED) "LOCKED" else it.kind ?: "OTHER" }
            for (key in listOf("LOCKED", "CREDIT_CARD", "BANK", "INVESTMENT", "OTHER")) {
                val group = groups[key] ?: continue
                item(key = "sec-$key") {
                    Text(SECTION_TITLES.getValue(key), style = MaterialTheme.typography.titleMedium,
                        color = if (key == "LOCKED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp))
                }
                items(group, key = { it.id }) { s ->
                    StatementRow(s, onOpen = { onOpenStatement(s.id) }, onUnlock = { unlocking = s }, onDelete = { vm.delete(s) })
                }
            }
        }
    }

    unlocking?.let { s ->
        UnlockDialog(s, onDismiss = { unlocking = null }, onUnlock = { pw, remember -> vm.unlock(s, pw, remember); unlocking = null })
    }
    if (addingPassword) AddPasswordDialog(onDismiss = { addingPassword = false }, onAdd = { l, p -> vm.addPassword(l, p); addingPassword = false })
}

private val SECTION_TITLES = mapOf(
    "LOCKED" to "Needs a password", "CREDIT_CARD" to "Credit card statements", "BANK" to "Bank statements",
    "INVESTMENT" to "Investment statements", "OTHER" to "Other statements",
)

@Composable
private fun StatementRow(s: StatementEntity, onOpen: () -> Unit, onUnlock: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = s.status == StatementEntity.PARSED, onClick = onOpen)) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint) = when (s.status) {
                StatementEntity.PARSED -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
                StatementEntity.LOCKED -> Icons.Filled.Lock to MaterialTheme.colorScheme.error
                else -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text("${s.bankName ?: s.sender.substringBefore('<').trim()} · ${Periods.dateTime(s.receivedAt)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(
                    when (s.status) {
                        StatementEntity.PARSED -> listOfNotNull(
                            s.transactionCount.takeIf { it > 0 }?.let { "$it transactions" }, s.holdingCount.takeIf { it > 0 }?.let { "$it holdings" },
                        ).joinToString(" · ")
                        StatementEntity.LOCKED -> "Needs a password"
                        StatementEntity.EMPTY -> "Nothing found in it"
                        else -> "Couldn't be read (maybe a scanned image)"
                    },
                    style = MaterialTheme.typography.labelMedium, color = tint,
                )
            }
            if (s.status == StatementEntity.LOCKED) OutlinedButton(onClick = onUnlock) { Text("Unlock") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remove from list") }
        }
    }
}

@Composable
private fun UnlockDialog(s: StatementEntity, onDismiss: () -> Unit, onUnlock: (String, Boolean) -> Unit) {
    var password by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock statement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.fileName, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("PDF password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(keep, { keep = it })
                    Text("Remember it for future statements", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onUnlock(password, keep) }, enabled = password.isNotEmpty()) { Text("Unlock") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddPasswordDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a statement password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("Label, e.g. HDFC credit card or CAS") }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(label, password) }, enabled = password.isNotEmpty()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
