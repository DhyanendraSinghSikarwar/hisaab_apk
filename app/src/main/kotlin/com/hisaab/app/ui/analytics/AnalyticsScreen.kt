package com.hisaab.app.ui.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.ui.charts.ChartSlice
import com.hisaab.app.ui.charts.DonutChart
import com.hisaab.app.ui.charts.HeatGrid
import com.hisaab.app.ui.charts.StackedMonthBars
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.Delta
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.components.KpiRow
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.components.Segmented
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.BookPeriodChips
import com.hisaab.app.ui.ledger.LedgerMath
import com.hisaab.app.ui.ledger.PeriodKind
import com.hisaab.app.ui.ledger.SpendGroup
import com.hisaab.app.ui.theme.Hx
import com.hisaab.app.ui.theme.color
import com.hisaab.app.ui.theme.icon
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.TransactionEntity
import java.time.YearMonth

private val SEGMENTS = listOf("Spending", "Cash flow", "Forecast", "Compare")

@Composable
fun AnalyticsRoute(
    contentPadding: PaddingValues,
    onOpenCategoryKey: (String) -> Unit = {},
    onOpenTransaction: (Long) -> Unit = {},
    vm: AnalyticsViewModel = hiltViewModel(),
) {
    val d by vm.data.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { n -> Category.entries.firstOrNull { it.name == n } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        Text("Analysis", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
        BookPeriodChips()
        Segmented(SEGMENTS, segment, { segment = it })
        Crossfade(targetState = segment, animationSpec = tween(220), label = "segment") { seg ->
            Column(verticalArrangement = Arrangement.spacedBy(CardGap)) {
                when (seg) {
                    0 -> SpendingSegment(d, sections, selected, { selectedName = it?.name }, onOpenCategoryKey, onOpenTransaction)
                    1 -> CashFlowSegment(d)
                    2 -> ForecastSegment(d, onThisMonth = vm::showThisMonth)
                    else -> CompareSegment(d, onOpenCategoryKey)
                }
            }
        }
    }
}

/** The key the category screen expects; for a calendar month it also opens on that month. */
internal fun categoryKey(c: Category, month: YearMonth?): String = if (month != null) "${c.name}?month=$month" else c.name

private fun periodMonth(d: AnalyticsData): YearMonth? =
    d.slice.filter.let { f -> if (f.kind == PeriodKind.MONTH || f.kind == PeriodKind.LAST_MONTH) YearMonth.from(d.slice.from) else null }

// ---------------------------------------------------------------- Spending

@Composable
private fun SpendingSegment(
    d: AnalyticsData,
    sections: List<String>,
    selected: Category?,
    onSelect: (Category?) -> Unit,
    onOpenCategoryKey: (String) -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    AnimatedVisibility(selected != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("${(selected ?: Category.OTHER).label}  ✕", on = true) { onSelect(null) }
            Spacer(Modifier.width(8.dp))
            Text("cross-filter active", fontSize = 12.sp, color = Hx.text2)
        }
    }
    sections.forEach { key ->
        when (key) {
            "categories" -> CategoriesCard(d, selected, onSelect, onOpenCategoryKey)
            "monthly" -> MonthlyCard(d, selected, onOpenCategoryKey)
            "when" -> WhenCard(d, selected)
            "merchants" -> MerchantsCard(d, selected, onOpenTransaction)
        }
    }
}

@Composable
private fun CategoriesCard(d: AnalyticsData, selected: Category?, onSelect: (Category?) -> Unit, onOpenCategoryKey: (String) -> Unit) {
    val month = periodMonth(d)
    HCard(
        title = "Spend by category",
        action = if (selected != null) "Open ›" else null,
        onAction = selected?.let { c -> { onOpenCategoryKey(categoryKey(c, month)) } },
    ) {
        val saved = d.income - d.spent
        KpiRow(
            Triple("Spent", Money.compact(d.spent), Hx.neg),
            Triple("Income", Money.compact(d.income), Hx.pos),
            Triple(if (saved >= 0) "Saved" else "Overspent", Money.compact(kotlin.math.abs(saved)), if (saved >= 0) Hx.accent else Hx.warn),
            Triple("Per day", Money.compact(d.perDay), null),
        )
        Spacer(Modifier.height(14.dp))
        if (d.categories.isEmpty()) {
            Note("No spending in ${d.slice.filter.label}.")
            return@HCard
        }
        var all by rememberSaveable { mutableStateOf(false) }
        val slices = remember(d.categories) { d.categories.map { (c, v) -> ChartSlice(c.label, v, c.color) } }
        val selIndex = d.categories.indexOfFirst { it.first == selected }.takeIf { it >= 0 }
        val total = d.spent.coerceAtLeast(1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = slices, selected = selIndex, onSelect = { i -> onSelect(i?.let { d.categories[it].first }) },
                centerLabel = "Spent", centerValue = { Money.compact(it) }, modifier = Modifier.width(136.dp), thickness = 16.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                d.categories.take(6).forEach { (c, v) ->
                    CategoryLegend(c, v, total, c == selected, { onSelect(if (c == selected) null else c) }, { onOpenCategoryKey(categoryKey(c, month)) })
                }
            }
        }
        if (d.categories.size > 6) {
            AnimatedVisibility(all, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 2.dp)) {
                    d.categories.drop(6).forEach { (c, v) ->
                        CategoryLegend(c, v, total, c == selected, { onSelect(if (c == selected) null else c) }, { onOpenCategoryKey(categoryKey(c, month)) })
                    }
                }
            }
            Text(
                if (all) "Show fewer" else "All ${d.categories.size} categories",
                color = Hx.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { all = !all }.padding(4.dp),
            )
        }
        Text("Tap to filter the cards below · long-press to open", fontSize = 11.sp, color = Hx.text2, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryLegend(c: Category, value: Long, total: Long, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (selected) Hx.accentSoft else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() })
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(c.color))
        Spacer(Modifier.width(6.dp))
        Text(c.label, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else null)
        Text("${value * 100 / total}%", fontSize = 11.sp, color = Hx.text2, modifier = Modifier.padding(end = 6.dp))
        Text(Money.compact(value), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthlyCard(d: AnalyticsData, selected: Category?, onOpenCategoryKey: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    val months = d.slice.months
    HCard(title = "Monthly spend · 12 months") {
        if (months.isEmpty() || d.monthly.all { it.spent == 0L }) {
            Note("No spending in the last 12 months.")
            return@HCard
        }
        val catByMonth = remember(d.slice.year, selected) {
            if (selected == null) null
            else d.slice.year.filter { LedgerMath.isSpend(it) && it.category == selected }
                .groupBy { YearMonth.from(Periods.localDate(it.timestamp)) }.mapValues { (_, l) -> l.sumOf(LedgerMath::rupees) }
        }
        val stacks = d.monthly.map { m ->
            if (catByMonth != null) { val c = catByMonth[m.month] ?: 0L; listOf(c, (m.spent - c).coerceAtLeast(0)) }
            else listOf(m.byGroup[SpendGroup.ESSENTIALS] ?: 0L, m.byGroup[SpendGroup.LIFESTYLE] ?: 0L, m.byGroup[SpendGroup.OTHER] ?: 0L)
        }
        val colors = if (selected != null) listOf(selected.color, Hx.border) else listOf(Hx.palette[0], Hx.palette[1], Hx.palette[7])
        val history = (if (catByMonth != null) d.monthly.map { catByMonth[it.month] ?: 0L } else d.monthly.map { it.spent }).dropLast(1)
        val avg = if (history.isEmpty()) null else history.sum() / history.size
        StackedMonthBars(
            labels = months.map { Periods.monthShort(it) }, stacks = stacks, colors = colors,
            averageLabel = avg?.let { "avg ${Money.compact(it)}" }, average = avg,
            onTap = { open = it }, selected = open,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) { Swatch(selected.color, selected.label); Swatch(Hx.border, "Rest") }
            else { Swatch(colors[0], "Essentials"); Swatch(colors[1], "Lifestyle"); Swatch(colors[2], "Other") }
            Spacer(Modifier.weight(1f))
            Text("Tap a bar", fontSize = 11.sp, color = Hx.text2)
        }

        val i = open
        if (i != null && i in months.indices) {
            val m = months[i]
            val monthAvg = d.monthly.filterIndexed { j, _ -> j != i }.map { it.spent }.let { l -> if (l.isEmpty()) 0L else l.sum() / l.size }
            ModalBottomSheet(onDismissRequest = { open = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                MonthSheet(d, m, monthAvg) { c -> open = null; onOpenCategoryKey(categoryKey(c, m)) }
            }
        }
    }
}

@Composable
private fun MonthSheet(d: AnalyticsData, m: YearMonth, average: Long, onCategory: (Category) -> Unit) {
    val txs = remember(d.slice.year, m) { d.slice.year.filter { YearMonth.from(Periods.localDate(it.timestamp)) == m } }
    val cats = remember(txs) { LedgerMath.byCategory(txs) }
    val total = cats.sumOf { it.second }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
        Text(Periods.month(m), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Money.format(total, showPaise = false), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            if (average > 0) {
                val diff = total - average
                val pct = (kotlin.math.abs(diff) * 100 / average)
                Delta("${if (diff > 0) "▲" else "▼"} $pct% ${if (diff > 0) "above" else "below"} avg", good = diff <= 0)
            }
        }
        if (cats.isEmpty()) Note("No spending this month.")
        cats.forEach { (c, v) ->
            HRow(
                c.label, "${v * 100 / total.coerceAtLeast(1)}% of the month",
                leading = { CategoryIcon(c) }, onClick = { onCategory(c) },
            ) { Text(Money.format(v, showPaise = false), fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
    }
}

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val SLOTS = listOf("6a", "9a", "12p", "3p", "6p", "9p", "12a", "3a")

/** The heat-grid cell a spend falls in (weekday row, three-hour column), or null without a known time. */
private fun cellOf(t: TransactionEntity): Pair<Int, Int>? {
    if (!t.hasExplicitTime) return null
    val z = java.time.Instant.ofEpochMilli(t.timestamp).atZone(Periods.zone)
    return (z.dayOfWeek.value - 1) to ((z.hour - 6 + 24) % 24) / 3
}

@Composable
private fun WhenCard(d: AnalyticsData, selected: Category?) {
    val txs = remember(d.slice.txs, selected) { d.slice.txs.filter { LedgerMath.isSpend(it) && (selected == null || it.category == selected) } }
    val grid = remember(txs) { LedgerMath.heat(txs) }
    var cell by remember(txs) { mutableStateOf<Pair<Int, Int>?>(null) }
    fun topCategory(r: Int, c: Int): Category? = txs.filter { cellOf(it) == r to c }
        .groupBy { it.category }.maxByOrNull { (_, l) -> l.sumOf(LedgerMath::rupees) }?.key
    HCard(title = "When you spend") {
        val max = grid.maxOf { row -> row.max() }
        if (max == 0L) {
            Note("No spends with a time of day in this period.")
            return@HCard
        }
        HeatGrid(grid, DAYS, SLOTS, onCell = { r, c -> cell = if (cell == r to c) null else r to c })
        AnimatedContent(cell, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }, label = "cell") { sel ->
            val text = if (sel == null) "Tap a cell for its amount"
            else {
                val (r, c) = sel
                val top = if (selected == null && grid[r][c] > 0) topCategory(r, c)?.let { " · mostly ${it.label}" }.orEmpty() else ""
                "${DAYS[r]} ${SLOTS[c]}: ${Money.format(grid[r][c], showPaise = false)}$top"
            }
            Text(text, fontSize = 12.sp, color = if (sel == null) Hx.text2 else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (sel == null) null else FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        }
        val peak = remember(grid) {
            var pr = 0; var pc = 0
            grid.forEachIndexed { r, row -> row.forEachIndexed { c, v -> if (v > grid[pr][pc]) { pr = r; pc = c } } }
            // Neighbouring days in the same slot that come close to the peak widen it to a range.
            var a = pr; var b = pr
            while (a > 0 && grid[a - 1][pc] * 10 >= grid[pr][pc] * 6) a--
            while (b < 6 && grid[b + 1][pc] * 10 >= grid[pr][pc] * 6) b++
            val days = if (a == b) DAYS[a] else "${DAYS[a]}–${DAYS[b]}"
            val top = if (selected == null) topCategory(pr, pc)?.let { ", mostly ${it.label}" }.orEmpty() else ""
            "Peak: $days, ${SLOTS[pc]}–${SLOTS[(pc + 1) % 8]}$top"
        }
        Text(peak, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantsCard(d: AnalyticsData, selected: Category?, onOpenTransaction: (Long) -> Unit) {
    val spends = remember(d.slice.txs, selected) { d.slice.txs.filter { LedgerMath.isSpend(it) && (selected == null || it.category == selected) } }
    val top = remember(spends) { LedgerMath.byMerchant(spends).take(6) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    HCard(title = if (selected != null) "Top merchants · ${selected.label}" else "Top merchants") {
        if (top.isEmpty()) {
            Note(if (selected != null) "No ${selected.label.lowercase()} spends in this period." else "No spending in this period.")
            return@HCard
        }
        val max = top.first().total.coerceAtLeast(1)
        top.forEach { m ->
            val f by animateFloatAsState(m.total.toFloat() / max, tween(600), label = "merchant")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = m.name }.padding(vertical = 8.dp, horizontal = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Money.format(m.total, showPaise = false), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(" · ${m.count}×", fontSize = 12.sp, color = Hx.text2)
                }
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Hx.surface2)) {
                    Box(Modifier.fillMaxWidth(f).height(4.dp).clip(CircleShape).background(m.category.color))
                }
            }
        }
    }
    val name = open
    if (name != null) {
        val list = remember(spends, name) { spends.filter { (it.merchant ?: it.bankName) == name }.sortedByDescending { it.timestamp } }
        ModalBottomSheet(onDismissRequest = { open = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${list.size} payment${if (list.size == 1) "" else "s"} · ${Money.format(list.sumOf(LedgerMath::rupees), showPaise = false)} · ${d.slice.filter.label}",
                    fontSize = 13.sp, color = Hx.text2, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                if (list.isEmpty()) Note("No payments in this period.")
                list.take(60).forEach { t ->
                    HRow(
                        t.note?.takeIf { it.isNotBlank() } ?: t.category.label, Periods.dateTime(t.timestamp),
                        leading = { CategoryIcon(t.category) }, onClick = { open = null; onOpenTransaction(t.id) },
                    ) { Text(Money.format(LedgerMath.rupees(t), showPaise = false), fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                }
                if (list.size > 60) Note("Showing the latest 60.")
            }
        }
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, color = Hx.text2, modifier = modifier.padding(vertical = 4.dp))
}

@Composable
internal fun Swatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = Hx.text2)
    }
}

@Composable
internal fun CategoryIcon(c: Category) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(c.icon, null, tint = c.color, modifier = Modifier.size(18.dp))
    }
}
