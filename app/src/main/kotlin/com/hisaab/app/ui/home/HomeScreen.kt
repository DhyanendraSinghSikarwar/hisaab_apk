package com.hisaab.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.ui.components.CategoryBadge
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.SectionHeader
import com.hisaab.app.ui.components.rememberSmsPermission
import com.hisaab.app.ui.components.StatCard
import com.hisaab.app.ui.components.TransactionRow
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.MoneyColors
import com.hisaab.app.ui.theme.color
import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountWithActivity

@Composable
fun HomeRoute(
    onOpenTransaction: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenBudgets: () -> Unit,
    contentPadding: PaddingValues,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sms = rememberSmsPermission(onGranted = { vm.scanInbox(full = true) })
    HomeScreen(
        state = state, hasSmsPermission = sms.granted, smsBlocked = sms.blocked,
        onGrantSms = sms::request, onDismissSms = vm::dismissSmsPrompt,
        onOpenTransaction = onOpenTransaction, onSeeAllTransactions = onSeeAllTransactions, onOpenAccounts = onOpenAccounts,
        onOpenReview = onOpenReview, onOpenBudgets = onOpenBudgets, contentPadding = contentPadding,
        onPreviousMonth = vm::previousMonth, onNextMonth = vm::nextMonth,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    hasSmsPermission: Boolean,
    onGrantSms: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenBudgets: () -> Unit,
    contentPadding: PaddingValues,
    smsBlocked: Boolean = false,
    onDismissSms: () -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hisaab")
                        Text(Periods.month(state.month), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onPreviousMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                    IconButton(onClick = onNextMonth, enabled = !state.isCurrentMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.testTag("home"),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            if (!hasSmsPermission && !state.smsPromptDismissed) item { PermissionCard(smsBlocked, onGrantSms, onDismissSms) }
            if (state.scan.running) item { ScanCard(state.scan) }
            if (state.reviewCount > 0) item { ReviewBanner(state.reviewCount, onOpenReview) }

            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val monthName = Periods.monthShort(state.month)
                    StatCard("Spent in $monthName", Money.format(state.spent, showPaise = false), Modifier.weight(1f), MoneyColors.debit, Icons.AutoMirrored.Filled.CallMade)
                    StatCard("Income in $monthName", Money.format(state.income, showPaise = false), Modifier.weight(1f), MoneyColors.credit, Icons.AutoMirrored.Filled.CallReceived)
                }
            }
            item {
                val label = if (state.balanceAccounts > 0) "Balance across ${state.balanceAccounts} account${if (state.balanceAccounts > 1) "s" else ""}"
                else "Balance (from bank messages, or set it in Accounts)"
                StatCard(label, state.balance?.let { Money.format(it) } ?: "—",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp), icon = Icons.Filled.AccountBalanceWallet)
            }

            if (state.accounts.isNotEmpty()) {
                item { SectionHeader("Accounts") { TextButton(onClick = onOpenAccounts) { Text("See all") } } }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.accounts, key = { it.id }) { AccountChip(it, onOpenAccounts) }
                    }
                }
            }

            if (state.categories.isNotEmpty()) {
                item { SectionHeader("Where it went") { TextButton(onClick = onOpenBudgets) { Text("Budgets") } } }
                items(state.categories.take(5), key = { it.category }) { c -> CategoryLine(c, state.spent) }
            }

            item {
                SectionHeader(if (state.isCurrentMonth) "Recent" else "Latest in ${Periods.monthShort(state.month)}") {
                    TextButton(onClick = onSeeAllTransactions) { Text("See all") }
                }
            }
            if (state.recent.isEmpty() && state.loaded) {
                item {
                    EmptyState(Icons.Filled.Inbox, if (state.isCurrentMonth) "No transactions this month" else "No transactions in ${Periods.month(state.month)}",
                        if (hasSmsPermission) "Bank SMS will appear here as soon as they are read. Connect Gmail in Settings for email alerts."
                        else "Allow SMS access to read bank alerts from your inbox.")
                }
            }
            items(state.recent, key = { it.id }) { tx -> TransactionRow(tx, onClick = { onOpenTransaction(tx.id) }) }
            state.lastScanResult?.let { item { Text("Last SMS scan: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) } }
        }
    }
}

@Composable
private fun PermissionCard(blocked: Boolean, onGrant: () -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Sms, null)
                Spacer(Modifier.width(8.dp))
                Text("Read bank SMS", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Hisaab reads SMS only from known bank senders, on this phone. Nothing is uploaded; there is no server.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (blocked) {
                Text(
                    "Android didn't show the permission prompt. Open App settings, then Permissions, SMS, Allow. " +
                        "If SMS is greyed out, first tap the menu at the top right of App info and choose Allow restricted settings.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGrant, modifier = Modifier.testTag("grant-sms")) { Text(if (blocked) "Open App settings" else "Allow SMS access") }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun ScanCard(p: ScanProgress) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Reading bank SMS… ${p.scanned} checked, ${p.found} transactions", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ReviewBanner(count: Int, onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                "$count possible duplicate${if (count > 1) "s" else ""} to review",
                color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium,
            )
            Text("Review", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun AccountChip(a: AccountWithActivity, onClick: () -> Unit) {
    Card(Modifier.width(170.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (a.kind == AccountKind.CARD) Icons.Filled.CreditCard else Icons.Filled.AccountBalanceWallet, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(a.nickname ?: a.bankName, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            Text("•• ${a.last4}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val value = a.currentBalanceMinor?.let { if (a.kind == AccountKind.CARD) "Limit left " + Money.compact(it) else Money.format(it, showPaise = false) }
            Text(value ?: "Spent ${Money.compact(a.monthSpent)}", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun CategoryLine(c: CategorySpend, totalSpent: Long) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(c.category, size = 32)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(c.category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    Money.format(c.spent, showPaise = false) + (c.budget?.let { " / " + Money.compact(it) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val fraction = if (c.budget != null && c.budget > 0) c.spent.toFloat() / c.budget else if (totalSpent > 0) c.spent.toFloat() / totalSpent else 0f
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = if (c.budget != null && c.spent > c.budget) MaterialTheme.colorScheme.error else c.category.color,
            )
        }
    }
}
