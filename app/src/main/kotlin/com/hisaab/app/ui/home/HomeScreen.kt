package com.hisaab.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.ui.components.CategoryBadge
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.SectionHeader
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
    val context = LocalContext.current
    var hasSms by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        hasSms = granted[Manifest.permission.READ_SMS] == true
        if (hasSms) vm.scanInbox(full = true)
    }
    HomeScreen(
        state = state, hasSmsPermission = hasSms,
        onGrantSms = { launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) },
        onOpenTransaction = onOpenTransaction, onSeeAllTransactions = onSeeAllTransactions, onOpenAccounts = onOpenAccounts,
        onOpenReview = onOpenReview, onOpenBudgets = onOpenBudgets, contentPadding = contentPadding,
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
) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Hisaab")
                    Text(Periods.month(state.month), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.testTag("home"),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            if (!hasSmsPermission) item { PermissionCard(onGrantSms) }
            if (state.scan.running) item { ScanCard(state.scan) }
            if (state.reviewCount > 0) item { ReviewBanner(state.reviewCount, onOpenReview) }

            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Spent", Money.format(state.spent, showPaise = false), Modifier.weight(1f), MoneyColors.debit, Icons.AutoMirrored.Filled.CallMade)
                    StatCard("Income", Money.format(state.income, showPaise = false), Modifier.weight(1f), MoneyColors.credit, Icons.AutoMirrored.Filled.CallReceived)
                }
            }
            item {
                val label = if (state.balanceAccounts > 0) "Balance across ${state.balanceAccounts} account${if (state.balanceAccounts > 1) "s" else ""}"
                else "Balance (shown once a bank message includes it)"
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

            item { SectionHeader("Recent") { TextButton(onClick = onSeeAllTransactions) { Text("See all") } } }
            if (state.recent.isEmpty() && state.loaded) {
                item {
                    EmptyState(Icons.Filled.Inbox, "No transactions yet",
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
private fun PermissionCard(onGrant: () -> Unit) {
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
            Button(onClick = onGrant, modifier = Modifier.testTag("grant-sms")) { Text("Allow SMS access") }
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
            val value = if (a.kind == AccountKind.CARD) a.availableLimitMinor?.let { "Limit left " + Money.compact(it) } else a.latestBalanceMinor?.let { Money.format(it, showPaise = false) }
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
