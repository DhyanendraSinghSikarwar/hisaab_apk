package com.hisaab.app.ui.transactions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.BrandMark
import com.hisaab.app.ui.components.Brands
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.SplitBar
import com.hisaab.app.ui.components.TransactionAvatar
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.Hx
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.TransactionEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM")

/** "+₹1,200" in green, "−₹450" in the body colour, "₹0" muted. */
@Composable
internal fun NetText(net: Long, modifier: Modifier = Modifier, fontSize: Int = 13) {
    val (text, color) = when {
        net > 0 -> "+" + Money.format(net, showPaise = false) to Hx.pos
        net < 0 -> "−" + Money.format(-net, showPaise = false) to MaterialTheme.colorScheme.onSurface
        else -> Money.format(0, showPaise = false) to Hx.text2
    }
    Text(text, modifier, color = color, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

/** The search box: a rounded tray with an icon, a match count and a clear button. */
@Composable
internal fun SearchBox(query: String, matches: Int, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(shape).background(Hx.surface2)
            .border(1.dp, if (focused) Hx.accent.copy(alpha = 0.6f) else Hx.border, shape).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = if (focused) Hx.accent else Hx.text2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(t("Search merchant, amount, note…"), color = Hx.text2, fontSize = 14.sp, maxLines = 1)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(Hx.accent), interactionSource = interaction,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().testTag("search"),
            )
        }
        if (query.isNotEmpty()) {
            Text(
                "$matches", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Hx.accent,
                modifier = Modifier.clip(CircleShape).background(Hx.accentSoft).padding(horizontal = 7.dp, vertical = 1.dp),
            )
            IconButton(onClick = { onChange(""); focus.clearFocus() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Clear, t("Clear search"), tint = Hx.text2, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** A compact filter chip, optionally led by a logo or icon. */
@Composable
private fun FilterChip(text: String, on: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(9.dp)
    Row(
        Modifier.height(30.dp).clip(shape).background(if (on) Hx.accentSoft else Hx.surface2)
            .border(1.dp, if (on) Hx.accent else Hx.border, shape).clickable(onClick = onClick)
            .padding(start = if (leading != null) 6.dp else 11.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(6.dp)) }
        Text(
            text, color = if (on) Hx.accent else MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A dropdown chip: "Label ▾", or the current choice in accent style, opening a small menu of [options]. */
@Composable
private fun DropChip(
    label: String, value: String?, options: List<DropOption>, onPick: (Int) -> Unit, modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip((value ?: label) + " ▾", value != null, { open = true }, leading)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(
                    text = { Text(o.text, fontWeight = if (o.chosen) FontWeight.SemiBold else FontWeight.Normal, color = if (o.chosen) Hx.accent else MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = o.leading,
                    trailingIcon = if (o.chosen) ({ Icon(Icons.Filled.Check, null, tint = Hx.accent, modifier = Modifier.size(16.dp)) }) else null,
                    onClick = { open = false; onPick(i) },
                )
            }
        }
    }
}

private class DropOption(val text: String, val chosen: Boolean, val leading: (@Composable () -> Unit)? = null)

/**
 * The filters under the search box: one line of three dropdown chips (type, bank or card issuer with logos, source).
 * Chips combine (AND); a chip shows its choice when set, and a clear button appears when any is.
 */
@Composable
internal fun TxFilters(
    kind: TxKind, bank: String?, source: TxSource?, banks: List<BankOption>, sourceChips: List<TxSource>, reviewCount: Int,
    onKind: (TxKind) -> Unit, onBank: (String?) -> Unit, onSource: (TxSource?) -> Unit, onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = kind != TxKind.ALL || bank != null || source != null
    // A bank chosen in an earlier period stays selectable so it can be cleared.
    val bankOptions = if (bank != null && banks.none { it.key == bank }) listOf(BankOption(bank, bank.removeSuffix(" Bank"), bank, 0)) + banks else banks
    val kinds = TxKind.CHIPS + if (kind == TxKind.REVIEW) listOf(TxKind.REVIEW) else emptyList()
    val sources = sourceChips
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        DropChip(
            t("Type"), if (kind == TxKind.ALL) null else t(kind.label),
            kinds.map { DropOption(t(it.label), kind == it) }, { onKind(kinds[it]) },
        )
        DropChip(
            t("Bank"), bankOptions.firstOrNull { it.key == bank }?.short,
            listOf(DropOption(t("All banks"), bank == null)) + bankOptions.map { o ->
                DropOption(o.short, bank == o.key, { BrandMark(Brands.forBank(o.bankName), size = 18.dp) })
            },
            { onBank(if (it == 0) null else bankOptions[it - 1].key) },
            leading = bankOptions.firstOrNull { it.key == bank }?.let { o -> { BrandMark(Brands.forBank(o.bankName), size = 18.dp) } },
        )
        DropChip(
            t("Source"), source?.let { t(it.label) },
            listOf(DropOption(t("All sources"), source == null)) + sources.map { DropOption(t(it.label), source == it) },
            { onSource(if (it == 0) null else sources[it - 1]) },
        )
        if (active) {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Clear, t("Clear filters"), tint = Hx.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** The amber "N transactions need review" strip. */
@Composable
internal fun ReviewBanner(count: Int, onSelect: () -> Unit, onReview: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Hx.warn.copy(alpha = 0.10f)).border(1.dp, Hx.warn.copy(alpha = 0.45f), shape)
            .clickable(onClick = onSelect).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, tint = Hx.warn, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (count == 1) t("1 transaction needs review") else t("{n} transactions need review", "n" to count),
            fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
        )
        Text(
            t("Review ›"), color = Hx.warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onReview).padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

private fun sourceLabel(s: String) = when (s.uppercase()) {
    "SMS" -> t("SMS")
    "EMAIL" -> t("Email")
    "CSV" -> t("Imported")
    "MANUAL" -> t("Manual")
    "STATEMENT" -> t("Statement")
    "NOTIFICATION" -> t("Notification")
    else -> s.lowercase().replaceFirstChar { it.uppercase() }
}

/** One transaction: logo, merchant, "Category · Bank ••1234", where it came from, and the signed amount. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TxLine(
    tx: TransactionEntity,
    sources: List<String>?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showDate: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) Hx.accentSoft else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.size(40.dp).background(Hx.accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, t("Selected"), tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            TransactionAvatar(tx, size = 38.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(com.hisaab.app.ui.components.TxStyle.merchant(tx), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                com.hisaab.app.ui.components.TxStyle.subtitle(tx, showDate), fontSize = 12.sp, color = Hx.text2,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp),
            )
            if (tx.needsReview) {
                Text(t("Needs review"), fontSize = 11.sp, color = Hx.warn, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 1.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        val (text, color) = com.hisaab.app.ui.components.TxStyle.amount(tx)
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

/** A day's transactions in one card, with the day's net on the right of the header. */
@Composable
internal fun DayCard(
    day: DayGroup,
    sources: Map<Long, List<String>>,
    selected: Set<Long>,
    onClick: (TransactionEntity) -> Unit,
    onLongClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    HCard(modifier, padding = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Periods.dayHeader(day.date), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            NetText(day.net)
        }
        day.txs.forEach { tx ->
            TxLine(tx, sources[tx.id], tx.id in selected, onClick = { onClick(tx) }, onLongClick = { onLongClick(tx) })
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** A merchant: total, count and a bar against the biggest; tapping opens its transactions. */
@Composable
internal fun MerchantCard(
    m: MerchantGroup,
    top: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
    sources: Map<Long, List<String>>,
    selected: Set<Long>,
    onClick: (TransactionEntity) -> Unit,
    onLongClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    HCard(modifier, padding = 0.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TransactionAvatar(m.txs.first(), size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    if (m.net == 0L) Text(Money.format(m.gross, showPaise = false), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Hx.transfer)
                    else NetText(m.net, fontSize = 14)
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SplitBar(
                        listOf((if (top > 0) m.gross.toFloat() / top else 0f).coerceIn(0f, 1f) to (if (m.net > 0) Hx.pos else Hx.accent)),
                        Modifier.weight(1f), height = 5.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(if (m.txs.size == 1) t("1 txn") else t("{n} txns", "n" to m.txs.size), fontSize = 11.sp, color = Hx.text2)
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.KeyboardArrowDown, if (expanded) t("Collapse") else t("Expand"), tint = Hx.text2, modifier = Modifier.size(20.dp).rotate(turn))
        }
        if (expanded) {
            HorizontalDivider(color = Hx.border)
            m.txs.forEach { tx ->
                TxLine(tx, sources[tx.id], tx.id in selected, onClick = { onClick(tx) }, onLongClick = { onLongClick(tx) }, showDate = true)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** A Monday-first month grid; each day shows its total, tinted by how big it is against the month's largest day. */
@Composable
internal fun CalendarCard(
    month: YearMonth,
    from: LocalDate,
    to: LocalDate,
    values: Map<LocalDate, Long>,
    caption: String,
    tint: Color,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now(Periods.zone)
    val max = (1..month.lengthOfMonth()).maxOfOrNull { values[month.atDay(it)] ?: 0L }?.coerceAtLeast(1L) ?: 1L
    HCard(modifier, padding = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPrev?.invoke() }, enabled = onPrev != null, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Previous month"))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Periods.month(month), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(caption, fontSize = 11.sp, color = Hx.text2)
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Next month"))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(t(it), Modifier.weight(1f), fontSize = 11.sp, color = Hx.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val lead = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - lead + 1
                        if (dayNum < 1 || dayNum > month.lengthOfMonth()) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val d = month.atDay(dayNum)
                            DayCell(
                                d, values[d] ?: 0L, max, tint, inRange = !d.isBefore(from) && !d.isAfter(to),
                                isToday = d == today, isSelected = d == selected, onClick = { onSelect(d) }, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    d: LocalDate, value: Long, max: Long, tint: Color, inRange: Boolean, isToday: Boolean, isSelected: Boolean,
    onClick: () -> Unit, modifier: Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val strength = if (value > 0) 0.10f + 0.45f * (value.toFloat() / max) else 0f
    Column(
        modifier.aspectRatio(0.82f).clip(shape)
            .background(if (value > 0) tint.copy(alpha = strength) else Hx.surface2.copy(alpha = if (inRange) 0.6f else 0.25f))
            .border(
                if (isSelected) 2.dp else 1.dp,
                when { isSelected -> Hx.accent; isToday -> Hx.accent.copy(alpha = 0.5f); else -> Color.Transparent },
                shape,
            )
            .clickable(enabled = inRange, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${d.dayOfMonth}", fontSize = 12.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (inRange) MaterialTheme.colorScheme.onSurface else Hx.text2.copy(alpha = 0.5f),
        )
        Text(
            if (value > 0) Money.compact(value).removePrefix("₹") else "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, fontWeight = FontWeight.Medium,
        )
    }
}

/** The tapped calendar day, or a hint when none is chosen. */
@Composable
internal fun CalendarDayHint(date: LocalDate?, modifier: Modifier = Modifier) {
    Text(
        if (date == null) t("Tap a day to see its transactions.") else t("No transactions on {date}.", "date" to Periods.dayHeader(date)),
        fontSize = 13.sp, color = Hx.text2, modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
