package com.hisaab.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountWithActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(private val dao: AccountDao) : ViewModel() {
    val accounts = dao.observeWithActivity(Periods.startOfMonth(System.currentTimeMillis()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(id: Long, nickname: String, color: Int?) = viewModelScope.launch { dao.rename(id, nickname.trim().ifEmpty { null }, color) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsRoute(onBack: () -> Unit, onOpenAccount: (Long) -> Unit, vm: AccountsViewModel = hiltViewModel()) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AccountWithActivity?>(null) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Accounts") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        if (accounts.isEmpty()) {
            EmptyState(Icons.Filled.AccountBalance, "No accounts yet", "Accounts are created from the bank and last 4 digits in your messages.", Modifier.padding(inner))
        }
        LazyColumn(contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(accounts, key = { it.id }) { a -> AccountCard(a, onClick = { onOpenAccount(a.id) }, onEdit = { editing = a }) }
        }
    }
    editing?.let { a -> EditDialog(a, onDismiss = { editing = null }, onSave = { name, color -> vm.rename(a.id, name, color); editing = null }) }
}

@Composable
private fun AccountCard(a: AccountWithActivity, onClick: () -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = a.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
            Box(Modifier.size(44.dp).background(tint.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (a.kind == AccountKind.CARD) Icons.Filled.CreditCard else Icons.Filled.AccountBalance, null, tint = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.nickname ?: a.bankName, style = MaterialTheme.typography.titleMedium)
                Text("${if (a.kind == AccountKind.CARD) "Card" else "Account"} •• ${a.last4} · ${a.transactionCount} transactions",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Spent this month ${Money.format(a.monthSpent, showPaise = false)}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                val main = if (a.kind == AccountKind.CARD) a.availableLimitMinor else a.latestBalanceMinor
                Text(main?.let { Money.format(it, showPaise = false) } ?: "—", style = MaterialTheme.typography.titleMedium)
                Text(if (a.kind == AccountKind.CARD) "limit left" else a.balanceUpdatedAt?.let { "as of " + Periods.dateTime(it) } ?: "balance",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
            }
        }
    }
}

@Composable
private fun EditDialog(a: AccountWithActivity, onDismiss: () -> Unit, onSave: (String, Int?) -> Unit) {
    var name by remember { mutableStateOf(a.nickname.orEmpty()) }
    var color by remember { mutableStateOf(a.colorArgb) }
    val controller = rememberColorPickerController()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${a.bankName} •• ${a.last4}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nickname") }, singleLine = true)
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                HsvColorPicker(
                    modifier = Modifier.fillMaxWidth().height(180.dp), controller = controller,
                    onColorChanged = { if (it.fromUser) color = it.color.toArgb() },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, color) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
