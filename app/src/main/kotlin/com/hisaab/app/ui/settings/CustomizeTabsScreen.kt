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
class CustomizeTabsViewModel @Inject constructor(private val store: TabLayoutStore) : ViewModel() {
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
