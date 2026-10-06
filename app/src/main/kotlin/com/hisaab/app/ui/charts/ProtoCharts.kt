package com.hisaab.app.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.hisaab.app.ui.theme.Hx
import kotlin.math.abs
import kotlin.math.max

/** A line that draws itself in, with a soft fill below and a dot on the latest value. */
@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, color: Color = Hx.accent, fill: Boolean = true, height: Dp = 46.dp) {
    if (values.size < 2) { Box(modifier.fillMaxWidth().height(height)); return }
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(700)) }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val mn = values.min(); val mx = values.max(); val span = (mx - mn).takeIf { it > 0f } ?: 1f
        val pts = values.mapIndexed { i, v -> Offset(i * size.width / (values.size - 1), size.height - 4f - (v - mn) / span * (size.height - 10f)) }
        val shown = (pts.size * grow.value).toInt().coerceIn(2, pts.size)
        val line = Path().apply { pts.take(shown).forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        if (fill) {
            val area = Path().apply { addPath(line); lineTo(pts[shown - 1].x, size.height); lineTo(0f, size.height); close() }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        }
        drawPath(line, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 3.5.dp.toPx(), pts[shown - 1])
    }
}

/** A ring that fills to [fraction], with an optional label in the middle. */
@Composable
fun ProgressRing(fraction: Float, color: Color, size: Dp, stroke: Dp = 4.dp, label: String? = null) {
    val sweep = remember(fraction) { Animatable(0f) }
    LaunchedEffect(fraction) { sweep.animateTo(fraction.coerceIn(0f, 1f), tween(700)) }
    val track = Hx.surface2
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx(); val d = this.size.minDimension - s
            drawArc(track, 0f, 360f, false, Offset(s / 2, s / 2), Size(d, d), style = Stroke(s))
            drawArc(color, -90f, 360f * sweep.value, false, Offset(s / 2, s / 2), Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        if (label != null) Text(label, fontSize = (size.value / 5).sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Months as stacked bars (one colour per stack), the latest bar at full strength, and a dashed average line.
 * Tapping a bar calls [onTap] with its index.
 */
@Composable
fun StackedMonthBars(
    labels: List<String>,
    stacks: List<List<Long>>,
    colors: List<Color>,
    averageLabel: String?,
    average: Long?,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selected: Int? = null,
) {
    val measurer = rememberTextMeasurer()
    val text2 = Hx.text2; val border = Hx.border; val warn = Hx.warn
    val grow = remember(stacks) { Animatable(0f) }
    LaunchedEffect(stacks) { grow.animateTo(1f, tween(600)) }
    val max = max(stacks.maxOfOrNull { it.sum() } ?: 0L, average ?: 0L).coerceAtLeast(1L)
    Canvas(
        modifier.fillMaxWidth().height(160.dp).pointerInput(labels) {
            detectTapGestures { o ->
                val left = 30.dp.toPx(); val w = (size.width - left) / labels.size
                val i = ((o.x - left) / w).toInt(); if (i in labels.indices) onTap(i)
            }
        },
    ) {
        val left = 30.dp.toPx(); val bottom = size.height - 18.dp.toPx(); val top = 6.dp.toPx()
        val slot = (size.width - left) / labels.size; val bw = slot * 0.55f
        val style = TextStyle(fontSize = 9.sp, color = text2)
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = bottom - f * (bottom - top)
            drawLine(border, Offset(left - 4f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            drawText(measurer, compactAxis((max * f).toLong()), Offset(0f, y - 6.dp.toPx()), style)
        }
        stacks.forEachIndexed { i, parts ->
            var y = bottom
            val x = left + i * slot + (slot - bw) / 2
            val alpha = when { selected != null -> if (i == selected) 1f else 0.35f; i == stacks.lastIndex -> 1f; else -> 0.75f }
            parts.forEachIndexed { j, v ->
                val h = v.toFloat() / max * (bottom - top) * grow.value
                y -= h
                drawRoundRect(colors[j % colors.size].copy(alpha = alpha), Offset(x, y), Size(bw, h), CornerRadius(if (j == parts.lastIndex) 4f else 0f))
            }
            val lbl = measurer.measure(labels[i], style)
            drawText(lbl, topLeft = Offset(x + bw / 2 - lbl.size.width / 2, bottom + 4.dp.toPx()))
        }
        if (average != null && average > 0) {
            val y = bottom - average.toFloat() / max * (bottom - top)
            drawLine(warn, Offset(left - 4f, y), Offset(size.width, y), 1.5.dp.toPx())
            if (averageLabel != null) drawText(measurer, averageLabel, Offset(left, y - 14.dp.toPx()), TextStyle(fontSize = 9.5.sp, color = warn, fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Layers stacked on top of each other over time (net worth by asset class). */
@Composable
fun StackedArea(layers: List<List<Float>>, colors: List<Color>, labels: List<String>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(); val text2 = Hx.text2
    val n = layers.firstOrNull()?.size ?: 0
    if (n < 2) { Box(modifier.fillMaxWidth().height(140.dp)); return }
    val grow = remember(layers) { Animatable(0f) }
    LaunchedEffect(layers) { grow.animateTo(1f, tween(700)) }
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        val bottom = size.height - 14.dp.toPx()
        val totals = (0 until n).map { i -> layers.sumOf { it[i].toDouble() }.toFloat() }
        val mx = (totals.maxOrNull() ?: 1f).coerceAtLeast(1f) * 1.05f
        fun x(i: Int) = i * size.width / (n - 1)
        fun y(v: Float) = bottom - v / mx * (bottom - 4f) * grow.value
        var base = FloatArray(n)
        layers.forEachIndexed { li, layer ->
            val topVals = FloatArray(n) { base[it] + layer[it] }
            val p = Path().apply {
                moveTo(x(0), y(topVals[0])); for (i in 1 until n) lineTo(x(i), y(topVals[i]))
                for (i in n - 1 downTo 0) lineTo(x(i), y(base[i])); close()
            }
            drawPath(p, colors[li % colors.size].copy(alpha = 0.85f))
            base = topVals
        }
        labels.forEachIndexed { i, l ->
            if (labels.size <= 6 || i % 2 == 0) drawText(measurer, l, Offset((x(i * (n - 1) / (labels.size - 1).coerceAtLeast(1)) - 8f).coerceAtLeast(0f), bottom + 2.dp.toPx()), TextStyle(fontSize = 9.sp, color = text2))
        }
    }
}

data class SankeyNode(val label: String, val value: Long, val color: Color)

/** Income sources on the left flowing into where the money went on the right. */
@Composable
fun Sankey(inputs: List<SankeyNode>, outputs: List<SankeyNode>, valueText: (Long) -> String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface; val text2 = Hx.text2
    val grow = remember(inputs, outputs) { Animatable(0f) }
    LaunchedEffect(inputs, outputs) { grow.animateTo(1f, tween(800)) }
    val ins = inputs.filter { it.value > 0 }; val outs = outputs.filter { it.value > 0 }
    if (ins.isEmpty() || outs.isEmpty()) return
    Canvas(modifier.fillMaxWidth().height(230.dp)) {
        val total = max(ins.sumOf { it.value }, outs.sumOf { it.value }).toFloat()
        val gapIn = 12.dp.toPx(); val gapOut = 8.dp.toPx(); val nodeW = 10.dp.toPx()
        val sc = (size.height - 20.dp.toPx() - gapOut * (outs.size - 1)) / total
        val left = mutableListOf<Pair<Float, Float>>()
        var y = 10.dp.toPx()
        ins.forEach { f -> val h = f.value * sc; left += y to h; y += h + gapIn }
        // Ribbons first, so the nodes and labels sit on top.
        var li = 0; var lo = left[0].first; var yo = 10.dp.toPx()
        val rightX = size.width - nodeW
        val outTops = mutableListOf<Float>()
        outs.forEach { f ->
            outTops += yo
            var rem = f.value * sc; var yy = yo
            while (rem > 0.01f && li < left.size) {
                val avail = left[li].first + left[li].second - lo
                val th = minOf(rem, avail)
                val p = Path().apply {
                    moveTo(nodeW, lo); cubicTo(size.width * 0.5f, lo, size.width * 0.5f, yy, rightX, yy)
                    lineTo(rightX, yy + th); cubicTo(size.width * 0.5f, yy + th, size.width * 0.5f, lo + th, nodeW, lo + th); close()
                }
                drawPath(p, f.color.copy(alpha = 0.28f * grow.value))
                lo += th; yy += th; rem -= th
                if (lo >= left[li].first + left[li].second - 0.01f) { li++; if (li < left.size) lo = left[li].first }
            }
            yo += f.value * sc + gapOut
        }
        ins.forEachIndexed { i, f ->
            drawRoundRect(f.color, Offset(0f, left[i].first), Size(nodeW, left[i].second.coerceAtLeast(2f)), CornerRadius(4f))
            drawText(measurer, f.label, Offset(nodeW + 4.dp.toPx(), left[i].first), TextStyle(fontSize = 10.5.sp, color = onSurface, fontWeight = FontWeight.SemiBold))
        }
        outs.forEachIndexed { i, f ->
            val h = f.value * sc
            drawRoundRect(f.color, Offset(rightX, outTops[i]), Size(nodeW, h.coerceAtLeast(2f)), CornerRadius(4f))
            val t = measurer.measure("${f.label}  ${valueText(f.value)}", TextStyle(fontSize = 10.5.sp, color = onSurface, fontWeight = FontWeight.SemiBold))
            drawText(t, topLeft = Offset(rightX - 4.dp.toPx() - t.size.width, outTops[i] + (h / 2 - t.size.height / 2).coerceAtLeast(0f)))
        }
    }
}

/**
 * Month-end forecast: the actual cumulative spend so far (solid), the expected path (dashed) inside a
 * low–high band, and the budget as a red dashed line.
 */
@Composable
fun FanChart(
    actual: List<Long>,
    expected: List<Long>,
    low: List<Long>,
    high: List<Long>,
    budget: Long?,
    daysInMonth: Int,
    budgetLabel: String?,
    dayLabel: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val accent = Hx.accent; val neg = Hx.neg; val text2 = Hx.text2; val onSurface = MaterialTheme.colorScheme.onSurface
    val grow = remember(actual) { Animatable(0f) }
    LaunchedEffect(actual) { grow.animateTo(1f, tween(700)) }
    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        val bottom = size.height - 16.dp.toPx(); val leftPad = 4.dp.toPx()
        val mx = listOfNotNull(high.maxOrNull(), actual.maxOrNull(), budget).maxOrNull()?.coerceAtLeast(1L)?.times(1.08f) ?: 1f
        fun x(day: Int) = leftPad + (day - 1f) / (daysInMonth - 1).coerceAtLeast(1) * (size.width - leftPad * 2)
        fun y(v: Long) = bottom - v / mx * (bottom - 6.dp.toPx())
        val start = actual.size // forecast starts at today
        if (expected.isNotEmpty()) {
            val band = Path().apply {
                high.forEachIndexed { i, v -> val d = start + i; if (i == 0) moveTo(x(d), y(v)) else lineTo(x(d), y(v)) }
                low.indices.reversed().forEach { i -> lineTo(x(start + i), y(low[i])) }; close()
            }
            drawPath(band, accent.copy(alpha = 0.14f * grow.value))
            val e = Path().apply { expected.forEachIndexed { i, v -> val d = start + i; if (i == 0) moveTo(x(d), y(v)) else lineTo(x(d), y(v)) } }
            drawPath(e, accent.copy(alpha = grow.value), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        }
        val shown = (actual.size * grow.value).toInt().coerceAtLeast(1).coerceAtMost(actual.size)
        if (actual.isNotEmpty()) {
            val a = Path().apply { actual.take(shown).forEachIndexed { i, v -> if (i == 0) moveTo(x(1), y(v)) else lineTo(x(i + 1), y(v)) } }
            drawPath(a, onSurface, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
        }
        if (budget != null && budget > 0) {
            val by = y(budget)
            drawLine(neg, Offset(x(1), by), Offset(x(daysInMonth), by), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            if (budgetLabel != null) drawText(measurer, budgetLabel, Offset(x(1), by - 14.dp.toPx()), TextStyle(fontSize = 9.5.sp, color = neg, fontWeight = FontWeight.SemiBold))
        }
        listOf(1, 8, 15, 22, daysInMonth).forEach { d ->
            val t = measurer.measure(dayLabel(d), TextStyle(fontSize = 9.sp, color = text2))
            drawText(t, topLeft = Offset((x(d) - t.size.width / 2).coerceIn(0f, size.width - t.size.width), bottom + 3.dp.toPx()))
        }
    }
}

/** Weekdays by time of day, each cell tinted by how much was spent then. Tapping a cell shows its amount. */
@Composable
fun HeatGrid(grid: Array<LongArray>, rows: List<String>, cols: List<String>, onCell: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val accent = Hx.accent; val base = Hx.surface2; val text2 = Hx.text2
    val mx = grid.maxOf { r -> r.maxOrNull() ?: 0L }.coerceAtLeast(1L)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.width(30.dp))
            cols.forEach { c -> Text(c, Modifier.weight(1f), fontSize = 10.sp, color = text2, textAlign = TextAlign.Center) }
        }
        grid.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rows[r], Modifier.width(30.dp), fontSize = 10.sp, color = text2)
                row.forEachIndexed { c, v ->
                    val f = v.toFloat() / mx
                    Box(
                        Modifier.weight(1f).aspectRatio(1.4f).clip(RoundedCornerShape(4.dp))
                            .background(lerpColor(base, accent, f)).clickable { onCell(r, c) },
                    )
                }
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, f: Float): Color = androidx.compose.ui.graphics.lerp(a, b, f.coerceIn(0f, 1f))

/** One row of a diverging bar: decreases grow left in green, increases grow right in red. */
@Composable
fun DivergingRow(label: String, percent: Int, modifier: Modifier = Modifier) {
    val pos = Hx.pos; val neg = Hx.neg
    Row(modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(110.dp), fontSize = 12.5.sp, maxLines = 1)
        val f = (abs(percent).coerceAtMost(50) / 50f)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            if (percent < 0) Box(Modifier.fillMaxWidth(f).height(10.dp).clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)).background(pos))
        }
        Row(Modifier.weight(1f)) {
            if (percent > 0) Box(Modifier.fillMaxWidth(f).height(10.dp).clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(neg))
        }
        Text(
            (if (percent > 0) "+" else "") + "$percent%", Modifier.width(48.dp), fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
            color = if (percent > 0) neg else pos, textAlign = TextAlign.End,
        )
    }
}

private fun compactAxis(minor: Long): String {
    if (com.hisaab.app.ui.format.AmountPrivacy.hidden) return "•"
    val r = minor / 100.0
    return when {
        r >= 1_00_000 -> "%.1fL".format(r / 1_00_000)
        r >= 1_000 -> "%.0fK".format(r / 1_000)
        else -> "%.0f".format(r)
    }
}
