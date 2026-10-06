package com.hisaab.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Accent colours for the few branded moments (sign-in, avatars). */
object BackdropColors {
    val emerald = Color(0xFF2F5BEA)
    val sapphire = Color(0xFF7A5AE0)
    val gold = Color(0xFFC9A227)
}

/** The app background: one flat, calm colour, so cards with hairline borders carry the structure. */
@Composable
fun Modifier.appBackdrop(): Modifier = background(androidx.compose.material3.MaterialTheme.colorScheme.background)

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
