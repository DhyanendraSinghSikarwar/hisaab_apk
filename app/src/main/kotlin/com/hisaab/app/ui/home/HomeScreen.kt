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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.ui.components.AccountAvatar
import com.hisaab.app.ui.plan.PlanSnapshot
import androidx.compose.ui.draw.clip
import com.hisaab.app.ui.plan.UpcomingRow
import com.hisaab.parser.model.Category
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.HorizontalDivider
import com.hisaab.app.ui.components.AnimatedAmount
import com.hisaab.app.ui.components.pressable
import com.hisaab.app.ui.components.CategoryBadge
import com.hisaab.app.ui.components.KindColors
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
import java.time.YearMonth

@Composable
fun HomeRoute(
    onOpenTransaction: (Long) -> Unit,
    onSeeAllTransactions: (month: YearMonth) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenBudgets: () -> Unit,
    onAdd: () -> Unit,
    onOpenInvestments: () -> Unit,
    onOpenStatements: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenCategory: (Category, YearMonth) -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
    onOpenProfile: () -> Unit = {},
    onOpenCategoryKey: (String) -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val budgetSummary by vm.budgetSummary.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val customSpend by vm.customSpend.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    var setupDismissed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var addingRecurring by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (addingRecurring) com.hisaab.app.ui.plan.RecurringSheet(existing = null, onDismiss = { addingRecurring = false })
    val sms = rememberSmsPermission(onGranted = { vm.scanInbox(full = true) })
    // Ask once for notifications, so "a statement needs its password" can reach the user.
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && vm.shouldAskNotifications()) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    HomeScreen(
        state = state, hasSmsPermission = sms.granted, smsBlocked = sms.blocked,
        onGrantSms = sms::request, onDismissSms = vm::dismissSmsPrompt,
        onOpenTransaction = onOpenTransaction, onSeeAllTransactions = { onSeeAllTransactions(state.month) }, onOpenAccounts = onOpenAccounts,
        onOpenReview = onOpenReview, onOpenBudgets = onOpenBudgets, contentPadding = contentPadding,
        onPreviousMonth = vm::previousMonth, onNextMonth = vm::nextMonth, onToggleHide = vm::toggleHideAmounts, onAdd = onAdd, onOpenInvestments = onOpenInvestments,
        onOpenStatements = onOpenStatements,
        plan = plan, onOpenBills = onOpenBills, onOpenAnalytics = onOpenAnalytics, budgetSummary = budgetSummary,
        updateVersion = (update as? com.hisaab.app.update.UpdateState.Available)?.release?.version, onOpenSettings = onOpenSettings,
        onOpenCategory = { onOpenCategory(it, state.month) },
        activity = activity, syncing = syncing, onSync = vm::syncAll, onAddRecurring = { addingRecurring = true },
        photoPath = profile?.first?.photoPath, onOpenProfile = onOpenProfile,
        customSpend = customSpend, onOpenCategoryKey = { onOpenCategoryKey("$it?month=${state.month}") },
        sections = sections,
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
    onAdd: () -> Unit = {},
    onOpenInvestments: () -> Unit = {},
    onOpenStatements: () -> Unit = {},
    plan: PlanSnapshot = PlanSnapshot(),
    onOpenBills: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onOpenCategory: (Category) -> Unit = {},
    onToggleHide: () -> Unit = {},
    budgetSummary: BudgetSummary? = null,
    updateVersion: String? = null,
    onOpenSettings: () -> Unit = {},
    activity: Map<java.time.LocalDate, Long> = emptyMap(),
    syncing: Boolean = false,
    onSync: () -> Unit = {},
    photoPath: String? = null,
    onOpenProfile: () -> Unit = {},
    customSpend: List<Pair<com.hisaab.shared.db.CustomCategoryEntity, Long>> = emptyList(),
    onOpenCategoryKey: (String) -> Unit = {},
    onAddRecurring: () -> Unit = {},
    sections: List<String> = emptyList(),
) {
    var showNotices by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val notices = buildList {
        if (state.reviewCount > 0) add(HomeNotice("${state.reviewCount} possible duplicate${if (state.reviewCount > 1) "s" else ""}", "Review and merge",
            Icons.Filled.ContentCopy, onOpenReview))
        state.lockedStatements.forEach { st ->
            add(HomeNotice("Statement needs a password", st.bankName ?: st.sender.substringBefore('<').trim(), Icons.Filled.Lock, onOpenStatements))
        }
        updateVersion?.let { add(HomeNotice("Hisaab $it is available", "Install the update", Icons.Filled.SystemUpdate, onOpenSettings)) }
    }
    if (showNotices) NotificationsSheet(notices, onDismiss = { showNotices = false })
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Once the big header has scrolled away, a compact one floats at the top.
    val collapsed by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    // The user's own name at the top; until they add one, an invitation to create the profile.
    val name = state.displayName ?: "Welcome"
    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        LazyColumn(
            state = listState,
            modifier = Modifier.testTag("home"),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp),
        ) {
            item(key = "header") {
                HomeHeader(name, onOpenAccounts, onOpenProfile, notices.size, { showNotices = true }, photoPath = photoPath,
                    hasName = state.displayName != null,
                    modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 16.dp))
            }
            item(key = "hero") { SpendHero(state, onPreviousMonth, onNextMonth, onToggleHide, onOpenInvestments) }

            if (!hasSmsPermission && !state.smsPromptDismissed) item { PermissionCard(smsBlocked, onGrantSms, onDismissSms) }
            if (state.scan.running) item { ScanCard(state.scan) }

            // Sections in the order (and visibility) set under Settings → Customize tabs. All follow the month above.
            sections.forEach { section ->
                when (section) {
                    "budgets" -> {
                        item(key = "budgets-h") { HomeSection("Budgets", "View All", onOpenBudgets) }
                        item(key = "budgets") {
                            val budgeted = state.categories.filter { it.budget != null }
                            when {
                                budgetSummary == null -> CreateBudgetCard(onOpenBudgets)
                                budgeted.isEmpty() -> BudgetsCard(budgetSummary, onOpenBudgets)
                                else -> BudgetLines(budgeted.sortedByDescending { it.spent.toFloat() / (it.budget ?: 1) }.take(3), onOpenBudgets)
                            }
                        }
                    }
                    "subscriptions" -> {
                        val regular = plan.recurring.filter { !it.income }
                        if (regular.isNotEmpty() || plan.policies.isNotEmpty()) {
                            item(key = "subs-h") { HomeSection("Upcoming Subscriptions", "View All", onOpenBills) }
                            item(key = "subs") { SubscriptionsSummary(regular.size, regular.filter { !it.yearly }.sumOf { it.amountMinor }, onOpenBills) }
                            val soon = plan.upcoming.filter { it.daysLeft in 0..14 }
                            items(soon.take(3), key = { "up-${it.name}-${it.due}" }) { u -> UpcomingRow(u, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                        }
                    }
                    "categories" -> if (state.categories.isNotEmpty()) {
                        item(key = "cats-h") { HomeSection("Where it went", "Analytics", onOpenAnalytics) }
                        // Categories only; tapping one drills down into its sub-categories. The user's own categories
                        // are counted in Other by the built-in totals, so they are taken out of it here.
                        val ownTotal = customSpend.sumOf { it.second }
                        val lines = (state.categories.map { c ->
                            val spent = if (c.category == Category.OTHER) (c.spent - ownTotal).coerceAtLeast(0) else c.spent
                            Triple(com.hisaab.app.ui.category.CategoryLook.of(c.category), spent, c.budget)
                        } + customSpend.map { (c, t) -> Triple(com.hisaab.app.ui.category.CategoryLook.of(c), t, null) })
                            .filter { it.second > 0 }.sortedByDescending { it.second }.take(6)
                        items(lines, key = { it.first.key }) { (look, spent, budget) ->
                            LookLine(look, spent, budget, state.spent, onClick = { onOpenCategoryKey(look.key) })
                        }
                    }
                    "activity" -> {
                        item(key = "activity-h") { HomeSection("Activity") }
                        item(key = "activity") { ActivityHeatmap(activity, until = state.month.atEndOfMonth()) }
                    }
                }
            }

        }

        androidx.compose.animation.AnimatedVisibility(
            collapsed, modifier = Modifier.align(Alignment.TopCenter),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
        ) {
            CollapsedHeader { HomeHeader(name, onOpenAccounts, onOpenProfile, notices.size, { showNotices = true }, compact = true, photoPath = photoPath) }
        }
        // Nothing scrolls visibly behind the status bar.
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background),
        )

        HomeFabs(
            onAdd = onAdd, onAddRecurring = onAddRecurring, syncing = syncing, onSync = onSync,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        )
    }
}

/**
 * The ＋ button opens two choices (a transaction, or a recurring payment or income); below it, refresh
 * syncs SMS and every connected inbox, and spins while that runs.
 */
@Composable
private fun HomeFabs(onAdd: () -> Unit, onAddRecurring: () -> Unit, syncing: Boolean, onSync: () -> Unit, modifier: Modifier = Modifier) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 45f else 0f, label = "plus")
    val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "sync")
    val angle by spin.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "angle")
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.animation.AnimatedVisibility(
            open,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { open = false; onAddRecurring() }, icon = { Icon(Icons.Filled.EventRepeat, null) },
                    text = { Text("Recurring / subscription") },
                )
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { open = false; onAdd() }, icon = { Icon(Icons.Filled.Receipt, null) }, text = { Text("Transaction") },
                )
            }
        }
        FloatingActionButton(onClick = { open = !open }, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(Icons.Filled.Add, if (open) "Close" else "Add", modifier = Modifier.rotate(turn))
        }
        FloatingActionButton(onClick = { if (!syncing) onSync() }, containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Filled.Sync, if (syncing) "Syncing" else "Sync everything", modifier = Modifier.rotate(if (syncing) -angle else 0f))
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

/** The month at a glance: what went out large, with what came in and the difference beneath. */
@Composable
private fun HeroCard(state: HomeState) {
    val scheme = MaterialTheme.colorScheme
    val net = state.income - state.spent
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Spent in ${Periods.month(state.month)}", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
            AnimatedAmount(state.spent, MaterialTheme.typography.displaySmall, color = scheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("Income", style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.8f))
                    AnimatedAmount(state.income, MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer)
                }
                Column {
                    Text(if (net >= 0) "Saved" else "Overspent", style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.8f))
                    AnimatedAmount(kotlin.math.abs(net), MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun LockedStatementsBanner(locked: List<com.hisaab.shared.db.StatementEntity>, onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val first = locked.first().bankName ?: "A"
                Text(
                    if (locked.size == 1) "$first statement detected" else "${locked.size} statements detected",
                    color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Medium,
                )
                Text("Enter the password so Hisaab can read ${if (locked.size == 1) "it" else "them"}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Text("Unlock", color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
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
    Card(Modifier.width(176.dp).pressable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(a.bankName, a.kind, a.accountType, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(a.nickname ?: a.bankName, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Text("${a.accountType?.label ?: if (a.kind == AccountKind.CARD) "Card" else "Account"} ••${a.last4}",
                        style = MaterialTheme.typography.bodySmall, color = KindColors.of(a.kind, a.accountType), maxLines = 1)
                }
            }
            val value = a.currentBalanceMinor?.let { if (a.kind == AccountKind.CARD) "Limit left " + Money.compact(it) else Money.format(it, showPaise = false) }
            Text(value ?: "Spent ${Money.compact(a.monthSpent)}", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun BudgetsCard(b: BudgetSummary, onOpen: () -> Unit) {
    val ratio = (b.spent.toFloat() / b.limit.coerceAtLeast(1)).coerceIn(0f, 1f)
    val progress by androidx.compose.animation.core.animateFloatAsState(ratio, androidx.compose.animation.core.tween(700), label = "budgets")
    val warn = b.nearOrOver > 0
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).pressable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Savings, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Budgets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${Money.format(b.spent, showPaise = false)} / ${Money.format(b.limit, showPaise = false)}", style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp).clip(androidx.compose.foundation.shape.CircleShape),
                color = if (warn) Color(0xFFF29900) else MaterialTheme.colorScheme.primary, drawStopIndicator = {},
            )
            Text(
                if (warn) "${b.nearOrOver} of ${b.count} budget${if (b.count > 1) "s" else ""} near or over the limit" else "All ${b.count} budget${if (b.count > 1) "s" else ""} on track",
                style = MaterialTheme.typography.bodySmall, color = if (warn) Color(0xFFF29900) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Last month's savings (investments count as saved) and, at that pace, a year's. */
@Composable
private fun SavingsCard(s: com.hisaab.shared.insight.SavingsOutlook) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Savings, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("Savings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                com.hisaab.app.ui.components.InfoButton(
                    "Savings",
                    "Saved = income minus spending. SIPs, stocks and other investments count as saved, not spent. Transfers between your own accounts and card bill payments are left out.",
                    "The yearly estimate is your average over the last three complete months, plus how fast your EPF and NPS balances grow, times twelve.",
                )
            }
            s.lastMonth?.let { m ->
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Last month (${Periods.monthShort(m.month)})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(Money.format(m.savedMinor, showPaise = false), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            (if (m.incomeMinor > 0) "${(m.rate * 100).toInt()}% of income" else "") +
                                (if (m.investedMinor > 0) " · ${Money.format(m.investedMinor, showPaise = false)} invested" else ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f))
            Column {
                Text("At this pace, in a year", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                AnimatedAmount(s.yearlyEstimateMinor, MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                if (s.pfNpsMonthlyMinor > 0) {
                    Text("Includes about ${Money.format(s.pfNpsMonthlyMinor, showPaise = false)} a month into EPF and NPS",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
fun InsightRow(i: com.hisaab.shared.insight.Insight, modifier: Modifier = Modifier) {
    val tint = when (i.tone) {
        com.hisaab.shared.insight.Tone.GOOD -> MoneyColors.credit
        com.hisaab.shared.insight.Tone.WARN -> Color(0xFFF29900)
        com.hisaab.shared.insight.Tone.INFO -> MaterialTheme.colorScheme.primary
    }
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                when (i.tone) {
                    com.hisaab.shared.insight.Tone.GOOD -> Icons.AutoMirrored.Filled.TrendingDown
                    com.hisaab.shared.insight.Tone.WARN -> Icons.AutoMirrored.Filled.TrendingUp
                    com.hisaab.shared.insight.Tone.INFO -> Icons.Filled.Lightbulb
                },
                null, tint = tint,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(i.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(i.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CategoryLine(c: CategorySpend, totalSpent: Long, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
            val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
                fraction.coerceIn(0f, 1f), androidx.compose.animation.core.tween(700), label = "category",
            )
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = if (c.budget != null && c.spent > c.budget) MaterialTheme.colorScheme.error else c.category.color,
            )
        }
    }
}

@Composable
private fun LookLine(look: com.hisaab.app.ui.category.CategoryLook, spent: Long, budget: Long?, totalSpent: Long, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        com.hisaab.app.ui.category.IconBadge(look.icon, look.color, 32)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(look.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Money.format(spent, showPaise = false) + (budget?.let { " / " + Money.compact(it) } ?: ""), style = MaterialTheme.typography.bodyMedium)
            }
            val fraction = if (budget != null && budget > 0) spent.toFloat() / budget else if (totalSpent > 0) spent.toFloat() / totalSpent else 0f
            val animated by androidx.compose.animation.core.animateFloatAsState(fraction.coerceIn(0f, 1f), androidx.compose.animation.core.tween(700), label = "category")
            LinearProgressIndicator(
                progress = { animated }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = if (budget != null && spent > budget) MaterialTheme.colorScheme.error else look.color,
            )
        }
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
