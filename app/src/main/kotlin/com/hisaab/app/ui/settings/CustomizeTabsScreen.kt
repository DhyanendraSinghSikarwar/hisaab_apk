package com.hisaab.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.settings.TabLayout
import com.hisaab.app.settings.TabLayoutStore
import com.hisaab.app.settings.TabLayouts
import com.hisaab.app.settings.ThemeMode
import com.hisaab.app.settings.ThemePalette
import com.hisaab.app.settings.PaletteGroup
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.ui.components.pressScale
import com.hisaab.app.ui.theme.LocalDarkTheme
import com.hisaab.app.ui.theme.pureBlack
import com.hisaab.app.ui.theme.spec
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.Segmented
import com.hisaab.app.ui.more.MoreScaffold
import com.hisaab.app.ui.theme.Hx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomizeTabsViewModel @Inject constructor(private val store: TabLayoutStore, private val app: AppSettingsStore) : ViewModel() {
    fun setPalette(palette: ThemePalette) = viewModelScope.launch { app.setPalette(palette) }
    fun setPureBlack(value: Boolean) = viewModelScope.launch { app.setPureBlack(value) }
    val layout = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, TabLayout())
    fun move(tab: String, key: String, by: Int) = viewModelScope.launch { store.move(tab, key, by) }
    fun setVisible(tab: String, key: String, visible: Boolean) = viewModelScope.launch { store.setVisible(tab, key, visible) }
    fun reset(tab: String) = viewModelScope.launch { store.reset(tab) }
}

/** How the app looks: the theme, and each tab's sections (show or hide them, move them up or down). */
@Composable
fun CustomizeTabsRoute(onBack: () -> Unit, vm: CustomizeTabsViewModel = hiltViewModel(), settings: SettingsViewModel = hiltViewModel()) {
    val layout by vm.layout.collectAsStateWithLifecycle()
    val s by settings.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(TabLayouts.HOME) }
    MoreScaffold("Customise", onBack) { inner ->
        Column(
            Modifier.padding(top = inner.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HCard(title = "Appearance") {
                val modes = ThemeMode.entries
                Segmented(
                    modes.map { m -> m.name.lowercase().replaceFirstChar { it.uppercase() } },
                    modes.indexOf(s.app?.theme ?: ThemeMode.SYSTEM),
                    onSelect = { settings.setTheme(modes[it]) },
                )
                val pureBlack = s.app?.pureBlack ?: false
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Pure black in dark mode", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(pureBlack, { vm.setPureBlack(it) })
                }
                val selected = s.app?.palette ?: ThemePalette.CLASSIC
                PaletteGroup.entries.forEach { group ->
                    Text(group.label, style = MaterialTheme.typography.labelLarge, color = Hx.text2, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    PalettePicker(ThemePalette.entries.filter { it.group == group }, selected, pureBlack, onPick = { vm.setPalette(it) })
                }
            }

            HCard(title = "Tab sections", action = "Reset", onAction = { vm.reset(tab) }) {
                Segmented(TabLayouts.TABS.map { it.second }, TabLayouts.TABS.indexOfFirst { it.first == tab }, onSelect = { tab = TabLayouts.TABS[it].first })
                val labels = TabLayouts.DEFAULTS.getValue(tab).associate { it.key to it.label }
                val order = layout.order(tab)
                Column(Modifier.padding(top = 8.dp)) {
                    order.forEachIndexed { i, key ->
                        val shown = !layout.isHidden(tab, key)
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(labels[key] ?: key, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                color = if (shown) MaterialTheme.colorScheme.onSurface else Hx.text2)
                            IconButton(onClick = { vm.move(tab, key, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { vm.move(tab, key, 1) }, enabled = i < order.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                            Switch(shown, { vm.setVisible(tab, key, it) }, Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A row of palette swatches: each shows its surface, its hero gradient, and its name; the chosen one is ringed and ticked. */
@Composable
private fun PalettePicker(palettes: List<ThemePalette>, selected: ThemePalette, pureBlack: Boolean, onPick: (ThemePalette) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        palettes.forEach { p -> PaletteSwatch(p, p == selected, pureBlack) { onPick(p) } }
    }
}

@Composable
private fun PaletteSwatch(palette: ThemePalette, selected: Boolean, pureBlack: Boolean, onClick: () -> Unit) {
    val spec = remember(palette, pureBlack) { if (pureBlack) palette.spec.pureBlack() else palette.spec }
    val dark = LocalDarkTheme.current
    val scheme = if (dark) spec.dark else spec.light
    val hero = if (dark) spec.heroDark else spec.heroLight
    val springy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    val ring by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "ring")
    val ringWidth by animateDpAsState(if (selected) 2.dp else 0.dp, label = "ringWidth")
    val lift by animateFloatAsState(if (selected) 1.06f else 1f, springy, label = "lift")
    val label by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else Hx.text2, label = "label")
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier.width(72.dp).pressScale(press, 0.94f).clip(MaterialTheme.shapes.small)
            .clickable(press, LocalIndication.current, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).graphicsLayer { scaleX = lift; scaleY = lift }
                .border(ringWidth, ring, CircleShape).padding(4.dp)
                .clip(CircleShape).background(scheme.background).border(1.dp, scheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(hero)), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(visible = selected, enter = scaleIn(springy) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            palette.label, color = label, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
