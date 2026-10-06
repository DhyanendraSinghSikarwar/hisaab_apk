package com.hisaab.app.ui.invest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.settings.WorthPoint
import com.hisaab.app.ui.charts.ChartSlice
import com.hisaab.app.ui.charts.DonutChart
import com.hisaab.app.ui.charts.Sparkline
import com.hisaab.app.ui.components.CardTitle
import com.hisaab.app.ui.components.Delta
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.components.KpiRow
import com.hisaab.app.ui.components.LegendDot
import com.hisaab.app.ui.components.LegendItem
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.components.Segmented
import com.hisaab.app.ui.components.SplitBar
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.ledger.AssetClass
import com.hisaab.app.ui.ledger.NetWorth
import com.hisaab.app.ui.theme.Hx
import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.HoldingEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One colour per asset class, from the shared chart palette. */
val AssetClass.color: Color
    get() = Hx.palette[
        when (this) {
            AssetClass.EQUITY -> 0; AssetClass.RETIREMENT -> 2; AssetClass.DEBT -> 4
            AssetClass.GOLD -> 5; AssetClass.CASH -> 6; AssetClass.OTHER -> 7
        },
    ]

/** A row in the holdings section: a holding (editable) or an account that counts towards a class. */
internal data class PortfolioLine(
    val key: String,
    val title: String,
    val subtitle: String?,
    val valueMinor: Long?,
    val investedMinor: Long?,
    val previousMinor: Long?,
    val cls: AssetClass,
    val icon: ImageVector,
    val holding: HoldingEntity? = null,
    /** The Accounts tab to open for an account line. */
    val accountTab: Int? = null,
)

/** Everything the Portfolio sections draw, worked out once per change in net worth. */
internal class PortfolioModel(
    val lines: List<PortfolioLine>,
    val holdingsValueMinor: Long,
    val depositsMinor: Long,
    val investedMinor: Long,
    val gainMinor: Long,
    val classes: List<AssetClass>,
    val slices: List<ChartSlice>,
) {
    /** Holdings plus deposits (FD, RD, PPF). Bank balances are left out. */
    val valueMinor: Long get() = holdingsValueMinor + depositsMinor
    val isEmpty: Boolean get() = lines.none { it.cls != AssetClass.CASH }
    val returnPct: Double? get() = if (investedMinor > 0) gainMinor * 100.0 / investedMinor else null

    companion object {
        fun of(nw: NetWorth): PortfolioModel {
            val holdingLines = nw.holdings.sortedByDescending { it.valueMinor ?: 0 }.map { h ->
                PortfolioLine(
                    key = "h-${h.id}", title = h.name,
                    subtitle = h.units?.let { "${"%,.3f".format(it)} units · ${h.kind.label}" } ?: h.kind.label,
                    valueMinor = h.valueMinor, investedMinor = h.investedMinor, previousMinor = h.previousValueMinor,
                    cls = AssetClass.of(h.kind), icon = h.kind.icon, holding = h,
                )
            }
            val accountLines = nw.accounts.mapNotNull { a ->
                val type = a.accountType
                val bal = a.currentBalanceMinor
                val cls = when {
                    type == AccountType.FD || type == AccountType.RD -> AssetClass.DEBT
                    type == AccountType.PPF -> AssetClass.RETIREMENT
                    a.kind == AccountKind.ACCOUNT && type?.liquid != false && (bal ?: 0) > 0 -> AssetClass.CASH
                    else -> return@mapNotNull null
                }
                PortfolioLine(
                    key = "a-${a.id}", title = a.displayName, subtitle = listOfNotNull(type?.label ?: "Bank account", a.last4.takeIf { it.isNotBlank() }?.let { "••$it" }).joinToString(" · "),
                    valueMinor = bal, investedMinor = null, previousMinor = null, cls = cls,
                    icon = when (cls) { AssetClass.DEBT -> Icons.Filled.Lock; AssetClass.RETIREMENT -> Icons.Filled.Savings; else -> Icons.Filled.AccountBalance },
                    accountTab = if (cls == AssetClass.CASH) 0 else 2,
                )
            }.sortedByDescending { it.valueMinor ?: 0 }
            val deposits = accountLines.filter { it.cls != AssetClass.CASH }.sumOf { it.valueMinor ?: 0 }
            val withCost = nw.holdings.filter { it.investedMinor != null && it.valueMinor != null }
            val invested = withCost.sumOf { it.investedMinor!! }
            val classes = AssetClass.entries.filter { (nw.byClass[it] ?: 0) > 0 }
            return PortfolioModel(
                lines = holdingLines + accountLines,
                holdingsValueMinor = nw.holdings.sumOf { it.valueMinor ?: 0 },
                depositsMinor = deposits,
                investedMinor = invested,
                gainMinor = withCost.sumOf { it.valueMinor!! } - invested,
                classes = classes,
                slices = classes.map { ChartSlice(it.label, nw.byClass[it] ?: 0, it.color) },
            )
        }
    }
}

internal val AccountWithActivity.displayName: String
    get() = nickname?.takeIf { it.isNotBlank() } ?: bankName

private fun signed(minor: Long) = (if (minor >= 0) "+" else "−") + Money.format(abs(minor), showPaise = false)
private fun pct(p: Double) = (if (p >= 0) "+" else "−") + "%.1f%%".format(abs(p))
private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val shortDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yy")

// ---- 1. Portfolio value ----

@Composable
internal fun ValueCard(m: PortfolioModel, modifier: Modifier = Modifier) {
    HCard(modifier, title = "Portfolio value") {
        Text(Money.format(m.valueMinor, showPaise = false), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.investedMinor > 0) {
                Delta("${if (m.gainMinor >= 0) "▲" else "▼"} ${Money.compact(abs(m.gainMinor))} overall", good = m.gainMinor >= 0)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (m.depositsMinor > 0) "Holdings ${Money.compact(m.holdingsValueMinor)} · Deposits ${Money.compact(m.depositsMinor)}" else "Across your holdings",
                fontSize = 12.sp, color = Hx.text2, maxLines = 1,
            )
        }
        Spacer(Modifier.height(14.dp))
        val gainColor = if (m.gainMinor >= 0) Hx.pos else Hx.neg
        KpiRow(
            Triple("Invested", if (m.investedMinor > 0) Money.format(m.investedMinor, showPaise = false) else "—", null),
            Triple("Gain", if (m.investedMinor > 0) signed(m.gainMinor) else "—", if (m.investedMinor > 0) gainColor else null),
            Triple("Return", m.returnPct?.let { pct(it) } ?: "—", if (m.investedMinor > 0) gainColor else null),
        )
        if (m.investedMinor > 0 && (m.depositsMinor > 0 || m.lines.any { it.holding != null && it.investedMinor == null })) {
            Text("Gain and return cover holdings with a purchase cost.", fontSize = 11.sp, color = Hx.text2, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

// ---- 2. Asset allocation ----

@Composable
internal fun AllocationCard(m: PortfolioModel, filter: AssetClass?, onFilter: (AssetClass?) -> Unit, modifier: Modifier = Modifier) {
    val total = m.slices.sumOf { it.value }.coerceAtLeast(1)
    HCard(modifier, title = "Asset allocation") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = m.slices,
                selected = filter?.let { m.classes.indexOf(it) }?.takeIf { it >= 0 },
                onSelect = { i -> onFilter(i?.let { m.classes.getOrNull(it) }) },
                centerLabel = if (m.classes.size == 1) "1 class" else "${m.classes.size} classes",
                centerValue = { Money.compact(it) },
                modifier = Modifier.size(140.dp),
                thickness = 18.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                m.classes.forEachIndexed { i, c ->
                    val v = m.slices[i].value
                    val p = v * 100.0 / total
                    LegendItem(
                        c.color, c.label, if (p < 1.0 && v > 0) "<1%" else "%.0f%%".format(p),
                        selected = filter == c, onClick = { onFilter(if (filter == c) null else c) },
                    )
                }
            }
        }
        Text(
            filter?.let { "Showing ${it.label} in holdings · tap again to clear" } ?: "Tap a slice to filter holdings",
            fontSize = 11.sp, color = Hx.text2, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ---- 3. Net worth ----

private val RANGES = listOf("1M" to 30L, "6M" to 182L, "1Y" to 365L, "3Y" to 1095L, "All" to null)

@Composable
internal fun NetWorthCard(nw: NetWorth, range: Int, onRange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val days = RANGES.getOrNull(range)?.second
    val points: List<WorthPoint> = remember(nw.history, days) {
        val start = days?.let { LocalDate.now().minusDays(it) }
        nw.history.filter { start == null || !it.day.isBefore(start) }
    }
    HCard(modifier, title = "Net worth") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Money.format(nw.netMinor, showPaise = false), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
            if (points.size >= 2) {
                val change = points.last().netMinor - points.first().netMinor
                Spacer(Modifier.width(8.dp))
                Delta("${if (change >= 0) "▲" else "▼"} ${Money.compact(abs(change))}", good = change >= 0)
            }
        }
        Spacer(Modifier.height(12.dp))
        Segmented(RANGES.map { it.first }, range, onRange)
        Spacer(Modifier.height(12.dp))
        when {
            nw.history.size < 2 -> Note("Your net worth chart builds a point each day you open Hisaab.")
            points.size < 2 -> Note("Not enough history for this range yet. Try a longer one.")
            else -> {
                Sparkline(points.map { it.netMinor / 100f }, height = 96.dp, color = if (points.last().netMinor >= points.first().netMinor) Hx.pos else Hx.neg)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(points.first().day.format(shortDateFmt), fontSize = 11.sp, color = Hx.text2, modifier = Modifier.weight(1f))
                    Text(points.last().day.format(shortDateFmt), fontSize = 11.sp, color = Hx.text2)
                }
            }
        }
        val gross = (nw.assetsMinor + nw.liabilitiesMinor).coerceAtLeast(1)
        Spacer(Modifier.height(14.dp))
        SplitBar(listOf(nw.assetsMinor.toFloat() / gross to Hx.pos, nw.liabilitiesMinor.toFloat() / gross to Hx.neg), height = 6.dp)
        Spacer(Modifier.height(10.dp))
        KpiRow(
            Triple("Assets", Money.compact(nw.assetsMinor), Hx.pos),
            Triple("Liabilities", Money.compact(nw.liabilitiesMinor), if (nw.liabilitiesMinor > 0) Hx.neg else null),
            Triple("Net", Money.compact(nw.netMinor), null),
        )
    }
}

@Composable
private fun Note(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Hx.surface2).padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 13.sp, color = Hx.text2) }
}

// ---- 4. Holdings ----

internal fun LazyListScope.holdingsSection(
    m: PortfolioModel,
    filter: AssetClass?,
    onClearFilter: () -> Unit,
    onEdit: (HoldingEntity) -> Unit,
    onOpenAccounts: (Int) -> Unit,
) {
    if (m.lines.isEmpty()) return
    val groups = m.lines.groupBy { it.cls }
    val shown = AssetClass.entries.filter { (filter == null || it == filter) && groups[it].orEmpty().isNotEmpty() }
    item(key = "holdings-filter") {
        AnimatedVisibility(filter != null, Modifier.animateItem(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("Showing ${filter?.label.orEmpty()}", on = true, leading = Icons.Filled.Close, onClick = onClearFilter)
            }
        }
    }
    if (shown.isEmpty() && filter != null) {
        item(key = "holdings-none") {
            HCard(Modifier.animateItem(), title = filter.label) { Note("Nothing here is held as individual holdings.") }
        }
    }
    shown.forEach { cls ->
        val lines = groups[cls].orEmpty()
        item(key = "group-${cls.name}") {
            HCard(Modifier.animateItem()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(cls.color)
                    Spacer(Modifier.width(8.dp))
                    CardTitle(cls.label, Modifier.weight(1f))
                    Text(Money.format(lines.sumOf { it.valueMinor ?: 0 }, showPaise = false), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                lines.forEachIndexed { i, line ->
                    if (i > 0) HorizontalDivider(color = Hx.border.copy(alpha = 0.6f))
                    LineRow(line, onClick = {
                        line.holding?.let(onEdit) ?: line.accountTab?.let(onOpenAccounts)
                    })
                }
            }
        }
    }
}

@Composable
private fun LineRow(line: PortfolioLine, onClick: () -> Unit) {
    val gain = if (line.investedMinor != null && line.investedMinor > 0 && line.valueMinor != null) line.valueMinor - line.investedMinor else null
    val tone = if ((gain ?: 0) >= 0) Hx.pos else Hx.neg
    HRow(
        title = line.title, onClick = onClick,
        subtitle = if (line.holding != null && line.investedMinor != null) "Invested ${Money.format(line.investedMinor, showPaise = false)}" else line.subtitle,
        leading = {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(line.cls.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(line.icon, null, tint = line.cls.color, modifier = Modifier.size(18.dp)) }
        },
    ) {
        val trend = listOfNotNull(line.investedMinor, line.previousMinor, line.valueMinor).map { it / 100f }
        if (trend.size >= 2) {
            Sparkline(trend, Modifier.width(44.dp), color = tone, fill = false, height = 22.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(line.valueMinor?.let { Money.format(it, showPaise = false) } ?: "—", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (gain != null) {
                Text(pct(gain * 100.0 / line.investedMinor!!), fontSize = 12.sp, color = tone, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ---- 5. Maturity calendar ----

@Composable
internal fun MaturityCard(accounts: List<AccountWithActivity>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val due = remember(accounts, today) {
        val end = today.plusDays(365)
        accounts.filter { it.accountType in setOf(AccountType.FD, AccountType.RD, AccountType.PPF) }
            .mapNotNull { a -> a.maturityDay?.let { LocalDate.ofEpochDay(it) }?.takeIf { !it.isBefore(today) && !it.isAfter(end) }?.let { a to it } }
            .sortedBy { it.second }
    }
    HCard(modifier, title = "Maturity calendar · 12 months", action = "Deposits", onAction = onOpen) {
        if (due.isEmpty()) {
            Note("No deposits mature in the next 12 months.")
            return@HCard
        }
        due.forEachIndexed { i, (a, date) ->
            if (i > 0) HorizontalDivider(color = Hx.border.copy(alpha = 0.6f))
            val days = ChronoUnit.DAYS.between(today, date)
            val months = ChronoUnit.MONTHS.between(today, date)
            val soon = days <= 30
            HRow(
                title = a.displayName,
                subtitle = listOfNotNull(date.format(dateFmt), a.maturityAction?.label ?: a.accountType?.label).joinToString(" · "),
                onClick = onOpen,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(a.currentBalanceMinor?.let { Money.format(it, showPaise = false) } ?: "—", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when { days == 0L -> "Today"; days < 31 -> "in $days days"; months == 1L -> "in 1 month"; else -> "in ${months.coerceAtLeast(2)} months" },
                        fontSize = 12.sp, color = if (soon) Hx.warn else Hx.text2,
                    )
                }
            }
            SplitBar(listOf((months.coerceIn(0, 12) / 12f).coerceAtLeast(0.02f) to if (soon) Hx.warn else Hx.accent), Modifier.padding(bottom = 10.dp), height = 5.dp)
        }
        Text("Bar shows months remaining out of 12.", fontSize = 11.sp, color = Hx.text2, style = MaterialTheme.typography.bodySmall)
    }
}
