package com.hisaab.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.hisaab.app.ui.format.Money

/**
 * An amount that counts to its new value instead of jumping (about half a second). The last frame is
 * always the exact figure, so rounding during the animation never shows as the final number.
 */
@Composable
fun AnimatedAmount(
    minor: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    showPaise: Boolean = false,
) {
    val anim = remember { Animatable(minor.toFloat()) }
    LaunchedEffect(minor) { anim.animateTo(minor.toFloat(), tween(550, easing = FastOutSlowInEasing)) }
    val shown = if (anim.isRunning) anim.value.toLong() else minor
    Text(Money.format(shown, showPaise = showPaise), style = style, color = color, fontWeight = fontWeight, modifier = modifier, maxLines = 1)
}

/** A click that also gives the pressed card a slight squeeze, springing back on release. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = ripple(), enabled = enabled, onClick = onClick)
}
