package com.hisaab.app.ui.budgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.ui.components.AnimatedAmount
import com.hisaab.app.ui.components.CategoryBadge
import com.hisaab.app.ui.components.CategorySheet
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.InfoButton
import com.hisaab.app.ui.components.pressable
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class BudgetLine(val category: Category, val limit: Long, val spent: Long)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgets: BudgetDao,
    transactions: TransactionDao,
    private val settings: AppSettingsStore,
) : ViewModel() {
    private val range = Periods.range(YearMonth.now(Periods.zone))

    /** Budgets are monthly limits: the same limit applies to every month, measured against that month's spending. */
    val lines = combine(budgets.observeAll(), transactions.observeCategoryTotals(range.first, range.last)) { b, spent ->
        val byCat = spent.associate { it.category to it.total }
        b.map { BudgetLine(it.category, it.monthlyLimitMinor, byCat[it.category] ?: 0) }.sortedByDescending { it.spent.toDouble() / it.limit }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val alertPercent = settings.settings.map { it.budgetAlertPercent }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 90)

    fun save(category: Category, limitMinor: Long) = viewModelScope.launch { budgets.upsert(BudgetEntity(category, limitMinor)) }
    fun delete(category: Category) = viewModelScope.launch { budgets.delete(category.name) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsRoute(
    contentPadding: PaddingValues,
    onBack: (() -> Unit)? = null,
    onOpenCategory: (Category) -> Unit = {},
    vm: BudgetsViewModel = hiltViewModel(),
) {
    val lines by vm.lines.collectAsStateWithLifecycle()
    val alertAt by vm.alertPercent.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BudgetLine?>(null) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, 
        topBar = {
            TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), 
                title = { Text("Budgets") },
                navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } } },
                actions = {
                    InfoButton(
                        "Budgets",
                        "A budget is a monthly limit for a category. Set it once: it applies to every month, and resets on the 1st.",
                        "You get an alert when spending reaches your alert level (90% unless you change it) and again at 100%.",
                        "Tap a budget to see every transaction behind it.",
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Budget") },
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()))
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (lines.isEmpty()) {
                item { EmptyState(Icons.Filled.Savings, "No budgets yet", "Set a monthly limit for a category. It applies every month, and you'll be alerted before you cross it.") }
            } else {
                item { Overview(lines) }
            }
            items(lines, key = { it.category }) { l -> BudgetCard(l, alertAt, onOpen = { onOpenCategory(l.category) }, onEdit = { editing = l }) }
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
private fun Overview(lines: List<BudgetLine>) {
    val limit = lines.sumOf { it.limit }
    val spent = lines.sumOf { it.spent }
    val today = LocalDate.now(Periods.zone)
    val daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1
    val progress by animateFloatAsState((spent.toFloat() / limit.coerceAtLeast(1)).coerceIn(0f, 1f), tween(700), label = "overview")
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("${Periods.month(YearMonth.now(Periods.zone))} · $daysLeft days left", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedAmount(spent, MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("  of ${Money.format(limit, showPaise = false)}", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(bottom = 4.dp))
            }
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun BudgetCard(l: BudgetLine, alertAt: Int, onOpen: () -> Unit, onEdit: () -> Unit) {
    val ratio = l.spent.toFloat() / l.limit.coerceAtLeast(1)
    val over = l.spent > l.limit
    val near = !over && ratio * 100 >= alertAt
    val target = when { over -> MaterialTheme.colorScheme.error; near -> Color(0xFFF29900); else -> l.category.color }
    val barColor by animateColorAsState(target, tween(400), label = "bar")
    val progress by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(700), label = "budget")
    val today = LocalDate.now(Periods.zone)
    val daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1
    Card(Modifier.fillMaxWidth().pressable(onClick = onOpen)) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(l.category, size = 42)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.category.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = barColor)
                }
                // The exact amount, to the paisa.
                Text("${Money.format(l.spent, showPaise = true)} of ${Money.format(l.limit, showPaise = false)}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                LinearProgressIndicator(
                    progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).height(6.dp).clip(CircleShape),
                    color = barColor, trackColor = barColor.copy(alpha = 0.15f), drawStopIndicator = {},
                )
                val left = l.limit - l.spent
                Text(
                    if (over) "Over by ${Money.format(-left, showPaise = false)}"
                    else "${Money.format(left, showPaise = false)} left · about ${Money.format(left / daysLeft, showPaise = false)} a day",
                    style = MaterialTheme.typography.bodySmall, color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit budget") }
        }
    }
}

@Composable
private fun BudgetDialog(initial: BudgetLine?, taken: Set<Category>, onDismiss: () -> Unit, onSave: (Category, Long) -> Unit, onDelete: (() -> Unit)?) {
    var category by remember { mutableStateOf(initial?.category ?: Category.entries.first { it !in taken && it.isSpend }) }
    var amount by remember { mutableStateOf(initial?.limit?.let { (it / 100).toString() } ?: "") }
    var picking by remember { mutableStateOf(false) }
    val minor = Money.parseInput(amount)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New budget" else "Edit budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedCard(Modifier.fillMaxWidth().clickable(enabled = initial == null) { picking = true }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(category, size = 32)
                        Spacer(Modifier.width(10.dp))
                        Text(category.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (initial == null) Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("Monthly limit") }, prefix = { Text("₹") },
                    supportingText = { Text("Applies every month") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = minor != null, onClick = { onSave(category, minor!!) }) { Text("Save") } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (picking) {
        CategorySheet(current = category, onPick = { if (it.isSpend && (it !in taken || it == initial?.category)) category = it }, onDismiss = { picking = false },
            title = "Budget for which category?")
    }
}

private val Category.isSpend: Boolean
    get() = this !in setOf(Category.SALARY, Category.INCOME, Category.REFUND, Category.TRANSFER)
