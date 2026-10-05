package com.hisaab.app.ui.add

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hisaab.app.security.AppLockGate
import com.hisaab.app.ui.components.AccountAvatar
import com.hisaab.app.ui.components.CategoryBadge
import com.hisaab.app.ui.components.CategorySheet
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.parser.FreeTextParser
import com.hisaab.parser.TransactionDraft
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountEntity
import com.hisaab.shared.repo.IngestOutcome
import com.hisaab.shared.repo.TransactionRepository
import com.hisaab.shared.repo.TransactionsChangedNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AddState(
    val amount: String = "",
    val type: TransactionType = TransactionType.DEBIT,
    val merchant: String = "",
    val category: Category = Category.OTHER,
    val accountId: Long? = null,
    val time: Long = System.currentTimeMillis(),
    val note: String = "",
    val reference: String? = null,
    val upiId: String? = null,
    val currency: String = "INR",
    /** OCR text when the form was filled from a screenshot; kept as the transaction's source text. */
    val scannedText: String? = null,
    val scanning: Boolean = false,
    val message: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TransactionRepository,
    private val notifier: TransactionsChangedNotifier,
    accountDao: AccountDao,
) : ViewModel() {
    private val parser = FreeTextParser()
    private val _state = MutableStateFlow(AddState())
    val state = _state.asStateFlow()
    val accounts = accountDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun edit(transform: (AddState) -> AddState) = _state.update { transform(it).copy(message = null) }

    /** Reads the screenshot on the device (ML Kit, no network) and fills in whatever the parser finds. */
    fun scan(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(scanning = true, message = null) }
        try {
            val image = InputImage.fromFilePath(context, uri)
            val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await().text
            val d = parser.draft(text, System.currentTimeMillis())
            if (d == null) {
                _state.update { it.copy(scanning = false, scannedText = text, message = "No amount found in that screenshot. Fill the details in yourself.") }
                return@launch
            }
            _state.update { it.fill(d, text, accounts.value) }
        } catch (e: Exception) {
            _state.update { it.copy(scanning = false, message = "Couldn't read that image: ${e.message ?: "unknown error"}") }
        }
    }

    private fun AddState.fill(d: TransactionDraft, text: String, accounts: List<AccountEntity>): AddState {
        val account = d.last4?.let { l4 -> accounts.firstOrNull { it.last4.endsWith(l4) && (d.bankName == null || it.bankName == d.bankName) } }
        return copy(
            amount = d.amountMinor?.let { (it / 100.0).toBigDecimal().stripTrailingZeros().toPlainString() } ?: amount,
            type = d.type?.takeIf { it != TransactionType.INVESTMENT } ?: type,
            merchant = d.merchant ?: merchant, category = d.category, accountId = account?.id ?: accountId,
            time = d.time, reference = d.reference, upiId = d.upiId, currency = d.currency,
            scannedText = text, scanning = false,
            message = "Filled in from the screenshot. Check the details, then save.",
        )
    }

    fun save() = viewModelScope.launch {
        val s = _state.value
        val minor = Money.parseInput(s.amount)?.takeIf { it > 0 } ?: run {
            _state.update { it.copy(message = "Enter the amount") }
            return@launch
        }
        val account = accounts.value.firstOrNull { it.id == s.accountId }
        val draft = TransactionDraft(
            amountMinor = minor, currency = s.currency, type = s.type, merchant = s.merchant.trim().ifEmpty { null }, upiId = s.upiId,
            reference = s.reference, bankName = account?.bankName, last4 = account?.last4, accountKind = account?.kind ?: AccountKind.ACCOUNT,
            channel = if (s.upiId != null) Channel.UPI else Channel.OTHER, category = s.category, time = s.time, hasExplicitTime = true,
        )
        val source = if (s.scannedText != null) Source.SCREENSHOT else Source.MANUAL
        val tx = parser.toParsed(draft, s.type, source, sender = account?.bankName ?: "Cash / other", messageTimestamp = System.currentTimeMillis())
        val outcome = repository.addManual(tx, s.scannedText ?: s.note.ifBlank { null })
        notifier.onTransactionsChanged()
        _state.update {
            it.copy(
                saved = true,
                message = if (outcome == IngestOutcome.MERGED) "This matched a transaction already recorded, so the two were merged." else null,
            )
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionRoute(onDone: () -> Unit, vm: AddTransactionViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var pickingCategory by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::scan) }
    LaunchedEffect(s.saved, s.message) { if (s.saved && s.message == null) onDone() }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text("Add transaction") }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedCard(Modifier.fillMaxWidth().clickable(enabled = !s.scanning) {
                AppLockGate.skipNextLock()
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fill from a screenshot", style = MaterialTheme.typography.titleSmall)
                        Text("A payment receipt from GPay, PhonePe, Paytm or a bank app. Read on this phone; nothing is uploaded.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (s.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.message?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (s.saved) { Button(onClick = onDone, Modifier.fillMaxWidth()) { Text("Done") }; return@Column }

            val types = listOf(TransactionType.DEBIT to "Spent", TransactionType.CREDIT to "Received", TransactionType.TRANSFER to "Transfer")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { i, (t, label) ->
                    SegmentedButton(selected = s.type == t, onClick = { vm.edit { it.copy(type = t) } }, shape = SegmentedButtonDefaults.itemShape(i, types.size)) { Text(label) }
                }
            }
            OutlinedTextField(
                s.amount, { v -> vm.edit { it.copy(amount = v) } }, Modifier.fillMaxWidth(), label = { Text("Amount") },
                prefix = { Text(if (s.currency == "INR") "₹" else s.currency + " ") }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(s.merchant, { v -> vm.edit { it.copy(merchant = v) } }, Modifier.fillMaxWidth(),
                label = { Text(if (s.type == TransactionType.CREDIT) "From" else "Paid to") }, singleLine = true)

            Card(Modifier.fillMaxWidth().clickable { pickingCategory = true }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(s.category, size = 36)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.category.label, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            AccountPicker(accounts, s.accountId) { id -> vm.edit { it.copy(accountId = id) } }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(Modifier.weight(1f).clickable { pickingDate = true }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                        Text(Periods.dayHeader(Periods.localDate(s.time)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedCard(Modifier.weight(1f).clickable { pickingTime = true }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(8.dp))
                        Text(Periods.time(s.time), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            s.reference?.let { Text("Reference $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedTextField(s.note, { v -> vm.edit { it.copy(note = v) } }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") })
            Button(onClick = vm::save, Modifier.fillMaxWidth(), enabled = !s.scanning) { Text("Save transaction") }
            Text(
                "If your bank later sends an SMS or email for the same payment, Hisaab merges the two instead of counting it twice.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickingCategory) CategorySheet(current = s.category, onPick = { c -> vm.edit { it.copy(category = c) } }, onDismiss = { pickingCategory = false })
    if (pickingDate) {
        val zone = Periods.zone
        val local = Instant.ofEpochMilli(s.time).atZone(zone)
        val state = rememberDatePickerState(initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.edit { it.copy(time = LocalDateTime.of(date, local.toLocalTime()).atZone(zone).toInstant().toEpochMilli()) }
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (pickingTime) {
        val local = Instant.ofEpochMilli(s.time).atZone(Periods.zone)
        val state = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text("Time") },
            text = { TimeInput(state) },
            confirmButton = {
                TextButton(onClick = {
                    vm.edit { it.copy(time = local.withHour(state.hour).withMinute(state.minute).toInstant().toEpochMilli()) }
                    pickingTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AccountPicker(accounts: List<AccountEntity>, selected: Long?, onPick: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = accounts.firstOrNull { it.id == selected }
    Column {
        OutlinedCard(Modifier.fillMaxWidth().clickable { open = true }) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current != null) AccountAvatar(current.bankName, current.kind, current.accountType, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Account", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(current?.let { "${it.nickname ?: it.bankName} ••${it.last4}" } ?: "None (cash or other)", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("None (cash or other)") }, onClick = { onPick(null); open = false })
            accounts.forEach { a ->
                DropdownMenuItem(
                    text = { Text("${a.nickname ?: a.bankName} ••${a.last4}") },
                    leadingIcon = { AccountAvatar(a.bankName, a.kind, a.accountType, size = 24.dp) },
                    onClick = { onPick(a.id); open = false },
                )
            }
        }
    }
}
