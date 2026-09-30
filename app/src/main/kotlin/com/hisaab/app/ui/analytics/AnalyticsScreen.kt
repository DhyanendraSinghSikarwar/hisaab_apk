package com.hisaab.app.ui.analytics

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.MoneyColors
import com.hisaab.app.ui.theme.color
import com.hisaab.shared.db.CategoryTotal
import com.hisaab.shared.db.DayTotal
import com.hisaab.shared.db.MonthTotal
import com.hisaab.shared.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.ehsannarmani.compose_charts.ColumnChart
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.Bars
import ir.ehsannarmani.compose_charts.models.DrawStyle
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import ir.ehsannarmani.compose_charts.models.Line
import ir.ehsannarmani.compose_charts.models.Pie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import javax.inject.Inject

data class AnalyticsState(
    val month: YearMonth = YearMonth.now(),
    val categories: List<CategoryTotal> = emptyList(),
    val monthly: List<MonthTotal> = emptyList(),
    val daily: List<DayTotal> = emptyList(),
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(private val dao: TransactionDao) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = month.flatMapLatest { m ->
        val range = Periods.range(m)
        val sixMonths = Periods.range(m.minusMonths(5)).first until range.last + 1
        val offset = Periods.offsetMillis(range.first)
        combine(
            dao.observeCategoryTotals(range.first, range.last),
            dao.observeMonthly(sixMonths.first, sixMonths.last, offset),
            dao.observeDailySpend(range.first, range.last, offset),
        ) { cats, monthly, daily -> AnalyticsState(m, cats, monthly, daily) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    fun shift(months: Long) = month.update { (it.plusMonths(months)).coerceAtMost(YearMonth.now()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsRoute(contentPadding: PaddingValues, vm: AnalyticsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Analytics") }) }) { inner ->
        Column(
            Modifier.padding(top = inner.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                Text(Periods.month(s.month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.shift(1) }, enabled = s.month < YearMonth.now()) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
            }
            val total = s.categories.sumOf { it.total }
            ChartCard("Spending by category", subtitle = Money.format(total, showPaise = false)) {
                if (s.categories.isEmpty()) {
                    EmptyState(Icons.Filled.PieChart, "No spending", "Nothing was spent in ${Periods.month(s.month)}.")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pies = remember(s.categories) { s.categories.map { Pie(label = it.category.label, data = it.total / 100.0, color = it.category.color) } }
                        // Our own legend sits beside the chart; the library's would repeat it, truncated.
                        PieChart(modifier = Modifier.size(160.dp), data = pies, style = Pie.Style.Stroke(width = 34.dp),
                            labelHelperProperties = LabelHelperProperties(enabled = false))
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.categories.take(7).forEach { c ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).background(c.category.color, CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                    Text(c.category.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text("${(c.total * 100 / total.coerceAtLeast(1))}%", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            val spentColor = MoneyColors.debit
            val incomeColor = MoneyColors.credit
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            val textStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
            ChartCard("Last six months", subtitle = "Spent and income") {
                val bars = remember(s.monthly, spentColor, incomeColor) {
                    s.monthly.map { m ->
                        Bars(
                            label = Periods.monthShort(YearMonth.parse(m.month)),
                            values = listOf(
                                Bars.Data(label = "Spent", value = m.spent / 100.0, color = SolidColor(spentColor)),
                                Bars.Data(label = "Income", value = m.income / 100.0, color = SolidColor(incomeColor)),
                            ),
                        )
                    }
                }
                if (bars.isNotEmpty()) {
                    ColumnChart(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        data = bars,
                        labelProperties = LabelProperties(enabled = true, textStyle = textStyle),
                        indicatorProperties = HorizontalIndicatorProperties(textStyle = textStyle, contentBuilder = { Money.compact((it * 100).toLong()) }),
                    )
                }
            }

            ChartCard("Daily spending", subtitle = Periods.month(s.month)) {
                val days = s.month.lengthOfMonth()
                val firstDay = Periods.range(s.month).first
                val offset = Periods.offsetMillis(firstDay)
                val startDay = (firstDay + offset) / 86_400_000L
                val values = remember(s.daily) {
                    val byDay = s.daily.associate { (it.day - startDay).toInt() to it.total / 100.0 }
                    List(days) { byDay[it] ?: 0.0 }
                }
                val lineColor = MaterialTheme.colorScheme.primary
                if (values.any { it > 0 }) {
                    LineChart(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        data = remember(values, lineColor) {
                            listOf(Line(label = "Spent", values = values, color = SolidColor(lineColor), firstGradientFillColor = lineColor.copy(alpha = .3f),
                                secondGradientFillColor = lineColor.copy(alpha = 0f), drawStyle = DrawStyle.Stroke(2.dp)))
                        },
                        indicatorProperties = HorizontalIndicatorProperties(textStyle = textStyle, contentBuilder = { Money.compact((it * 100).toLong()) }),
                    )
                } else {
                    Text("No spending this month.", style = MaterialTheme.typography.bodyMedium, color = labelColor)
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
