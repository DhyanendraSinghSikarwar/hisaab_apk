package com.hisaab.app.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.pressScale
import com.hisaab.app.ui.theme.LocalReduceMotion

/** One bottom-bar destination. */
class TabBarItem(val icon: ImageVector, val label: String, val tag: String)

/**
 * The bottom bar: icons only, on a flat surface with a hairline on top. A soft accent pill slides to the chosen
 * tab and the chosen icon gives a small spring. [selected] is -1 when no tab is chosen (the pill hides).
 * The pill is drawn in the draw phase, so the slide costs no recomposition.
 */
@Composable
fun AnimatedTabBar(items: List<TabBarItem>, selected: Int, onSelect: (Int) -> Unit) {
    val reduce = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary
    val pill = accent.copy(alpha = 0.12f)
    val target = selected.coerceAtLeast(0).toFloat()
    val pos = animateFloatAsState(
        target,
        if (reduce) snap<Float>() else spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "tab-pill",
    )
    val shown = animateFloatAsState(if (selected >= 0) 1f else 0f, label = "tab-pill-alpha")
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).height(42.dp)
                .drawBehind {
                    if (items.isEmpty()) return@drawBehind
                    val slot = size.width / items.size
                    val w = minOf(68.dp.toPx(), slot - 4.dp.toPx())
                    val x = slot * pos.value + (slot - w) / 2f
                    drawRoundRect(
                        pill.copy(alpha = pill.alpha * shown.value), Offset(x, 0f), Size(w, size.height),
                        CornerRadius(16.dp.toPx()),
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                val tint by animateColorAsState(if (on) accent else MaterialTheme.colorScheme.onSurfaceVariant, label = "tab-tint")
                val scale = remember { Animatable(1f) }
                var first by remember { mutableStateOf(true) }
                LaunchedEffect(on) {
                    if (first) { first = false; return@LaunchedEffect }
                    if (on && !reduce) {
                        scale.animateTo(1.15f, spring(stiffness = Spring.StiffnessHigh))
                        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                    }
                }
                val press = remember { MutableInteractionSource() }
                Box(
                    Modifier.weight(1f).height(42.dp).testTag(item.tag)
                        .semantics { this.selected = on }
                        .clickable(press, indication = null, role = Role.Tab) {
                            if (!on) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        item.icon, t(item.label), tint = tint,
                        modifier = Modifier.pressScale(press, 0.9f).graphicsLayer { scaleX = scale.value; scaleY = scale.value }.size(26.dp),
                    )
                }
            }
        }
    }
}
