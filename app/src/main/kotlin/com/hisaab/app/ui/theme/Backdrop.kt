package com.hisaab.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Accent colours for the few branded moments (sign-in, avatars). */
object BackdropColors {
    val emerald = Color(0xFF2F5BEA)
    val sapphire = Color(0xFF7A5AE0)
    val gold = Color(0xFFC9A227)
}

/**
 * The app background: the calm base colour, a faint vertical wash, and three soft glows (blue, violet, teal)
 * that sit below the top bars so the bars still meet the background seamlessly.
 */
@Composable
fun Modifier.appBackdrop(): Modifier {
    val dark = LocalDarkTheme.current
    val base = androidx.compose.material3.MaterialTheme.colorScheme.background
    val bottom = if (dark) Color(0xFF0B0D13) else Color(0xFFF2F3F7)
    val k = if (dark) 1f else 0.62f
    return drawWithCache {
        val w = size.width; val h = size.height
        val wash = Brush.verticalGradient(0f to base, 0.35f to base, 1f to bottom)
        val blue = Brush.radialGradient(listOf(Color(0xFF2F5BEA).copy(alpha = 0.16f * k), Color.Transparent), Offset(w * 0.05f, h * 0.30f), w * 0.85f)
        val violet = Brush.radialGradient(listOf(Color(0xFF7A5AE0).copy(alpha = 0.13f * k), Color.Transparent), Offset(w * 1.0f, h * 0.55f), w * 0.8f)
        val teal = Brush.radialGradient(listOf(Color(0xFF16A394).copy(alpha = 0.09f * k), Color.Transparent), Offset(w * 0.2f, h * 0.98f), w * 0.9f)
        onDrawBehind { drawRect(wash); drawRect(blue); drawRect(violet); drawRect(teal) }
    }
}

/** The brand gradient for hero cards: deep blue into violet. */
val HeroBrush: Brush
    @Composable get() = if (LocalDarkTheme.current) {
        Brush.linearGradient(listOf(Color(0xFF2B4BC4), Color(0xFF5B3FB8), Color(0xFF3A2A86)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF2F5BEA), Color(0xFF5A48D6), Color(0xFF7A5AE0)))
    }

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
