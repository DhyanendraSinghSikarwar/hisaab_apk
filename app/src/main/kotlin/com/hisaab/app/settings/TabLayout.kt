package com.hisaab.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisaab.parser.model.HoldingKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The sections each tab can show, in their default order. Users reorder and hide them in Settings. */
object TabLayouts {
    const val HOME = "home"
    const val ANALYTICS = "analytics"
    const val PORTFOLIO = "portfolio"

    data class Section(val key: String, val label: String)

    val TABS = listOf(HOME to "Home", ANALYTICS to "Analytics", PORTFOLIO to "Portfolio")

    val DEFAULTS: Map<String, List<Section>> = mapOf(
        HOME to listOf(
            Section("budgets", "Budgets"), Section("categories", "Where it went"),
            Section("subscriptions", "Upcoming subscriptions"), Section("activity", "Activity"),
        ),
        ANALYTICS to listOf(
            Section("categories", "Where it went"), Section("merchants", "Top merchants"),
            Section("trend", "Month by month"), Section("daily", "Daily spending"),
        ),
        PORTFOLIO to HoldingKind.entries.map { Section(it.name, it.label) },
    )
}

/** Each tab's section order and which sections are hidden ("tab:key"). */
data class TabLayout(val orders: Map<String, List<String>> = emptyMap(), val hidden: Set<String> = emptySet()) {
    /** Saved order, with any section added in a later version appended at the end. */
    fun order(tab: String): List<String> {
        val known = TabLayouts.DEFAULTS[tab].orEmpty().map { it.key }
        val saved = orders[tab].orEmpty().filter { it in known }
        return saved + known.filter { it !in saved }
    }

    fun visible(tab: String): List<String> = order(tab).filter { "$tab:$it" !in hidden }
    fun isHidden(tab: String, key: String) = "$tab:$key" in hidden
}

private val Context.layoutStore: DataStore<Preferences> by preferencesDataStore(name = "tab_layout")

@Singleton
class TabLayoutStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.layoutStore

    val settings: Flow<TabLayout> = store.data.map { p ->
        TabLayout(
            orders = TabLayouts.DEFAULTS.keys.associateWith { tab -> p[orderKey(tab)]?.split(',')?.filter { it.isNotBlank() }.orEmpty() },
            hidden = p[HIDDEN].orEmpty(),
        )
    }

    /** Moves a section one place up ([by] = -1) or down (+1). */
    suspend fun move(tab: String, key: String, by: Int) = store.edit { p ->
        val current = TabLayout(orders = mapOf(tab to p[orderKey(tab)]?.split(',').orEmpty())).order(tab).toMutableList()
        val i = current.indexOf(key)
        val j = i + by
        if (i < 0 || j !in current.indices) return@edit
        current[i] = current[j].also { current[j] = current[i] }
        p[orderKey(tab)] = current.joinToString(",")
    }

    suspend fun setVisible(tab: String, key: String, visible: Boolean) = store.edit { p ->
        val set = p[HIDDEN].orEmpty().toMutableSet()
        if (visible) set -= "$tab:$key" else set += "$tab:$key"
        p[HIDDEN] = set
    }

    suspend fun reset(tab: String) = store.edit { p ->
        p.remove(orderKey(tab))
        p[HIDDEN] = p[HIDDEN].orEmpty().filterNot { it.startsWith("$tab:") }.toSet()
    }

    private fun orderKey(tab: String) = stringPreferencesKey("order_$tab")

    private companion object {
        val HIDDEN = stringSetPreferencesKey("hidden")
    }
}
