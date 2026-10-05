package com.hisaab.app.ui.category

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.charts.ChartSlice
import com.hisaab.app.ui.charts.DonutChart
import com.hisaab.app.ui.components.IconLibrary
import com.hisaab.app.ui.components.TransactionRow
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.CategoryDao
import com.hisaab.shared.db.CustomSubcategoryEntity
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.Subcategories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import javax.inject.Inject

/** One sub-category's share of a category's spending, with its transactions. */
data class SubSpend(val name: String, val icon: String, val total: Long, val transactions: List<TransactionEntity>)

data class CategoryDetailState(
    val look: CategoryLook? = null,
    val builtIn: Category? = null,
    val month: YearMonth = YearMonth.now(Periods.zone),
    val subs: List<SubSpend> = emptyList(),
    val total: Long = 0,
    val customSubs: List<CustomSubcategoryEntity> = emptyList(),
)

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(handle: SavedStateHandle, private val dao: CategoryDao) : ViewModel() {
    /** An enum name ("FOOD") or "custom:<id>". */
    val key: String = checkNotNull(handle.get<String>("key"))
    private val customId: Long? = key.removePrefix("custom:").takeIf { key.startsWith("custom:") }?.toLongOrNull()
    private val builtIn: Category? = if (customId == null) runCatching { Category.valueOf(key) }.getOrNull() else null
    private val month = MutableStateFlow(handle.get<String>("month")?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.now(Periods.zone))

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = month.flatMapLatest { m ->
        val r = Periods.range(m)
        combine(dao.observeSpendIn(builtIn?.name ?: Category.OTHER.name, customId, r.first, r.last), dao.observeCustom(), dao.observeSubs()) { txs, customs, subs ->
            val look = builtIn?.let { CategoryLook.of(it) } ?: customs.firstOrNull { it.id == customId }?.let { CategoryLook.of(it) }
            val known = subsFor(key, builtIn, subs).associateBy { it.name }
            val groups = txs.groupBy { t -> if (builtIn != null) Subcategories.of(builtIn, t.subcategory, t.merchant, t.upiId) else t.subcategory ?: Subcategories.OTHER }
                .map { (name, list) -> SubSpend(name, known[name]?.icon ?: "label", list.sumOf { it.amountMinor }, list) }
                .sortedByDescending { it.total }
            CategoryDetailState(look, builtIn, m, groups, groups.sumOf { it.total }, subs.filter { it.parent == key })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryDetailState())

    fun shift(by: Long) = month.update { (it.plusMonths(by)).coerceAtMost(YearMonth.now(Periods.zone)) }
}

/** A category opened from Home: what it was spent on, by sub-category, and the transactions in each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailRoute(onBack: () -> Unit, onOpenTransaction: (Long) -> Unit, vm: CategoryDetailViewModel = hiltViewModel(), cats: CategoriesViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val look = s.look
    var selected by rememberSaveable(s.month) { mutableStateOf<Int?>(null) }
    var expanded by rememberSaveable(s.month) { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(
            colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text(look?.name ?: "Category") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { inner ->
        if (look == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                    Text(Periods.month(s.month), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { vm.shift(1) }, enabled = s.month < YearMonth.now(Periods.zone)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                    }
                }
            }
            if (s.subs.isEmpty()) {
                item {
                    Text("Nothing spent on ${look.name} in ${Periods.month(s.month)}.", Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                item {
                    val palette = remember(look.color, s.subs.size) { shades(look.color, s.subs.size) }
                    val slices = s.subs.mapIndexed { i, sub -> ChartSlice(sub.name, sub.total, palette[i]) }
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        DonutChart(
                            slices = slices, selected = selected, onSelect = { selected = it }, centerLabel = look.name,
                            centerValue = { Money.format(it, showPaise = false) }, modifier = Modifier.fillMaxWidth(0.66f),
                        )
                    }
                }
                item {
                    Text("By sub-category", Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium)
                }
                val palette = shades(look.color, s.subs.size)
                s.subs.forEachIndexed { i, sub ->
                    item(key = "sub-${sub.name}") {
                        SubRow(sub, s.total, palette[i], open = expanded == sub.name) { expanded = if (expanded == sub.name) null else sub.name; selected = i }
                    }
                    if (expanded == sub.name) {
                        items(sub.transactions, key = { "t-${it.id}" }) { tx -> TransactionRow(tx, onClick = { onOpenTransaction(tx.id) }, showDate = true) }
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { creating = true }) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add a sub-category")
                    }
                    if (s.customSubs.isNotEmpty()) {
                        Text("Your sub-categories", style = MaterialTheme.typography.labelLarge)
                        s.customSubs.forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(IconLibrary.get(c.icon), look.color, 32)
                                Text(c.name, Modifier.weight(1f).padding(start = 10.dp))
                                IconButton(onClick = { cats.deleteSub(c.id) }) { Icon(Icons.Filled.Delete, "Delete ${c.name}") }
                            }
                        }
                    }
                    Text(
                        "Tap a sub-category to see its transactions. To move a transaction, open it and change its sub-category.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (creating) {
        NewItemDialog("New sub-category in ${look?.name}", withColor = false, onDismiss = { creating = false }) { name, icon, _ ->
            cats.createSub(vm.key, name, icon); creating = false
        }
    }
}

@Composable
private fun SubRow(sub: SubSpend, total: Long, color: Color, open: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(IconLibrary.get(sub.icon), color, 40)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row {
                    Text(sub.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(Money.format(sub.total, showPaise = false), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "${sub.transactions.size} payment${if (sub.transactions.size == 1) "" else "s"} · ${sub.total * 100 / total.coerceAtLeast(1)}%",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { sub.total.toFloat() / total.coerceAtLeast(1) }, color = color, trackColor = color.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp).clip(CircleShape), drawStopIndicator = {},
                )
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
    }
}

/** Shades of one colour, so sub-categories read as parts of their category. */
private fun shades(base: Color, n: Int): List<Color> = List(n.coerceAtLeast(1)) { i ->
    val t = if (n <= 1) 0f else i.toFloat() / (n - 1)
    androidx.compose.ui.graphics.lerp(base, if (i % 2 == 0) Color.White else Color.Black, t * 0.55f)
}
