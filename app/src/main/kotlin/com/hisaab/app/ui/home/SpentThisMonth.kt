package com.hisaab.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.AccountAvatar
import com.hisaab.app.ui.format.AmountPrivacy
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.Book
import com.hisaab.app.ui.ledger.ViewFilterStore
import com.hisaab.app.ui.theme.Hx
import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.AccountUsage
import com.hisaab.shared.db.CardNetwork
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.insight.MonthSpend
import com.hisaab.shared.insight.MonthSpendResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.abs

/** One account or card and what it spent this month. */
data class SpendRow(
    val id: Long, val name: String, val last4: String, val kind: AccountKind, val type: AccountType?, val network: CardNetwork?,
    val business: Boolean, val amount: Long,
)

/** [other] is spend on accounts that are not listed (hidden, merged or no account), so the rows and it add up to the total. */
data class SpentMonth(val result: MonthSpendResult, val rows: List<SpendRow>, val other: Long, val month: YearMonth)

/** This calendar month's spend, whatever period Home is set to; follows the Book filter only. */
@HiltViewModel
class SpentThisMonthViewModel @Inject constructor(
    transactions: TransactionDao,
    accounts: AccountDao,
    filters: ViewFilterStore,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val spent: StateFlow<SpentMonth?> = filters.filter.map { it.book }.distinctUntilChanged().flatMapLatest { book ->
        val today = LocalDate.now(Periods.zone)
        val from = today.withDayOfMonth(1).minusMonths(1).atStartOfDay(Periods.zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(Periods.zone).toInstant().toEpochMilli() - 1
        combine(transactions.observeBetween(from, to), accounts.observeWithActivity(0L)) { txs, accs ->
            val usage = accs.associate { it.id to it.usage }
            val inBook = txs.filter {
                when (book) {
                    Book.ALL -> true
                    Book.PERSONAL -> it.accountId == null || usage[it.accountId] != AccountUsage.BUSINESS
                    Book.BUSINESS -> it.accountId != null && usage[it.accountId] == AccountUsage.BUSINESS
                }
            }
            val r = MonthSpend.compute(inBook, today, Periods.zone)
            val byId = accs.associateBy { it.id }
            val rows = r.byAccount.mapNotNull { s ->
                val a = s.accountId?.let { byId[it] } ?: return@mapNotNull null
                SpendRow(a.id, a.nickname?.takeIf { it.isNotBlank() } ?: a.bankName, a.last4, a.kind, a.accountType, a.cardNetwork, a.usage == AccountUsage.BUSINESS, s.amount)
            }
            SpentMonth(r, rows, r.spent - rows.sumOf { it.amount }, YearMonth.from(today))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val Teal: Color @Composable get() = Hx.palette[2]

/**
 * Home card: this month's spend against the same days of last month, a cumulative line for both,
 * income / expenses / saved, and a folded per-account and per-card list.
 */
@Composable
fun SpentThisMonthWidget(
    onToggleHide: () -> Unit,
    onOpenAccount: ((Long) -> Unit)?,
    edit: WidgetEdit?,
    vm: SpentThisMonthViewModel = hiltViewModel(),
) {
    val data by vm.spent.collectAsState()
    WidgetCard(t("Spent this month"), edit = edit, hero = true) {
        val s = data
        if (s == null) {
            Text(t("Loading…"), color = Hx.text2, fontSize = 13.sp)
            return@WidgetCard
        }
        val r = s.result
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.hisaab.app.ui.components.AnimatedAmount(
                r.spent, Modifier.weight(1f, fill = false), style = TextStyle(fontSize = 30.sp),
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                if (AmountPrivacy.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                if (AmountPrivacy.hidden) t("Show") else t("Hide"), tint = Hx.text2,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onToggleHide).padding(6.dp),
            )
        }
        Text(
            t("Last month: {amount}", "amount" to Money.format(r.lastMonthSpent, showPaise = false)),
            Modifier.padding(top = 2.dp), fontSize = 13.sp, color = Hx.text2,
        )
        r.changePercent?.let { pct ->
            val less = pct <= 0
            val tone = if (less) Hx.pos else Hx.neg
            Text(
                if (less) t("{n}% less vs last month", "n" to abs(pct)) else t("{n}% more vs last month", "n" to pct),
                Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).background(tone.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tone,
            )
        }
        Spacer(Modifier.height(14.dp))
        SpendLines(r, s.month.lengthOfMonth())
        Spacer(Modifier.height(16.dp))
        Text(t("This month"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Hx.text2)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(t("Income"), r.income, Hx.pos, Modifier.weight(1f))
            Stat(t("Expenses"), r.spent, Hx.neg, Modifier.weight(1f))
            Stat(t("Saved"), r.saved, Teal, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        ByAccount(s, onOpenAccount)
    }
}

@Composable
private fun Stat(label: String, minor: Long, color: Color, modifier: Modifier) {
    Row(modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, fontSize = 11.sp, color = Hx.text2, maxLines = 1)
            Text(
                (if (minor < 0) "−" else "") + Money.compact(abs(minor)), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Cumulative spend, this month solid in the accent and last month dashed and muted, with the top and bottom of the scale. */
@Composable
private fun SpendLines(r: MonthSpendResult, days: Int) {
    val accent = Hx.accent
    val muted = Hx.text2.copy(alpha = 0.6f)
    val grid = Hx.border
    val top = maxOf(r.thisCum.maxOrNull() ?: 0L, r.lastCum.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(110.dp)) {
        Column(Modifier.fillMaxHeight().width(44.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(Money.compact(top), fontSize = 10.sp, color = Hx.text2, maxLines = 1)
            Text(Money.compact(0), fontSize = 10.sp, color = Hx.text2, maxLines = 1)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val pad = 4.dp.toPx()
            val h = size.height - 2 * pad
            fun pt(day: Int, v: Long) = Offset(
                if (days <= 1) 0f else size.width * day / (days - 1).toFloat(),
                pad + h * (1f - v.toFloat() / top),
            )
            fun smooth(values: List<Long>): Path {
                val p = Path()
                val pts = values.mapIndexed { i, v -> pt(i, v) }
                if (pts.isEmpty()) return p
                p.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]
                    val b = pts[i]
                    val mx = (a.x + b.x) / 2
                    p.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                }
                return p
            }
            drawLine(grid, Offset(0f, pad), Offset(size.width, pad), 1.dp.toPx())
            drawLine(grid, Offset(0f, size.height - pad), Offset(size.width, size.height - pad), 1.dp.toPx())
            drawPath(smooth(r.lastCum), muted, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            drawPath(smooth(r.thisCum), accent, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
        }
    }
    Row(Modifier.padding(top = 6.dp, start = 44.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(t("This month"), Hx.accent, dashed = false)
        LegendItem(t("Last month"), Hx.text2.copy(alpha = 0.6f), dashed = true)
    }
}

@Composable
private fun LegendItem(label: String, color: Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(16.dp).height(4.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = Hx.text2)
    }
}

/** Folded by default: spend per bank account and card with its share of the month's total. */
@Composable
private fun ByAccount(s: SpentMonth, onOpenAccount: ((Long) -> Unit)?) {
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(150), label = "spent-chevron")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t("By account & card"), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Hx.text2)
        Icon(
            Icons.Filled.KeyboardArrowDown, if (open) t("Collapse") else t("Expand"), tint = Hx.text2,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
        )
    }
    AnimatedVisibility(
        open,
        enter = expandVertically(tween(150)) + fadeIn(tween(100)),
        exit = shrinkVertically(tween(120)) + fadeOut(tween(80)),
    ) {
        Column {
            if (s.rows.isEmpty() && s.other == 0L) {
                Text(t("No spending this month."), fontSize = 13.sp, color = Hx.text2, modifier = Modifier.padding(vertical = 6.dp))
            }
            val total = s.result.spent.coerceAtLeast(1L)
            s.rows.forEach { row ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .then(if (onOpenAccount != null) Modifier.clickable { onOpenAccount(row.id) } else Modifier).padding(vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountAvatar(row.name, row.kind, row.type, size = 32.dp, business = row.business, network = row.network)
                        Spacer(Modifier.width(10.dp))
                        Text("${row.name} ••${row.last4}", Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Money.format(row.amount, showPaise = false), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    ShareBar((row.amount.toFloat() / total).coerceIn(0f, 1f), Modifier.padding(start = 42.dp, top = 6.dp))
                }
            }
            if (s.other > 0) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Other"), Modifier.weight(1f), fontSize = 14.sp, color = Hx.text2)
                    Text(Money.format(s.other, showPaise = false), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp).background(Hx.border))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Total"), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(Money.format(s.result.spent, showPaise = false), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ShareBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Hx.border.copy(alpha = 0.6f))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Hx.accent))
    }
}
