package com.hisaab.app.ui.invest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.StackedLineChart
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.hisaab.app.settings.TabLayoutStore
import com.hisaab.app.settings.TabLayouts
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.ledger.AssetClass
import com.hisaab.app.ui.ledger.NetWorth
import com.hisaab.app.ui.ledger.NetWorthSource
import com.hisaab.parser.model.HoldingKind
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.HoldingEntity
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.StatementEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class InvestmentsViewModel @Inject constructor(
    private val dao: HoldingDao,
    statements: StatementDao,
    layout: TabLayoutStore,
    worth: NetWorthSource,
) : ViewModel() {
    /** Holdings, deposits, bank balances and the daily net-worth history. */
    val netWorth: StateFlow<NetWorth> = worth.netWorth

    /** The Portfolio sections to show, in order, as set under Settings → Customize tabs. */
    val sections = layout.settings.map { it.visible(TabLayouts.PORTFOLIO) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TabLayouts.DEFAULTS[TabLayouts.PORTFOLIO].orEmpty().map { it.key })

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
    get() = AssetClass.of(this).color

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
    val nw by vm.netWorth.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val locked by vm.lockedStatements.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HoldingEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    // The asset class picked on the allocation donut; filters the holdings.
    var filterName by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = filterName?.let { n -> AssetClass.entries.firstOrNull { it.name == n } }
    var range by rememberSaveable { mutableStateOf(2) }
    val model = remember(nw) { PortfolioModel.of(nw) }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = com.hisaab.app.ui.theme.clearTopBar(),
                title = { Text("Portfolio") },
                navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } } },
                actions = {
                    // Statements, with a red dot while one is waiting for its password.
                    IconButton(onClick = onOpenStatements) {
                        BadgedBox(badge = { if (locked > 0) Badge() }) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, "Statements")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true }, modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            ) { Icon(Icons.Filled.Add, "Add a holding") }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding() + 4.dp, start = 14.dp, end = 14.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            if (nw.loaded && model.isEmpty) {
                item(key = "empty") {
                    EmptyState(Icons.Filled.PieChart, "No investments yet",
                        "EPF from EPFO SMS; funds and shares from CAS and broker statements. Or tap + to add one.")
                }
            }
            sections.forEach { key ->
                when (key) {
                    "value" -> if (!model.isEmpty) item(key = "value") { ValueCard(model, Modifier.animateItem()) } else Unit
                    "allocation" -> if (model.slices.isNotEmpty()) item(key = "allocation") {
                        AllocationCard(model, filter, onFilter = { filterName = it?.name }, modifier = Modifier.animateItem())
                    } else Unit
                    "networth" -> item(key = "networth") { NetWorthCard(nw, range, onRange = { range = it }, modifier = Modifier.animateItem()) }
                    "holdings" -> holdingsSection(model, filter, onClearFilter = { filterName = null },
                        onEdit = { editing = it }, onOpenAccounts = onOpenAccounts)
                    "maturity" -> item(key = "maturity") { MaturityCard(nw.accounts, onOpen = { onOpenAccounts(2) }, modifier = Modifier.animateItem()) }
                    else -> Unit
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
