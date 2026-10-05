package com.hisaab.app.ui.charts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Charts drawn straight onto a Canvas: no chart library, one draw pass per frame, and every gesture
 * resolved with arithmetic rather than hit-testing objects. That keeps them smooth on mid-range phones.
 */

@Immutable
data class ChartSlice(val label: String, val value: Long, val color: Color)

/**
 * A donut that sweeps in, and pops the slice you tap (tap it again, or the hole, to clear). The centre
 * shows the selected slice, or the total, and cross-fades between them.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    centerLabel: String,
    centerValue: (Long) -> String,
    modifier: Modifier = Modifier,
    thickness: Dp = 26.dp,
) {
    val haptics = LocalHapticFeedback.current
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val sweep = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(slices) { sweep.snapTo(0f); sweep.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
    LaunchedEffect(selected) { pop.snapTo(0f); if (selected != null) pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    val currentSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(slices) {
                detectTapGestures { p ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = minOf(size.width, size.height) / 2f
                    val d = hypot(p.x - c.x, p.y - c.y)
                    val ring = thickness.toPx() * 1.6f
                    if (d < r - ring || d > r) { currentSelect(null); return@detectTapGestures }
                    // Angle clockwise from 12 o'clock, 0..360.
                    var a = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat() + 90f
                    if (a < 0) a += 360f
                    var acc = 0f
                    for ((i, s) in slices.withIndex()) {
                        acc += s.value * 360f / total
                        if (a <= acc) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentSelect(if (currentSelected == i) null else i)
                            return@detectTapGestures
                        }
                    }
                }
            },
        ) {
            val base = thickness.toPx()
            val inset = base * 0.8f
            val arcSize = Size(size.minDimension - inset * 2, size.minDimension - inset * 2)
            val topLeft = Offset((size.width - arcSize.width) / 2f, (size.height - arcSize.height) / 2f)
            val gap = if (slices.size > 1) 1.6f else 0f
            var start = -90f
            for ((i, s) in slices.withIndex()) {
                val full = s.value * 360f / total
                val angle = (full * sweep.value - gap).coerceAtLeast(0.1f)
                val isSel = i == selected
                val width = if (isSel) base * (1f + 0.35f * pop.value) else base
                val alpha = if (selected == null || isSel) 1f else 1f - 0.55f * pop.value.coerceAtLeast(0.6f)
                drawArc(
                    color = s.color.copy(alpha = alpha), startAngle = start + gap / 2, sweepAngle = angle, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = width, cap = StrokeCap.Butt),
                )
                start += full * sweep.value
            }
        }
        val sel = selected?.let { slices.getOrNull(it) }
        AnimatedContent(
            targetState = sel?.let { it.label to it.value } ?: (centerLabel to slices.sumOf { it.value }),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "donut-centre",
        ) { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(centerValue(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (sel != null) {
                    Text("${(value * 1000 / total / 10.0)}%", style = MaterialTheme.typography.labelMedium, color = sel.color)
                }
            }
        }
    }
}

@Immutable
data class BarGroup(val label: String, val values: List<Long>)

/**
 * Grouped bars (e.g. spent vs income per month) that grow in. Tap or slide across to pick a group;
 * a tooltip shows its values. Gridlines carry compact amounts.
 */
@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    seriesNames: List<String>,
    colors: List<Color>,
    format: (Long) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 250.dp,
) {
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(groups) { grow.snapTo(0f); grow.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    var selected by remember(groups) { mutableStateOf(groups.indices.lastOrNull()) }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val tipBg = MaterialTheme.colorScheme.inverseSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (groups.flatMap { it.values }.maxOrNull() ?: 0L).coerceAtLeast(1)
    val axisW = with(androidx.compose.ui.platform.LocalDensity.current) { 44.dp.toPx() }

    fun pick(x: Float, width: Float) {
        if (groups.isEmpty()) return
        val slot = (width - axisW) / groups.size
        val i = ((x - axisW) / slot).toInt().coerceIn(0, groups.size - 1)
        if (i != selected) { selected = i; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    Canvas(
        modifier.fillMaxWidth().height(height)
            .pointerInput(groups) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
            .pointerInput(groups) { detectHorizontalDragGestures { change, _ -> pick(change.position.x, size.width.toFloat()) } },
    ) {
        val top = 70.dp.toPx() // room for the tooltip above the bars
        val bottom = size.height - 20.dp.toPx()
        val plotH = bottom - top
        // Gridlines at 0, half and max.
        for (f in listOf(0f, 0.5f, 1f)) {
            val y = bottom - plotH * f
            drawLine(grid, Offset(axisW, y), Offset(size.width, y), strokeWidth = 1f,
                pathEffect = if (f == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val t = measurer.measure(format((max * f).toLong()), labelStyle)
            drawText(t, topLeft = Offset(axisW - t.size.width - 6.dp.toPx(), y - t.size.height / 2f))
        }
        if (groups.isEmpty()) return@Canvas
        val slot = (size.width - axisW) / groups.size
        val n = groups.first().values.size
        val barW = (slot * 0.62f / n).coerceAtMost(22.dp.toPx())
        val corner = CornerRadius(barW / 3f, barW / 3f)
        for ((gi, g) in groups.withIndex()) {
            val dim = selected != null && selected != gi
            val groupLeft = axisW + slot * gi + (slot - barW * n - 3.dp.toPx() * (n - 1)) / 2f
            for ((si, v) in g.values.withIndex()) {
                val h = plotH * (v.toFloat() / max) * grow.value
                val x = groupLeft + si * (barW + 3.dp.toPx())
                drawRoundRect(colors[si].copy(alpha = if (dim) 0.35f else 1f), Offset(x, bottom - h), Size(barW, h), corner)
            }
            val t = measurer.measure(g.label, labelStyle)
            drawText(t, topLeft = Offset(axisW + slot * gi + (slot - t.size.width) / 2f, bottom + 4.dp.toPx()))
        }
        selected?.let { gi -> groups.getOrNull(gi)?.let { g ->
            val text = seriesNames.indices.joinToString("\n") { "${seriesNames[it]}  ${format(g.values.getOrElse(it) { 0 })}" }
            tooltip(measurer, "${g.label}\n$text", tipStyle, tipBg, centerX = axisW + slot * gi + slot / 2f, top = 0f)
        } }
    }
}

/**
 * A smooth area line (daily spending) with an optional faint comparison line (last month). Drag along
 * it to scrub: a guide, a dot and a tooltip follow the finger. It draws itself in from the left.
 */
@Composable
fun AreaLineChart(
    values: List<Long>,
    compare: List<Long>?,
    xLabel: (Int) -> String,
    format: (Long) -> String,
    color: Color,
    modifier: Modifier = Modifier,
    seriesName: String = "This month",
    compareName: String = "Last month",
    height: Dp = 240.dp,
) {
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(values, compare) { reveal.snapTo(0f); reveal.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    var scrub by remember(values) { mutableStateOf<Int?>(null) }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val tipBg = MaterialTheme.colorScheme.inverseSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val compareColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val surface = MaterialTheme.colorScheme.surface
    val max = (values + (compare ?: emptyList())).maxOrNull()?.coerceAtLeast(1) ?: 1L

    fun pick(x: Float, width: Float) {
        if (values.size < 2) return
        val i = (x / width * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
        if (i != scrub) { scrub = i; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    Canvas(
        modifier.fillMaxWidth().height(height)
            .pointerInput(values) { detectTapGestures { p -> if (scrub != null) scrub = null else pick(p.x, size.width.toFloat()) } }
            .pointerInput(values) { detectHorizontalDragGestures(onDragEnd = {}) { change, _ -> pick(change.position.x, size.width.toFloat()) } },
    ) {
        val top = 70.dp.toPx() // room for the tooltip above the line
        val bottom = size.height - 18.dp.toPx()
        val plotH = bottom - top
        for (f in listOf(0f, 0.5f, 1f)) {
            val y = bottom - plotH * f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = if (f == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            if (f > 0f) drawText(measurer.measure(format((max * f).toLong()), labelStyle), topLeft = Offset(0f, y - 16.dp.toPx()))
        }
        if (values.size < 2) return@Canvas
        fun pt(i: Int, v: Long) = Offset(size.width * i / (values.size - 1), bottom - plotH * (v.toFloat() / max))
        clipRect(right = size.width * reveal.value) {
            compare?.takeIf { it.size >= 2 }?.let { c ->
                drawPath(smooth(c.indices.map { pt(it, c[it]) }), compareColor, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            val pts = values.indices.map { pt(it, values[it]) }
            val line = smooth(pts)
            val fill = Path().apply { addPath(line); lineTo(pts.last().x, bottom); lineTo(pts.first().x, bottom); close() }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)), startY = top, endY = bottom))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        for (i in listOf(0, values.size / 2, values.size - 1)) {
            val t = measurer.measure(xLabel(i), labelStyle)
            drawText(t, topLeft = Offset((size.width * i / (values.size - 1) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), bottom + 2.dp.toPx()))
        }
        scrub?.let { i ->
            val p = pt(i, values[i])
            drawLine(color.copy(alpha = 0.5f), Offset(p.x, top - 6.dp.toPx()), Offset(p.x, bottom), 1.5.dp.toPx())
            drawCircle(surface, 6.dp.toPx(), p)
            drawCircle(color, 4.dp.toPx(), p)
            val lines = buildString {
                append(xLabel(i)); append("\n"); append(seriesName); append("  "); append(format(values[i]))
                compare?.getOrNull(i)?.let { append("\n"); append(compareName); append("  "); append(format(it)) }
            }
            tooltip(measurer, lines, tipStyle, tipBg, centerX = p.x, top = 0f)
        }
    }
}

private fun smooth(pts: List<Offset>): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    moveTo(pts.first().x, pts.first().y)
    for (i in 1 until pts.size) {
        val a = pts[i - 1]; val b = pts[i]
        val mx = (a.x + b.x) / 2f
        cubicTo(mx, a.y, mx, b.y, b.x, b.y)
    }
}

private fun DrawScope.tooltip(
    measurer: androidx.compose.ui.text.TextMeasurer, text: String, style: TextStyle, bg: Color, centerX: Float, top: Float,
) {
    val t = measurer.measure(text, style.copy(textAlign = TextAlign.Start))
    val pad = 8.dp.toPx()
    val w = t.size.width + pad * 2
    val h = t.size.height + pad * 2
    val left = (centerX - w / 2f).coerceIn(0f, max(0f, size.width - w))
    drawRoundRect(bg, Offset(left, top), Size(w, h), CornerRadius(10.dp.toPx()))
    drawText(t, topLeft = Offset(left + pad, top + pad))
}
