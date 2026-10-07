package com.hisaab.app.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.category.CategoryLook
import com.hisaab.app.ui.charts.ChartSlice
import com.hisaab.app.ui.charts.DonutChart
import com.hisaab.app.ui.charts.ProgressRing
import com.hisaab.app.ui.charts.Sparkline
import com.hisaab.app.ui.components.AccountAvatar
import com.hisaab.app.ui.components.CardTitle
import com.hisaab.app.ui.components.Delta
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.components.KpiRow
import com.hisaab.app.ui.components.LegendItem
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.components.SplitBar
import com.hisaab.app.ui.components.Tag
import com.hisaab.app.ui.components.TransactionRow
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.theme.Hx
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.Insight
import com.hisaab.shared.insight.Tone
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Edit-mode controls for one widget: move up, move down, show or hide. */
data class WidgetEdit(
    val hidden: Boolean,
    val canUp: Boolean,
    val canDown: Boolean,
    val onUp: () -> Unit,
    val onDown: () -> Unit,
    val onToggle: () -> Unit,
)

/**
 * The frame every Home widget sits in: a flat card with a small-caps title and an action on the right. In edit
 * mode the action gives way to ↑ ↓ and show/hide, and a hidden widget is drawn faded. A [hero] card (net worth)
 * keeps the same surface and size but carries an accent strip on top, an accent-tinted border and title.
 */
@Composable
fun WidgetCard(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    edit: WidgetEdit? = null,
    contentPadding: Dp = 16.dp,
    hero: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = com.hisaab.app.ui.theme.LocalDarkTheme.current
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier.fillMaxWidth().alpha(if (edit?.hidden == true) 0.35f else 1f)
            .shadow(
                if (dark) 0.dp else 10.dp, shape,
                ambientColor = Hx.accent.copy(alpha = if (hero) 0.12f else 0.06f), spotColor = Hx.accent.copy(alpha = if (hero) 0.18f else 0.10f),
            ),
        shape = shape, color = Hx.surface, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (hero) Hx.accent.copy(alpha = 0.45f) else Hx.border),
    ) {
        Column(Modifier.animateContentSize()) {
            if (hero) Box(Modifier.fillMaxWidth().height(3.dp).background(Hx.accent))
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = if (edit != null) 8.dp else 12.dp, top = if (hero) 9.dp else 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hero) {
                    Text(title.uppercase(), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = Hx.accent)
                } else CardTitle(title, Modifier.weight(1f))
                if (edit != null) {
                    EditButton(Icons.Filled.KeyboardArrowUp, t("Move up"), edit.canUp, edit.onUp)
                    EditButton(Icons.Filled.KeyboardArrowDown, t("Move down"), edit.canDown, edit.onDown)
                    EditButton(if (edit.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (edit.hidden) t("Show") else t("Hide"), true, edit.onToggle)
                } else if (action != null && onAction != null) {
                    Text(
                        action, color = Hx.accent, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp),
                    )
                }
            }
            Column(Modifier.padding(start = contentPadding, end = contentPadding, bottom = 16.dp)) { content() }
        }
    }
}

@Composable
private fun EditButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 4.dp).size(30.dp).clip(CircleShape).background(Hx.surface2).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (enabled) MaterialTheme.colorScheme.onSurface else Hx.text2.copy(alpha = 0.4f), modifier = Modifier.size(18.dp)) }
}

/** Grey helper copy for empty states. */
@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = Hx.text2)
}

/** "−₹1.2K" for negatives, "₹1.2K" otherwise. */
private fun signedCompact(v: Long) = (if (v < 0) "−" else "") + Money.compact(abs(v))

private fun usageColor(fraction: Float, pos: Color, warn: Color, neg: Color) = when {
    fraction >= 1f -> neg
    fraction >= 0.8f -> warn
    else -> pos
}

// ---------------------------------------------------------------------------------------------------------
// 1. Net worth

/** The net figure, change over about 30 days, its history as a line, and assets against liabilities. */
@Composable
fun NetWorthWidget(w: HomeWidgets, onOpenPortfolio: () -> Unit, edit: WidgetEdit?) {
    val nw = w.netWorth
    WidgetCard(t("Net worth"), action = t("Portfolio ›"), onAction = onOpenPortfolio, edit = edit, hero = true) {
        if (nw.loaded && nw.assetsMinor == 0L && nw.liabilitiesMinor == 0L) {
            Hint(t("Add account balances, deposits or holdings to see your net worth."))
            return@WidgetCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.hisaab.app.ui.components.AnimatedAmount(
                nw.netMinor, Modifier.weight(1f, fill = false), style = androidx.compose.ui.text.TextStyle(fontSize = 28.sp),
                color = Hx.accent, fontWeight = FontWeight.Bold, format = ::signedMoney,
            )
            w.netWorthBefore?.let { before ->
                val diff = nw.netMinor - before
                val text = if (before != 0L) "%s %.1f%%".format(if (diff >= 0) "▲" else "▼", abs(diff) * 100.0 / abs(before))
                else "${if (diff >= 0) "▲" else "▼"} ${Money.compact(abs(diff))}"
                Spacer(Modifier.width(10.dp))
                Delta(t("{change} · 30d", "change" to text), good = diff >= 0)
            }
        }
        // The last year, recorded days plus months estimated from transactions.
        val yearAgo = java.time.LocalDate.now().minusDays(365)
        val points = nw.history.filter { !it.day.isBefore(yearAgo) }.map { it.netMinor.toFloat() }
        if (points.size >= 2) {
            Spacer(Modifier.height(8.dp))
            Sparkline(points, color = Hx.accent, height = 46.dp)
        }
        Spacer(Modifier.height(12.dp))
        // One bar split by account and asset class, with a legend; then totals.
        com.hisaab.app.ui.invest.WorthBreakdown(nw)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            LabelValue(t("Assets"), Money.format(nw.assetsMinor, showPaise = false), Hx.pos, Modifier.weight(1f))
            LabelValue(t("Liabilities"), Money.format(nw.liabilitiesMinor, showPaise = false), if (nw.liabilitiesMinor > 0) Hx.neg else Hx.text2)
        }
        com.hisaab.app.ui.invest.CardLimits(nw)
    }
}

private fun signedMoney(v: Long) = (if (v < 0) "−" else "") + Money.format(abs(v), showPaise = false)

@Composable
private fun LabelValue(label: String, value: String, color: Color, modifier: Modifier = Modifier, onHero: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", fontSize = 12.sp, color = if (onHero) Color.White.copy(alpha = 0.75f) else Hx.text2)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------------------
// 2. Cash flow

/** Income, spent, invested and saved for the period, as figures and as shares of income. */
@Composable
fun CashFlowWidget(w: HomeWidgets, onAnalyse: () -> Unit, edit: WidgetEdit?) {
    val c = w.cash
    WidgetCard(t("{period} cash flow", "period" to t(w.filter.label)), action = t("Analyse ›"), onAction = onAnalyse, edit = edit) {
        KpiRow(
            Triple(t("Income"), Money.compact(c.income), Hx.pos),
            Triple(t("Spent"), Money.compact(c.spent), null),
            Triple(t("Invested"), Money.compact(c.invested), Hx.accent),
            Triple(t("Saved"), signedCompact(c.saved), if (c.saved >= 0) Hx.pos else Hx.neg),
        )
        Spacer(Modifier.height(12.dp))
        if (c.income > 0) {
            val inc = c.income.toFloat()
            SplitBar(listOf(c.spent / inc to Hx.neg, c.invested / inc to Hx.accent, c.saved.coerceAtLeast(0) / inc to Hx.pos))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Savings rate") + " ", fontSize = 13.sp, color = Hx.text2)
                Text("${c.rate}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if ((c.rate ?: 0) >= 0) Hx.pos else Hx.neg)
                w.bestRateOf?.let { Text("  ·  " + t("best in {n} months", "n" to it), fontSize = 13.sp, color = Hx.pos) }
            }
        } else {
            Hint(t("No income recorded in this period."))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// 3. Safe to spend

/** What can be spent per day for the rest of the month, after bills still due; other periods show the daily average. */
@Composable
fun SafeWidget(w: HomeWidgets, onOpenBudgets: () -> Unit, edit: WidgetEdit?) {
    val s = w.safe ?: return
    val needsBase = s.current && s.base <= 0
    WidgetCard(
        if (s.current) t("Safe to spend") else t("Daily spend"), edit = edit,
        action = if (needsBase) t("Set budgets ›") else null, onAction = if (needsBase) onOpenBudgets else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val used = when {
                s.current && s.base > 0 -> (s.spent + s.reserved).toFloat() / s.base
                !s.current && w.cash.income > 0 -> s.spent.toFloat() / w.cash.income
                else -> 0f
            }
            ProgressRing(used.coerceIn(0f, 1f), usageColor(used, Hx.pos, Hx.warn, Hx.neg), 64.dp, stroke = 6.dp, label = "${(used * 100).toInt().coerceAtMost(999)}%")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (s.current) t("Safe to spend today") else t("Average spent per day"), fontSize = 12.sp, color = Hx.text2)
                Text(
                    if (needsBase) "—" else t("{amount}/day", "amount" to Money.format(s.perDay, showPaise = false)),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
                Text(
                    when {
                        needsBase -> t("Add income or set budgets to get a daily figure")
                        s.current -> if (s.daysLeft == 1) t("{n} day left · bills & SIPs already reserved", "n" to s.daysLeft)
                            else t("{n} days left · bills & SIPs already reserved", "n" to s.daysLeft)
                        else -> if (s.daysLeft == 1) t("Over {n} day · {amount} spent", "n" to s.daysLeft, "amount" to Money.format(s.spent, showPaise = false))
                            else t("Over {n} days · {amount} spent", "n" to s.daysLeft, "amount" to Money.format(s.spent, showPaise = false))
                    },
                    fontSize = 12.sp, color = Hx.text2,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// 4. Upcoming

private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM")

/** Bills, EMIs and SIPs due in the next seven days, each with a date badge and the amount coloured by kind. */
@Composable
fun UpcomingWidget(w: HomeWidgets, onCalendar: () -> Unit, edit: WidgetEdit?) {
    WidgetCard(t("Upcoming · 7 days"), action = t("Calendar ›"), onAction = onCalendar, edit = edit) {
        if (w.upcoming.isEmpty()) { Hint(t("Nothing due in the next 7 days.")); return@WidgetCard }
        w.upcoming.take(5).forEach { (u, tone) ->
            val color = when (tone) {
                DueTone.CARD -> Hx.neg
                DueTone.EMI -> Hx.warn
                DueTone.INVEST -> Hx.accent
                DueTone.INCOME -> Hx.pos
                DueTone.OTHER -> MaterialTheme.colorScheme.onSurface
            }
            val whenText = when (u.daysLeft) { 0L -> t("Due today"); 1L -> t("Tomorrow"); else -> t("In {n} days", "n" to u.daysLeft) }
            val from = u.account?.let { a -> "${a.nickname ?: a.bankName} ••${a.last4}" }
            HRow(
                title = u.name, subtitle = listOfNotNull(whenText, from, t("short of funds").takeIf { u.short }).joinToString(" · "),
                leading = { DateBadge(u.due.dayOfMonth, u.due.format(MONTH_SHORT)) },
                onClick = onCalendar,
            ) {
                Text((if (tone == DueTone.INCOME) "+" else "") + Money.format(u.amountMinor, showPaise = false),
                    color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DateBadge(day: Int, month: String) {
    Column(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Hx.surface2),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("$day", fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
        Text(month.uppercase(), fontSize = 9.sp, color = Hx.text2, fontWeight = FontWeight.SemiBold, lineHeight = 10.sp)
    }
}

// ---------------------------------------------------------------------------------------------------------
// 5. Insights

/** Where an insight leads: a category, the bills list, or the transactions. */
private sealed interface InsightLink {
    data class ToCategory(val category: Category) : InsightLink
    data object ToBills : InsightLink
    data object ToTransactions : InsightLink
}

private fun linkOf(i: Insight): InsightLink =
    Category.entries.firstOrNull { i.title.startsWith(it.label + " ") }?.let { InsightLink.ToCategory(it) }
        ?: if (i.title.contains("regular payment", ignoreCase = true)) InsightLink.ToBills else InsightLink.ToTransactions

/** Insights as a snapping carousel: tag, title, detail, a way in, and Dismiss. */
@Composable
fun InsightsWidget(
    w: HomeWidgets, onOpenCategory: (Category) -> Unit, onOpenBills: () -> Unit, onSeeTransactions: () -> Unit,
    onDismiss: (Insight) -> Unit, edit: WidgetEdit?,
) {
    WidgetCard(if (w.insights.isEmpty()) t("Insights") else t("Insights · {n}", "n" to w.insights.size), edit = edit, contentPadding = 0.dp) {
        if (w.insights.isEmpty()) { Hint(t("No insights right now."), Modifier.padding(horizontal = 16.dp)); return@WidgetCard }
        val state = rememberLazyListState()
        LazyRow(
            state = state, flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(w.insights, key = { it.title }) { i ->
                val (tag, color) = when (i.tone) {
                    Tone.WARN -> t("Heads up") to Hx.warn
                    Tone.GOOD -> t("Good news") to Hx.pos
                    Tone.INFO -> t("Pattern") to Hx.accent
                }
                val link = linkOf(i)
                Column(
                    Modifier.fillParentMaxWidth(0.86f).animateItem().clip(RoundedCornerShape(14.dp)).background(Hx.surface2).padding(14.dp),
                ) {
                    Tag(tag, color)
                    Text(i.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp), maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(i.detail, fontSize = 13.sp, color = Hx.text2, modifier = Modifier.padding(top = 4.dp).height(54.dp), maxLines = 3,
                        overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (link) {
                            is InsightLink.ToCategory -> Pill(t("See transactions"), on = true) { onOpenCategory(link.category) }
                            InsightLink.ToBills -> Pill(t("See bills"), on = true, onClick = onOpenBills)
                            InsightLink.ToTransactions -> Pill(t("See transactions"), on = true, onClick = onSeeTransactions)
                        }
                        Pill(t("Dismiss")) { onDismiss(i) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// 6. Spend by category

/** A donut of the top seven categories (the rest as Others) with a legend; a slice or a legend line opens that category. */
@Composable
fun CategoriesWidget(w: HomeWidgets, onOpenCategory: (CategoryLook) -> Unit, onTrends: () -> Unit, edit: WidgetEdit?) {
    WidgetCard(t("Spend by category"), action = t("Trends ›"), onAction = onTrends, edit = edit) {
        if (w.categories.isEmpty()) { Hint(t("No spending in this period.")); return@WidgetCard }
        val top = w.categories.take(7)
        val rest = w.categories.drop(7).sumOf { it.amount }
        val others = Hx.palette.last()
        val slices = top.map { ChartSlice(t(it.look.name), it.amount, it.look.color) } + listOfNotNull(rest.takeIf { it > 0 }?.let { ChartSlice(t("Others"), it, others) })
        val total = slices.sumOf { it.value }.coerceAtLeast(1)
        val open: (Int) -> Unit = { i -> top.getOrNull(i)?.let { onOpenCategory(it.look) } ?: onTrends() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = slices, selected = null, onSelect = { i -> if (i != null) open(i) },
                centerLabel = t("Spent"), centerValue = { Money.compact(it) }, modifier = Modifier.size(132.dp), thickness = 18.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                slices.forEachIndexed { i, s ->
                    LegendItem(s.color, s.label, "${(s.value * 100 / total)}%", onClick = { open(i) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// 8. Recent

/** The last four transactions of the period. */
@Composable
fun RecentWidget(w: HomeWidgets, onOpenTransaction: (Long) -> Unit, onSeeAll: () -> Unit, edit: WidgetEdit?) {
    WidgetCard(t("Recent"), action = t("All ›"), onAction = onSeeAll, edit = edit, contentPadding = 0.dp) {
        if (w.recent.isEmpty()) { Hint(t("No transactions in this period."), Modifier.padding(horizontal = 16.dp)); return@WidgetCard }
        w.recent.forEach { tx: TransactionEntity -> TransactionRow(tx, onClick = { onOpenTransaction(tx.id) }, showDate = true) }
    }
}

// ---------------------------------------------------------------------------------------------------------
// 9. Budgets

/** Up to three budgets as rings, most used first. */
@Composable
fun BudgetsWidget(w: HomeWidgets, onOpenBudgets: () -> Unit, edit: WidgetEdit?) {
    WidgetCard(t("Budgets"), action = if (w.hasBudgets) t("Manage ›") else null, onAction = onOpenBudgets, edit = edit) {
        if (!w.hasBudgets) {
            Hint(t("Set monthly limits for the categories you want to watch."))
            Spacer(Modifier.height(10.dp))
            Pill(t("Set budgets"), on = true, onClick = onOpenBudgets)
            return@WidgetCard
        }
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpenBudgets), horizontalArrangement = Arrangement.SpaceEvenly) {
            w.budgets.forEach { b ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    ProgressRing(b.fraction.coerceIn(0f, 1f), usageColor(b.fraction, Hx.pos, Hx.warn, Hx.neg), 60.dp, stroke = 6.dp,
                        label = "${(b.fraction * 100).toInt().coerceAtMost(999)}%")
                    Text(t(b.look.name), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp))
                    Text(t("{spent} of {limit}", "spent" to Money.compact(b.spent), "limit" to Money.compact(b.limit)), fontSize = 11.sp, color = Hx.text2, maxLines = 1)
                }
            }
        }
    }
}
