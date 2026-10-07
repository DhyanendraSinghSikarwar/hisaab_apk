package com.hisaab.app.ui.accounts

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MergeType
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
import com.hisaab.app.i18n.t
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A debit card that probably belongs to [account], and why we think so. */
data class LinkSuggestion(val card: AccountWithActivity, val account: AccountWithActivity, val reason: String)

/** Two entries that are probably the same account or card: [source] would be merged into [target]. */
data class MergeSuggestion(val source: AccountWithActivity, val target: AccountWithActivity) {
    val key get() = minOf(source.id, target.id) to maxOf(source.id, target.id)
}

data class AccountsState(
    val accounts: List<AccountWithActivity> = emptyList(),
    val cards: List<AccountWithActivity> = emptyList(),
    /** FD, RD, PPF and loans: money that isn't spendable, kept on its own tab. */
    val deposits: List<AccountWithActivity> = emptyList(),
    val suggestions: List<LinkSuggestion> = emptyList(),
    val byId: Map<Long, AccountWithActivity> = emptyMap(),
    /** Removed from view by the user; shown again from the bottom of the screen. */
    val hidden: List<AccountWithActivity> = emptyList(),
    val duplicates: List<MergeSuggestion> = emptyList(),
)

data class AccountEdit(
    val nickname: String, val color: Int?, val type: AccountType?, val network: CardNetwork?, val linkedAccountId: Long?,
    val balance: String, val clearBalance: Boolean,
    val usage: com.hisaab.shared.db.AccountUsage = com.hisaab.shared.db.AccountUsage.PERSONAL,
    val maturityDay: Long? = null,
    val maturityAction: com.hisaab.shared.db.MaturityAction? = null,
    /** Forex markup in percent, as typed ("3.5"). */
    val markup: String = "",
)

/** A card or account the user adds by hand. */
data class NewAccount(val kind: AccountKind, val bank: String, val last4: String, val type: AccountType?, val network: CardNetwork?, val balance: String)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val dao: AccountDao,
    private val forex: com.hisaab.shared.db.ForexDao,
    private val filters: com.hisaab.app.ui.ledger.ViewFilterStore,
    private val transactions: com.hisaab.shared.db.TransactionDao,
    private val repo: com.hisaab.shared.repo.TransactionRepository,
) : ViewModel() {
    /** Deletes the account or card. With [withTransactions] its transactions go too, and their messages are never read again. */
    fun delete(a: AccountWithActivity, withTransactions: Boolean) = viewModelScope.launch {
        if (withTransactions) repo.deleteTransactions(transactions.idsForAccount(a.id))
        dao.delete(a.id)
    }

    init { viewModelScope.launch { dao.closeMatured(java.time.LocalDate.now(Periods.zone).toEpochDay()) } }

    /** Adds a card or account by hand. False when that bank and number already exist. */
    fun add(n: NewAccount, done: (Boolean) -> Unit) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val id = dao.insert(
            com.hisaab.shared.db.AccountEntity(
                bankName = n.bank.trim(), last4 = n.last4, kind = n.kind, createdAt = now,
                accountType = n.type, cardNetwork = n.network,
                // Added while the Business book is shown: it belongs to the business.
                usage = if (filters.filter.value.book == com.hisaab.app.ui.ledger.Book.BUSINESS) com.hisaab.shared.db.AccountUsage.BUSINESS
                else com.hisaab.shared.db.AccountUsage.PERSONAL,
            ),
        )
        if (id > 0) Money.parseInput(n.balance)?.let { dao.setManualBalance(id, it, now) }
        done(id > 0)
    }

    private val dismissed = MutableStateFlow(emptySet<Long>())
    /** Pairs the user said are not duplicates, for this session. */
    private val notDuplicates = MutableStateFlow(emptySet<Pair<Long, Long>>())

    /** The lists follow the global book (the Book chip): Business shows only Business accounts and cards; All shows everything. */
    val state = combine(
        dao.observeWithActivity(Periods.startOfMonth(System.currentTimeMillis())), dismissed, filters.filter.map { it.book }.distinctUntilChanged(), notDuplicates,
    ) { everything, dismissedCards, book, notDup ->
        val all = everything.filter { book == com.hisaab.app.ui.ledger.Book.ALL || it.usage.name == book.name }
        val visible = all.filter { !it.hidden }
        val accounts = visible.filter { it.kind == AccountKind.ACCOUNT && it.accountType?.liquid != false }
        val cards = visible.filter { it.kind == AccountKind.CARD }
        val deposits = visible.filter { it.kind == AccountKind.ACCOUNT && it.accountType?.liquid == false }
        AccountsState(accounts, cards, deposits, suggestions(cards, accounts).filter { it.card.id !in dismissedCards }, everything.associateBy { it.id },
            hidden = all.filter { it.hidden }, duplicates = duplicates(visible).filter { it.key !in notDup })
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
                    byBalance != null -> LinkSuggestion(card, byBalance, t("The balance in this card's SMS matches this account."))
                    sameBank.size == 1 -> LinkSuggestion(card, sameBank.first(), t("It's your only {bank} account.", "bank" to card.bankName))
                    sameBank.isNotEmpty() -> LinkSuggestion(card, sameBank.maxBy { it.transactionCount }, t("Your most used {bank} account.", "bank" to card.bankName))
                    else -> null
                }
            }

    fun link(card: AccountWithActivity, account: AccountWithActivity) = viewModelScope.launch {
        dao.link(card.id, account.id)
        if (card.accountType == null) dao.setType(card.id, AccountType.DEBIT_CARD, card.cardNetwork)
    }

    fun dismiss(card: AccountWithActivity) = dismissed.update { it + card.id }
    fun notDuplicate(m: MergeSuggestion) = notDuplicates.update { it + m.key }

    /**
     * Likely duplicates: same bank, kind and (when both are set) type, and numbers ending alike (last 2 to 4 digits),
     * or one with a blank or odd number. The target is the one with a proper number, else the most used.
     */
    private fun duplicates(visible: List<AccountWithActivity>): List<MergeSuggestion> {
        val out = ArrayList<MergeSuggestion>()
        val merged = HashSet<Long>()
        for (group in visible.groupBy { it.bankName.trim().lowercase() to it.kind }.values) {
            if (group.size < 2) continue
            for (i in group.indices) for (j in i + 1 until group.size) {
                val a = group[i]; val b = group[j]
                if (a.id in merged || b.id in merged) continue
                if (a.accountType != null && b.accountType != null && a.accountType != b.accountType) continue
                if (!sameNumber(a.last4, b.last4)) continue
                val aFirst = compareValuesBy(a, b, { !oddNumber(it.last4) }, { it.transactionCount }) >= 0
                val (target, source) = if (aFirst) a to b else b to a
                out += MergeSuggestion(source, target)
                merged += source.id
            }
        }
        return out
    }

    private fun oddNumber(n: String) = n.isBlank() || n.length !in 2..4 || !n.all(Char::isDigit)

    private fun sameNumber(a: String, b: String): Boolean {
        if (oddNumber(a) || oddNumber(b)) return true
        val n = minOf(a.length, b.length)
        return a.takeLast(n) == b.takeLast(n)
    }

    /**
     * Merges [source] into [target]: its transactions, linked debit cards and recurring payments move over,
     * and its loan terms too when [target] has none. [target]'s settings are kept; [source] is deleted.
     */
    fun merge(source: AccountWithActivity, target: AccountWithActivity) = viewModelScope.launch {
        val src = dao.getById(source.id) ?: return@launch
        val dst = dao.getById(target.id) ?: return@launch
        transactions.moveAccount(src.id, dst.id, dst.last4)
        dao.moveLinkedCards(src.id, dst.id)
        dao.moveRecurring(src.id, dst.id)
        if (dst.linkedAccountId == null && src.linkedAccountId != null && src.linkedAccountId != dst.id) dao.link(dst.id, src.linkedAccountId)
        val dstHasTerms = dst.loanPrincipalMinor != null || dst.loanRateBps != null || dst.loanTenureMonths != null || dst.loanStartDay != null
        val srcHasTerms = src.loanPrincipalMinor != null || src.loanRateBps != null || src.loanTenureMonths != null || src.loanStartDay != null
        if (!dstHasTerms && srcHasTerms) dao.setLoanTerms(dst.id, src.loanPrincipalMinor, src.loanRateBps, src.loanTenureMonths, src.loanStartDay)
        dao.delete(src.id)
    }
    fun setHidden(a: AccountWithActivity, hidden: Boolean) = viewModelScope.launch { dao.setHidden(a.id, hidden) }

    fun save(a: AccountWithActivity, e: AccountEdit) = viewModelScope.launch {
        dao.rename(a.id, e.nickname.trim().ifEmpty { null }, e.color)
        if (e.usage != a.usage) dao.setUsage(a.id, e.usage)
        dao.setType(a.id, e.type, if (a.kind == AccountKind.CARD) e.network else null)
        dao.link(a.id, if (e.type == AccountType.DEBIT_CARD) e.linkedAccountId else null)
        val deposit = e.type == AccountType.FD || e.type == AccountType.RD
        dao.setMaturity(a.id, e.maturityDay.takeIf { deposit }, e.maturityAction.takeIf { deposit })
        if (deposit) dao.closeMatured(java.time.LocalDate.now(Periods.zone).toEpochDay())
        if (a.kind == AccountKind.CARD) {
            val bps = e.markup.trim().toDoubleOrNull()?.takeIf { it in 0.0..20.0 }?.let { Math.round(it * 100).toInt() }
            if (bps != a.forexMarkupBps) { dao.setForexMarkup(a.id, bps); forex.recompute() }
        }
        when {
            e.clearBalance -> dao.setManualBalance(a.id, null, null)
            else -> Money.parseInput(e.balance)?.let { dao.setManualBalance(a.id, it, System.currentTimeMillis()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsRoute(
    onBack: () -> Unit, onOpenAccount: (Long) -> Unit, initialTab: Int = 0,
    /** A loan opens the loan tracker rather than the account chart. */
    onOpenLoan: (Long) -> Unit = onOpenAccount,
    vm: AccountsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, 2)) }
    var editing by remember { mutableStateOf<AccountWithActivity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf<MergeSuggestion?>(null) }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, floatingActionButton = {
        androidx.compose.material3.FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, t("Add")) }
    }, topBar = {
        Column {
            TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text(t("Accounts")) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back")) } })
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf(
                    Triple(t("Accounts"), Icons.Filled.AccountBalance, s.accounts.size),
                    Triple(t("Cards"), Icons.Filled.CreditCard, s.cards.size),
                    Triple(t("Deposits & loans"), Icons.Filled.Savings, s.deposits.size),
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
            contentPadding = PaddingValues(top = inner.calculateTopPadding() + 12.dp, start = 16.dp, end = 16.dp, bottom = 96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val listIds = list.mapTo(HashSet()) { it.id }
            items(s.duplicates.filter { it.source.id in listIds }, key = { "m${it.source.id}-${it.target.id}" }) { m ->
                DuplicateCard(m, onMerge = { merging = m }, onDismiss = { vm.notDuplicate(m) }, modifier = Modifier.animateItem())
            }
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
                        when (tab) { 0 -> t("No accounts yet"); 1 -> t("No cards yet"); else -> t("No deposits or loans") },
                        if (tab == 2) t("Set an account's type to FD, RD, PPF or Loan.") else t("Added automatically from your bank messages."),
                    )
                }
            }
            items(list, key = { it.id }) { a ->
                AccountCard(a, linked = a.linkedAccountId?.let(s.byId::get), onClick = { if (a.accountType == AccountType.LOAN) onOpenLoan(a.id) else onOpenAccount(a.id) }, onEdit = { editing = a },
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
                    Text(t("Hidden ({n})", "n" to hiddenHere.size), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, start = 4.dp))
                }
                items(hiddenHere, key = { "h${it.id}" }) { a ->
                    Row(Modifier.fillMaxWidth().animateItem().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccountAvatar(a.bankName, a.kind, a.accountType, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("${title(a)} ••${a.last4}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.setHidden(a, false) }) { Text(t("Show again")) }
                    }
                }
            }
        }
    }
    if (adding) {
        AddAccountSheet(
            kind = if (tab == 1) AccountKind.CARD else AccountKind.ACCOUNT, deposit = tab == 2,
            onDismiss = { adding = false }, onAdd = { n, done -> vm.add(n, done) },
        )
    }
    editing?.let { a ->
        val sameKind = s.byId.values.filter { it.kind == a.kind && it.id != a.id }
            .sortedWith(compareByDescending<AccountWithActivity> { it.bankName == a.bankName }.thenBy { it.bankName })
        EditSheet(a, accounts = s.accounts + s.deposits, mergeTargets = sameKind, onMerge = { target -> merging = MergeSuggestion(a, target) },
            onDismiss = { editing = null }, onSave = { e -> vm.save(a, e); editing = null },
            onHide = { vm.setHidden(a, true); editing = null },
            onDelete = { withTx -> vm.delete(a, withTx); editing = null })
    }
    merging?.let { m ->
        MergeDialog(m, onDismiss = { merging = null }, onConfirm = { vm.merge(m.source, m.target); merging = null; editing = null })
    }
}

/** "These look like the same card": merge, or say they are not. */
@Composable
private fun DuplicateCard(m: MergeSuggestion, onMerge: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (m.source.kind == AccountKind.CARD) t("Possible duplicate card") else t("Possible duplicate account"),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            MergeRow(m.source)
            MergeRow(m.target)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(t("Not duplicates")) }
                Button(onClick = onMerge) { Text(t("Merge")) }
            }
        }
    }
}

@Composable
private fun MergeRow(a: AccountWithActivity, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(a.bankName, a.kind, a.accountType, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title(a) + (a.last4.takeIf { it.isNotBlank() }?.let { " ••$it" } ?: ""), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(t("{n} transactions", "n" to a.transactionCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Confirms merging one account or card into another. */
@Composable
private fun MergeDialog(m: MergeSuggestion, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val from = "${title(m.source)} ••${m.source.last4}"
    val into = "${title(m.target)} ••${m.target.last4}"
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Merge into {name}?", "name" to into)) },
        text = {
            Text(
                t("{n} transactions move from {from} to {into}. {into} keeps its settings; {from} is removed. This can't be undone.",
                    "n" to m.source.transactionCount, "from" to from, "into" to into),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(t("Merge")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

@Composable
private fun DepositSummary(deposits: List<AccountWithActivity>) {
    val saved = deposits.filter { it.accountType != AccountType.LOAN }.sumOf { it.currentBalanceMinor ?: 0 }
    val owed = deposits.filter { it.accountType == AccountType.LOAN }.sumOf { it.currentBalanceMinor ?: 0 }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(t("Deposits"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Money.format(saved, showPaise = false), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (owed > 0) Column(Modifier.weight(1f)) {
                Text(t("Loans"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
            Text(t("In your bank accounts"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                if (liquid.isEmpty()) "—" else Money.format(liquid.sumOf { it.currentBalanceMinor!! }, showPaise = false),
                style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (deposits.isNotEmpty()) {
                Text(
                    t("Plus {amount} in deposits, PPF and loans", "amount" to Money.format(deposits.sumOf { it.currentBalanceMinor!! }, showPaise = false)),
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
                Text(t("Link this debit card?"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f))
                InfoButton(t("Linking a debit card"), *Info.CARD_LINK)
            }
            Text(
                t("{card} card ••{cardlast} → {account} ••{accountlast}.", "card" to title(s.card), "cardlast" to s.card.last4, "account" to title(s.account), "accountlast" to s.account.last4) + " ${s.reason} " +
                    t("Its spends and withdrawals will then count against that account."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(t("Not now")) }
                Button(onClick = onLink) { Text(t("Link")) }
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
                val kindLabel = t(a.accountType?.label ?: if (a.kind == AccountKind.CARD) "Card" else "Bank account")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(kindLabel + (a.last4.takeIf { it.isNotBlank() }?.let { " · ••$it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = KindColors.of(a.kind, a.accountType))
                    a.cardNetwork?.let { n -> Spacer(Modifier.width(6.dp)); BrandMark(Brands.forNetwork(n), size = 20.dp) }
                    if (a.usage == com.hisaab.shared.db.AccountUsage.BUSINESS) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t("Business"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                val sub = when {
                    linked != null -> t("Linked to {name} ••{last}", "name" to title(linked), "last" to linked.last4)
                    a.maturityDay != null -> maturityLine(a.maturityDay!!, a.maturityAction)
                    else -> t("Spent this month {amount} · {n} transactions", "amount" to Money.format(a.monthSpent, showPaise = false), "n" to a.transactionCount)
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Column(horizontalAlignment = Alignment.End) {
                val (value, caption) = valueFor(a, linked)
                Text(value, style = MaterialTheme.typography.titleMedium)
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, t("Edit")) }
        }
    }
}

/** What the right-hand figure shows: an account's balance, a credit card's limit left, or a debit card's account balance. */
private fun valueFor(a: AccountWithActivity, linked: AccountWithActivity?): Pair<String, String> = when {
    a.isDebitCard || (a.kind == AccountKind.CARD && a.accountType == null && a.latestBalanceMinor != null) -> {
        val bal = linked?.currentBalanceMinor ?: a.latestBalanceMinor
        (bal?.let { Money.format(it, showPaise = false) } ?: "—") to t("account balance")
    }
    a.kind == AccountKind.CARD -> (a.currentBalanceMinor?.let { Money.format(it, showPaise = false) } ?: "—") to t("limit left")
    else -> (a.currentBalanceMinor?.let { Money.format(it, showPaise = false) } ?: "—") to
        (a.balanceAsOf?.let { (if (a.balanceIsManual) t("set by you") + " · " else "") + Periods.dateTime(it) } ?: t("balance"))
}

private val SWATCHES = listOf(
    Color(0xFF1A63C6), Color(0xFF00897B), Color(0xFF43A047), Color(0xFFF9A825),
    Color(0xFFE65100), Color(0xFFC62828), Color(0xFF8E24AA), Color(0xFF546E7A),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditSheet(
    a: AccountWithActivity, accounts: List<AccountWithActivity>, onDismiss: () -> Unit, onSave: (AccountEdit) -> Unit, onHide: () -> Unit,
    onDelete: (Boolean) -> Unit = {},
    /** Other accounts or cards of the same kind, to merge this one into. */
    mergeTargets: List<AccountWithActivity> = emptyList(),
    onMerge: (AccountWithActivity) -> Unit = {},
) {
    var showMerge by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) DeleteAccountDialog(a, onDismiss = { confirmDelete = false }, onConfirm = { withTx -> confirmDelete = false; onDelete(withTx) })
    val isCard = a.kind == AccountKind.CARD
    var name by remember { mutableStateOf(a.nickname.orEmpty()) }
    var color by remember { mutableStateOf(a.colorArgb) }
    var type by remember { mutableStateOf(a.accountType) }
    var network by remember { mutableStateOf(a.cardNetwork) }
    var linkedId by remember { mutableStateOf(a.linkedAccountId) }
    var balance by remember { mutableStateOf("") }
    var clearBalance by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(a.usage) }
    var maturityDay by remember { mutableStateOf(a.maturityDay) }
    var maturityAction by remember { mutableStateOf(a.maturityAction) }
    var markup by remember { mutableStateOf(a.forexMarkupBps?.let { "%.2f".format(it / 100.0).trimEnd('0').trimEnd('.') }.orEmpty()) }
    val badMarkup = markup.isNotBlank() && markup.trim().toDoubleOrNull()?.let { it in 0.0..20.0 } != true
    val invalid = (balance.isNotBlank() && Money.parseInput(balance) == null) || badMarkup
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
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(t("Nickname")) }, singleLine = true)

            Label(t("Used for"))
            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                com.hisaab.shared.db.AccountUsage.entries.forEachIndexed { i, u ->
                    SegmentedButton(
                        usage == u, { usage = u },
                        androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, com.hisaab.shared.db.AccountUsage.entries.size),
                    ) { Text(t(u.label)) }
                }
            }

            Label(if (isCard) t("Card type") else t("Account type"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AccountType.forKind(a.kind).forEach { ty ->
                    FilterChip(selected = type == ty, onClick = { type = if (type == ty) null else ty }, label = { Text(t(ty.label)) },
                        leadingIcon = com.hisaab.app.ui.components.typeShort(ty)?.let { code -> { com.hisaab.app.ui.components.TypeCode(code, 22.dp) } })
                }
            }
            if (!isCard && type?.liquid == false) {
                Text(t("Not counted in your Home balance."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (type == AccountType.LOAN) {
                Text(t("Amount, rate and tenure are set on the loan itself: tap it in the list."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (type == AccountType.FD || type == AccountType.RD) {
                Label(t("On maturity"))
                MaturityFields(maturityDay, { maturityDay = it }, maturityAction, { maturityAction = it })
            }
            if (isCard) {
                OutlinedTextField(
                    markup, { markup = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, Modifier.fillMaxWidth(),
                    label = { Text(t("Forex markup")) }, suffix = { Text("%") }, singleLine = true, isError = badMarkup,
                    placeholder = { Text("3.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text(t("Added to spends in other currencies. Include GST: 3.5% + 18% = 4.13%.")) },
                )
            }

            if (isCard) {
                Label(t("Network"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardNetwork.entries.forEach { n ->
                        FilterChip(
                            selected = network == n, onClick = { network = if (network == n) null else n }, label = { Text(t(n.label)) },
                            leadingIcon = { BrandMark(Brands.forNetwork(n), size = 18.dp) },
                        )
                    }
                }
            }

            if (isCard && type == AccountType.DEBIT_CARD) {
                Label(t("Bank account this card draws from"))
                val options = accounts.sortedByDescending { it.bankName == a.bankName }
                if (options.isEmpty()) Text(t("No bank accounts yet."), style = MaterialTheme.typography.bodySmall)
                options.forEach { acc ->
                    Row(
                        Modifier.fillMaxWidth().clickable { linkedId = if (linkedId == acc.id) null else acc.id }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccountAvatar(acc.bankName, acc.kind, acc.accountType, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("${title(acc)} ••${acc.last4}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (linkedId == acc.id) Icon(Icons.Filled.Check, t("Linked"), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (showBalance) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(if (isCard) t("Available limit") else t("Current balance"))
                    InfoButton(if (isCard) t("Available limit") else t("Current balance"), *Info.MANUAL_BALANCE)
                }
                OutlinedTextField(
                    balance, { balance = it; clearBalance = false }, Modifier.fillMaxWidth(),
                    label = { Text(if (isCard) t("Available limit (optional)") else t("Current balance (optional)")) },
                    placeholder = { a.currentBalanceMinor?.let { Text(Money.format(it)) } },
                    prefix = { Text("₹") }, singleLine = true, isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        Text(if (invalid) t("Enter an amount, like 12500 or 12,500.50")
                        else t("Leave blank to keep it. New transactions update it; a newer balance in a bank message replaces it."))
                    },
                )
                if (a.manualBalanceMinor != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearBalance, onCheckedChange = { clearBalance = it; if (it) balance = "" })
                        Text(t("Remove the balance I set"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Label(t("Colour"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatch(null, selected = color == null) { color = null }
                SWATCHES.forEach { c -> Swatch(c, selected = color == c.toArgb()) { color = c.toArgb() } }
            }

            if (mergeTargets.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().clickable { showMerge = !showMerge }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MergeType, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(t("Merge into…"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(if (showMerge) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
                if (showMerge) mergeTargets.forEach { target -> MergeRow(target, onClick = { onMerge(target) }) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onHide) { Text(t("Hide"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { confirmDelete = true }) { Text(t("Delete"), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(t("Cancel")) }
                Button(onClick = { onSave(AccountEdit(name, color, type, network, linkedId, balance, clearBalance, usage, maturityDay, maturityAction, markup)) }, enabled = !invalid) { Text(t("Save")) }
            }
            Text(t("Hide keeps its transactions and can be undone at the bottom of Accounts. Delete removes it for good."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun Label(text: String) {
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


/** Confirms deleting an account or card; its transactions go too unless the box is cleared. */
@Composable
private fun DeleteAccountDialog(a: AccountWithActivity, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var withTx by remember { mutableStateOf(true) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Delete {name}?", "name" to "${title(a)} ••${a.last4}")) },
        text = {
            Column {
                Text(t("This can't be undone."), style = MaterialTheme.typography.bodyMedium)
                if (a.transactionCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Checkbox(checked = withTx, onCheckedChange = { withTx = it })
                        Text(t("Also delete its {n} transactions", "n" to a.transactionCount), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(withTx && a.transactionCount > 0) }) { Text(t("Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}
