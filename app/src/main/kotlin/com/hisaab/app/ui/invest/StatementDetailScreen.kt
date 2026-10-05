package com.hisaab.app.ui.invest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.components.BrandMark
import com.hisaab.app.ui.components.Brands
import com.hisaab.app.ui.components.TransactionRow
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.MoneyColors
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.StatementEntity
import com.hisaab.shared.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatementDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    statements: StatementDao,
    transactions: TransactionDao,
    private val processor: com.hisaab.email.statement.StatementProcessor,
) : ViewModel() {
    private val id: Long = checkNotNull(handle.get<Long>("id"))
    val statement = statements.observeById(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val rows = statements.observeById(id).flatMapLatest { s -> s?.let { transactions.observeForStatement(it.key) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Rows that matched a payment an SMS or email had already reported, so they were not added twice. */
    val matched = statements.observeById(id).flatMapLatest { s -> s?.let { transactions.observeMatchedForStatement(it.key) } ?: flowOf(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun canReread(s: StatementEntity) = processor.canReread(s)

    fun reread() = viewModelScope.launch {
        busy.value = true
        val r = runCatching { processor.reread(id) }.getOrNull()
        busy.value = false
        message.value = when {
            r == null -> "Couldn't read it again."
            r.inserted + r.flagged == 0 -> "Read again: nothing new. The account is up to date."
            else -> "Read again: ${r.inserted + r.flagged} new transactions added."
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun day(epochDay: Long?) = epochDay?.let { LocalDate.ofEpochDay(it).format(DAY) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementDetailRoute(onBack: () -> Unit, onOpenTransaction: (Long) -> Unit, onOpenInvestments: () -> Unit, vm: StatementDetailViewModel = hiltViewModel()) {
    val s by vm.statement.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val matched by vm.matched.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text("Statement") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        val st = s ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, bottom = 32.dp)) {
            item { Header(st) }
            item { Summary(st, rows.filter { it.type == TransactionType.CREDIT }.sumOf { it.amountMinor },
                rows.filter { it.type == TransactionType.DEBIT || it.type == TransactionType.INVESTMENT }.sumOf { it.amountMinor }) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (rows.isNotEmpty()) {
                        Text(
                            "${rows.size - matched} added from this statement · $matched matched SMS or email already in Hisaab",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    accountLine(st)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (vm.canReread(st)) {
                        androidx.compose.material3.OutlinedButton(onClick = vm::reread, enabled = !busy) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (busy) "Reading…" else "Read again")
                        }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
            }
            if (st.holdingCount > 0) {
                item {
                    TextButton(onClick = onOpenInvestments, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("${st.holdingCount} holdings updated. See them in Investments")
                    }
                }
            }
            if (rows.isNotEmpty()) {
                item {
                    Text("Transactions (${rows.size})", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                }
                items(rows, key = { it.id }) { tx -> TransactionRow(tx, onClick = { onOpenTransaction(tx.id) }, showDate = true) }
            }
        }
    }
}

@Composable
private fun Header(s: StatementEntity) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandMark(Brands.forBank(s.bankName ?: s.sender), size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text((s.bankName ?: s.sender.substringBefore('<').trim()) + (s.last4?.let { " ••$it" }.orEmpty()), style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(KIND_LABELS[s.kind], day(s.statementEpochDay)?.let { "dated $it" } ?: "received ${Periods.dateTime(s.receivedAt)}")
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(s.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private val KIND_LABELS = mapOf("CREDIT_CARD" to "Credit card statement", "BANK" to "Bank statement", "INVESTMENT" to "Investment statement", "OTHER" to "Statement")

@Composable
private fun Summary(s: StatementEntity, moneyIn: Long, moneyOut: Long) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.kind == "CREDIT_CARD") {
                Figure("Total amount due", s.totalDueMinor?.let { Money.format(it) } ?: "—", big = true)
                Row {
                    Figure("Minimum due", s.minDueMinor?.let { Money.format(it) } ?: "—", Modifier.weight(1f))
                    Figure("Pay by", day(s.dueEpochDay) ?: "—", Modifier.weight(1f))
                }
                s.creditLimitMinor?.let { Figure("Credit limit", Money.format(it, showPaise = false)) }
                s.dueEpochDay?.let { due ->
                    val left = due - LocalDate.now(Periods.zone).toEpochDay()
                    if (left in 0..10) Text(if (left == 0L) "Due today" else "Due in $left day${if (left > 1) "s" else ""}",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
                s.availableMinor?.let { Figure("Available limit", Money.format(it, showPaise = false)) }
            } else {
                s.closingMinor?.let { Figure("Closing balance", Money.format(it), big = true) }
                Row {
                    Figure("Money in", Money.format(s.creditsMinor ?: moneyIn, showPaise = false), Modifier.weight(1f), color = MoneyColors.credit)
                    Figure("Money out", Money.format(s.debitsMinor ?: moneyOut, showPaise = false), Modifier.weight(1f), color = MoneyColors.debit)
                }
                s.openingMinor?.let { Figure("Opening balance", Money.format(it)) }
            }
            Text(
                "${s.transactionCount} transactions read" + if (s.holdingCount > 0) " · ${s.holdingCount} holdings" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false, color: androidx.compose.ui.graphics.Color? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(value, style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = color ?: MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** What reading the statement changed in the account, in words. */
private fun accountLine(s: StatementEntity): String? {
    val acct = (s.bankName ?: return null) + (s.last4?.let { " ••$it" } ?: return null)
    return when {
        s.kind == "CREDIT_CARD" && (s.availableMinor != null || s.creditLimitMinor != null) ->
            "Updated $acct: available limit ${Money.format(s.availableMinor ?: ((s.creditLimitMinor ?: 0) - (s.totalDueMinor ?: 0)).coerceAtLeast(0), showPaise = false)}"
        s.closingMinor != null -> "Updated $acct: balance ${Money.format(s.closingMinor!!)}"
        else -> null
    }
}
