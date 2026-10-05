package com.hisaab.app.ui.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.charts.AreaLineChart
import com.hisaab.app.ui.charts.BarGroup
import com.hisaab.app.ui.charts.ChartSlice
import com.hisaab.app.ui.charts.DonutChart
import com.hisaab.app.ui.charts.GroupedBarChart
import com.hisaab.app.ui.components.AnimatedAmount
import com.hisaab.app.ui.components.CategoryBadge
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.MoneyColors
import com.hisaab.app.ui.theme.color
import com.hisaab.shared.db.CategoryTotal
import com.hisaab.shared.db.DayTotal
import com.hisaab.shared.db.MerchantTotal
import com.hisaab.shared.db.MonthTotal
import com.hisaab.shared.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.filled.Tune
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.flowOf
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import javax.inject.Inject

enum class Period(val label: String) {
    THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_FY("This FY"), ALL_TIME("All time"), CUSTOM("Custom")
}

/** Which money to count: personal, business, or both. Names match the SQL the DAO expects. */
enum class Scope(val label: String) { ALL("All"), PERSONAL("Personal"), BUSINESS("Business") }

enum class KindFilter(val label: String, val sql: String) { ALL("All accounts", "ALL"), ACCOUNT("Bank accounts", "ACCOUNT"), CARD("Cards", "CARD") }

data class AnalyticsFilter(
    val period: Period = Period.THIS_MONTH,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val scope: Scope = Scope.ALL,
    val kind: KindFilter = KindFilter.ALL,
)

data class AnalyticsState(
    val filter: AnalyticsFilter = AnalyticsFilter(),
    val from: LocalDate = LocalDate.now(Periods.zone).withDayOfMonth(1),
    val to: LocalDate = LocalDate.now(Periods.zone),
    val categories: List<CategoryTotal> = emptyList(),
    val income: Long = 0,
    val monthly: List<MonthTotal> = emptyList(),
    val daily: List<DayTotal> = emptyList(),
    val previousDaily: List<DayTotal> = emptyList(),
    val merchants: List<MerchantTotal> = emptyList(),
    val loaded: Boolean = false,
) {
    val spent: Long get() = categories.sumOf { it.total }

    /** Days in the period up to today; the per-day figure and the daily chart stop there. */
    val elapsedDays: Int get() = (ChronoUnit.DAYS.between(from, minOf(to, LocalDate.now(Periods.zone))) + 1).toInt().coerceAtLeast(1)
    val totalDays: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()

    val label: String get() = when (filter.period) {
        Period.THIS_MONTH, Period.LAST_MONTH -> Periods.month(YearMonth.from(from))
        Period.THIS_FY -> "FY ${from.year}–${(from.year + 1) % 100}"
        else -> "${from.format(SHORT_DATE)} – ${to.format(SHORT_DATE)}"
    }

    companion object {
        val SHORT_DATE: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
    }
}

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val dao: TransactionDao,
    plans: com.hisaab.app.ui.plan.PlanSource,
    layout: com.hisaab.app.settings.TabLayoutStore,
) : ViewModel() {
    val plan = plans.snapshot

    /** The report's sections, in the order and visibility set under Settings → Customize tabs. */
    val sections = layout.settings.map { it.visible(com.hisaab.app.settings.TabLayouts.ANALYTICS) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.hisaab.app.settings.TabLayouts.DEFAULTS.getValue(com.hisaab.app.settings.TabLayouts.ANALYTICS).map { it.key })

    private val filter = MutableStateFlow(AnalyticsFilter())

    private suspend fun resolve(f: AnalyticsFilter): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now(Periods.zone)
        return when (f.period) {
            Period.THIS_MONTH -> today.withDayOfMonth(1) to today
            Period.LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
            // India's financial year runs April to March.
            Period.THIS_FY -> LocalDate.of(if (today.monthValue >= 4) today.year else today.year - 1, 4, 1) to today
            Period.ALL_TIME -> (dao.firstTimestamp()?.let { Periods.localDate(it) } ?: today).coerceAtMost(today) to today
            Period.CUSTOM -> (f.customFrom ?: today) to (f.customTo ?: today)
        }
    }

    private fun LocalDate.startMillis() = atStartOfDay(Periods.zone).toInstant().toEpochMilli()
    private fun LocalDate.endMillis() = plusDays(1).atStartOfDay(Periods.zone).toInstant().toEpochMilli() - 1

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = filter.flatMapLatest { f ->
        val (from, to) = resolve(f)
        val start = from.startMillis()
        val end = to.endMillis()
        val offset = Periods.offsetMillis(start)
        val scope = f.scope.name
        val kind = f.kind.sql
        // Bars: at least six months ending with the period, at most twelve.
        val lastMonth = YearMonth.from(to)
        val months = ChronoUnit.MONTHS.between(YearMonth.from(from), lastMonth).toInt() + 1
        val trendFrom = lastMonth.minusMonths((months.coerceIn(6, 12) - 1).toLong()).atDay(1).startMillis()
        // The daily line compares with the same number of days just before the period.
        val days = ChronoUnit.DAYS.between(from, to) + 1
        val prevStart = from.minusDays(days).startMillis()
        combine(
            combine(dao.categoryTotalsFor(start, end, scope, kind), dao.monthlyFor(start, end, offset, scope, kind)) { a, b -> a to b.sumOf { it.income } },
            dao.monthlyFor(trendFrom, end, offset, scope, kind),
            if (days <= 62) dao.dailyFor(start, end, offset, scope, kind) else flowOf(emptyList()),
            if (days <= 62) dao.dailyFor(prevStart, start - 1, offset, scope, kind) else flowOf(emptyList()),
            dao.topMerchantsFor(start, end, 6, scope, kind),
        ) { (cats, income), monthly, daily, prevDaily, merchants ->
            AnalyticsState(f, from, to, cats, income, monthly, daily, prevDaily, merchants, loaded = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    fun setPeriod(p: Period) = filter.update { it.copy(period = p) }
    fun setCustom(from: LocalDate, to: LocalDate) = filter.update { it.copy(period = Period.CUSTOM, customFrom = from, customTo = to) }
    fun setScope(s: Scope) = filter.update { it.copy(scope = s) }
    fun setKind(k: KindFilter) = filter.update { it.copy(kind = k) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsRoute(contentPadding: PaddingValues, vm: AnalyticsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(title = { Text("Analytics") }, colors = com.hisaab.app.ui.theme.clearTopBar())
    }) { inner ->
        Column(
            Modifier.padding(top = inner.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Kpis(s)
            FilterBar(s, vm)
            if (plan.insights.isNotEmpty() && s.filter.period == Period.THIS_MONTH) {
                Text("Insights", style = MaterialTheme.typography.titleMedium)
                plan.insights.forEach { com.hisaab.app.ui.home.InsightRow(it) }
            }
            // The report rises in whenever the filter changes.
            AnimatedContent(
                targetState = s,
                contentKey = { it.filter to it.from },
                transitionSpec = {
                    (slideInVertically(tween(260)) { it / 12 } + fadeIn(tween(220))) togetherWith fadeOut(tween(140)) using SizeTransform(clip = false)
                },
                label = "report",
            ) { state -> Report(state, sections) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(s: AnalyticsState, vm: AnalyticsViewModel) {
    var picking by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var periodMenu by remember { mutableStateOf(false) }
    val active = listOf(s.filter.scope != Scope.ALL, s.filter.kind != KindFilter.ALL).count { it }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            FilterChip(
                selected = true, onClick = { periodMenu = true },
                label = { Text(if (s.filter.period == Period.CUSTOM) s.label else s.filter.period.label, maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
            )
            DropdownMenu(periodMenu, { periodMenu = false }) {
                Period.entries.forEach { p ->
                    DropdownMenuItem(text = { Text(if (p == Period.CUSTOM) "Custom range…" else p.label) },
                        onClick = { periodMenu = false; if (p == Period.CUSTOM) picking = true else vm.setPeriod(p) })
                }
            }
        }
        FilterChip(
            selected = active > 0, onClick = { sheet = true },
            leadingIcon = { Icon(Icons.Filled.Tune, null, Modifier.size(18.dp)) },
            label = { Text(if (active > 0) "Filters · $active" else "Filters") },
        )
        if (active > 0 || s.filter.period != Period.THIS_MONTH) {
            AssistChip(onClick = { vm.setPeriod(Period.THIS_MONTH); vm.setScope(Scope.ALL); vm.setKind(KindFilter.ALL) }, label = { Text("Clear") })
        }
    }
    if (sheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { sheet = false }) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Filters", style = MaterialTheme.typography.titleLarge)
                Text("Money", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Scope.entries.forEachIndexed { i, sc ->
                        SegmentedButton(s.filter.scope == sc, { vm.setScope(sc) }, SegmentedButtonDefaults.itemShape(i, Scope.entries.size)) { Text(sc.label) }
                    }
                }
                Text("Paid from", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    KindFilter.entries.forEachIndexed { i, k ->
                        SegmentedButton(s.filter.kind == k, { vm.setKind(k) }, SegmentedButtonDefaults.itemShape(i, KindFilter.entries.size)) { Text(k.label, maxLines = 1) }
                    }
                }
                androidx.compose.material3.Button(onClick = { sheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Show results") }
            }
        }
    }
    if (picking) {
        val utc = java.time.ZoneOffset.UTC
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = s.from.atStartOfDay(utc).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = s.to.atStartOfDay(utc).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(enabled = state.selectedStartDateMillis != null, onClick = {
                    val a = java.time.Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(utc).toLocalDate()
                    val b = state.selectedEndDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate() } ?: a
                    vm.setCustom(a, b); picking = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state, Modifier.weight(1f), title = { Text("Choose dates", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

@Composable
private fun Report(s: AnalyticsState, sections: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        sections.forEach { key ->
            when (key) {
                "categories" -> CategoriesCard(s)
                "merchants" -> if (s.merchants.isNotEmpty()) MerchantsCard(s.merchants, s.spent)
                "trend" -> TrendCard(s)
                "daily" -> if (s.totalDays <= 62) DailyCard(s)
            }
        }
    }
}

@Composable
private fun Kpis(s: AnalyticsState) {
    val days = s.elapsedDays
    val saved = s.income - s.spent
    Text(s.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Kpi("Spent", s.spent, MoneyColors.debit, Modifier.weight(1f))
        Kpi("Income", s.income, MoneyColors.credit, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Kpi(if (saved >= 0) "Saved" else "Overspent", kotlin.math.abs(saved), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        Kpi("Per day", s.spent / days.coerceAtLeast(1), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
    }
}

@Composable
private fun Kpi(label: String, minor: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedAmount(minor, MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String?, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                action?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun CategoriesCard(s: AnalyticsState) {
    var selected by rememberSaveable(s.label) { mutableStateOf<Int?>(null) }
    ChartCard("Where it went", "Tap a slice or a row for its share") {
        if (s.categories.isEmpty()) {
            EmptyState(Icons.Filled.PieChart, "No spending", "Nothing was spent in ${s.label}.")
            return@ChartCard
        }
        val slices = remember(s.categories) { s.categories.map { ChartSlice(it.category.label, it.total, it.category.color) } }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                slices = slices, selected = selected, onSelect = { selected = it }, centerLabel = "Total spent",
                centerValue = { Money.format(it, showPaise = false) }, modifier = Modifier.fillMaxWidth(0.72f),
            )
        }
        Spacer(Modifier.height(12.dp))
        val total = s.spent.coerceAtLeast(1)
        s.categories.forEachIndexed { i, c ->
            val share by animateFloatAsState(c.total.toFloat() / total, tween(600), label = "share")
            val isSel = selected == i
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) c.category.color.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { selected = if (isSel) null else i }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(c.category, size = 32)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(c.category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                            fontWeight = if (isSel) FontWeight.SemiBold else null)
                        Text(Money.format(c.total, showPaise = false), style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(
                        progress = { share }, color = c.category.color, trackColor = c.category.color.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(5.dp).clip(CircleShape),
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun MerchantsCard(merchants: List<MerchantTotal>, spent: Long) {
    ChartCard("Top merchants", null) {
        merchants.forEachIndexed { i, m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text("${m.count} payment${if (m.count > 1) "s" else ""} · ${m.total * 100 / spent.coerceAtLeast(1)}% of spend",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(m.total, showPaise = false), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TrendCard(s: AnalyticsState) {
    val spent = MoneyColors.debit
    val income = MoneyColors.credit
    ChartCard("Month by month", "Tap or slide across the bars") {
        val groups = remember(s.monthly) { s.monthly.map { BarGroup(Periods.monthShort(YearMonth.parse(it.month)), listOf(it.spent, it.income)) } }
        GroupedBarChart(groups, listOf("Spent", "Income"), listOf(spent, income), format = { Money.compact(it) })
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("Spent", spent); Legend("Income", income)
        }
    }
}

@Composable
private fun DailyCard(s: AnalyticsState) {
    var cumulative by rememberSaveable { mutableStateOf(true) }
    val color = MaterialTheme.colorScheme.primary
    val values = remember(s.daily, s.from) { perDay(s.daily, s.from, s.totalDays) }
    val previous = remember(s.previousDaily, s.from) { perDay(s.previousDaily, s.from.minusDays(s.totalDays.toLong()), s.totalDays) }
    // The line stops at today; the comparison covers the same number of days just before.
    val upTo = s.elapsedDays
    val shown = (if (cumulative) values.runningReduce { a, b -> a + b } else values).take(upTo)
    val compare = (if (cumulative) previous.runningReduce { a, b -> a + b } else previous).take(upTo)
    val monthly = s.filter.period == Period.THIS_MONTH || s.filter.period == Period.LAST_MONTH
    val nowName = if (monthly) Periods.monthShort(YearMonth.from(s.from)) else "This period"
    val prevName = if (monthly) Periods.monthShort(YearMonth.from(s.from).minusMonths(1)) else "Before"
    ChartCard(
        if (cumulative) "Spending so far" else "Daily spending", "Drag along the line",
        action = {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(cumulative, { cumulative = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Total") }
                SegmentedButton(!cumulative, { cumulative = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Daily") }
            }
        },
    ) {
        if (values.none { it > 0 }) {
            Text("No spending in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ChartCard
        }
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM")
        AreaLineChart(
            values = shown, compare = compare.takeIf { previous.any { it > 0 } }, color = color,
            xLabel = { s.from.plusDays(it.toLong()).format(fmt) }, format = { Money.compact(it) },
            seriesName = nowName, compareName = prevName,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(nowName, color)
            if (previous.any { it > 0 }) Legend(prevName, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

private fun perDay(daily: List<DayTotal>, from: LocalDate, days: Int): List<Long> {
    val first = from.atStartOfDay(Periods.zone).toInstant().toEpochMilli()
    val startDay = (first + Periods.offsetMillis(first)) / 86_400_000L
    val byDay = daily.associate { (it.day - startDay).toInt() to it.total }
    return List(days) { byDay[it] ?: 0L }
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
