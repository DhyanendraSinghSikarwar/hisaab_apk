package com.hisaab.app.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.CategorySheet
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.KpiRow
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.components.RoundIcon
import com.hisaab.app.ui.components.Segmented
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.BookPeriodChips
import com.hisaab.app.ui.ledger.LedgerMath
import com.hisaab.app.ui.theme.Hx
import com.hisaab.app.ui.theme.clearTopBar
import com.hisaab.parser.model.Category
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private val SEGMENTS = listOf("List", "Calendar", "By merchant")

/**
 * The Transactions tab: global book and period, search, type/bank/source chips, the period's in/out/net, and the
 * transactions as a day list, a calendar, or by merchant. Long-press a row to select several.
 *
 * [onOpenReview] opens the review queue; when it is not wired, "Review" narrows the list to flagged rows instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsRoute(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    contentPadding: PaddingValues,
    onOpenBills: () -> Unit = {},
    onOpenReview: (() -> Unit)? = null,
    vm: TransactionsViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val bank by vm.bank.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val scope by vm.scope.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var segment by rememberSaveable { mutableStateOf(0) }
    var expanded by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }

    // Long-press starts selecting; while anything is selected, a tap toggles instead of opening.
    var selected by rememberSaveable(stateSaver = LongSetSaver) { mutableStateOf(emptySet<Long>()) }
    var pickingCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmMerge by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val coroutines = rememberCoroutineScope()
    val mergedText = t("Merged into one payment.")
    val undoText = t("Undo")
    val selecting = selected.isNotEmpty()
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    val tap: (com.hisaab.shared.db.TransactionEntity) -> Unit = { if (selecting) toggle(it.id) else onOpen(it.id) }
    val longTap: (com.hisaab.shared.db.TransactionEntity) -> Unit = { toggle(it.id) }
    BackHandler(enabled = selecting) { selected = emptySet() }

    // Calendar: the period's last month first; arrows stay inside the period.
    val firstMonth = YearMonth.from(ui.from)
    val lastMonth = YearMonth.from(ui.to)
    var calMonthText by rememberSaveable(ui.from, ui.to) { mutableStateOf(lastMonth.toString()) }
    val calMonth = YearMonth.parse(calMonthText).let { if (it.isBefore(firstMonth)) firstMonth else if (it.isAfter(lastMonth)) lastMonth else it }
    var calDayText by rememberSaveable(ui.from, ui.to) { mutableStateOf<String?>(null) }
    val calDay = calDayText?.let(LocalDate::parse)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, t("Cancel selection")) } },
                    actions = {
                        IconButton(onClick = { selected = ui.shown.map { it.id }.toSet() }) { Icon(Icons.Filled.SelectAll, t("Select all")) }
                        if (selected.size >= 2) IconButton(onClick = { confirmMerge = true }) { Icon(Icons.Filled.CallMerge, t("Merge")) }
                        IconButton(onClick = { pickingCategory = true }) { Icon(Icons.Filled.Category, t("Change category")) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, t("Delete")) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            } else {
                TopAppBar(
                    colors = clearTopBar(), title = { Text(t("Transactions")) },
                    actions = { RoundIcon(Icons.Filled.EventRepeat, t("Bills"), Modifier.padding(end = 14.dp), onClick = onOpenBills) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton(onClick = onAdd, modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding())) {
                    Icon(Icons.Filled.Add, t("Add a transaction"))
                }
            }
        },
    ) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding()).fillMaxSize()) {
            // Fixed controls: book and period, any scope from a link, search, and filter chips.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BookPeriodChips(showBook = false)
                scope.accountId?.let { id ->
                    val a = accounts.firstOrNull { it.id == id }
                    Pill(a?.let { "${it.nickname ?: it.bankName} ••${it.last4}" } ?: t("One account"), on = true, leading = Icons.Filled.Close) { vm.clearAccount() }
                }
                scope.category?.let { c -> Pill(t(c.label), on = true, leading = Icons.Filled.Close) { vm.clearCategory() } }
            }
            SearchBox(query, ui.shown.size, vm::setQuery, Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp))
            TxFilters(
                kind, bank, source, ui.banks, ui.sourceChips, ui.reviewCount,
                onKind = vm::setKind, onBank = vm::setBank, onSource = vm::setSource, onClear = vm::clearFilters,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            )

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp),
                verticalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                item(key = "kpi") {
                    HCard(Modifier.animateItem(), padding = 14.dp) {
                        val net = ui.inMinor - ui.outMinor
                        KpiRow(
                            Triple(t("In"), Money.format(ui.inMinor, showPaise = false), Hx.pos),
                            Triple(t("Out"), Money.format(ui.outMinor, showPaise = false), null),
                            Triple(t("Net"), (if (net < 0) "−" else "+") + Money.format(kotlin.math.abs(net), showPaise = false), if (net < 0) Hx.neg else Hx.pos),
                        )
                        Text(
                            t("{n} transactions · {period}", "n" to ui.shown.size, "period" to t(ui.periodLabel)) +
                                if (ui.outMinor > 0 && LedgerMath.invested(ui.shown) > 0) " · " + t("Out includes investments") else "",
                            fontSize = 11.sp, color = Hx.text2, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (ui.reviewCount > 0) {
                    item(key = "review") {
                        ReviewBanner(
                            ui.reviewCount, modifier = Modifier.animateItem(),
                            onSelect = { vm.setKind(TxKind.REVIEW) },
                            onReview = { if (onOpenReview != null) onOpenReview() else vm.setKind(TxKind.REVIEW) },
                        )
                    }
                }
                item(key = "segments") { Segmented(SEGMENTS.map { t(it) }, segment, { segment = it }, Modifier.animateItem()) }

                if (ui.loaded && ui.shown.isEmpty()) {
                    item(key = "empty") {
                        val body = when {
                            query.isNotBlank() -> t("Nothing matches “{query}” in {period}.", "query" to query.trim(), "period" to t(ui.periodLabel))
                            filtered -> t("No transactions match these filters in {period}.", "period" to t(ui.periodLabel))
                            else -> t("No transactions in {period}.", "period" to t(ui.periodLabel))
                        }
                        EmptyState(Icons.Filled.ReceiptLong, t("Nothing here"), body, Modifier.animateItem())
                    }
                } else when (segment) {
                    0 -> items(ui.days, key = { "d${it.date}" }) { day ->
                        DayCard(day, sources, selected, tap, longTap, Modifier.animateItem())
                    }
                    1 -> {
                        item(key = "calendar") {
                            val values = remember(ui.shown, kind) {
                                ui.shown.filter { kind != TxKind.ALL || LedgerMath.isSpend(it) }
                                    .groupBy { Periods.localDate(it.timestamp) }.mapValues { (_, l) -> l.sumOf(LedgerMath::rupees) }
                            }
                            val caption = when (kind) {
                                TxKind.ALL, TxKind.EXPENSE -> t("Daily spend")
                                TxKind.INCOME -> t("Daily income")
                                TxKind.TRANSFER -> t("Daily transfers")
                                TxKind.INVESTMENT -> t("Daily investments")
                                TxKind.REVIEW -> t("Flagged amounts by day")
                            }
                            CalendarCard(
                                calMonth, ui.from, ui.to, values, caption, if (kind == TxKind.INCOME) Hx.pos else Hx.accent, calDay,
                                onSelect = { d -> calDayText = if (d == calDay) null else d.toString() },
                                onPrev = if (calMonth.isAfter(firstMonth)) ({ calMonthText = calMonth.minusMonths(1).toString() }) else null,
                                onNext = if (calMonth.isBefore(lastMonth)) ({ calMonthText = calMonth.plusMonths(1).toString() }) else null,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        val day = calDay?.let { d -> ui.days.firstOrNull { it.date == d } }
                        item(key = "calendar-day") {
                            if (day != null) DayCard(day, sources, selected, tap, longTap, Modifier.animateItem())
                            else CalendarDayHint(calDay, Modifier.animateItem())
                        }
                    }
                    else -> {
                        val top = ui.merchants.firstOrNull()?.gross ?: 0L
                        items(ui.merchants, key = { "m${it.name}" }) { m ->
                            MerchantCard(
                                m, top, expanded = m.name in expanded,
                                onToggle = { expanded = if (m.name in expanded) expanded - m.name else expanded + m.name },
                                sources = sources, selected = selected, onClick = tap, onLongClick = longTap, modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
    BulkDialogs(
        count = selected.size, pickingCategory = pickingCategory, confirmDelete = confirmDelete, confirmMerge = confirmMerge,
        onMerge = {
            val ids = selected
            selected = emptySet(); confirmMerge = false
            vm.merge(ids) { done ->
                if (done) coroutines.launch {
                    val r = snack.showSnackbar(mergedText, actionLabel = undoText, duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) vm.undoMerge()
                }
            }
        },
        onDismissMerge = { confirmMerge = false },
        onPick = { c -> vm.setCategory(selected, c); selected = emptySet(); pickingCategory = false },
        onDelete = { vm.delete(selected); selected = emptySet(); confirmDelete = false },
        onDismissPicker = { pickingCategory = false }, onDismissDelete = { confirmDelete = false },
    )
}

@Composable
private fun BulkDialogs(
    count: Int, pickingCategory: Boolean, confirmDelete: Boolean, confirmMerge: Boolean, onMerge: () -> Unit, onDismissMerge: () -> Unit,
    onPick: (Category) -> Unit, onDelete: () -> Unit, onDismissPicker: () -> Unit, onDismissDelete: () -> Unit,
) {
    if (pickingCategory) CategorySheet(current = null, onPick = onPick, onDismiss = onDismissPicker, title = t("Category for {n} transactions", "n" to count))
    if (confirmMerge) {
        AlertDialog(
            onDismissRequest = onDismissMerge,
            title = { Text(t("Merge into one payment?")) },
            text = { Text(t("The messages stay linked, and you can split them again from the transaction page.")) },
            confirmButton = { TextButton(onClick = onMerge) { Text(t("Merge"), fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = onDismissMerge) { Text(t("Cancel")) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(t("Delete {n} transactions?", "n" to count)) },
            text = { Text(t("They are removed from DhanKosh and won't come back on a rescan. The SMS and emails themselves are not touched.")) },
            confirmButton = { TextButton(onClick = onDelete) { Text(t("Delete"), fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text(t("Cancel")) } },
        )
    }
}

private val LongSetSaver = Saver<Set<Long>, LongArray>(save = { it.toLongArray() }, restore = { it.toSet() })
private val StringSetSaver = Saver<Set<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toSet() })
