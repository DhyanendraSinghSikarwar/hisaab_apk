package com.hisaab.app.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import com.hisaab.app.ui.components.CategorySheet
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

    /** Also remembered for this merchant, so its next transactions get the same category. */
    fun setCategory(c: Category) = viewModelScope.launch { repository.setCategory(listOf(id), c) }
    fun setNote(note: String) = viewModelScope.launch { dao.setNote(id, note.trim().ifEmpty { null }) }
    fun split(source: TransactionSourceEntity) = viewModelScope.launch { repository.split(id, source.id) }
    fun delete(then: () -> Unit) = viewModelScope.launch { repository.deleteTransactions(listOf(id)); then() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailRoute(onBack: () -> Unit, vm: TransactionDetailViewModel = hiltViewModel()) {
    val tx by vm.transaction.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(),
            title = { Text("Transaction") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") } },
        )
    }) { inner ->
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete this transaction?") },
                text = { Text("It is removed from Hisaab and won't come back on a rescan. The message itself is not touched.") },
                confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
        val t = tx ?: return@Scaffold
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.hisaab.app.ui.components.TransactionAvatar(t, size = 52.dp)
                Spacer(Modifier.padding(6.dp))
                Column {
                    Text(t.merchant ?: t.category.label, style = MaterialTheme.typography.titleLarge)
                    Text(signedAmount(t), style = MaterialTheme.typography.headlineMedium, color = signedAmountColor(t.type))
                }
            }
            CategoryPicker(t, vm::setCategory)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Type", t.type.name.lowercase().replaceFirstChar { it.uppercase() })
                    if (t.currency != "INR") Field("In rupees", t.inrMinor?.let { "≈ " + Money.format(it) } ?: "Add a rate in Settings › Forex rates")
                    Field("When", Periods.dateTime(t.timestamp))
                    Field(if (t.accountKind.name == "CARD") "Card" else "Account", "${t.bankName}${t.accountLast4?.let { " •• $it" }.orEmpty()}")
                    if (t.channel.name != "OTHER") Field("Channel", t.channel.name.replace('_', ' '))
                    t.upiId?.let { Field("UPI id", it) }
                    t.referenceNumber?.let { Field("Reference", it) }
                    t.balanceMinor?.let { Field("Balance after", Money.format(it)) }
                    t.availableLimitMinor?.let { Field("Limit left", Money.format(it)) }
                    t.reviewReason?.let { Field("Flagged", it) }
                }
            }
            var note by remember(t.id) { mutableStateOf(t.note.orEmpty()) }
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note") }, trailingIcon = {
                if (note != t.note.orEmpty()) TextButton(onClick = { vm.setNote(note) }) { Text("Save") }
            })

            Text(if (sources.size > 1) "Messages (${sources.size})" else "Message", style = MaterialTheme.typography.titleMedium)
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

/**
 * The category and sub-category as two rows. Choosing a category opens its sub-categories next; the user's own
 * categories and sub-categories are offered alongside the built-in ones.
 */
@Composable
private fun CategoryPicker(t: com.hisaab.shared.db.TransactionEntity, onPick: (Category) -> Unit, cats: com.hisaab.app.ui.category.CategoriesViewModel = hiltViewModel()) {
    val custom by cats.custom.collectAsStateWithLifecycle()
    val subs by cats.subs.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var subOpen by remember { mutableStateOf(false) }
    val own = t.customCategoryId?.let { id -> custom.firstOrNull { it.id == id } }
    val look = own?.let { com.hisaab.app.ui.category.CategoryLook.of(it) } ?: com.hisaab.app.ui.category.CategoryLook.of(t.category)
    val builtIn = if (own == null) t.category else null
    val sub = t.subcategory ?: builtIn?.let { com.hisaab.shared.insight.Subcategories.guess(it, t.merchant, t.upiId) }
    val subIcon = com.hisaab.app.ui.category.subsFor(look.key, builtIn, subs).firstOrNull { it.name == sub }?.icon
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.hisaab.app.ui.category.IconBadge(look.icon, look.color, 36)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(look.name, style = MaterialTheme.typography.bodyLarge)
            }
            Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Row(Modifier.clickable { subOpen = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.hisaab.app.ui.category.IconBadge(com.hisaab.app.ui.components.IconLibrary.get(subIcon), look.color, 36)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Sub-category" + if (t.subcategory == null && sub != null) " (automatic)" else "",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sub ?: "None", style = MaterialTheme.typography.bodyLarge)
            }
            Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (open) {
        CategorySheet(
            current = if (own == null) t.category else null,
            onPick = { c -> onPick(c); subOpen = true },
            onDismiss = { open = false },
            custom = custom, currentCustomId = own?.id,
            onPickCustom = { c -> cats.setCustomCategory(listOf(t.id), c.id); subOpen = true },
            onCreateCustom = { name, icon, color -> cats.createCategory(name, icon, color) { id -> cats.setCustomCategory(listOf(t.id), id) } },
        )
    }
    if (subOpen && !open) {
        com.hisaab.app.ui.category.SubcategorySheet(
            look = look, builtIn = builtIn, custom = subs, current = t.subcategory,
            onPick = { cats.setSubcategory(listOf(t.id), it) },
            onCreate = { name, icon -> cats.createSub(look.key, name, icon) },
            onDismiss = { subOpen = false },
        )
    }
}

@Composable
private fun SourceCard(s: TransactionSourceEntity, canSplit: Boolean, onSplit: () -> Unit) {
    com.hisaab.app.ui.components.MessageView(
        s,
        footer = if (canSplit) ({ OutlinedButton(onClick = onSplit) { Text("Not the same transaction: split it out") } }) else null,
    )
}

private fun sourceLabel(s: TransactionSourceEntity): String = when (s.source) {
    "SMS" -> "SMS from ${s.sender}"
    "EMAIL" -> "Email from ${s.sender}"
    "APP" -> "${s.sender} notification"
    "SCREENSHOT" -> "Added from a screenshot"
    "MANUAL" -> "Added by you"
    "CSV" -> "Imported from CSV"
    "STATEMENT" -> "Statement from ${s.sender.substringBefore('<').trim().ifEmpty { s.sender }}"
    else -> "${s.source} from ${s.sender}"
}
