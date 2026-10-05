package com.hisaab.app.ui.invest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.StackedLineChart
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.saveable.rememberSaveable
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.Info
import com.hisaab.app.ui.components.InfoButton
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.MoneyColors
import com.hisaab.parser.model.HoldingKind
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.HoldingEntity
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.StatementEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** The sub-tabs on the Investments screen. */
enum class InvestTab(val label: String, val kinds: Set<HoldingKind>, val icon: ImageVector) {
    ALL("All", HoldingKind.entries.toSet(), Icons.Filled.PieChart),
    EPF("EPF", setOf(HoldingKind.EPF), Icons.Filled.Work),
    NPS("NPS", setOf(HoldingKind.NPS), Icons.Filled.Elderly),
    MF("Mutual funds", setOf(HoldingKind.MUTUAL_FUND), Icons.Filled.PieChart),
    STOCKS("Stocks & ETFs", setOf(HoldingKind.STOCK, HoldingKind.ETF), Icons.AutoMirrored.Filled.ShowChart),
    OTHER("Other", setOf(HoldingKind.PPF, HoldingKind.BOND, HoldingKind.GOLD, HoldingKind.FD, HoldingKind.OTHER), Icons.Filled.Savings),
}

@HiltViewModel
class InvestmentsViewModel @Inject constructor(private val dao: HoldingDao, statements: StatementDao) : ViewModel() {
    val holdings = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lockedStatements = statements.observeAll().map { all -> all.count { it.status == StatementEntity.LOCKED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun save(existing: HoldingEntity?, kind: HoldingKind, name: String, value: Long?, invested: Long?, units: Double?) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        if (existing == null) {
            dao.insert(HoldingEntity(kind = kind, name = name.trim(), identifier = "manual:${UUID.randomUUID()}", units = units, valueMinor = value,
                investedMinor = invested, asOf = now, source = "MANUAL", note = null, updatedAt = now))
        } else {
            dao.update(existing.copy(kind = kind, name = name.trim(), units = units, valueMinor = value, investedMinor = invested, asOf = now, updatedAt = now))
        }
    }

    fun delete(h: HoldingEntity) = viewModelScope.launch { dao.delete(h.id) }
}

val HoldingKind.icon: ImageVector
    get() = when (this) {
        HoldingKind.EPF -> Icons.Filled.Work
        HoldingKind.MUTUAL_FUND -> Icons.Filled.PieChart
        HoldingKind.STOCK -> Icons.AutoMirrored.Filled.ShowChart
        HoldingKind.ETF -> Icons.Filled.StackedLineChart
        HoldingKind.NPS -> Icons.Filled.Elderly
        HoldingKind.PPF -> Icons.Filled.Savings
        HoldingKind.BOND -> Icons.Filled.Description
        HoldingKind.GOLD -> Icons.Filled.Diamond
        HoldingKind.FD -> Icons.Filled.Lock
        HoldingKind.OTHER -> Icons.Filled.Category
    }

val HoldingKind.color: Color
    get() = when (this) {
        HoldingKind.EPF -> Color(0xFF00897B)
        HoldingKind.MUTUAL_FUND -> Color(0xFF5E35B1)
        HoldingKind.STOCK -> Color(0xFF1E88E5)
        HoldingKind.ETF -> Color(0xFF3949AB)
        HoldingKind.NPS -> Color(0xFF6D4C41)
        HoldingKind.PPF -> Color(0xFF43A047)
        HoldingKind.BOND -> Color(0xFF546E7A)
        HoldingKind.GOLD -> Color(0xFFF9A825)
        HoldingKind.FD -> Color(0xFF8E24AA)
        HoldingKind.OTHER -> Color(0xFF757575)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentsRoute(
    onOpenStatements: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    onBack: (() -> Unit)? = null,
    /** Opens Accounts on a tab: 0 accounts, 1 cards, 2 deposits & loans. */
    onOpenAccounts: (Int) -> Unit = {},
    vm: InvestmentsViewModel = hiltViewModel(),
) {
    val all by vm.holdings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val holdings = all.filter { it.kind in InvestTab.entries[tab].kinds }
    val locked by vm.lockedStatements.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HoldingEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, 
        topBar = {
            Column {
                TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), 
                    title = { Text("Portfolio") },
                    navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } } },
                    actions = {
                        TextButton(onClick = onOpenStatements) { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null); Spacer(Modifier.width(6.dp)); Text("Statements") }
                    },
                )
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                    InvestTab.entries.forEachIndexed { i, t ->
                        val count = all.count { it.kind in t.kinds }
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(if (count > 0 && t != InvestTab.ALL) "${t.label} ($count)" else t.label) })
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add") },
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Summary(holdings) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("Accounts", Icons.Filled.AccountBalance, 0),
                        Triple("Cards", Icons.Filled.CreditCard, 1),
                        Triple("Deposits & loans", Icons.Filled.Savings, 2),
                    ).forEach { (label, icon, tab) ->
                        Card(Modifier.weight(1f).clickable { onOpenAccounts(tab) }) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                                    modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            if (locked > 0) {
                item {
                    Card(Modifier.fillMaxWidth().clickable(onClick = onOpenStatements),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text("$locked statement${if (locked > 1) "s need" else " needs"} a password. Tap to unlock.",
                            Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (holdings.isEmpty()) {
                item {
                    EmptyState(
                        InvestTab.entries[tab].icon, if (tab == 0) "No investments yet" else "No ${InvestTab.entries[tab].label} yet",
                        "EPF balances come from EPFO SMS. Mutual funds and shares come from your CAS (CAMS, KFintech, NSDL or CDSL) " +
                            "and broker statements by email, or a PDF you import under Statements. You can also add any holding yourself.",
                    )
                }
            }
            HoldingKind.entries.forEach { kind ->
                val group = holdings.filter { it.kind == kind }
                if (group.isNotEmpty()) {
                    item(key = "h-${kind.name}") {
                        Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(kind.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(Money.format(group.sumOf { it.valueMinor ?: 0 }, showPaise = false), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    items(group, key = { it.id }) { h -> HoldingRow(h) { editing = h } }
                }
            }
        }
    }
    if (adding || editing != null) {
        HoldingSheet(editing, onDismiss = { adding = false; editing = null },
            onSave = { kind, name, value, invested, units -> vm.save(editing, kind, name, value, invested, units); adding = false; editing = null },
            onDelete = editing?.let { h -> { vm.delete(h); editing = null } })
    }
}

@Composable
private fun Summary(holdings: List<HoldingEntity>) {
    val value = holdings.sumOf { it.valueMinor ?: 0 }
    val withCost = holdings.filter { it.investedMinor != null && it.valueMinor != null }
    val invested = withCost.sumOf { it.investedMinor!! }
    val gain = withCost.sumOf { it.valueMinor!! } - invested
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Current value", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(Money.format(value, showPaise = false), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (invested > 0) {
                val pct = gain * 100.0 / invested
                Text(
                    "Invested ${Money.format(invested, showPaise = false)} · ${if (gain >= 0) "+" else "−"}${Money.format(kotlin.math.abs(gain), showPaise = false)} " +
                        "(${"%.1f".format(pct)}%) where cost is known",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun HoldingRow(h: HoldingEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(h.kind.color.copy(alpha = 0.16f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(h.kind.icon, null, tint = h.kind.color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(h.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                val units = h.units?.let { "${"%,.3f".format(it)} units · " }.orEmpty()
                val source = when (h.source) { "SMS" -> "from SMS"; "STATEMENT" -> "from statement"; else -> "added by you" }
                Text("$units${h.asOf?.let { Periods.dateTime(it) + " · " }.orEmpty()}$source",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(h.valueMinor?.let { Money.format(it, showPaise = false) } ?: "—", style = MaterialTheme.typography.titleMedium)
                if (h.investedMinor != null && h.valueMinor != null && h.investedMinor!! > 0) {
                    val g = h.valueMinor!! - h.investedMinor!!
                    Text("${if (g >= 0) "+" else "−"}${"%.1f".format(kotlin.math.abs(g) * 100.0 / h.investedMinor!!)}%",
                        style = MaterialTheme.typography.labelMedium, color = if (g >= 0) MoneyColors.credit else MoneyColors.debit)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HoldingSheet(
    existing: HoldingEntity?, onDismiss: () -> Unit,
    onSave: (HoldingKind, String, Long?, Long?, Double?) -> Unit, onDelete: (() -> Unit)?,
) {
    var kind by remember { mutableStateOf(existing?.kind ?: HoldingKind.MUTUAL_FUND) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var value by remember { mutableStateOf(existing?.valueMinor?.let { (it / 100.0).toBigDecimal().stripTrailingZeros().toPlainString() }.orEmpty()) }
    var invested by remember { mutableStateOf(existing?.investedMinor?.let { (it / 100.0).toBigDecimal().stripTrailingZeros().toPlainString() }.orEmpty()) }
    var units by remember { mutableStateOf(existing?.units?.toString().orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (existing == null) "Add an investment" else "Edit investment", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HoldingKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }, leadingIcon = { Icon(k.icon, null, Modifier.size(18.dp)) })
                }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text("Current value") }, prefix = { Text("₹") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(invested, { invested = it }, Modifier.fillMaxWidth(), label = { Text("Amount invested (optional)") }, prefix = { Text("₹") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(units, { units = it }, Modifier.fillMaxWidth(), label = { Text("Units or shares (optional)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (existing != null && existing.source != "MANUAL") {
                Text("A newer SMS or statement will update the figures again.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { onSave(kind, name, Money.parseInput(value), Money.parseInput(invested), units.replace(",", "").toDoubleOrNull()) },
                    enabled = name.isNotBlank() && Money.parseInput(value) != null,
                ) { Text("Save") }
            }
        }
    }
}
