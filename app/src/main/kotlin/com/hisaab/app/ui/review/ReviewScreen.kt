package com.hisaab.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.TransactionRow
import com.hisaab.app.ui.components.signedAmount
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.db.TransactionSourceDao
import com.hisaab.shared.db.TransactionSourceEntity
import com.hisaab.shared.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewPair(
    val flagged: TransactionEntity,
    val candidate: TransactionEntity?,
    val flaggedSources: List<TransactionSourceEntity> = emptyList(),
    val candidateSources: List<TransactionSourceEntity> = emptyList(),
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val dao: TransactionDao,
    private val sources: TransactionSourceDao,
    private val repository: TransactionRepository,
) : ViewModel() {
    val pairs = dao.observeNeedsReview().map { list ->
        list.map {
            val other = it.duplicateOfId?.let { id -> dao.getById(id) }
            ReviewPair(it, other, sources.forTransaction(it.id), other?.let { o -> sources.forTransaction(o.id) }.orEmpty())
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun merge(id: Long) = viewModelScope.launch { repository.mergeFlagged(id) }
    fun keep(id: Long) = viewModelScope.launch { repository.keepSeparate(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewRoute(onBack: () -> Unit, onOpen: (Long) -> Unit, onCompare: (Long, Long) -> Unit, vm: ReviewViewModel = hiltViewModel()) {
    val pairs by vm.pairs.collectAsStateWithLifecycle()
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), 
            title = { Text("Possible duplicates") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { com.hisaab.app.ui.components.InfoButton("Possible duplicates", *com.hisaab.app.ui.components.Info.DUPLICATES) },
        )
    }) { inner ->
        if (pairs.isEmpty()) EmptyState(Icons.Filled.DoneAll, "All clear", "Nothing needs a decision.", Modifier.padding(inner))
        LazyColumn(contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Each pair looks like one payment reported twice. Tap a transaction to see it, or Compare to see both side by side.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(pairs, key = { it.flagged.id }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(p.flagged.reviewReason ?: "These look like the same transaction", style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        TransactionRow(p.flagged, onClick = { onOpen(p.flagged.id) }, showDate = true)
                        p.flaggedSources.forEach { s -> com.hisaab.app.ui.components.MessageView(s, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), collapsedLines = 4) }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        p.candidate?.let { TransactionRow(it, onClick = { onOpen(it.id) }, showDate = true) }
                        p.candidateSources.forEach { s -> com.hisaab.app.ui.components.MessageView(s, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), collapsedLines = 4) }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            p.candidate?.let { c ->
                                TextButton(onClick = { onCompare(p.flagged.id, c.id) }) {
                                    Icon(Icons.AutoMirrored.Filled.CompareArrows, null)
                                    Spacer(Modifier.padding(2.dp))
                                    Text("Compare")
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = { vm.keep(p.flagged.id) }) { Text("Keep both") }
                            Spacer(Modifier.padding(4.dp))
                            Button(onClick = { vm.merge(p.flagged.id) }) { Text("Merge") }
                        }
                    }
                }
            }
        }
    }
}

// Side-by-side comparison of a flagged transaction and the one it may duplicate.

data class Side(val tx: TransactionEntity, val sources: List<TransactionSourceEntity>)

@HiltViewModel
class CompareViewModel @Inject constructor(
    handle: SavedStateHandle,
    dao: TransactionDao,
    sources: TransactionSourceDao,
    private val repository: TransactionRepository,
) : ViewModel() {
    private val flaggedId: Long = checkNotNull(handle.get<Long>("a"))
    private val otherId: Long = checkNotNull(handle.get<Long>("b"))

    val sides = combine(
        dao.observeById(flaggedId), sources.observeForTransaction(flaggedId), dao.observeById(otherId), sources.observeForTransaction(otherId),
    ) { a, sa, b, sb -> if (a == null || b == null) null else Side(a, sa) to Side(b, sb) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun merge(then: () -> Unit) = viewModelScope.launch { repository.mergeFlagged(flaggedId); then() }
    fun keep(then: () -> Unit) = viewModelScope.launch { repository.keepSeparate(flaggedId); then() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareRoute(onBack: () -> Unit, vm: CompareViewModel = hiltViewModel()) {
    val sides by vm.sides.collectAsStateWithLifecycle()
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text("Compare") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        val (a, b) = sides ?: return@Scaffold
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompareRow("", "This one", "Earlier one", header = true)
                    HorizontalDivider()
                    CompareRow("Amount", signedAmount(a.tx), signedAmount(b.tx))
                    CompareRow("When", Periods.dateTime(a.tx.timestamp), Periods.dateTime(b.tx.timestamp))
                    CompareRow("Merchant", a.tx.merchant ?: "—", b.tx.merchant ?: "—")
                    CompareRow("Account", account(a.tx), account(b.tx))
                    CompareRow("Channel", a.tx.channel.name.replace('_', ' '), b.tx.channel.name.replace('_', ' '))
                    CompareRow("Reference", a.tx.referenceNumber ?: "—", b.tx.referenceNumber ?: "—")
                    CompareRow("Balance after", a.tx.balanceMinor?.let { Money.format(it) } ?: "—", b.tx.balanceMinor?.let { Money.format(it) } ?: "—")
                    CompareRow("Category", a.tx.category.label, b.tx.category.label)
                    CompareRow("Reported by", a.sources.joinToString { it.source }, b.sources.joinToString { it.source })
                }
            }
            Text(
                "Different balances after, different times far apart, or two separate messages from the same bank usually mean two real payments.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Messages("This one", a.sources)
            Messages("Earlier one", b.sources)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = { vm.keep(onBack) }) { Text("Keep both") }
                Button(onClick = { vm.merge(onBack) }) { Text("Merge into one") }
            }
        }
    }
}

private fun account(t: TransactionEntity) = t.bankName + (t.accountLast4?.let { " ••$it" } ?: "")

@Composable
private fun CompareRow(label: String, left: String, right: String, header: Boolean = false) {
    val differs = !header && left != right
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    val color = if (differs) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(0.28f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(left, Modifier.weight(0.36f), style = MaterialTheme.typography.bodySmall, fontWeight = weight, color = color)
        Text(right, Modifier.weight(0.36f), style = MaterialTheme.typography.bodySmall, fontWeight = weight, color = color)
    }
}

@Composable
private fun Messages(title: String, sources: List<TransactionSourceEntity>) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    sources.forEach { s -> com.hisaab.app.ui.components.MessageView(s) }
}
