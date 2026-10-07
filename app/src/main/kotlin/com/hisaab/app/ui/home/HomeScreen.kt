package com.hisaab.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.i18n.t
import com.hisaab.app.settings.TabLayout
import com.hisaab.app.settings.TabLayouts
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.enterOnce
import androidx.compose.foundation.lazy.itemsIndexed
import com.hisaab.app.ui.components.rememberSmsPermission
import com.hisaab.app.ui.ledger.BookPeriodChips
import com.hisaab.app.ui.theme.Hx
import com.hisaab.parser.model.Category
import com.hisaab.shared.insight.Insight
import java.time.YearMonth

/**
 * Home. Every widget follows the global Book and Period filter (the chips under the header). The widgets, their
 * order and which are shown are the user's, set in More > Customise and kept in [com.hisaab.app.settings.TabLayoutStore].
 */
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
    onOpenAccount: ((Long) -> Unit)? = null,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val widgets by vm.widgets.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    var addingRecurring by rememberSaveable { mutableStateOf(false) }
    if (addingRecurring) com.hisaab.app.ui.plan.RecurringSheet(existing = null, onDismiss = { addingRecurring = false })
    val sms = rememberSmsPermission(onGranted = { vm.scanInbox(full = true) })
    // Ask once for notifications, so "a statement needs its password" can reach the user.
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && vm.shouldAskNotifications()) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val month = widgets.month
    HomeScreen(
        state = state, widgets = widgets, layout = layout,
        hasSmsPermission = sms.granted, smsBlocked = sms.blocked, onGrantSms = sms::request, onDismissSms = vm::dismissSmsPrompt,
        onOpenTransaction = onOpenTransaction, onSeeAllTransactions = { onSeeAllTransactions(month) }, onOpenAccounts = onOpenAccounts,
        onOpenReview = onOpenReview, onOpenBudgets = onOpenBudgets, contentPadding = contentPadding,
        onToggleHide = vm::toggleHideAmounts, onAdd = onAdd, onAddRecurring = { addingRecurring = true },
        onOpenInvestments = onOpenInvestments, onOpenStatements = onOpenStatements, onOpenBills = onOpenBills, onOpenAnalytics = onOpenAnalytics,
        onOpenCategory = { onOpenCategory(it, month) }, onOpenCategoryKey = { onOpenCategoryKey("$it?month=$month") }, onOpenAccount = { onOpenAccount?.invoke(it) ?: onOpenAccounts() },
        updateVersion = (update as? com.hisaab.app.update.UpdateState.Available)?.release?.version, onOpenSettings = onOpenSettings,
        syncing = syncing, sync = sync, onSync = vm::syncAll, photoPath = profile?.first?.photoPath, onOpenProfile = onOpenProfile,
        onDismissInsight = vm::dismissInsight,
    )
}

/** Home without its view model: header, the global filter chips, prompts, then the widgets in the saved order. */
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
    widgets: HomeWidgets = HomeWidgets(),
    layout: TabLayout = TabLayout(),
    smsBlocked: Boolean = false,
    onDismissSms: () -> Unit = {},
    onToggleHide: () -> Unit = {},
    onAdd: () -> Unit = {},
    onAddRecurring: () -> Unit = {},
    onOpenInvestments: () -> Unit = {},
    onOpenStatements: () -> Unit = {},
    onOpenBills: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onOpenCategory: (Category) -> Unit = {},
    onOpenCategoryKey: (String) -> Unit = {},
    onOpenAccount: (Long) -> Unit = {},
    updateVersion: String? = null,
    onOpenSettings: () -> Unit = {},
    syncing: Boolean = false,
    sync: SyncStatus = SyncStatus(),
    onSync: () -> Unit = {},
    photoPath: String? = null,
    onOpenProfile: () -> Unit = {},
    onDismissInsight: (Insight) -> Unit = {},
) {
    var showNotices by rememberSaveable { mutableStateOf(false) }
    val notices = buildList {
        if (state.reviewCount > 0) add(HomeNotice(if (state.reviewCount > 1) t("{n} possible duplicates", "n" to state.reviewCount) else t("{n} possible duplicate", "n" to state.reviewCount), t("Review and merge"),
            Icons.Filled.ContentCopy, onOpenReview))
        state.lockedStatements.forEach { st ->
            add(HomeNotice(t("Statement needs a password"), st.bankName ?: st.sender.substringBefore('<').trim(), Icons.Filled.Lock, onOpenStatements))
        }
        updateVersion?.let { add(HomeNotice(t("DhanKosh {version} is available", "version" to it), t("Install the update"), Icons.Filled.SystemUpdate, onOpenSettings, whatsNew = true)) }
    }
    if (showNotices) NotificationsSheet(notices, onDismiss = { showNotices = false })
    val listState = rememberLazyListState()
    // Once the header has scrolled away, a compact one floats at the top.
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val name = state.displayName ?: t("Welcome")
    val shown = layout.visible(TabLayouts.HOME)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.testTag("home"),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                HomeHeader(
                    name, onOpenAccounts, onOpenProfile, notices.size, { showNotices = true }, photoPath = photoPath,
                    hasName = state.displayName != null, onToggleHide = onToggleHide,
                    modifier = Modifier.statusBarsPadding().padding(start = 14.dp, end = 4.dp, top = 12.dp),
                )
            }
            item(key = "filters") {
                BookPeriodChips(Modifier.fillMaxWidth().padding(horizontal = 14.dp))
            }
            if (!hasSmsPermission && !state.smsPromptDismissed) item(key = "sms") { PermissionCard(smsBlocked, onGrantSms, onDismissSms) }

            itemsIndexed(shown, key = { _, k -> "w-$k" }) { index, key ->
                // Order and visibility are set in More > Customise; Home has no edit mode.
                val edit: WidgetEdit? = null
                Box(Modifier.animateItem().enterOnce(index).padding(horizontal = 14.dp)) {
                    when (key) {
                        "networth" -> NetWorthWidget(widgets, onOpenInvestments, edit)
                        "cards" -> CreditCardsWidget(widgets, onOpenAccount, edit)
                        "cashflow" -> CashFlowWidget(widgets, onOpenAnalytics, edit)
                        "safe" -> SafeWidget(widgets, onOpenBudgets, edit)
                        "upcoming" -> UpcomingWidget(widgets, onOpenBills, edit)
                        "insights" -> InsightsWidget(widgets, onOpenCategory, onOpenBills, onSeeAllTransactions, onDismissInsight, edit)
                        "categories" -> CategoriesWidget(widgets, { onOpenCategoryKey(it.key) }, onOpenAnalytics, edit)
                        "recent" -> RecentWidget(widgets, onOpenTransaction, onSeeAllTransactions, edit)
                        "budgets" -> BudgetsWidget(widgets, onOpenBudgets, edit)
                    }
                }
            }
        }

        AnimatedVisibility(
            collapsed, modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it },
        ) {
            CollapsedHeader {
                HomeHeader(name, onOpenAccounts, onOpenProfile, notices.size, { showNotices = true }, compact = true, photoPath = photoPath,
                    onToggleHide = onToggleHide)
            }
        }
        // Nothing scrolls visibly behind the status bar.
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background),
        )

        // Refresh progress: a slim bar under the status bar while anything is read.
        AnimatedVisibility(
            sync.active, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding(), enter = fadeIn(), exit = fadeOut(),
        ) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Hx.accent, trackColor = Hx.accent.copy(alpha = 0.15f))
        }
        SyncPill(
            sync,
            Modifier.align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 88.dp, bottom = contentPadding.calculateBottomPadding() + 26.dp),
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
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 45f else 0f, label = "plus")
    val spin = rememberInfiniteTransition(label = "sync")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            open,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { open = false; onAddRecurring() }, icon = { Icon(Icons.Filled.EventRepeat, null) },
                    text = { Text(t("Recurring / subscription")) },
                )
                ExtendedFloatingActionButton(
                    onClick = { open = false; onAdd() }, icon = { Icon(Icons.Filled.Receipt, null) }, text = { Text(t("Transaction")) },
                )
            }
        }
        FloatingActionButton(onClick = { open = !open }, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(Icons.Filled.Add, if (open) t("Close") else t("Add"), modifier = Modifier.rotate(turn))
        }
        FloatingActionButton(onClick = { if (!syncing) onSync() }, containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Filled.Sync, if (syncing) t("Syncing") else t("Sync everything"), modifier = Modifier.rotate(if (syncing) -angle else 0f))
        }
    }
}

/** Asks for SMS access, explaining that nothing leaves the phone; explains the manual route when Android blocks the prompt. */
@Composable
private fun PermissionCard(blocked: Boolean, onGrant: () -> Unit, onDismiss: () -> Unit) {
    HCard(Modifier.padding(horizontal = 14.dp), container = Hx.accentSoft) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Sms, null, tint = Hx.accent)
            Spacer(Modifier.width(8.dp))
            Text(t("Read bank SMS"), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            t("DhanKosh reads SMS only from known bank senders, on this phone. Nothing is uploaded; there is no server."),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp),
        )
        if (blocked) {
            Text(
                t(
                    "Android didn't show the permission prompt. Open App settings, then Permissions, SMS, Allow. " +
                        "If SMS is greyed out, first tap the menu at the top right of App info and choose Allow restricted settings.",
                ),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onGrant, modifier = Modifier.testTag("grant-sms")) { Text(if (blocked) t("Open App settings") else t("Allow SMS access")) }
            TextButton(onClick = onDismiss) { Text(t("Not now")) }
        }
    }
}

/**
 * Beside the refresh button: "Reading messages · n", then "Reading email", and for three seconds after, what was read.
 * Counts come from the finished SMS scan and email sync of this run only.
 */
@Composable
private fun SyncPill(sync: SyncStatus, modifier: Modifier = Modifier) {
    var sawSms by remember { mutableStateOf(false) }
    var sawMail by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    if (sync.smsActive) sawSms = true
    if (sync.mailActive) sawMail = true
    val doneText = when {
        sawSms && sawMail && sync.smsRead != null && sync.mailRead != null ->
            t("{sms} messages · {mail} emails read", "sms" to sync.smsRead, "mail" to sync.mailRead)
        sawSms && sync.smsRead != null -> t("{sms} messages read", "sms" to sync.smsRead)
        sawMail && sync.mailRead != null -> t("{mail} emails read", "mail" to sync.mailRead)
        else -> null
    }
    LaunchedEffect(sync.active) {
        if (!sync.active && (sawSms || sawMail)) {
            done = doneText
            sawSms = false; sawMail = false
            kotlinx.coroutines.delay(3_000)
            done = null
        }
    }
    val text = when {
        sync.smsActive -> if (sync.smsSoFar > 0) t("Reading messages · {n}", "n" to sync.smsSoFar) else t("Reading messages…")
        sync.mailActive -> if (sync.mailSoFar > 0) t("Reading email · {n}", "n" to sync.mailSoFar) else t("Reading email…")
        else -> done
    }
    // Kept while the pill fades out.
    var shown by remember { mutableStateOf("") }
    if (text != null) shown = text
    AnimatedVisibility(text != null, modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, Hx.border, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (sync.active) Icons.Filled.Sync else Icons.Filled.Check, null, tint = Hx.accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(shown, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
