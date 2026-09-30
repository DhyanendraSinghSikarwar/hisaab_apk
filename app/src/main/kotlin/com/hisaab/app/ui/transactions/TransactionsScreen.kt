package com.hisaab.app.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.TransactionRow
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsRoute(onOpen: (Long) -> Unit, contentPadding: PaddingValues, vm: TransactionsViewModel = hiltViewModel()) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val rows by vm.transactions.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 15 } ?: false } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    Scaffold(topBar = { TopAppBar(title = { Text("Transactions") }) }) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding()).fillMaxSize()) {
            OutlinedTextField(
                value = filter.search, onValueChange = { q -> vm.update { it.copy(search = q) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search"),
                placeholder = { Text("Search merchant, bank, amount, reference") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (filter.search.isNotEmpty()) IconButton(onClick = { vm.update { it.copy(search = "") } }) { Icon(Icons.Filled.Clear, "Clear") } },
                singleLine = true,
            )
            Filters(filter, accounts, vm::update)
            val grouped = remember(rows) { rows.groupBy { Periods.localDate(it.timestamp) } }
            if (rows.isEmpty()) {
                EmptyState(Icons.Filled.ReceiptLong, "Nothing here", "No transactions match these filters.")
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
                grouped.forEach { (day, txs) ->
                    stickyHeader(key = "h$day") {
                        val out = txs.filter { it.type == TransactionType.DEBIT || it.type == TransactionType.INVESTMENT }.sumOf { it.amountMinor }
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(Periods.dayHeader(day), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            if (out > 0) Text("−" + Money.format(out, showPaise = false), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(txs, key = { it.id }) { tx -> TransactionRow(tx, onClick = { onOpen(tx.id) }) }
                }
            }
        }
    }
}

@Composable
private fun Filters(f: TransactionFilter, accounts: List<AccountEntity>, update: ((TransactionFilter) -> TransactionFilter) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceFilter.entries.forEach { s ->
            FilterChip(selected = f.source == s, onClick = { update { it.copy(source = s) } }, label = { Text(s.label) })
        }
        Menu(
            label = f.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Type", selected = f.type != null,
            options = listOf<Pair<String, TransactionType?>>("Any type" to null) + TransactionType.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it },
            onPick = { t -> update { it.copy(type = t) } },
        )
        Menu(
            label = f.category?.label ?: "Category", selected = f.category != null,
            options = listOf<Pair<String, Category?>>("Any category" to null) + Category.entries.map { it.label to it },
            onPick = { c -> update { it.copy(category = c) } },
        )
        Menu(
            label = accounts.firstOrNull { it.id == f.accountId }?.let { "${it.bankName} ••${it.last4}" } ?: "Account", selected = f.accountId != null,
            options = listOf<Pair<String, Long?>>("Any account" to null) + accounts.map { "${it.nickname ?: it.bankName} ••${it.last4}" to it.id },
            onPick = { id -> update { it.copy(accountId = id) } },
        )
    }
}

@Composable
private fun <T> Menu(label: String, selected: Boolean, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { open = true }, label = { Text(label) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) }) }
        }
    }
}
