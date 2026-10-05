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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hisaab.app.ui.components.AnimatedAmount
import com.hisaab.app.ui.components.BrandMark
import com.hisaab.app.ui.components.Brands
import com.hisaab.app.ui.components.KindColors
import com.hisaab.app.ui.components.pressable
import com.hisaab.app.ui.format.AmountPrivacy
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.LocalDarkTheme
import com.hisaab.app.ui.theme.MoneyColors
import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountWithActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Emerald, sapphire and gold light behind the top of Home, fading into the app backdrop. Drawn once, no images. */
@Composable
fun BoxScope.AuroraBackground() {
    val dark = LocalDarkTheme.current
    val a = if (dark) 0.55f else 0.30f
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(460.dp)
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.drawBehind {
            drawRect(Brush.radialGradient(listOf(Color(0xFF0F7B5A).copy(alpha = a), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.15f), size.width * 0.75f))
            drawRect(Brush.radialGradient(listOf(Color(0xFF1F5FA8).copy(alpha = a), Color.Transparent), Offset(size.width * 0.55f, size.height * 0.25f), size.width * 0.7f))
            drawRect(Brush.radialGradient(listOf(Color(0xFFC9A227).copy(alpha = a * 0.6f), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.05f), size.width * 0.55f))
            // Fade the glow out towards the bottom so it melts into the backdrop with no edge.
            drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height * 0.35f, endY = size.height),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
        },
    )
}

fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

@Composable
fun Avatar(name: String, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "H" }
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF2E7D32), Color(0xFF00897B))))
            .border(2.dp, Color(0xFFFFB300).copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** Avatar, name and greeting, with Accounts and a ••• menu. */
@Composable
fun HomeHeader(
    name: String, onOpenAccounts: () -> Unit, onToggleHide: () -> Unit, onOpenSettings: () -> Unit, onOpenBills: () -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = false,
    photoPath: String? = null, onOpenProfile: () -> Unit = {}, hasName: Boolean = true,
) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Avatar and name open the profile.
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpenProfile).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.hisaab.app.ui.profile.ProfileAvatar(name, photoPath, if (compact) 36.dp else 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (!compact) Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (!compact && !hasName) {
                    Text("Tap to create your profile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        IconButton(onClick = onOpenAccounts) { Icon(Icons.Filled.AccountBalance, "Accounts") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreHoriz, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(if (AmountPrivacy.hidden) "Show amounts" else "Hide amounts") },
                    leadingIcon = { Icon(if (AmountPrivacy.hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null) },
                    onClick = { menu = false; onToggleHide() })
                DropdownMenuItem(text = { Text("Bills & insurance") }, leadingIcon = { Icon(Icons.Filled.EventRepeat, null) },
                    onClick = { menu = false; onOpenBills() })
                DropdownMenuItem(text = { Text("Profile") }, leadingIcon = { Icon(Icons.Filled.Person, null) },
                    onClick = { menu = false; onOpenProfile() })
                DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Filled.MoreHoriz, null) },
                    onClick = { menu = false; onOpenSettings() })
            }
        }
    }
}

/**
 * "Spent this month": the amount large, an eye to hide it, how it compares with last month, the balance
 * across accounts, and a chevron that opens income, saved and investments.
 */
@Composable
fun SpendHero(
    state: HomeState, onPrev: () -> Unit, onNext: () -> Unit, onToggleHide: () -> Unit, onOpenInvestments: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "chevron")
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.isCurrentMonth) "Spent this month" else "Spent in ${Periods.month(state.month)}",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = onPrev, Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                IconButton(onClick = onNext, Modifier.size(32.dp), enabled = !state.isCurrentMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                AnimatedAmount(state.spent, MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                IconButton(onClick = onToggleHide) {
                    Icon(if (AmountPrivacy.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        if (AmountPrivacy.hidden) "Show amounts" else "Hide amounts", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.prevSpent > 0) {
                val pct = ((state.spent - state.prevSpent) * 100 / state.prevSpent).toInt()
                val up = pct > 0
                val tint = if (up) MoneyColors.debit else MoneyColors.credit
                Row(
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${abs(pct)}% vs ${Periods.monthShort(state.month.minusMonths(1))}", style = MaterialTheme.typography.labelMedium, color = tint)
                }
            }
            Text(
                "Balance: ${state.balance?.let { Money.format(it, showPaise = false) } ?: "—"} · ${state.balanceAccounts} account${if (state.balanceAccounts == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp),
            )
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroLine("Income", Money.format(state.income, showPaise = false), MoneyColors.credit)
                    val net = state.income - state.spent
                    HeroLine(if (net >= 0) "Saved" else "Overspent", Money.format(abs(net), showPaise = false), MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth().clickable(onClick = onOpenInvestments)) {
                        HeroLine("Investments", if (state.holdingCount > 0) Money.format(state.investments, showPaise = false) else "Add", MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().clickable { open = !open }.padding(top = 6.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ExpandMore, if (open) "Less" else "More", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevron))
            }
        }
    }
}

@Composable
private fun HeroLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

/** "Budgets   View All >" header used by the Home sections. */
@Composable
fun HomeSection(title: String, action: String? = null, onAction: () -> Unit = {}, extra: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        extra?.invoke()
        if (action != null) TextButton(onClick = onAction) { Text(action); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp)) }
    }
}

/** The empty state for budgets on Home: one line and one action. */
@Composable
fun CreateBudgetCard(onCreate: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).pressable(onClick = onCreate), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Savings, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Set a budget to track your spending", style = MaterialTheme.typography.bodyLarge)
                Text("Create Budget →", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Swipeable account cards: logo, last digits, type, and the balance with its own eye. */
@Composable
fun AccountsCarousel(accounts: List<AccountWithActivity>, onOpen: () -> Unit, onToggleHide: () -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(accounts, key = { it.id }) { a ->
            Card(Modifier.width(300.dp).pressable(onClick = onOpen), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(Brands.forBank(a.bankName), size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(a.nickname ?: a.bankName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp))
                        Text("••${a.last4}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        val kindColor = KindColors.of(a.kind, a.accountType)
                        Text(a.accountType?.label?.substringBefore(' ') ?: if (a.kind == AccountKind.CARD) "Card" else "Account",
                            style = MaterialTheme.typography.labelMedium, color = kindColor, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(kindColor.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (a.kind == AccountKind.CARD && !a.isDebitCard) "Limit left" else "Balance",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(a.currentBalanceMinor?.let { Money.format(it, showPaise = false) } ?: "—", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = onToggleHide, Modifier.size(36.dp)) {
                            Icon(if (AmountPrivacy.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** "3 active subscriptions · Monthly total ₹5,000 · View". */
@Composable
fun SubscriptionsSummary(count: Int, monthlyTotal: Long, onView: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).pressable(onClick = onView), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.EventRepeat, null)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("$count active subscription${if (count == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
                Text("Monthly total: ${Money.format(monthlyTotal, showPaise = false)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("View", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Daily spending as a grid, one column per week and one square per day, darker for days that cost more
 * (like a contribution graph). Tap a square for that day's total.
 */
@Composable
fun ActivityHeatmap(spendByDay: Map<LocalDate, Long>, weeks: Int = 18) {
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val today = LocalDate.now(Periods.zone)
    // The grid ends with the current week; columns start on Monday.
    val end = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val start = end.minusDays(weeks * 7L - 1)
    val max = spendByDay.filterKeys { it >= start }.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val green = Color(0xFF7BD389)
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE, d MMM") }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(170.dp).pointerInput(spendByDay) {
                    detectTapGestures { p ->
                        val left = 22.dp.toPx(); val cell = (size.width - left) / weeks
                        val col = ((p.x - left) / cell).toInt(); val row = (p.y / cell).toInt()
                        if (col in 0 until weeks && row in 0..6) {
                            val d = start.plusDays(col * 7L + row)
                            if (d <= today) { picked = d; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        }
                    }
                },
            ) {
                val left = 22.dp.toPx()
                val cell = (size.width - left) / weeks
                val gap = cell * 0.16f
                listOf(0 to "M", 2 to "W", 4 to "F").forEach { (r, l) ->
                    drawText(measurer.measure(l, labelStyle), topLeft = Offset(0f, r * cell + (cell - 14.dp.toPx()) / 2f))
                }
                var lastMonth = -1
                for (w in 0 until weeks) for (r in 0..6) {
                    val d = start.plusDays(w * 7L + r)
                    if (r == 0 && d.monthValue != lastMonth && w > 0) {
                        lastMonth = d.monthValue
                        drawText(measurer.measure(d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, labelStyle),
                            topLeft = Offset(left + w * cell, 7 * cell + 2.dp.toPx()))
                    } else if (r == 0 && w == 0) lastMonth = d.monthValue
                    if (d > today) continue
                    val v = spendByDay[d] ?: 0
                    val color = if (v <= 0) empty else green.copy(alpha = 0.25f + 0.75f * (v.toFloat() / max).coerceIn(0f, 1f))
                    drawRoundRect(color, Offset(left + w * cell + gap / 2, r * cell + gap / 2), Size(cell - gap, cell - gap), CornerRadius(cell * 0.22f))
                    if (d == picked) drawRoundRect(Color.White, Offset(left + w * cell + gap / 2, r * cell + gap / 2), Size(cell - gap, cell - gap),
                        CornerRadius(cell * 0.22f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
            }
            Text(
                picked?.let { "${it.format(fmt)}: ${Money.format(spendByDay[it] ?: 0, showPaise = false)} spent" } ?: "Tap a day to see what you spent",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Budget lines for Home: the top few categories with a bar each. */
@Composable
fun BudgetLines(lines: List<CategorySpend>, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).pressable(onClick = onOpen), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            lines.forEach { c ->
                val limit = c.budget ?: return@forEach
                val ratio = (c.spent.toFloat() / limit.coerceAtLeast(1)).coerceIn(0f, 1f)
                val p by animateFloatAsState(ratio, tween(700), label = "b")
                val over = c.spent > limit
                Column {
                    Row {
                        Text(c.category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${Money.format(c.spent, showPaise = false)} / ${Money.format(limit, showPaise = false)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(
                        progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(CircleShape),
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

/** The header that floats over the content once Home is scrolled: a blurred pill. */
@Composable
fun CollapsedHeader(content: @Composable () -> Unit) {
    Box(
        Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { content() }
}
