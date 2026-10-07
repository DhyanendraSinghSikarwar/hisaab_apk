package com.hisaab.app.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.IconLibrary
import com.hisaab.app.ui.theme.color
import com.hisaab.app.ui.theme.icon
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.CategoryDao
import com.hisaab.shared.db.CustomCategoryEntity
import com.hisaab.shared.db.CustomSubcategoryEntity
import com.hisaab.shared.insight.Subcategories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Categories and sub-categories of the user's own, and the actions that place transactions in them. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(private val dao: CategoryDao) : ViewModel() {
    val custom = dao.observeCustom().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val subs = dao.observeSubs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createCategory(name: String, icon: String, color: Int, then: (Long) -> Unit = {}) = viewModelScope.launch {
        then(dao.insertCustom(CustomCategoryEntity(name = name.trim(), icon = icon, colorArgb = color, createdAt = System.currentTimeMillis())))
    }

    fun createSub(parent: String, name: String, icon: String) = viewModelScope.launch {
        dao.insertSub(CustomSubcategoryEntity(parent = parent, name = name.trim(), icon = icon, createdAt = System.currentTimeMillis()))
    }

    fun deleteCategory(id: Long) = viewModelScope.launch {
        dao.releaseCustom(id)
        dao.deleteSubsOf(CustomSubcategoryEntity.parentOf(id))
        dao.deleteCustom(id)
    }

    fun deleteSub(id: Long) = viewModelScope.launch { dao.deleteSub(id) }
    fun setSubcategory(ids: List<Long>, name: String?) = viewModelScope.launch { dao.setSubcategory(ids, name) }
    fun setCustomCategory(ids: List<Long>, id: Long) = viewModelScope.launch { dao.setCustomCategory(ids, id) }
}

/** What a category row or tile shows, whether it is built in or the user's own. */
data class CategoryLook(val key: String, val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color) {
    companion object {
        fun of(c: Category) = CategoryLook(c.name, c.label, c.icon, c.color)
        fun of(c: CustomCategoryEntity) = CategoryLook(CustomSubcategoryEntity.parentOf(c.id), c.name, IconLibrary.get(c.icon), Color(c.colorArgb))
    }
}

/** A sub-category as shown: built in or the user's own. */
data class SubLook(val name: String, val icon: String, val customId: Long? = null)

/** The sub-categories under a category: the built-in ones, then the user's own, then Other. */
fun subsFor(parent: String, builtIn: Category?, custom: List<CustomSubcategoryEntity>): List<SubLook> {
    val own = custom.filter { it.parent == parent }.map { SubLook(it.name, it.icon, it.id) }
    val base = builtIn?.let { Subcategories.builtIn(it).map { s -> SubLook(s.name, s.icon) } }.orEmpty()
    return (base + own).distinctBy { it.name.lowercase() }
}

@Composable
fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, size: Int = 40) {
    Box(Modifier.size(size.dp).background(color.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size((size * 0.55).dp))
    }
}

/** Picks a sub-category for one category, or makes a new one. [onPick] gets null for "none". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubcategorySheet(
    look: CategoryLook, builtIn: Category?, custom: List<CustomSubcategoryEntity>, current: String?,
    onPick: (String?) -> Unit, onCreate: (name: String, icon: String) -> Unit, onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    val subs = subsFor(look.key, builtIn, custom)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(look.icon, look.color, 36)
            Column(Modifier.padding(start = 12.dp)) {
                Text(t("Sub-category"), style = MaterialTheme.typography.titleLarge)
                Text(t("in {category}", "category" to t(look.name)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(subs, key = { it.name }) { s ->
                Tile(IconLibrary.get(s.icon), t(s.name), look.color, selected = s.name == current) { onPick(s.name); onDismiss() }
            }
            item(key = "none") { Tile(Icons.Filled.Block, t("None"), MaterialTheme.colorScheme.onSurfaceVariant, selected = current == null) { onPick(null); onDismiss() } }
            item(key = "new") { Tile(Icons.Filled.Add, t("New sub-category"), MaterialTheme.colorScheme.primary, selected = false) { creating = true } }
            item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    t("DhanKosh places well-known merchants on its own (Swiggy and Zomato go to Food delivery). Pick one to override it."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
    if (creating) {
        NewItemDialog(
            title = t("New sub-category in {category}", "category" to t(look.name)), withColor = false,
            onDismiss = { creating = false },
            onCreate = { name, icon, _ -> onCreate(name, icon); onPick(name.trim()); creating = false; onDismiss() },
        )
    }
}

@Composable
fun Tile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.clip(shape)
            .then(if (selected) Modifier.border(2.dp, color, shape) else Modifier)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconBadge(icon, color, 48)
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, minLines = 2)
    }
}

private val COLORS = listOf(
    Color(0xFF0F7B5A), Color(0xFF1F5FA8), Color(0xFFC9A227), Color(0xFFE8704A), Color(0xFFD64E8C), Color(0xFF7E57C2),
    Color(0xFF00897B), Color(0xFF5C6BC0), Color(0xFF8D6E63), Color(0xFF546E7A),
)

/** Name, icon and (for categories) colour for something new. The icon grid shows everyday icons first. */
@Composable
fun NewItemDialog(title: String, withColor: Boolean, onDismiss: () -> Unit, onCreate: (name: String, icon: String, color: Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("category") }
    var color by remember { mutableStateOf(COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(t("Name")) }, singleLine = true,
                    leadingIcon = { Icon(IconLibrary.get(icon), null, tint = color) })
                if (withColor) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        COLORS.take(6).forEach { c ->
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(c).clickable { color = c }
                                    .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                            )
                        }
                    }
                }
                Text(t("Icon"), style = MaterialTheme.typography.labelLarge)
                LazyVerticalGrid(GridCells.Fixed(6), Modifier.height(240.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(IconLibrary.pickable, key = { it.first }) { (key, vector) ->
                        val selected = key == icon
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (selected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .then(if (selected) Modifier.border(2.dp, color, CircleShape) else Modifier)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vector, key, tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, icon, color.toArgb()) }, enabled = name.isNotBlank()) { Text(t("Create")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}
