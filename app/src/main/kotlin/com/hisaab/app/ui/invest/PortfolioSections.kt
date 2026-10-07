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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.i18n.t
import com.hisaab.app.settings.WorthPoint
import com.hisaab.app.ui.charts.ChartSlice
import com.hisaab.app.ui.charts.DonutChart
import com.hisaab.app.ui.charts.Sparkline
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.CardTitle
import com.hisaab.app.ui.components.Delta
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.CollapsibleCard
import com.hisaab.app.ui.components.HeroCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.components.KpiRow
import com.hisaab.app.ui.components.LegendItem
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.components.Segmented
import com.hisaab.app.ui.components.SplitBar
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.ledger.AssetClass
import com.hisaab.app.ui.ledger.Book
import com.hisaab.app.ui.ledger.NetWorth
import com.hisaab.app.ui.ledger.PartKind
import com.hisaab.app.ui.ledger.WorthPart
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

/** "PRAN ••1234 · Tier I" for an NPS holding read from a CRA SMS, email or statement; null for anything else. */
internal fun npsLabel(h: HoldingEntity): String? {
    if (h.kind != com.hisaab.parser.model.HoldingKind.NPS || !h.identifier.startsWith("NPS:")) return null
    val parts = h.identifier.split(':')
    val last4 = parts.getOrNull(1)?.takeIf { it.length == 4 } ?: return null
    return t("PRAN ••{last4} · {tier}", "last4" to last4, "tier" to t(if (parts.getOrNull(2) == "T2") "Tier II" else "Tier I"))
}

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
                    subtitle = npsLabel(h) ?: h.units?.let { t("{units} units · {kind}", "units" to "%,.3f".format(it), "kind" to t(h.kind.label)) } ?: t(h.kind.label),
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
                    key = "a-${a.id}", title = a.displayName, subtitle = listOfNotNull(type?.label?.let { t(it) } ?: t("Bank account"), a.last4.takeIf { it.isNotBlank() }?.let { "••$it" }).joinToString(" · "),
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
                slices = classes.map { ChartSlice(t(it.label), nw.byClass[it] ?: 0, it.color) },
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
    HeroCard(modifier) {
        Text(t("PORTFOLIO VALUE"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = Color.White.copy(alpha = 0.8f))
        com.hisaab.app.ui.components.AnimatedAmount(
            m.valueMinor, Modifier.padding(top = 4.dp), style = androidx.compose.ui.text.TextStyle(fontSize = 32.sp),
            fontWeight = FontWeight.Bold, format = { Money.format(it, showPaise = false) },
        )
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.investedMinor > 0) {
                Delta(t("{arrow} {amount} overall", "arrow" to if (m.gainMinor >= 0) "▲" else "▼", "amount" to Money.compact(abs(m.gainMinor))), good = m.gainMinor >= 0)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (m.depositsMinor > 0) t("Holdings {holdings} · Deposits {deposits}", "holdings" to Money.compact(m.holdingsValueMinor), "deposits" to Money.compact(m.depositsMinor)) else t("Across your holdings"),
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1,
            )
        }
        Spacer(Modifier.height(16.dp))
        val gainColor = if (m.gainMinor >= 0) Color(0xFF9DF2C9) else Color(0xFFFFB3AB)
        Row(Modifier.fillMaxWidth()) {
            listOf(
                Triple(t("Invested"), if (m.investedMinor > 0) Money.format(m.investedMinor, showPaise = false) else "—", Color.White),
                Triple(t("Gain"), if (m.investedMinor > 0) signed(m.gainMinor) else "—", if (m.investedMinor > 0) gainColor else Color.White),
                Triple(t("Return"), m.returnPct?.let { pct(it) } ?: "—", if (m.investedMinor > 0) gainColor else Color.White),
            ).forEach { (l, v, c) ->
                Column(Modifier.weight(1f)) {
                    Text(l, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                    Text(v, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (m.investedMinor > 0 && (m.depositsMinor > 0 || m.lines.any { it.holding != null && it.investedMinor == null })) {
            Text(t("Gain and return cover holdings with a purchase cost."), fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 10.dp))
        }
    }
}

// ---- 2. Asset allocation ----

@Composable
internal fun AllocationCard(
    m: PortfolioModel,
    filter: AssetClass?,
    onFilter: (AssetClass?) -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    onAnimated: () -> Unit = {},
) {
    val total = m.slices.sumOf { it.value }.coerceAtLeast(1)
    if (animate && m.slices.isNotEmpty()) LaunchedEffect(Unit) { onAnimated() }
    HCard(modifier, title = t("Asset allocation")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = m.slices,
                selected = filter?.let { m.classes.indexOf(it) }?.takeIf { it >= 0 },
                onSelect = { i -> onFilter(i?.let { m.classes.getOrNull(it) }) },
                centerLabel = if (m.classes.size == 1) t("1 class") else t("{n} classes", "n" to m.classes.size),
                centerValue = { Money.compact(it) },
                modifier = Modifier.size(140.dp),
                thickness = 18.dp,
                animate = animate,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                m.classes.forEachIndexed { i, c ->
                    val v = m.slices[i].value
                    val p = v * 100.0 / total
                    LegendItem(
                        c.color, t(c.label), if (p < 1.0 && v > 0) "<1%" else "%.0f%%".format(p),
                        selected = filter == c, onClick = { onFilter(if (filter == c) null else c) },
                    )
                }
            }
        }
        Text(
            filter?.let { t("Showing {class} in holdings · tap again to clear", "class" to t(it.label)) } ?: t("Tap a slice to filter holdings"),
            fontSize = 11.sp, color = Hx.text2, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ---- 3. Net worth ----

private val RANGES = listOf("1M" to 30L, "6M" to 182L, "1Y" to 365L, "3Y" to 1095L, "All" to null)

/**
 * The history inside a range. The last point before the range starts is carried to its first day, so every
 * range has a start figure even when points are a month apart.
 */
internal fun rangePoints(history: List<WorthPoint>, days: Long?, today: LocalDate = LocalDate.now()): List<WorthPoint> {
    val start = days?.let { today.minusDays(it) } ?: return history
    val inside = history.filter { !it.day.isBefore(start) }
    val before = history.lastOrNull { it.day.isBefore(start) }?.copy(day = start)
    return listOfNotNull(before) + inside
}

@Composable
internal fun NetWorthCard(nw: NetWorth, range: Int, onRange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val days = RANGES.getOrNull(range)?.second
    val points: List<WorthPoint> = remember(nw.history, days) { rangePoints(nw.history, days) }
    val change = if (points.size >= 2) points.last().netMinor - points.first().netMinor else null
    HCard(modifier, title = t("Net worth") + if (nw.book != Book.ALL) " · ${t(nw.book.label)}" else "") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Money.format(nw.netMinor, showPaise = false), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
            if (change != null) {
                Spacer(Modifier.width(8.dp))
                Delta("${if (change >= 0) "▲" else "▼"} ${Money.compact(abs(change))} · ${t(RANGES[range].first)}", good = change >= 0)
            }
        }
        Spacer(Modifier.height(12.dp))
        Segmented(RANGES.map { t(it.first) }, range, onRange)
        Spacer(Modifier.height(12.dp))
        when {
            nw.history.size < 2 -> Note(t("Your net worth chart builds a point each day you open DhanKosh, and earlier months are estimated from transactions."))
            points.size < 2 -> Note(t("Not enough history for this range yet. Try a longer one."))
            else -> {
                val first = points.first()
                val last = points.last()
                val up = last.netMinor >= first.netMinor
                Sparkline(points.map { it.netMinor / 100f }, height = 96.dp, color = if (up) Hx.pos else Hx.neg)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${first.day.format(shortDateFmt)} → ${last.day.format(shortDateFmt)}", fontSize = 11.sp, color = Hx.text2, modifier = Modifier.weight(1f))
                    Text(
                        "${Money.compact(first.netMinor)} → ${Money.compact(last.netMinor)} · ${signed(last.netMinor - first.netMinor)}",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (up) Hx.pos else Hx.neg, maxLines = 1,
                    )
                }
                nw.estimatedBefore?.takeIf { first.day.isBefore(it) }?.let { until ->
                    Text(
                        t("Estimated from transactions until {date}; recorded after.", "date" to until.format(shortDateFmt)),
                        fontSize = 11.sp, color = Hx.text2, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        WorthBreakdown(nw)
        Spacer(Modifier.height(10.dp))
        KpiRow(
            Triple(t("Assets"), Money.compact(nw.assetsMinor), Hx.pos),
            Triple(t("Liabilities"), Money.compact(nw.liabilitiesMinor), if (nw.liabilitiesMinor > 0) Hx.neg else null),
            Triple(t("Net"), Money.compact(nw.netMinor), null),
        )
        CardLimits(nw)
    }
}

// Segment colours: distinct per legend entry, lighter on the hero gradient.
private val heroAssetColors = listOf(
    Color(0xFF9DF2C9), Color(0xFFFFE08A), Color(0xFFA7D8FF), Color(0xFFFFC3E1), Color(0xFFC9B8FF), Color(0xFF8FE3E8),
)
private val heroLiabilityColors = listOf(Color(0xFFFFB3AB), Color(0xFFFF8A80), Color(0xFFFFD6CF), Color(0xFFFF6F61))
private val assetColorIndex = listOf(0, 2, 4, 5, 6, 3, 1)
private val liabilityAlphas = listOf(1f, 0.7f, 0.5f, 0.35f)

/** Legend entries with their colours: assets (top six, then Others), then liabilities. */
@Composable
private fun worthSegments(nw: NetWorth, onHero: Boolean): List<Pair<WorthPart, Color>> {
    val neg = Hx.neg
    val assets = nw.assetLegend().mapIndexed { i, p ->
        p to when {
            p.kind == PartKind.OTHERS -> if (onHero) Color.White.copy(alpha = 0.55f) else Hx.palette[7]
            onHero -> heroAssetColors[i % heroAssetColors.size]
            else -> Hx.palette[assetColorIndex[i % assetColorIndex.size]]
        }
    }
    val liabilities = nw.liabilityLegend().mapIndexed { i, p ->
        p to if (onHero) heroLiabilityColors[i % heroLiabilityColors.size] else neg.copy(alpha = liabilityAlphas[i % liabilityAlphas.size])
    }
    return assets + liabilities
}

/**
 * Assets and liabilities as one bar split by account and asset (each bank account, deposits, each holdings class,
 * then loans and card dues), with a pin per segment below it (dot, name, amount). Used by Portfolio and Home.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorthBreakdown(nw: NetWorth, modifier: Modifier = Modifier, onHero: Boolean = false) {
    val segs = worthSegments(nw, onHero)
    if (segs.isEmpty()) return
    val gross = segs.sumOf { it.first.amountMinor }.coerceAtLeast(1)
    val dim = if (onHero) Color.White.copy(alpha = 0.75f) else Hx.text2
    val strong = if (onHero) Color.White else MaterialTheme.colorScheme.onSurface
    val pinBg = if (onHero) Color.White.copy(alpha = 0.12f) else Hx.surface2
    val turn by androidx.compose.animation.core.animateFloatAsState(if (breakdownOpen) 180f else 0f, androidx.compose.animation.core.tween(150), label = "breakdown-chevron")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            segs.forEach { (p, c) ->
                val f = p.amountMinor.toFloat() / gross
                if (f > 0f) Box(Modifier.weight(f.coerceAtLeast(0.004f)).height(10.dp).background(c))
            }
        }
        // Pins are folded by default; the toggle under the bar shows them. Remembered for the session.
        Row(
            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable { breakdownOpen = !breakdownOpen }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("Breakdown"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = dim)
            Icon(
                Icons.Filled.KeyboardArrowDown, if (breakdownOpen) t("Collapse") else t("Expand"), tint = dim,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = turn },
            )
        }
        AnimatedVisibility(
            breakdownOpen,
            enter = fadeIn(androidx.compose.animation.core.tween(150)) + expandVertically(androidx.compose.animation.core.tween(150)),
            exit = fadeOut(androidx.compose.animation.core.tween(150)) + shrinkVertically(androidx.compose.animation.core.tween(150)),
        ) {
        // Pins: one rounded chip per segment, wrapping under the bar.
        FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            segs.forEach { (p, c) ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(pinBg)
                        .border(1.dp, if (onHero) Color.White.copy(alpha = 0.18f) else Hx.border.copy(alpha = 0.7f), RoundedCornerShape(50))
                        .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.width(6.dp))
                    Text(t(p.label), fontSize = 12.sp, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        (if (p.liability) "−" else "") + Money.compact(p.amountMinor), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = strong, maxLines = 1,
                    )
                }
            }
        }
        }
    }
}

/** Whether the net-worth pins are shown; lives for the session. */
private var breakdownOpen by androidx.compose.runtime.mutableStateOf(false)

/**
 * Every credit card's available limit (and total limit when a statement gave it), with Billed (the latest statement's
 * total due) and Unbilled (spends since that statement) when known. Not part of net worth.
 */
@Composable
fun CardLimits(nw: NetWorth, modifier: Modifier = Modifier, onHero: Boolean = false) {
    if (nw.cards.isEmpty()) return
    val dim = if (onHero) Color.White.copy(alpha = 0.75f) else Hx.text2
    val strong = if (onHero) Color.White else MaterialTheme.colorScheme.onSurface
    val warn = Hx.warn
    val accent = Hx.accent
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "cards-chevron")
    Column(modifier.fillMaxWidth().padding(top = 14.dp)) {
        HorizontalDivider(color = if (onHero) Color.White.copy(alpha = 0.2f) else Hx.border.copy(alpha = 0.6f))
        // Folded by default: a tap on the header shows each card and its limit.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                t("Credit cards · {n}", "n" to nw.cards.size), Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp, color = dim,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown, if (open) t("Collapse") else t("Expand"), tint = dim,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
            )
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column {
        nw.cards.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.name + (c.last4.takeIf { it.isNotBlank() }?.let { " ••$it" } ?: ""), fontSize = 13.sp, color = strong,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        c.availableMinor?.let { Money.format(it, showPaise = false) } ?: t("Limit unknown"),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = strong, maxLines = 1,
                    )
                    c.limitMinor?.let { lim -> Text(t("of {amount}", "amount" to Money.format(lim, showPaise = false)), fontSize = 11.sp, color = dim, maxLines = 1) }
                }
            }
            val dues = listOfNotNull(
                c.billedMinor?.let { t("Billed {amount}", "amount" to Money.format(it, showPaise = false)) },
                c.unbilledMinor?.let { t("Unbilled {amount}", "amount" to Money.format(it, showPaise = false)) },
            )
            if (dues.isNotEmpty()) {
                Text(dues.joinToString("  ·  "), fontSize = 11.sp, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 3.dp))
            }
            val avail = c.availableMinor
            val lim = c.limitMinor
            if (avail != null && lim != null && lim > 0) {
                val used = ((lim - avail).toFloat() / lim).coerceIn(0f, 1f)
                val tone = when { onHero -> Color(0xFFFFB3AB); used >= 0.8f -> warn; else -> accent }
                SplitBar(listOf(used to tone), Modifier.padding(bottom = 4.dp), height = 4.dp)
            }
        }
        }
        }
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
                Pill(t("Showing {class}", "class" to filter?.label?.let { t(it) }.orEmpty()), on = true, leading = Icons.Filled.Close, onClick = onClearFilter)
            }
        }
    }
    if (shown.isEmpty() && filter != null) {
        item(key = "holdings-none") {
            HCard(Modifier.animateItem(), title = t(filter.label)) { Note(t("Nothing here is held as individual holdings.")) }
        }
    }
    shown.forEach { cls ->
        val lines = groups[cls].orEmpty()
        item(key = "group-${cls.name}") {
            CollapsibleCard(
                "${t(cls.label)} · ${lines.size}", Modifier.animateItem(),
                trailing = Money.format(lines.sumOf { it.valueMinor ?: 0 }, showPaise = false),
                initiallyExpanded = false,
            ) {
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
        subtitle = if (line.holding != null && line.investedMinor != null) {
            listOfNotNull(npsLabel(line.holding), t("Invested {amount}", "amount" to Money.format(line.investedMinor, showPaise = false))).joinToString(" · ")
        } else line.subtitle,
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
internal fun MaturityCard(accounts: List<AccountWithActivity>, onOpen: () -> Unit, onOpenLoan: (Long) -> Unit = {}, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    Column(modifier) {
    PortfolioLoansCard(accounts, onOpenLoan, Modifier.padding(bottom = CardGap))
    val due = remember(accounts, today) {
        val end = today.plusDays(365)
        accounts.filter { it.accountType in setOf(AccountType.FD, AccountType.RD, AccountType.PPF) }
            .mapNotNull { a -> a.maturityDay?.let { LocalDate.ofEpochDay(it) }?.takeIf { !it.isBefore(today) && !it.isAfter(end) }?.let { a to it } }
            .sortedBy { it.second }
    }
    CollapsibleCard(t("Maturity calendar · 12 months"), Modifier, trailing = if (due.isEmpty()) null else "${due.size}", initiallyExpanded = false) {
        if (due.isEmpty()) {
            Note(t("No deposits mature in the next 12 months."))
            return@CollapsibleCard
        }
        due.forEachIndexed { i, (a, date) ->
            if (i > 0) HorizontalDivider(color = Hx.border.copy(alpha = 0.6f))
            val days = ChronoUnit.DAYS.between(today, date)
            val months = ChronoUnit.MONTHS.between(today, date)
            val soon = days <= 30
            HRow(
                title = a.displayName,
                subtitle = listOfNotNull(date.format(dateFmt), (a.maturityAction?.label ?: a.accountType?.label)?.let { t(it) }).joinToString(" · "),
                onClick = onOpen,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(a.currentBalanceMinor?.let { Money.format(it, showPaise = false) } ?: "—", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when { days == 0L -> t("Today"); days < 31 -> t("in {n} days", "n" to days); months == 1L -> t("in 1 month"); else -> t("in {n} months", "n" to months.coerceAtLeast(2)) },
                        fontSize = 12.sp, color = if (soon) Hx.warn else Hx.text2,
                    )
                }
            }
            SplitBar(listOf((months.coerceIn(0, 12) / 12f).coerceAtLeast(0.02f) to if (soon) Hx.warn else Hx.accent), Modifier.padding(bottom = 10.dp), height = 5.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Bar shows months remaining out of 12."), Modifier.weight(1f), fontSize = 11.sp, color = Hx.text2, style = MaterialTheme.typography.bodySmall)
            Pill(t("Deposits ›"), onClick = onOpen)
        }
    }
    }
}
