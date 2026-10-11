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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hisaab.app.i18n.t
import com.hisaab.app.security.AppLockGate
import com.hisaab.app.ui.components.AccountAvatar
import com.hisaab.app.ui.components.CategoryBadge
import com.hisaab.app.ui.accounts.GapTracker
import com.hisaab.app.ui.accounts.gapSummary
import com.hisaab.app.ui.accounts.parseGap
import com.hisaab.app.ui.accounts.rowLine
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
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.GapChoice
import com.hisaab.shared.insight.GapPrefill
import com.hisaab.shared.insight.UntrackedGap
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
    /** A loan payment was saved: ask which loan it belongs to before leaving. */
    val loanAsk: Boolean = false,
    /** Own transfer (type TRANSFER): the account the money left and the one it reached; either may be left out. */
    val fromId: Long? = null,
    val toId: Long? = null,
    /** Set when the form was opened from an untracked change (see GapCard). */
    val gap: GapPrefill? = null,
    val gapCard: Boolean = false,
    /** Existing account-less rows that could be that change. */
    val candidates: List<List<TransactionEntity>> = emptyList(),
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TransactionRepository,
    private val notifier: TransactionsChangedNotifier,
    accountDao: AccountDao,
    handle: SavedStateHandle,
    private val tracker: GapTracker,
) : ViewModel() {
    private val parser = FreeTextParser()
    private val _state = MutableStateFlow(AddState())
    val state = _state.asStateFlow()
    val accounts = accountDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        parseGap(handle.get<String>("gap"))?.let { g ->
            viewModelScope.launch {
                val p = GapPrefill.of(g, tracker.lastChoice(g.accountId))
                val amount = (p.amountMinor / 100.0).toBigDecimal().stripTrailingZeros().toPlainString()
                _state.update { it.withChoice(p, p.choice).copy(amount = amount, time = p.time, gap = p, gapCard = g.card) }
                _state.update { it.copy(candidates = tracker.candidates(g)) }
            }
        }
    }

    fun edit(transform: (AddState) -> AddState) = _state.update { transform(it).copy(message = null) }

    /** Payment, money received or own transfer for the gap's account; a transfer puts the account on its side. */
    private fun AddState.withChoice(p: GapPrefill, choice: GapChoice): AddState = when (choice) {
        GapChoice.PAYMENT -> copy(type = TransactionType.DEBIT, accountId = p.accountId, fromId = null, toId = null)
        GapChoice.RECEIVED -> copy(type = TransactionType.CREDIT, accountId = p.accountId, fromId = null, toId = null)
        GapChoice.TRANSFER -> copy(type = TransactionType.TRANSFER, accountId = null, fromId = p.fromAccountId, toId = p.toAccountId)
    }

    fun choose(choice: GapChoice) = _state.update { s -> s.gap?.let { s.withChoice(it, choice).copy(message = null) } ?: s }

    /** Puts the account-less rows that look like the gap on its account; nothing else to type. */
    fun assign(ids: List<Long>) = viewModelScope.launch {
        val account = _state.value.gap?.accountId ?: return@launch
        ids.forEach { repository.setAccount(it, account) }
        notifier.onTransactionsChanged()
        _state.update { it.copy(saved = true, message = null) }
    }

    /** Reads the screenshot on the device (ML Kit, no network) and fills in whatever the parser finds. */
    fun scan(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(scanning = true, message = null) }
        try {
            val image = InputImage.fromFilePath(context, uri)
            val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await().text
            val d = parser.draft(text, System.currentTimeMillis())
            if (d == null) {
                _state.update { it.copy(scanning = false, scannedText = text, message = t("No amount found in that screenshot. Fill the details in yourself.")) }
                return@launch
            }
            _state.update { it.fill(d, text, accounts.value) }
        } catch (e: Exception) {
            _state.update { it.copy(scanning = false, message = t("Couldn't read that image: {error}", "error" to (e.message ?: t("unknown error")))) }
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
            message = t("Filled in from the screenshot. Check the details, then save."),
        )
    }

    fun loanDone() = _state.update { it.copy(loanAsk = false) }

    fun save() = viewModelScope.launch {
        val s = _state.value
        val minor = Money.parseInput(s.amount)?.takeIf { it > 0 } ?: run {
            _state.update { it.copy(message = t("Enter the amount")) }
            return@launch
        }
        if (s.type == TransactionType.TRANSFER) {
            if ((s.fromId == null && s.toId == null) || s.fromId == s.toId) {
                _state.update { it.copy(message = t("Choose the accounts the money moved between")) }
                return@launch
            }
            repository.addOwnTransfer(s.fromId, s.toId, minor, s.time, s.note.ifBlank { null })
            s.gap?.let { tracker.rememberChoice(it.accountId, GapChoice.TRANSFER) }
            notifier.onTransactionsChanged()
            _state.update { it.copy(saved = true) }
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
        s.gap?.let { tracker.rememberChoice(it.accountId, if (s.type == TransactionType.CREDIT) GapChoice.RECEIVED else GapChoice.PAYMENT) }
        notifier.onTransactionsChanged()
        _state.update {
            it.copy(
                saved = true,
                loanAsk = s.category == Category.EMI_LOAN && s.type == TransactionType.DEBIT && s.merchant.trim().length >= 2,
                message = if (outcome == IngestOutcome.MERGED) t("This matched a transaction already recorded, so the two were merged.") else null,
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
    LaunchedEffect(s.saved, s.message, s.loanAsk) { if (s.saved && s.message == null && !s.loanAsk) onDone() }
    if (s.saved && s.loanAsk) com.hisaab.app.ui.loans.LoanLinkPrompt(com.hisaab.app.ui.loans.LoanRequest(merchant = s.merchant.trim()), onDone = vm::loanDone)

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text(t("Add transaction")) }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back")) } })
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
                        Text(t("Fill from a screenshot"), style = MaterialTheme.typography.titleSmall)
                        Text(t("A payment receipt from GPay, PhonePe, Paytm or a bank app. Read on this phone; nothing is uploaded."),
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
            if (s.saved) { Button(onClick = onDone, Modifier.fillMaxWidth()) { Text(t("Done")) }; return@Column }

            val gap = s.gap
            if (gap != null) {
                val untracked = UntrackedGap(gap.accountId, gap.fromTime, gap.toTime, if (gap.outgoing) -gap.amountMinor else gap.amountMinor, 0, 0, s.gapCard)
                Card(colors = CardDefaults.cardColors(containerColor = com.hisaab.app.ui.theme.Hx.warn.copy(alpha = 0.12f))) {
                    Text(t("Untracked change · {summary}", "summary" to gapSummary(untracked)), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                s.candidates.forEach { rows ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("Looks like: {rows}", "rows" to rows.joinToString(" + ") { rowLine(it) }), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { vm.assign(rows.map { it.id }) }) { Text(t("Assign")) }
                        }
                    }
                }
                if (gap.choices.size > 1) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        gap.choices.forEachIndexed { i, c ->
                            val label = when (c) {
                                GapChoice.PAYMENT -> t("Payment")
                                GapChoice.TRANSFER -> t("Transfer to my account")
                                GapChoice.RECEIVED -> t("Money received")
                            }
                            SegmentedButton(selected = choiceOf(s.type) == c, onClick = { vm.choose(c) }, shape = SegmentedButtonDefaults.itemShape(i, gap.choices.size)) { Text(label, maxLines = 1) }
                        }
                    }
                }
            } else {
                val types = listOf(TransactionType.DEBIT to t("Spent"), TransactionType.CREDIT to t("Received"), TransactionType.TRANSFER to t("Transfer"))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { i, (ty, label) ->
                        SegmentedButton(selected = s.type == ty, onClick = { vm.edit { it.copy(type = ty) } }, shape = SegmentedButtonDefaults.itemShape(i, types.size)) { Text(label) }
                    }
                }
            }
            val transfer = s.type == TransactionType.TRANSFER
            OutlinedTextField(
                s.amount, { v -> vm.edit { it.copy(amount = v) } }, Modifier.fillMaxWidth(), label = { Text(t("Amount")) },
                prefix = { Text(if (s.currency == "INR") "₹" else s.currency + " ") }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (transfer) {
                AccountPicker(accounts.filter { it.kind == AccountKind.ACCOUNT }, s.fromId, t("From"), t("Other account")) { id -> vm.edit { it.copy(fromId = id) } }
                AccountPicker(accounts, s.toId, t("To"), t("Other account")) { id -> vm.edit { it.copy(toId = id) } }
            } else OutlinedTextField(s.merchant, { v -> vm.edit { it.copy(merchant = v) } }, Modifier.fillMaxWidth(),
                label = { Text(if (s.type == TransactionType.CREDIT) t("From") else t("Paid to")) }, singleLine = true)

            if (!transfer) Card(Modifier.fillMaxWidth().clickable { pickingCategory = true }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(s.category, size = 36)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(t("Category"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t(s.category.label), style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(t("Change"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (!transfer) AccountPicker(accounts, s.accountId, t("Account"), t("None (cash or other)")) { id -> vm.edit { it.copy(accountId = id) } }

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
            s.reference?.let { Text(t("Reference {ref}", "ref" to it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedTextField(s.note, { v -> vm.edit { it.copy(note = v) } }, Modifier.fillMaxWidth(), label = { Text(t("Note (optional)")) })
            Button(onClick = vm::save, Modifier.fillMaxWidth(), enabled = !s.scanning) { Text(t("Save transaction")) }
            Text(
                t("If your bank later sends an SMS or email for the same payment, DhanKosh merges the two instead of counting it twice."),
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
                }) { Text(t("OK")) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(t("Cancel")) } },
        ) { DatePicker(state) }
    }
    if (pickingTime) {
        val local = Instant.ofEpochMilli(s.time).atZone(Periods.zone)
        val state = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text(t("Time")) },
            text = { TimeInput(state) },
            confirmButton = {
                TextButton(onClick = {
                    vm.edit { it.copy(time = local.withHour(state.hour).withMinute(state.minute).toInstant().toEpochMilli()) }
                    pickingTime = false
                }) { Text(t("OK")) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(t("Cancel")) } },
        )
    }
}

@Composable
private fun AccountPicker(accounts: List<AccountEntity>, selected: Long?, label: String, noneLabel: String, onPick: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = accounts.firstOrNull { it.id == selected }
    Column {
        OutlinedCard(Modifier.fillMaxWidth().clickable { open = true }) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current != null) AccountAvatar(current.bankName, current.kind, current.accountType, business = current.usage == com.hisaab.shared.db.AccountUsage.BUSINESS, network = current.cardNetwork, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(current?.let { "${it.nickname ?: it.bankName} ••${it.last4}" } ?: noneLabel, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onPick(null); open = false })
            accounts.filter { it.mergedIntoId == null }.forEach { a ->
                DropdownMenuItem(
                    text = { Text("${a.nickname ?: a.bankName} ••${a.last4}") },
                    leadingIcon = { AccountAvatar(a.bankName, a.kind, a.accountType, business = a.usage == com.hisaab.shared.db.AccountUsage.BUSINESS, network = a.cardNetwork, size = 24.dp) },
                    onClick = { onPick(a.id); open = false },
                )
            }
        }
    }
}

private fun choiceOf(type: TransactionType): GapChoice = when (type) {
    TransactionType.CREDIT -> GapChoice.RECEIVED
    TransactionType.TRANSFER -> GapChoice.TRANSFER
    else -> GapChoice.PAYMENT
}
