package com.hisaab.app.ui.profile

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.theme.LocalDarkTheme
import com.hisaab.app.ui.theme.heroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Up to three vivid colours that characterise the photo at [path], most prominent first, or null for a photo
 * with no real colour (greyscale, very dark, blown out). Samples a ~64 px thumbnail and buckets pixels by hue,
 * weighting each by how saturated and bright it is. Call off the main thread.
 */
internal fun photoPalette(path: String): List<Color>? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return@runCatching null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 64) sample *= 2
    val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
    val w = bmp.width; val h = bmp.height
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    bmp.recycle()

    val bins = 24 // 15° of hue each
    val weight = FloatArray(bins)
    val r = FloatArray(bins); val g = FloatArray(bins); val b = FloatArray(bins)
    val hsv = FloatArray(3)
    var total = 0f
    for (p in px) {
        android.graphics.Color.colorToHSV(p, hsv)
        val s = hsv[1]; val v = hsv[2]
        if (v < 0.18f || s < 0.16f || (v > 0.96f && s < 0.22f)) continue // near-black, grey, near-white
        val bin = (hsv[0] / 15f).toInt().coerceIn(0, bins - 1)
        val wt = s * s * v
        weight[bin] += wt
        r[bin] += android.graphics.Color.red(p) * wt
        g[bin] += android.graphics.Color.green(p) * wt
        b[bin] += android.graphics.Color.blue(p) * wt
        total += wt
    }
    if (total <= 0f) return@runCatching null

    val chosen = mutableListOf<Int>()
    for (bin in (0 until bins).sortedByDescending { weight[it] }) {
        if (weight[bin] < total * 0.04f || chosen.size == 3) break
        // Keep picks at least two bins (30°) apart so the gradient has some range.
        if (chosen.any { val d = abs(it - bin); min(d, bins - d) < 2 }) continue
        chosen += bin
    }
    if (chosen.isEmpty()) return@runCatching null
    val colors = chosen.map { Color(r[it] / weight[it] / 255f, g[it] / weight[it] / 255f, b[it] / weight[it] / 255f) }.toMutableList()
    // One dominant hue: derive companions by shifting it, so the wash still has depth.
    while (colors.size < 3) colors += shift(colors.first(), hueBy = if (colors.size == 1) 28f else -22f, valueBy = if (colors.size == 1) 0.82f else 1.1f)
    colors
}.getOrNull()

private fun shift(c: Color, hueBy: Float, valueBy: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
    hsv[0] = (hsv[0] + hueBy + 360f) % 360f
    hsv[2] = (hsv[2] * valueBy).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * The profile hero: a large rounded panel washed in the photo's own colours (or the theme's hero gradient when
 * there is no photo), with the avatar in a ring, a camera button, the name and a one-line summary.
 */
@Composable
fun ProfileHeader(
    name: String,
    photoPath: String?,
    subtitle: String,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val surface = MaterialTheme.colorScheme.surface
    val hero = heroColors()
    val fallback = listOf(hero.first(), hero[hero.size / 2], hero.last())

    val vivid by produceState<List<Color>?>(null, photoPath) {
        value = photoPath?.let { withContext(Dispatchers.Default) { photoPalette(it) } }
    }
    // Blend the photo colours toward the surface so text stays readable in either theme.
    val target = vivid?.map { lerp(it, surface, if (dark) 0.5f else 0.52f) } ?: fallback
    val spec = tween<Color>(700, easing = FastOutSlowInEasing)
    val c0 by animateColorAsState(target[0], spec, label = "hero0")
    val c1 by animateColorAsState(target[1], spec, label = "hero1")
    val c2 by animateColorAsState(target[2], spec, label = "hero2")
    val onHero = if (vivid == null) Color.White
    else if (target.map { it.luminance() }.average() > 0.42) Color(0xFF15171C) else Color.White
    val content by animateColorAsState(onHero, spec, label = "onHero")
    val ring = vivid ?: listOf(Color.White, Color.White.copy(alpha = 0.6f), Color.White)

    // A gentle rise on first show.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }

    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxWidth()
            .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 24.dp.toPx() }
            .shadow(18.dp, shape, ambientColor = c1.copy(alpha = 0.3f), spotColor = c2.copy(alpha = 0.4f))
            .clip(shape)
            .drawBehind {
                drawRect(Brush.linearGradient(listOf(c0, c1, c2), start = Offset.Zero, end = Offset(size.width, size.height)))
                // Soft light from the top and two faint rings, as on the other hero cards.
                drawRect(Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (dark) 0.08f else 0.22f), Color.Transparent),
                    Offset(size.width * 0.5f, 0f), size.width * 0.75f,
                ))
                drawCircle(content.copy(alpha = 0.06f), size.height * 0.75f, Offset(size.width * 1.05f, -size.height * 0.1f))
                drawCircle(content.copy(alpha = 0.04f), size.height * 0.45f, Offset(-size.width * 0.05f, size.height * 1.05f))
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Box(
                        Modifier.size(124.dp)
                            .graphicsLayer { scaleX = 0.9f + 0.1f * enter.value; scaleY = scaleX }
                            .border(3.dp, Brush.sweepGradient(ring + ring.first()), CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onPickPhoto),
                        contentAlignment = Alignment.Center,
                    ) { ProfileAvatar(name.ifBlank { t("You") }, photoPath, 112.dp, ring = false) }
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-2).dp).size(38.dp)
                            .shadow(6.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(3.dp, surface, CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onPickPhoto),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CameraAlt, t("Choose photo"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp)) }
                }
                Text(
                    name.ifBlank { t("Your name") }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
                )
                AnimatedVisibility(photoPath != null, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
                    Row(
                        Modifier.padding(top = 14.dp).clip(RoundedCornerShape(50))
                            .background(content.copy(alpha = 0.12f))
                            .clickable(onClick = onRemovePhoto)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(16.dp))
                        Text(t("Remove photo"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
