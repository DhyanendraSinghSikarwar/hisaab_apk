package com.hisaab.app.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.components.CategoryBadge
import com.hisaab.app.ui.components.signedAmount
import com.hisaab.app.ui.components.signedAmountColor
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionSourceDao
import com.hisaab.shared.db.TransactionSourceEntity
import com.hisaab.shared.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val dao: TransactionDao,
    sources: TransactionSourceDao,
    private val repository: TransactionRepository,
) : ViewModel() {
    val id: Long = checkNotNull(handle.get<Long>("id"))
    val transaction = dao.observeById(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sources = sources.observeForTransaction(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCategory(c: Category) = viewModelScope.launch { dao.setCategory(id, c.name) }
    fun setNote(note: String) = viewModelScope.launch { dao.setNote(id, note.trim().ifEmpty { null }) }
    fun split(source: TransactionSourceEntity) = viewModelScope.launch { repository.split(id, source.id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailRoute(onBack: () -> Unit, vm: TransactionDetailViewModel = hiltViewModel()) {
    val tx by vm.transaction.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Transaction") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        val t = tx ?: return@Scaffold
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(t.category, size = 52)
                Spacer(Modifier.padding(6.dp))
                Column {
                    Text(t.merchant ?: t.category.label, style = MaterialTheme.typography.titleLarge)
                    Text(signedAmount(t), style = MaterialTheme.typography.headlineMedium, color = signedAmountColor(t.type))
                }
            }
            CategoryPicker(t.category, vm::setCategory)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Type", t.type.name.lowercase().replaceFirstChar { it.uppercase() })
                    Field("When", Periods.dateTime(t.timestamp) + if (t.hasExplicitTime) "" else " (time the message arrived)")
                    Field("Account", "${t.bankName}${t.accountLast4?.let { " •• $it" }.orEmpty()} (${t.accountKind.name.lowercase()})")
                    Field("Channel", t.channel.name.replace('_', ' '))
                    t.upiId?.let { Field("UPI id", it) }
                    t.referenceNumber?.let { Field("Reference", it) }
                    t.balanceMinor?.let { Field("Balance after", Money.format(it)) }
                    t.availableLimitMinor?.let { Field("Limit left", Money.format(it)) }
                    Field("Parser confidence", "${(t.confidence * 100).toInt()}%")
                    t.reviewReason?.let { Field("Flagged", it) }
                }
            }
            var note by remember(t.id) { mutableStateOf(t.note.orEmpty()) }
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note") }, trailingIcon = {
                if (note != t.note.orEmpty()) TextButton(onClick = { vm.setNote(note) }) { Text("Save") }
            })

            Text("Sources (${sources.size})", style = MaterialTheme.typography.titleMedium)
            Text(
                "Every message that reported this transaction. An SMS and an email for the same payment are merged into one record.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sources.forEach { s -> SourceCard(s, canSplit = sources.size > 1, onSplit = { vm.split(s) }) }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun CategoryPicker(current: Category, onPick: (Category) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text("Category: ${current.label}") })
        DropdownMenu(open, { open = false }) {
            Category.entries.forEach { c -> DropdownMenuItem(text = { Text(c.label) }, onClick = { open = false; onPick(c) }) }
        }
    }
}

@Composable
private fun SourceCard(s: TransactionSourceEntity, canSplit: Boolean, onSplit: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (s.source) { "EMAIL" -> Icons.Filled.Email; "CSV" -> Icons.Filled.UploadFile; else -> Icons.Filled.Sms },
                    null, modifier = Modifier.padding(end = 8.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text("${s.source.lowercase().replaceFirstChar { it.uppercase() }} from ${s.sender}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(Periods.dateTime(s.receivedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (expanded && s.rawText != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(s.rawText!!, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                if (canSplit) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onSplit) { Text("Not the same transaction: split it out") }
                }
            }
        }
    }
}
