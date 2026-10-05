package com.hisaab.app.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.settings.TabLayout
import com.hisaab.app.settings.TabLayoutStore
import com.hisaab.app.settings.TabLayouts
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

/** Each tab's sections: show or hide them, and move them up or down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeTabsRoute(onBack: () -> Unit, vm: CustomizeTabsViewModel = hiltViewModel()) {
    val layout by vm.layout.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(TabLayouts.HOME) }
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(
            colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text("Customize tabs") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { TextButton(onClick = { vm.reset(tab) }) { Text("Reset") } },
        )
    }) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TabLayouts.TABS.forEachIndexed { i, (key, label) ->
                    SegmentedButton(tab == key, { tab = key }, SegmentedButtonDefaults.itemShape(i, TabLayouts.TABS.size)) { Text(label) }
                }
            }
            val labels = TabLayouts.DEFAULTS.getValue(tab).associate { it.key to it.label }
            val order = layout.order(tab)
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.animateContentSize()) {
                    order.forEachIndexed { i, key ->
                        val shown = !layout.isHidden(tab, key)
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(labels[key] ?: key, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                color = if (shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { vm.move(tab, key, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { vm.move(tab, key, 1) }, enabled = i < order.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                            Switch(shown, { vm.setVisible(tab, key, it) }, Modifier.padding(start = 4.dp, end = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
