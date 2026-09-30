package com.hisaab.app.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.components.CategoryBadge
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.color
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.BudgetDao
import com.hisaab.shared.db.BudgetEntity
import com.hisaab.shared.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class BudgetLine(val category: Category, val limit: Long, val spent: Long)

@HiltViewModel
class BudgetsViewModel @Inject constructor(private val budgets: BudgetDao, transactions: TransactionDao) : ViewModel() {
    val lines = combine(budgets.observeAll(), transactions.observeCategoryTotals(Periods.startOfMonth(System.currentTimeMillis()), Long.MAX_VALUE)) { b, spent ->
        val byCat = spent.associate { it.category to it.total }
        b.map { BudgetLine(it.category, it.monthlyLimitMinor, byCat[it.category] ?: 0) }.sortedByDescending { it.spent.toDouble() / it.limit }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(category: Category, limitMinor: Long) = viewModelScope.launch { budgets.upsert(BudgetEntity(category, limitMinor)) }
    fun delete(category: Category) = viewModelScope.launch { budgets.delete(category.name) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsRoute(contentPadding: PaddingValues, vm: BudgetsViewModel = hiltViewModel()) {
    val lines by vm.lines.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BudgetLine?>(null) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Budgets") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Budget") },
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()))
        },
    ) { inner ->
        if (lines.isEmpty()) {
            EmptyState(Icons.Filled.Savings, "No budgets", "Set a monthly limit for a category to track it here and on Home.", Modifier.padding(inner))
        }
        LazyColumn(contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(lines, key = { it.category }) { l -> BudgetCard(l) { editing = l } }
        }
    }
    if (adding || editing != null) {
        BudgetDialog(
            initial = editing, taken = lines.map { it.category }.toSet(),
            onDismiss = { adding = false; editing = null },
            onSave = { c, amt -> vm.save(c, amt); adding = false; editing = null },
            onDelete = editing?.let { e -> { vm.delete(e.category); editing = null } },
        )
    }
}

@Composable
private fun BudgetCard(l: BudgetLine, onClick: () -> Unit) {
    val over = l.spent > l.limit
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(l.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(l.category.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${Money.format(l.spent, showPaise = false)} of ${Money.format(l.limit, showPaise = false)}", style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(
                    progress = { (l.spent.toFloat() / l.limit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    color = if (over) MaterialTheme.colorScheme.error else l.category.color,
                )
                Text(
                    if (over) "Over by ${Money.format(l.spent - l.limit, showPaise = false)}" else "${Money.format(l.limit - l.spent, showPaise = false)} left",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BudgetDialog(initial: BudgetLine?, taken: Set<Category>, onDismiss: () -> Unit, onSave: (Category, Long) -> Unit, onDelete: (() -> Unit)?) {
    var category by remember { mutableStateOf(initial?.category ?: Category.entries.first { it !in taken && it.isSpend }) }
    var amount by remember { mutableStateOf(initial?.limit?.let { (it / 100).toString() } ?: "") }
    var menu by remember { mutableStateOf(false) }
    val minor = amount.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }?.movePointRight(2)?.toLong()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New budget" else "Edit budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = initial == null }) { Text(category.label) }
                    DropdownMenu(menu, { menu = false }) {
                        Category.entries.filter { it.isSpend && (it !in taken || it == initial?.category) }.forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, onClick = { category = c; menu = false })
                        }
                    }
                }
                OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Monthly limit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = minor != null, onClick = { onSave(category, minor!!) }) { Text("Save") } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete") } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private val Category.isSpend: Boolean
    get() = this !in setOf(Category.SALARY, Category.INCOME, Category.REFUND, Category.TRANSFER)
