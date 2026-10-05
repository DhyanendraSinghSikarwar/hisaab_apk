package com.hisaab.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.components.AccountAvatar
import com.hisaab.app.ui.components.BrandMark
import com.hisaab.app.ui.components.Brands
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.pressable
import com.hisaab.app.ui.components.Info
import com.hisaab.app.ui.components.InfoButton
import com.hisaab.app.ui.components.KindColors
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountType
import androidx.compose.material.icons.filled.Savings
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.CardNetwork
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A debit card that probably belongs to [account], and why we think so. */
data class LinkSuggestion(val card: AccountWithActivity, val account: AccountWithActivity, val reason: String)

data class AccountsState(
    val accounts: List<AccountWithActivity> = emptyList(),
    val cards: List<AccountWithActivity> = emptyList(),
    /** FD, RD, PPF and loans: money that isn't spendable, kept on its own tab. */
    val deposits: List<AccountWithActivity> = emptyList(),
    val suggestions: List<LinkSuggestion> = emptyList(),
    val byId: Map<Long, AccountWithActivity> = emptyMap(),
    /** Removed from view by the user; shown again from the bottom of the screen. */
    val hidden: List<AccountWithActivity> = emptyList(),
)

data class AccountEdit(
    val nickname: String, val color: Int?, val type: AccountType?, val network: CardNetwork?, val linkedAccountId: Long?,
    val balance: String, val clearBalance: Boolean,
    val usage: com.hisaab.shared.db.AccountUsage = com.hisaab.shared.db.AccountUsage.PERSONAL,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(private val dao: AccountDao) : ViewModel() {
    private val dismissed = MutableStateFlow(emptySet<Long>())

    val state = combine(dao.observeWithActivity(Periods.startOfMonth(System.currentTimeMillis())), dismissed) { all, dismissedCards ->
        val visible = all.filter { !it.hidden }
        val accounts = visible.filter { it.kind == AccountKind.ACCOUNT && it.accountType?.liquid != false }
        val cards = visible.filter { it.kind == AccountKind.CARD }
        val deposits = visible.filter { it.kind == AccountKind.ACCOUNT && it.accountType?.liquid == false }
        AccountsState(accounts, cards, deposits, suggestions(cards, accounts).filter { it.card.id !in dismissedCards }, all.associateBy { it.id },
            hidden = all.filter { it.hidden })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsState())

    /**
     * Debit cards with no account yet. A debit-card SMS states the account balance, so a card whose last
     * balance equals one account's balance is a strong match; otherwise the only account at the same bank.
     */
    private fun suggestions(cards: List<AccountWithActivity>, accounts: List<AccountWithActivity>): List<LinkSuggestion> =
        cards.filter { it.linkedAccountId == null && (it.isDebitCard || (it.accountType == null && it.latestBalanceMinor != null)) }
            .mapNotNull { card ->
                val sameBank = accounts.filter { it.bankName == card.bankName && it.isLiquid }
                val byBalance = sameBank.firstOrNull { card.latestBalanceMinor != null && it.latestBalanceMinor == card.latestBalanceMinor }
                when {
                    byBalance != null -> LinkSuggestion(card, byBalance, "The balance in this card's SMS matches this account.")
                    sameBank.size == 1 -> LinkSuggestion(card, sameBank.first(), "It's your only ${card.bankName} account.")
                    sameBank.isNotEmpty() -> LinkSuggestion(card, sameBank.maxBy { it.transactionCount }, "Your most used ${card.bankName} account.")
                    else -> null
                }
            }

    fun link(card: AccountWithActivity, account: AccountWithActivity) = viewModelScope.launch {
        dao.link(card.id, account.id)
        if (card.accountType == null) dao.setType(card.id, AccountType.DEBIT_CARD, card.cardNetwork)
    }

    fun dismiss(card: AccountWithActivity) = dismissed.update { it + card.id }
    fun setHidden(a: AccountWithActivity, hidden: Boolean) = viewModelScope.launch { dao.setHidden(a.id, hidden) }

    fun save(a: AccountWithActivity, e: AccountEdit) = viewModelScope.launch {
        dao.rename(a.id, e.nickname.trim().ifEmpty { null }, e.color)
        if (e.usage != a.usage) dao.setUsage(a.id, e.usage)
        dao.setType(a.id, e.type, if (a.kind == AccountKind.CARD) e.network else null)
        dao.link(a.id, if (e.type == AccountType.DEBIT_CARD) e.linkedAccountId else null)
        when {
            e.clearBalance -> dao.setManualBalance(a.id, null, null)
            else -> Money.parseInput(e.balance)?.let { dao.setManualBalance(a.id, it, System.currentTimeMillis()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsRoute(onBack: () -> Unit, onOpenAccount: (Long) -> Unit, initialTab: Int = 0, vm: AccountsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, 2)) }
    var editing by remember { mutableStateOf<AccountWithActivity?>(null) }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        Column {
            TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text("Accounts") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf(
                    Triple("Accounts", Icons.Filled.AccountBalance, s.accounts.size),
                    Triple("Cards", Icons.Filled.CreditCard, s.cards.size),
                    Triple("Deposits & loans", Icons.Filled.Savings, s.deposits.size),
                ).forEachIndexed { i, (label, icon, n) ->
                    Tab(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) },
                        text = { Text(if (n > 0) "$label · $n" else label, maxLines = 1, style = MaterialTheme.typography.labelMedium) })
                }
            }
        }
    }) { inner ->
        val list = when (tab) { 0 -> s.accounts; 1 -> s.cards; else -> s.deposits }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding() + 12.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tab == 0 && s.accounts.isNotEmpty()) item { BalanceSummary(s.accounts) }
            if (tab == 2 && s.deposits.isNotEmpty()) item { DepositSummary(s.deposits) }
            if (tab == 1) {
                items(s.suggestions, key = { "s${it.card.id}" }) { sug ->
                    SuggestionCard(sug, onLink = { vm.link(sug.card, sug.account) }, onDismiss = { vm.dismiss(sug.card) })
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        when (tab) { 0 -> Icons.Filled.AccountBalance; 1 -> Icons.Filled.CreditCard; else -> Icons.Filled.Savings },
                        when (tab) { 0 -> "No accounts yet"; 1 -> "No cards yet"; else -> "No deposits or loans" },
                        if (tab == 2) "Set an account's type to FD, RD, PPF or Loan." else "Added automatically from your bank messages.",
                    )
                }
            }
            items(list, key = { it.id }) { a ->
                AccountCard(a, linked = a.linkedAccountId?.let(s.byId::get), onClick = { onOpenAccount(a.id) }, onEdit = { editing = a },
                    modifier = Modifier.animateItem())
            }
            val hiddenHere = s.hidden.filter {
                when (tab) {
                    0 -> it.kind == AccountKind.ACCOUNT && it.accountType?.liquid != false
                    1 -> it.kind == AccountKind.CARD
                    else -> it.kind == AccountKind.ACCOUNT && it.accountType?.liquid == false
                }
            }
            if (hiddenHere.isNotEmpty()) {
                item(key = "hidden-header") {
                    Text("Hidden (${hiddenHere.size})", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, start = 4.dp))
                }
                items(hiddenHere, key = { "h${it.id}" }) { a ->
                    Row(Modifier.fillMaxWidth().animateItem().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccountAvatar(a.bankName, a.kind, a.accountType, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("${title(a)} ••${a.last4}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.setHidden(a, false) }) { Text("Show again") }
                    }
                }
            }
        }
    }
    editing?.let { a ->
        EditSheet(a, accounts = s.accounts + s.deposits, onDismiss = { editing = null }, onSave = { e -> vm.save(a, e); editing = null },
            onHide = { vm.setHidden(a, true); editing = null })
    }
}

@Composable
private fun DepositSummary(deposits: List<AccountWithActivity>) {
    val saved = deposits.filter { it.accountType != AccountType.LOAN }.sumOf { it.currentBalanceMinor ?: 0 }
    val owed = deposits.filter { it.accountType == AccountType.LOAN }.sumOf { it.currentBalanceMinor ?: 0 }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Deposits", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Money.format(saved, showPaise = false), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (owed > 0) Column(Modifier.weight(1f)) {
                Text("Loans", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Money.format(owed, showPaise = false), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BalanceSummary(accounts: List<AccountWithActivity>) {
    val liquid = accounts.filter { it.isLiquid && it.currentBalanceMinor != null }
    val deposits = accounts.filter { !it.isLiquid && it.currentBalanceMinor != null }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("In your bank accounts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                if (liquid.isEmpty()) "—" else Money.format(liquid.sumOf { it.currentBalanceMinor!! }, showPaise = false),
                style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (deposits.isNotEmpty()) {
                Text(
                    "Plus ${Money.format(deposits.sumOf { it.currentBalanceMinor!! }, showPaise = false)} in deposits, PPF and loans",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(s: LinkSuggestion, onLink: () -> Unit, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("Link this debit card?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f))
                InfoButton("Linking a debit card", *Info.CARD_LINK)
            }
            Text(
                "${title(s.card)} card ••${s.card.last4} → ${title(s.account)} ••${s.account.last4}. ${s.reason} " +
                    "Its spends and withdrawals will then count against that account.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Not now") }
                Button(onClick = onLink) { Text("Link") }
            }
        }
    }
}

private fun title(a: AccountWithActivity) = a.nickname ?: a.bankName

@Composable
private fun AccountCard(a: AccountWithActivity, linked: AccountWithActivity?, onClick: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val accent = a.colorArgb?.let { Color(it) }
    Card(
        modifier.fillMaxWidth().pressable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = accent?.copy(alpha = 0.10f) ?: MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(a.bankName, a.kind, a.accountType)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title(a), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val kindLabel = a.accountType?.label ?: if (a.kind == AccountKind.CARD) "Card" else "Bank account"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$kindLabel · ••${a.last4}", style = MaterialTheme.typography.bodySmall, color = KindColors.of(a.kind, a.accountType))
                    a.cardNetwork?.let { n -> Spacer(Modifier.width(6.dp)); BrandMark(Brands.forNetwork(n), size = 20.dp) }
                    if (a.usage == com.hisaab.shared.db.AccountUsage.BUSINESS) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Business", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                val sub = when {
                    linked != null -> "Linked to ${title(linked)} ••${linked.last4}"
                    else -> "Spent this month ${Money.format(a.monthSpent, showPaise = false)} · ${a.transactionCount} transactions"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Column(horizontalAlignment = Alignment.End) {
                val (value, caption) = valueFor(a, linked)
                Text(value, style = MaterialTheme.typography.titleMedium)
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
        }
    }
}

/** What the right-hand figure shows: an account's balance, a credit card's limit left, or a debit card's account balance. */
private fun valueFor(a: AccountWithActivity, linked: AccountWithActivity?): Pair<String, String> = when {
    a.isDebitCard || (a.kind == AccountKind.CARD && a.accountType == null && a.latestBalanceMinor != null) -> {
        val bal = linked?.currentBalanceMinor ?: a.latestBalanceMinor
        (bal?.let { Money.format(it, showPaise = false) } ?: "—") to "account balance"
    }
    a.kind == AccountKind.CARD -> (a.currentBalanceMinor?.let { Money.format(it, showPaise = false) } ?: "—") to "limit left"
    else -> (a.currentBalanceMinor?.let { Money.format(it, showPaise = false) } ?: "—") to
        (a.balanceAsOf?.let { (if (a.balanceIsManual) "set by you · " else "") + Periods.dateTime(it) } ?: "balance")
}

private val SWATCHES = listOf(
    Color(0xFF1A63C6), Color(0xFF00897B), Color(0xFF43A047), Color(0xFFF9A825),
    Color(0xFFE65100), Color(0xFFC62828), Color(0xFF8E24AA), Color(0xFF546E7A),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditSheet(a: AccountWithActivity, accounts: List<AccountWithActivity>, onDismiss: () -> Unit, onSave: (AccountEdit) -> Unit, onHide: () -> Unit) {
    val isCard = a.kind == AccountKind.CARD
    var name by remember { mutableStateOf(a.nickname.orEmpty()) }
    var color by remember { mutableStateOf(a.colorArgb) }
    var type by remember { mutableStateOf(a.accountType) }
    var network by remember { mutableStateOf(a.cardNetwork) }
    var linkedId by remember { mutableStateOf(a.linkedAccountId) }
    var balance by remember { mutableStateOf("") }
    var clearBalance by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(a.usage) }
    val invalid = balance.isNotBlank() && Money.parseInput(balance) == null
    val showBalance = !(isCard && type == AccountType.DEBIT_CARD)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(a.bankName, a.kind, type, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(a.bankName, style = MaterialTheme.typography.titleLarge)
                    Text("••${a.last4}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nickname") }, singleLine = true)

            Label("Used for")
            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                com.hisaab.shared.db.AccountUsage.entries.forEachIndexed { i, u ->
                    SegmentedButton(
                        usage == u, { usage = u },
                        androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, com.hisaab.shared.db.AccountUsage.entries.size),
                    ) { Text(u.label) }
                }
            }

            Label(if (isCard) "Card type" else "Account type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AccountType.forKind(a.kind).forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = if (type == t) null else t }, label = { Text(t.label) },
                        leadingIcon = com.hisaab.app.ui.components.typeShort(t)?.let { code -> { com.hisaab.app.ui.components.TypeCode(code, 22.dp) } })
                }
            }
            if (!isCard && type?.liquid == false) {
                Text("Not counted in your Home balance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (isCard) {
                Label("Network")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardNetwork.entries.forEach { n ->
                        FilterChip(
                            selected = network == n, onClick = { network = if (network == n) null else n }, label = { Text(n.label) },
                            leadingIcon = { BrandMark(Brands.forNetwork(n), size = 18.dp) },
                        )
                    }
                }
            }

            if (isCard && type == AccountType.DEBIT_CARD) {
                Label("Bank account this card draws from")
                val options = accounts.sortedByDescending { it.bankName == a.bankName }
                if (options.isEmpty()) Text("No bank accounts yet.", style = MaterialTheme.typography.bodySmall)
                options.forEach { acc ->
                    Row(
                        Modifier.fillMaxWidth().clickable { linkedId = if (linkedId == acc.id) null else acc.id }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccountAvatar(acc.bankName, acc.kind, acc.accountType, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("${title(acc)} ••${acc.last4}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (linkedId == acc.id) Icon(Icons.Filled.Check, "Linked", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (showBalance) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(if (isCard) "Available limit" else "Current balance")
                    InfoButton(if (isCard) "Available limit" else "Current balance", *Info.MANUAL_BALANCE)
                }
                OutlinedTextField(
                    balance, { balance = it; clearBalance = false }, Modifier.fillMaxWidth(),
                    label = { Text(if (isCard) "Available limit (optional)" else "Current balance (optional)") },
                    placeholder = { a.currentBalanceMinor?.let { Text(Money.format(it)) } },
                    prefix = { Text("₹") }, singleLine = true, isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        Text(if (invalid) "Enter an amount, like 12500 or 12,500.50"
                        else "Leave blank to keep it. New transactions update it; a newer balance in a bank message replaces it.")
                    },
                )
                if (a.manualBalanceMinor != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearBalance, onCheckedChange = { clearBalance = it; if (it) balance = "" })
                        Text("Remove the balance I set", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Label("Colour")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatch(null, selected = color == null) { color = null }
                SWATCHES.forEach { c -> Swatch(c, selected = color == c.toArgb()) { color = c.toArgb() } }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onHide) { Text("Remove from view", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(AccountEdit(name, color, type, network, linkedId, balance, clearBalance, usage)) }, enabled = !invalid) { Text("Save") }
            }
            Text("Removing from view hides it from your lists and balance. Its transactions stay, and you can show it again at the bottom of Accounts.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Swatch(color: Color?, selected: Boolean, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        Modifier.size(30.dp).background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) Text("—", style = MaterialTheme.typography.labelSmall)
    }
}
