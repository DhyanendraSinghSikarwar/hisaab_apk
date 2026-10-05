package com.hisaab.app.ui.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import javax.inject.Inject

data class AnalyticsState(
    val month: YearMonth = YearMonth.now(),
    val categories: List<CategoryTotal> = emptyList(),
    val monthly: List<MonthTotal> = emptyList(),
    val daily: List<DayTotal> = emptyList(),
    val previousDaily: List<DayTotal> = emptyList(),
    val merchants: List<MerchantTotal> = emptyList(),
    val loaded: Boolean = false,
) {
    val spent: Long get() = categories.sumOf { it.total }
    val income: Long get() = monthly.lastOrNull { it.month == month.toString() }?.income ?: 0
}

@HiltViewModel
class AnalyticsViewModel @Inject constructor(private val dao: TransactionDao, plans: com.hisaab.app.ui.plan.PlanSource) : ViewModel() {
    val plan = plans.snapshot

    private val month = MutableStateFlow(YearMonth.now(Periods.zone))

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = month.flatMapLatest { m ->
        val range = Periods.range(m)
        val prev = Periods.range(m.minusMonths(1))
        val sixMonths = Periods.range(m.minusMonths(5)).first until range.last + 1
        val offset = Periods.offsetMillis(range.first)
        combine(
            combine(dao.observeCategoryTotals(range.first, range.last), dao.observeMonthly(sixMonths.first, sixMonths.last, offset)) { a, b -> a to b },
            dao.observeDailySpend(range.first, range.last, offset),
            dao.observeDailySpend(prev.first, prev.last, Periods.offsetMillis(prev.first)),
            dao.observeTopMerchants(range.first, range.last, 6),
        ) { (cats, monthly), daily, prevDaily, merchants -> AnalyticsState(m, cats, monthly, daily, prevDaily, merchants, loaded = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    fun shift(months: Long) = month.update { (it.plusMonths(months)).coerceAtMost(YearMonth.now(Periods.zone)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsRoute(contentPadding: PaddingValues, vm: AnalyticsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    var forward by remember { mutableStateOf(true) }
    Scaffold(topBar = { TopAppBar(title = { Text("Analytics") }) }) { inner ->
        Column(
            Modifier.padding(top = inner.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MonthSwitcher(s.month, onPrev = { forward = false; vm.shift(-1) }, onNext = { forward = true; vm.shift(1) })
            if (plan.insights.isNotEmpty() && s.month == YearMonth.now(Periods.zone)) {
                Text("Insights", style = MaterialTheme.typography.titleMedium)
                plan.insights.forEach { com.hisaab.app.ui.home.InsightRow(it) }
            }
            // The whole report slides with the month, the way a calendar page turns.
            AnimatedContent(
                targetState = s,
                contentKey = { it.month },
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(160))) using SizeTransform(clip = false)
                },
                label = "report",
            ) { state -> Report(state) }
        }
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
        Text(Periods.month(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onNext, enabled = month < YearMonth.now(Periods.zone)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
    }
}

@Composable
private fun Report(s: AnalyticsState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Kpis(s)
        CategoriesCard(s)
        if (s.merchants.isNotEmpty()) MerchantsCard(s.merchants, s.spent)
        TrendCard(s)
        DailyCard(s)
    }
}

@Composable
private fun Kpis(s: AnalyticsState) {
    val today = LocalDate.now(Periods.zone)
    val days = if (s.month == YearMonth.from(today)) today.dayOfMonth else s.month.lengthOfMonth()
    val saved = s.income - s.spent
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
    var selected by rememberSaveable(s.month) { mutableStateOf<Int?>(null) }
    ChartCard("Where it went", "Tap a slice or a row for its share") {
        if (s.categories.isEmpty()) {
            EmptyState(Icons.Filled.PieChart, "No spending", "Nothing was spent in ${Periods.month(s.month)}.")
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
    ChartCard("Six months", "Tap or slide across the bars") {
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
    val days = s.month.lengthOfMonth()
    val values = remember(s.daily, s.month) { perDay(s.daily, s.month) }
    val previous = remember(s.previousDaily, s.month) { perDay(s.previousDaily, s.month.minusMonths(1)) }
    val today = LocalDate.now(Periods.zone)
    // In the current month the line stops at today; the comparison covers the same days of last month.
    val upTo = if (s.month == YearMonth.from(today)) today.dayOfMonth else days
    val shown = (if (cumulative) values.runningReduce { a, b -> a + b } else values).take(upTo)
    val compare = (if (cumulative) previous.runningReduce { a, b -> a + b } else previous).take(upTo)
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
            Text("No spending this month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ChartCard
        }
        AreaLineChart(
            values = shown, compare = compare.takeIf { previous.any { it > 0 } }, color = color,
            xLabel = { "${it + 1} ${Periods.monthShort(s.month)}" }, format = { Money.compact(it) },
            seriesName = Periods.monthShort(s.month), compareName = Periods.monthShort(s.month.minusMonths(1)),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(Periods.monthShort(s.month), color)
            if (previous.any { it > 0 }) Legend(Periods.monthShort(s.month.minusMonths(1)), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

private fun perDay(daily: List<DayTotal>, month: YearMonth): List<Long> {
    val first = Periods.range(month).first
    val startDay = (first + Periods.offsetMillis(first)) / 86_400_000L
    val byDay = daily.associate { (it.day - startDay).toInt() to it.total }
    return List(month.lengthOfMonth()) { byDay[it] ?: 0L }
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
