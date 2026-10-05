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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import com.hisaab.app.ui.components.CategorySheet
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.shadow
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
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsRoute(onOpen: (Long) -> Unit, onAdd: () -> Unit, contentPadding: PaddingValues, vm: TransactionsViewModel = hiltViewModel()) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val rows by vm.transactions.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 15 } ?: false } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    // Long-press starts selecting; while anything is selected, a tap toggles instead of opening.
    var selected by rememberSaveable(stateSaver = LongSetSaver) { mutableStateOf(emptySet<Long>()) }
    var pickingCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    BackHandler(enabled = selecting) { selected = emptySet() }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, 
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Cancel selection") } },
                    actions = {
                        IconButton(onClick = { selected = rows.map { it.id }.toSet() }) { Icon(Icons.Filled.SelectAll, "Select all") }
                        IconButton(onClick = { pickingCategory = true }) { Icon(Icons.Filled.Category, "Change category") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            } else {
                TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text("Transactions") })
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add") },
                    modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
                )
            }
        },
    ) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding()).fillMaxSize()) {
            SearchPill(filter.search, rows.size, onChange = { q -> vm.update { it.copy(search = q) } })
            Filters(filter, accounts, vm::update)
            val grouped = remember(rows) { rows.groupBy { Periods.localDate(it.timestamp) } }
            if (rows.isEmpty()) {
                EmptyState(Icons.Filled.ReceiptLong, "Nothing here",
                    filter.month?.let { "No transactions in ${Periods.month(it)} match these filters." } ?: "No transactions match these filters.")
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
                grouped.forEach { (day, txs) ->
                    stickyHeader(key = "h$day") {
                        val out = txs.filter { it.type == TransactionType.DEBIT || it.type == TransactionType.INVESTMENT }.sumOf { it.amountMinor }
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(Periods.dayHeader(day), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            if (out > 0) Text("−" + Money.format(out, showPaise = false), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(txs, key = { it.id }) { tx ->
                        TransactionRow(
                            tx, selected = tx.id in selected, modifier = Modifier.animateItem(),
                            onClick = { if (selecting) toggle(tx.id) else onOpen(tx.id) },
                            onLongClick = { toggle(tx.id) },
                        )
                    }
                }
            }
        }
    }
    BulkDialogs(
        count = selected.size, pickingCategory = pickingCategory, confirmDelete = confirmDelete,
        onPick = { c -> vm.setCategory(selected, c); selected = emptySet() },
        onDelete = { vm.delete(selected); selected = emptySet(); confirmDelete = false },
        onDismissPicker = { pickingCategory = false }, onDismissDelete = { confirmDelete = false },
    )
}

@Composable
private fun BulkDialogs(
    count: Int, pickingCategory: Boolean, confirmDelete: Boolean,
    onPick: (Category) -> Unit, onDelete: () -> Unit, onDismissPicker: () -> Unit, onDismissDelete: () -> Unit,
) {
    if (pickingCategory) CategorySheet(current = null, onPick = onPick, onDismiss = onDismissPicker, title = "Category for $count transactions")
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Delete $count transactions?") },
            text = { Text("They are removed from Hisaab and won't come back on a rescan. The SMS and emails themselves are not touched.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text("Cancel") } },
        )
    }
}

private val LongSetSaver = androidx.compose.runtime.saveable.Saver<Set<Long>, LongArray>(
    save = { it.toLongArray() }, restore = { it.toSet() },
)

@Composable
private fun Filters(f: TransactionFilter, accounts: List<AccountEntity>, update: ((TransactionFilter) -> TransactionFilter) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val thisMonth = YearMonth.now(Periods.zone)
        val months = (0L until MONTH_CHOICES).map { thisMonth.minusMonths(it) }.let { if (f.month != null && f.month !in it) it + f.month else it }
        Menu(
            label = f.month?.let(Periods::month) ?: "Any time", selected = f.month != null,
            options = listOf<Pair<String, YearMonth?>>("Any time" to null) + months.map { Periods.month(it) to it },
            onPick = { m -> update { it.copy(month = m) } },
        )
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

private const val MONTH_CHOICES = 24L

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

/**
 * A soft pill search box: no underline, a gradient ring that lights up while typing, a clear button, and a count
 * of what matched.
 */
@Composable
private fun SearchPill(query: String, matches: Int, onChange: (String) -> Unit) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring by androidx.compose.animation.core.animateFloatAsState(if (focused) 1f else 0f, label = "ring")
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    val c = MaterialTheme.colorScheme
    androidx.compose.material3.TextField(
        value = query, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("search")
            .shadow((2 + 6 * ring).dp, shape, ambientColor = c.primary, spotColor = c.primary)
            .border(
                (1 + ring).dp,
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(c.primary.copy(alpha = 0.25f + 0.6f * ring), c.tertiary.copy(alpha = 0.25f + 0.6f * ring)),
                ),
                shape,
            ),
        shape = shape,
        interactionSource = interaction,
        placeholder = { Text("Search merchant, bank, amount, ref…", maxLines = 1) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = if (focused) c.primary else c.onSurfaceVariant) },
        trailingIcon = {
            androidx.compose.animation.AnimatedVisibility(query.isNotEmpty(), enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("$matches", style = MaterialTheme.typography.labelMedium, color = c.onPrimaryContainer,
                        modifier = Modifier.background(c.primaryContainer, androidx.compose.foundation.shape.CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
                    IconButton(onClick = { onChange(""); focus.clearFocus() }) { Icon(Icons.Filled.Clear, "Clear") }
                }
            }
        },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = c.surfaceContainerHigh, unfocusedContainerColor = c.surfaceContainerHigh.copy(alpha = 0.85f),
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}
