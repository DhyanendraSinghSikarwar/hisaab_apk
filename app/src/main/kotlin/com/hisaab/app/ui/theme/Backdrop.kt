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

/**
 * Hisaab's own palette, deliberately unlike the purple and pink of other finance apps: deep emerald for money,
 * sapphire for trust, and a thread of gold. Light mode is a pale mint-to-ice wash; dark mode is a green-black
 * night that turns navy towards the bottom.
 */
object BackdropColors {
    val emerald = Color(0xFF0F7B5A)
    val sapphire = Color(0xFF1F5FA8)
    val gold = Color(0xFFC9A227)
}

/** Paints the app backdrop behind everything: a vertical wash with soft emerald, sapphire and gold glows. */
@Composable
fun Modifier.appBackdrop(): Modifier {
    val dark = LocalDarkTheme.current
    val top = if (dark) Color(0xFF0B1714) else Color(0xFFF2F8F5)
    val bottom = if (dark) Color(0xFF0A1220) else Color(0xFFEDF3F8)
    val glow = if (dark) 1f else 0.55f
    return drawWithCache {
        val w = size.width
        val h = size.height
        val wash = Brush.verticalGradient(listOf(top, bottom))
        val g1 = Brush.radialGradient(listOf(BackdropColors.emerald.copy(alpha = 0.30f * glow), Color.Transparent), Offset(w * 0.0f, h * 0.02f), w * 0.95f)
        val g2 = Brush.radialGradient(listOf(BackdropColors.sapphire.copy(alpha = 0.24f * glow), Color.Transparent), Offset(w * 1.0f, h * 0.22f), w * 0.9f)
        val g3 = Brush.radialGradient(listOf(BackdropColors.gold.copy(alpha = 0.10f * glow), Color.Transparent), Offset(w * 0.15f, h * 0.95f), w * 0.8f)
        onDrawBehind {
            drawRect(wash)
            drawRect(g1)
            drawRect(g2)
            drawRect(g3)
        }
    }
}

/** Top bars sit on the backdrop instead of covering it with a flat band. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun clearTopBar(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
