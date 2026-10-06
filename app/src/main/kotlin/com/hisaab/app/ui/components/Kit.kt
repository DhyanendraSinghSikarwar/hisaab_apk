package com.hisaab.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.ui.theme.Hx

/** A flat card with a hairline border and an optional small-caps title with an action on the right. */
@Composable
fun HCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    container: Color = Hx.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = com.hisaab.app.ui.theme.LocalDarkTheme.current
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier.fillMaxWidth()
            // A soft, accent-tinted shadow by day; at night a faint top highlight on the border does the lifting.
            .shadow(if (dark) 0.dp else 10.dp, shape, ambientColor = Hx.accent.copy(alpha = 0.06f), spotColor = Hx.accent.copy(alpha = 0.10f))
            .let { if (onClick != null) it.clip(shape).clickable(onClick = onClick) else it },
        shape = shape, color = container,
        border = BorderStroke(1.dp, if (dark) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Hx.border)) else SolidColor(Hx.border)),
    ) {
        Column(Modifier.padding(padding).animateContentSize()) {
            if (title != null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardTitle(title, Modifier.weight(1f))
                    if (action != null) {
                        Text(
                            action, color = Hx.accent, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = onAction != null) { onAction?.invoke() }.padding(4.dp),
                        )
                    }
                }
            }
            content()
        }
    }
}

/**
 * The headline card: a blue-to-violet gradient with white text and two faint decorative rings.
 * Content inside should use [LocalContentColor] (white).
 */
@Composable
fun HeroCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val brush = com.hisaab.app.ui.theme.HeroBrush
    Box(
        modifier.fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = Color(0xFF2F5BEA).copy(alpha = 0.25f), spotColor = Color(0xFF5A48D6).copy(alpha = 0.35f))
            .clip(shape).background(brush)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.height * 0.9f, center = Offset(size.width * 1.02f, -size.height * 0.15f))
                drawCircle(Color.White.copy(alpha = 0.05f), radius = size.height * 0.55f, center = Offset(size.width * 0.88f, size.height * 1.1f))
            },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
            Column(Modifier.padding(18.dp).animateContentSize(), content = content)
        }
    }
}

/** A card whose body folds away under its header; the chevron turns as it opens. */
@Composable
fun CollapsibleCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    initiallyExpanded: Boolean = true,
    expanded: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var local by androidx.compose.runtime.saveable.rememberSaveable(title) { androidx.compose.runtime.mutableStateOf(initiallyExpanded) }
    val open = expanded ?: local
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    HCard(modifier, padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().clickable { val n = !open; local = n; onToggle?.invoke(n) }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardTitle(title, Modifier.weight(1f))
            if (trailing != null) Text(trailing, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp))
            Icon(
                androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown, if (open) "Collapse" else "Expand",
                tint = Hx.text2, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            open,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), content = content)
        }
    }
}

/** "SPEND BY CATEGORY": the small, spaced, upper-case card title. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier, color = Hx.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** A small label over a bold figure. */
@Composable
fun Kpi(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Hx.text2, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun KpiRow(vararg items: Triple<String, String, Color?>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (l, v, c) -> Kpi(l, v, Modifier.weight(1f), c ?: MaterialTheme.colorScheme.onSurface) }
    }
}

/** "▲ 2.6%": a rounded pill tinted green (good) or red (bad). */
@Composable
fun Delta(text: String, good: Boolean, modifier: Modifier = Modifier) {
    val c = if (good) Hx.pos else Hx.neg
    Text(
        text, modifier.clip(CircleShape).background(c.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = c, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
    )
}

/** A coloured tag ("Spending", "Tax"). */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
    )
}

/** A pill button: outlined, or filled with the accent when [on]. */
@Composable
fun Pill(text: String, on: Boolean = false, modifier: Modifier = Modifier, leading: ImageVector? = null, onClick: () -> Unit) {
    val bg = if (on) Hx.accent else Hx.surface2
    val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.clip(CircleShape).background(bg).border(1.dp, if (on) Hx.accent else Hx.border, CircleShape).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { Icon(leading, null, tint = fg, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)) }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** A round icon button with a border, as in the top bar. */
@Composable
fun RoundIcon(icon: ImageVector, description: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Box(
        modifier.size(36.dp).clip(CircleShape).background(Hx.surface2).border(1.dp, Hx.border, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint, modifier = Modifier.size(18.dp)) }
}

/** A segmented control: equal buttons in a tray; the chosen one is lifted. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Hx.surface2).border(1.dp, Hx.border, RoundedCornerShape(12.dp)).padding(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) Hx.surface else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (on) MaterialTheme.colorScheme.onSurface else Hx.text2, maxLines = 1)
            }
        }
    }
}

/** A thin rounded bar split into coloured parts (fractions of the whole). */
@Composable
fun SplitBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Row(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Hx.surface2)) {
        parts.filter { it.first > 0f }.forEach { (f, c) -> Box(Modifier.weight(f.coerceAtLeast(0.001f)).height(height).background(c)) }
        val rest = 1f - parts.sumOf { it.first.toDouble() }.toFloat()
        if (rest > 0.001f) Spacer(Modifier.weight(rest))
    }
}

/** A list row: leading badge, title and subtitle, trailing content. */
@Composable
fun HRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().let { if (onClick != null) it.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else it }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Hx.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

/** A rounded-square letter badge in a colour. */
@Composable
fun LetterBadge(text: String, color: Color, size: Dp = 36.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(text.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** A coloured square in a legend. */
@Composable
fun LegendDot(color: Color) { Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color)) }

/** One legend entry: dot, name, and a value on the right. Tappable; [selected] tints it. */
@Composable
fun LegendItem(color: Color, name: String, value: String?, selected: Boolean = false, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (selected) Hx.accentSoft else Color.Transparent)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(color); Spacer(Modifier.width(6.dp))
        Text(name, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Space between stacked cards. */
val CardGap = 12.dp
