package com.hisaab.app.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.charts.DivergingRow
import com.hisaab.app.ui.charts.FanChart
import com.hisaab.app.ui.charts.Sankey
import com.hisaab.app.ui.charts.SankeyNode
import com.hisaab.app.ui.charts.Sparkline
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.KpiRow
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.SpendGroup
import com.hisaab.app.ui.theme.Hx
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM")

// ---------------------------------------------------------------- Cash flow

@Composable
internal fun CashFlowSegment(d: AnalyticsData) {
    HCard(title = t("Where your income went")) {
        if (d.income <= 0) {
            Note(t("No income recorded in {period}. Once a salary or other credit arrives, this shows where it went.", "period" to t(d.slice.filter.label)))
            return@HCard
        }
        val saved = d.income - d.spent - d.invested
        KpiRow(
            Triple(t("Income"), Money.compact(d.income), Hx.pos),
            Triple(t("Spent"), Money.compact(d.spent), Hx.neg),
            Triple(t("Invested"), Money.compact(d.invested), null),
            Triple(if (saved >= 0) t("Left over") else t("Shortfall"), Money.compact(abs(saved)), if (saved >= 0) Hx.accent else Hx.warn),
        )
        Spacer(Modifier.height(14.dp))
        val p = Hx.palette
        Sankey(
            inputs = listOf(
                SankeyNode(t("Salary"), d.salary, Hx.pos),
                SankeyNode(t("Other income"), d.otherIncome, p[2]),
                SankeyNode(t("Refunds & interest"), d.refunds, p[6]),
            ),
            outputs = listOf(
                SankeyNode(t("Essentials"), d.groups[SpendGroup.ESSENTIALS] ?: 0L, p[0]),
                SankeyNode(t("Lifestyle"), d.groups[SpendGroup.LIFESTYLE] ?: 0L, p[1]),
                SankeyNode(t("Other"), d.groups[SpendGroup.OTHER] ?: 0L, p[7]),
                SankeyNode(t("Invested"), d.invested, p[4]),
                SankeyNode(t("Saved"), saved.coerceAtLeast(0), Hx.pos),
            ),
            valueText = { Money.compact(it) },
        )
        if (saved < 0) Note(t("Spending and investing exceeded income by {amount}.", "amount" to Money.compact(-saved)), Modifier.padding(top = 6.dp))
    }

    HCard(title = t("Income vs spend · savings rate")) {
        val rates = d.savingsRates
        if (rates.size < 2) {
            Note(t("Needs at least two months with income in the last 12."))
            return@HCard
        }
        val avg = rates.map { it.second }.average().roundToInt()
        val last = rates.last()
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${last.second.roundToInt()}%", style = MaterialTheme.typography.headlineSmall, color = if (last.second >= 0) Hx.pos else Hx.neg)
            Spacer(Modifier.width(8.dp))
            Text(t("saved in {month}", "month" to Periods.month(last.first)), fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Sparkline(rates.map { it.second }, color = if (avg >= 0) Hx.pos else Hx.neg, height = 64.dp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(Periods.monthShort(rates.first().first), fontSize = 11.sp, color = Hx.text2)
            Spacer(Modifier.weight(1f))
            Text(t("avg {pct}% · {n} months", "pct" to avg, "n" to rates.size), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(Periods.monthShort(last.first), fontSize = 11.sp, color = Hx.text2)
        }
        Note(t("Share of each month's income not spent. Months without income are left out."), Modifier.padding(top = 4.dp))
    }
}

// ---------------------------------------------------------------- Forecast

@Composable
internal fun ForecastSegment(d: AnalyticsData, onThisMonth: () -> Unit) {
    if (!d.isCurrentMonth) {
        HCard(title = t("Month-end spend forecast")) {
            Text(t("Forecast is for the current month"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Note(t("It projects this month's spend and your cash for the next 30 days from today."))
            Spacer(Modifier.height(8.dp))
            Pill(t("Show {month}", "month" to Periods.month(YearMonth.now(Periods.zone))), on = true, onClick = onThisMonth)
        }
        return
    }
    val f = d.forecast
    HCard(title = t("Month-end spend forecast")) {
        if (f == null) { Note(t("Not enough data yet.")); return@HCard }
        Text("${Money.compact(f.lowEnd)} – ${Money.compact(f.highEnd)}", style = MaterialTheme.typography.headlineSmall)
        val budget = d.budget.takeIf { it > 0 }
        val verdict = when {
            budget == null -> t("no monthly budget set")
            f.likely <= budget -> t("within budget ({amount})", "amount" to Money.compact(budget))
            else -> t("over budget ({amount}) by {over}", "amount" to Money.compact(budget), "over" to Money.compact(f.likely - budget))
        }
        Text(
            t("Likely {amount} · {verdict}", "amount" to Money.compact(f.likely), "verdict" to verdict), fontSize = 13.sp,
            color = if (budget != null && f.likely > budget) Hx.neg else Hx.text2, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        val short = Periods.monthShort(f.month)
        FanChart(
            actual = f.actual, expected = f.expected, low = f.low, high = f.high, budget = budget,
            daysInMonth = f.month.lengthOfMonth(), budgetLabel = budget?.let { t("Budget {amount}", "amount" to Money.compact(it)) },
            dayLabel = { day -> "$day $short" },
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Swatch(MaterialTheme.colorScheme.onSurface, t("Actual"))
            Swatch(Hx.accent, t("Expected"))
            Swatch(Hx.accent.copy(alpha = 0.25f), t("Range"))
        }
        Note(
            t("About {amount} a day in everyday spending", "amount" to Money.compact(f.dailyRate)) +
                (if (f.billsLeft > 0) t(", plus {amount} in bills still due.", "amount" to Money.compact(f.billsLeft)) else "."),
            Modifier.padding(top = 6.dp),
        )
    }

    HCard(title = t("Cash balance · next 30 days")) {
        val c = d.cash
        if (c == null) {
            Note(t("No bank balance known yet. Balances are read from bank SMS, or you can set one on an account."))
            return@HCard
        }
        KpiRow(
            Triple(t("Today"), Money.compact(c.start), null),
            Triple(t("In 30 days"), Money.compact(c.series.last()), if (c.series.last() < 0) Hx.neg else null),
            Triple(t("Lowest"), Money.compact(c.lowest), if (c.lowest < 0) Hx.neg else null),
        )
        Spacer(Modifier.height(12.dp))
        Sparkline(c.series.map { it / 100f }, color = if (c.lowest < 0) Hx.neg else Hx.accent, height = 64.dp)
        Text(
            t("Lowest {amount} on {date}", "amount" to Money.format(c.lowest, showPaise = false), "date" to c.lowestOn.format(DAY_MONTH)),
            fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp),
        )
        if (c.lowest < 0) {
            Text(
                t("Shortfall of {amount} around {date}. Move money in before then.", "amount" to Money.format(-c.lowest, showPaise = false), "date" to c.lowestOn.format(DAY_MONTH)),
                fontSize = 13.sp, color = Hx.neg, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Hx.neg.copy(alpha = 0.10f)).padding(10.dp),
            )
        }
        Note(
            (if (c.bills == 1) t("Liquid bank balances, less {n} bill and {amount} a day of spending", "n" to c.bills, "amount" to Money.compact(c.dailyRate))
            else t("Liquid bank balances, less {n} bills and {amount} a day of spending", "n" to c.bills, "amount" to Money.compact(c.dailyRate))) +
                (if (c.incomes == 1) t(", plus {n} expected credit.", "n" to c.incomes) else if (c.incomes > 0) t(", plus {n} expected credits.", "n" to c.incomes) else "."),
            Modifier.padding(top = 4.dp),
        )
    }
}

// ---------------------------------------------------------------- Compare

@Composable
internal fun CompareSegment(d: AnalyticsData, onOpenCategoryKey: (String) -> Unit) {
    HCard(title = t("{period} vs previous · by category", "period" to t(d.slice.filter.label))) {
        val change = d.spent - d.previousSpent
        KpiRow(
            Triple(t(d.slice.filter.label), Money.compact(d.spent), null),
            Triple(d.previousLabel, Money.compact(d.previousSpent), null),
            Triple(
                t("Change"),
                (if (change > 0) "+" else if (change < 0) "−" else "") + Money.compact(abs(change)),
                if (change > 0) Hx.neg else if (change < 0) Hx.pos else null,
            ),
        )
        Spacer(Modifier.height(12.dp))
        if (d.changes.isEmpty()) {
            Note(t("No spending in either period."))
            return@HCard
        }
        val month = d.slice.filter.let { if (it.isMonth) it.month else null }
        d.changes.forEach { ch ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpenCategoryKey(categoryKey(ch.category, month)) }.padding(vertical = 2.dp)) {
                DivergingRow(t(ch.category.label), ch.percent)
                Text(
                    "${Money.compact(ch.before)} → ${Money.compact(ch.now)}", fontSize = 10.5.sp, color = Hx.text2,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Note(t("Green fell, red rose. Bars cap at ±50%. Tap a row to open it."), Modifier.padding(top = 6.dp))
    }
}
