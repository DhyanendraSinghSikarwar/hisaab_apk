package com.hisaab.app.ui.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.theme.Hx
import com.hisaab.parser.model.Category

/** Budgets for the selected period as bullet charts: an overall row, then one row per budgeted category. */
@Composable
internal fun BudgetsCard(b: BudgetSummary, periodLabel: String, selected: Category?, onOpenBudgets: () -> Unit) {
    HCard(title = "Budgets", action = "Manage ›", onAction = onOpenBudgets) {
        if (b.lines.isEmpty()) {
            Note("No budgets yet. A monthly limit per category shows here how each one is tracking.")
            Spacer(Modifier.height(8.dp))
            Pill("Set budgets", on = true, onClick = onOpenBudgets)
            return@HCard
        }
        val scope = if (b.months > 1) "$periodLabel · limits × ${b.months} months" else periodLabel
        Text(scope, fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(bottom = 10.dp))

        BudgetRow(
            leading = null, name = "All budgets", spent = b.spent, limit = b.limit, alertPercent = b.alertPercent,
            pace = b.pace, highlighted = false, bold = true, barHeight = 10.dp,
        )
        b.pace?.let { p ->
            val ideal = (b.limit * p).toLong()
            val ahead = b.spent - ideal
            Text(
                if (ahead > 0) "${Money.compact(ahead)} ahead of an even pace for this point in the month"
                else "${Money.compact(-ahead)} under an even pace for this point in the month",
                fontSize = 11.sp, color = Hx.text2, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Hx.border))
        b.lines.forEach { l ->
            BudgetRow(
                leading = l.category, name = l.category.label, spent = l.spent, limit = l.limit, alertPercent = b.alertPercent,
                pace = null, highlighted = l.category == selected, bold = false, barHeight = 6.dp,
            )
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Swatch(Hx.pos, "On track"); Spacer(Modifier.width(12.dp))
            Swatch(Hx.warn, "≥ ${b.alertPercent}%"); Spacer(Modifier.width(12.dp))
            Swatch(Hx.neg, "Over")
            if (b.pace != null) { Spacer(Modifier.width(12.dp)); MarkerKey() }
        }
    }
}

@Composable
private fun BudgetRow(
    leading: Category?,
    name: String,
    spent: Long,
    limit: Long,
    alertPercent: Int,
    pace: Float?,
    highlighted: Boolean,
    bold: Boolean,
    barHeight: Dp,
) {
    val used = if (limit > 0) spent.toFloat() / limit else 0f
    val tone = when {
        used >= 1f -> Hx.neg
        used * 100 >= alertPercent -> Hx.warn
        else -> Hx.pos
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (highlighted) Hx.accentSoft else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { CategoryIcon(leading); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name, fontSize = if (bold) 15.sp else 14.sp,
                    fontWeight = if (bold || highlighted) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(Money.compact(spent), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(" of ${Money.compact(limit)}", fontSize = 12.sp, color = Hx.text2)
            }
            BulletBar(used, pace, tone, barHeight, Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 4.dp)) {
                Text("${(used * 100).toInt()}% used", fontSize = 11.sp, color = Hx.text2, modifier = Modifier.weight(1f))
                val left = limit - spent
                Text(
                    if (left >= 0) "${Money.compact(left)} left" else "${Money.compact(-left)} over",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (left >= 0) Hx.text2 else Hx.neg,
                )
            }
        }
    }
}

/**
 * A bullet bar: the track spans max(limit, spent), the fill is the spend, a tick marks the limit,
 * and an optional thinner tick marks the ideal pace for today.
 */
@Composable
private fun BulletBar(used: Float, pace: Float?, tone: Color, height: Dp, modifier: Modifier = Modifier) {
    val span = maxOf(1f, used)
    val fill by animateFloatAsState((used / span).coerceIn(0f, 1f), tween(600), label = "budget")
    val limitAt = 1f / span
    val onSurface = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(modifier.fillMaxWidth().height(height + 6.dp), contentAlignment = Alignment.CenterStart) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(Hx.surface2)) {
            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(CircleShape).background(tone))
        }
        // Limit marker (only distinct from the end of the track once spend has passed it).
        if (span > 1f) Tick(w * limitAt, onSurface, 2.dp)
        pace?.let { p -> Tick(w * (p / span).coerceIn(0f, 1f), onSurface.copy(alpha = 0.55f), 1.5.dp) }
    }
}

@Composable
private fun Tick(x: Dp, color: Color, width: Dp) {
    Box(Modifier.offset(x = x - width / 2).width(width).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(color))
}

@Composable
private fun MarkerKey() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(1.5.dp).height(10.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)))
        Spacer(Modifier.width(5.dp))
        Text("Today's pace", fontSize = 11.sp, color = Hx.text2)
    }
}
