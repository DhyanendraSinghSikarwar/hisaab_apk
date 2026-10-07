package com.hisaab.app.ui.invest

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
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
import com.hisaab.app.i18n.t
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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

    /** The holdings an investment statement (CAS) listed, as read from it. */
    private val reloadHoldings = kotlinx.coroutines.flow.MutableStateFlow(0)
    val holdings = kotlinx.coroutines.flow.combine(statements.observeById(id), reloadHoldings) { s, _ -> s }
        .flatMapLatest { s ->
            kotlinx.coroutines.flow.flow {
                emit(s?.let { runCatching { processor.holdingsOf(it) }.getOrDefault(emptyList()) }.orEmpty())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun canReread(s: StatementEntity) = processor.canReread(s)

    fun reread() = viewModelScope.launch {
        busy.value = true
        val r = runCatching { processor.reread(id) }.getOrNull()
        busy.value = false
        reloadHoldings.value++
        message.value = when {
            r == null -> t("Couldn't read it again.")
            r.inserted + r.flagged == 0 -> t("Read again: nothing new. The account is up to date.")
            else -> t("Read again: {n} new transactions added.", "n" to r.inserted + r.flagged)
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
    val holdings by vm.holdings.collectAsStateWithLifecycle()
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text(t("Statement")) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back")) } })
    }) { inner ->
        val st = s ?: return@Scaffold
        // A CAS or broker statement lists holdings, not money in and out.
        val holdingsStatement = st.kind == "INVESTMENT" || (st.holdingCount > 0 && rows.isEmpty())
        LazyColumn(
            Modifier.fillMaxSize().padding(top = inner.calculateTopPadding()).clipToBounds(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            item { Header(st) }
            if (holdingsStatement) {
                item { HoldingsSummary(st, holdings) }
            } else {
                item { Summary(st, rows.filter { it.type == TransactionType.CREDIT }.sumOf { it.amountMinor },
                    rows.filter { it.type == TransactionType.DEBIT || it.type == TransactionType.INVESTMENT }.sumOf { it.amountMinor }) }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (rows.isNotEmpty()) {
                        Text(
                            t("{added} added from this statement · {matched} matched SMS or email already in DhanKosh", "added" to rows.size - matched, "matched" to matched),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    accountLine(st)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (vm.canReread(st)) {
                        androidx.compose.material3.OutlinedButton(onClick = vm::reread, enabled = !busy) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (busy) t("Reading…") else t("Read again"))
                        }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
            }
            if (st.holdingCount > 0) {
                item {
                    TextButton(onClick = onOpenInvestments, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(t("{n} holdings updated. See them in Portfolio", "n" to st.holdingCount))
                    }
                }
            }
            if (holdings.isNotEmpty()) {
                item {
                    Text(t("Holdings read ({n})", "n" to holdings.size), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                }
                items(holdings, key = { "h-" + it.identifier }) { h -> HoldingLine(h) }
            }
            if (st.subject != null || !st.emailText.isNullOrBlank()) {
                item { EmailSection(st) }
            }
            if (rows.isNotEmpty()) {
                item {
                    Text(t("Transactions ({n})", "n" to rows.size), style = MaterialTheme.typography.titleMedium,
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
                listOfNotNull(KIND_LABELS[s.kind]?.let { t(it) }, day(s.statementEpochDay)?.let { t("dated {date}", "date" to it) } ?: t("received {date}", "date" to Periods.dateTime(s.receivedAt)))
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
                Figure(t("Total amount due"), s.totalDueMinor?.let { Money.format(it) } ?: "—", big = true)
                Row {
                    Figure(t("Minimum due"), s.minDueMinor?.let { Money.format(it) } ?: "—", Modifier.weight(1f))
                    Figure(t("Pay by"), day(s.dueEpochDay) ?: "—", Modifier.weight(1f))
                }
                s.creditLimitMinor?.let { Figure(t("Credit limit"), Money.format(it, showPaise = false)) }
                s.dueEpochDay?.let { due ->
                    val left = due - LocalDate.now(Periods.zone).toEpochDay()
                    if (left in 0..10) Text(if (left == 0L) t("Due today") else if (left > 1) t("Due in {n} days", "n" to left) else t("Due in {n} day", "n" to left),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
                s.availableMinor?.let { Figure(t("Available limit"), Money.format(it, showPaise = false)) }
            } else {
                s.closingMinor?.let { Figure(t("Closing balance"), Money.format(it), big = true) }
                Row {
                    Figure(t("Money in"), Money.format(s.creditsMinor ?: moneyIn, showPaise = false), Modifier.weight(1f), color = MoneyColors.credit)
                    Figure(t("Money out"), Money.format(s.debitsMinor ?: moneyOut, showPaise = false), Modifier.weight(1f), color = MoneyColors.debit)
                }
                s.openingMinor?.let { Figure(t("Opening balance"), Money.format(it)) }
            }
            Text(
                t("{n} transactions read", "n" to s.transactionCount) + if (s.holdingCount > 0) " · " + t("{n} holdings", "n" to s.holdingCount) else "",
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
            t("Updated {account}: available limit {amount}", "account" to acct, "amount" to Money.format(s.availableMinor ?: ((s.creditLimitMinor ?: 0) - (s.totalDueMinor ?: 0)).coerceAtLeast(0), showPaise = false))
        s.closingMinor != null -> t("Updated {account}: balance {amount}", "account" to acct, "amount" to Money.format(s.closingMinor!!))
        else -> null
    }
}

/** An investment statement's figures: what the holdings read from it are worth, what went into them, and how many. */
@Composable
private fun HoldingsSummary(s: StatementEntity, holdings: List<com.hisaab.parser.model.HoldingSnapshot>) {
    val value = holdings.sumOf { it.valueMinor ?: 0L }
    val withCost = holdings.filter { it.investedMinor != null }
    val invested = withCost.sumOf { it.investedMinor ?: 0L }
    val count = if (holdings.isNotEmpty()) holdings.size else s.holdingCount
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Figure(t("Value of holdings"), if (holdings.isEmpty()) "—" else Money.format(value, showPaise = false), big = true)
            Row {
                Figure(t("Invested"), if (withCost.isEmpty()) "—" else Money.format(invested, showPaise = false), Modifier.weight(1f))
                Figure(t("Holdings"), count.toString(), Modifier.weight(1f))
            }
            if (withCost.isNotEmpty()) {
                // Gain only over the holdings whose cost the statement gives.
                val gain = withCost.sumOf { it.valueMinor ?: 0L } - invested
                Figure(
                    if (gain >= 0) t("Gain") else t("Loss"), (if (gain >= 0) "+" else "−") + Money.format(kotlin.math.abs(gain), showPaise = false),
                    color = if (gain >= 0) MoneyColors.credit else MoneyColors.debit,
                )
            }
            Text(
                t("{n} transactions read · {count} holdings", "n" to s.transactionCount, "count" to count),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun HoldingLine(h: com.hisaab.parser.model.HoldingSnapshot) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(h.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                listOfNotNull(t(h.kind.label), h.units?.let { t("{units} units", "units" to units(it)) }, h.identifier.takeUnless { it.startsWith("MF:") }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(h.valueMinor?.let { Money.format(it, showPaise = false) } ?: "—", style = MaterialTheme.typography.titleSmall)
            h.investedMinor?.let {
                Text(t("cost {amount}", "amount" to Money.format(it, showPaise = false)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun units(u: Double): String = java.math.BigDecimal(u).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** The email the statement came with: subject, and the text folded until tapped. */
@Composable
private fun EmailSection(s: StatementEntity) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val text = s.emailText?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString("\n")
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.clickable { open = !open }.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Email"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) t("Hide email") else t("Show email"))
            }
            s.subject?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
            Text(s.sender, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (open && text != null) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
