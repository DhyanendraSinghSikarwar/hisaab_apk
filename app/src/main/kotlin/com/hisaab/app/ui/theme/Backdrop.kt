package com.hisaab.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Accent colours for the few branded moments (sign-in, avatars), taken from the palette in use. */
object BackdropColors {
    /** The palette's accent (historical name). */
    val emerald: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.heroLight.first()
    /** The palette's second hero colour (historical name). */
    val sapphire: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.heroLight[1]
    val gold = Color(0xFFC9A227)
}

/**
 * The app background: the calm base colour, a faint vertical wash, and three soft glows in the palette's
 * colours that sit below the top bars so the bars still meet the background seamlessly.
 */
@Composable
fun Modifier.appBackdrop(): Modifier {
    val dark = LocalDarkTheme.current
    val palette = LocalPalette.current
    val base = androidx.compose.material3.MaterialTheme.colorScheme.background
    val bottom = if (dark) palette.bottomDark else palette.bottomLight
    val (g1, g2, g3) = palette.glows
    val k = (if (dark) 1f else 0.62f) * palette.glowStrength
    return drawWithCache {
        val w = size.width; val h = size.height
        val wash = Brush.verticalGradient(0f to base, 0.35f to base, 1f to bottom)
        val first = Brush.radialGradient(listOf(g1.copy(alpha = 0.16f * k), Color.Transparent), Offset(w * 0.05f, h * 0.30f), w * 0.85f)
        val second = Brush.radialGradient(listOf(g2.copy(alpha = 0.13f * k), Color.Transparent), Offset(w * 1.0f, h * 0.55f), w * 0.8f)
        val third = Brush.radialGradient(listOf(g3.copy(alpha = 0.09f * k), Color.Transparent), Offset(w * 0.2f, h * 0.98f), w * 0.9f)
        onDrawBehind { drawRect(wash); drawRect(first); drawRect(second); drawRect(third) }
    }
}

/** The palette's gradient for hero cards (deep blue into violet on Classic). White text reads on all of them. */
val HeroBrush: Brush
    @Composable get() = Brush.linearGradient(heroColors())

/** The hero gradient's colours, for shadows or anything else that should match it. */
@Composable
@ReadOnlyComposable
fun heroColors(): List<Color> = LocalPalette.current.let { if (LocalDarkTheme.current) it.heroDark else it.heroLight }

/**
 * Top bars in the backdrop's own top colour, solid, so content scrolling up never shows through them. The glows
 * start below the bar, so it still blends in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun clearTopBar(): TopAppBarColors {
    val bar = androidx.compose.material3.MaterialTheme.colorScheme.background
    return TopAppBarDefaults.topAppBarColors(containerColor = bar, scrolledContainerColor = bar)
}
