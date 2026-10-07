package com.hisaab.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.theme.CategoryGroup
import com.hisaab.app.ui.theme.color
import com.hisaab.parser.model.Category

/** The one place a category is chosen: grouped icon tiles in a sheet, with a quick search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySheet(
    current: Category?, onPick: (Category) -> Unit, onDismiss: () -> Unit, title: String = t("Choose a category"),
    custom: List<com.hisaab.shared.db.CustomCategoryEntity> = emptyList(),
    currentCustomId: Long? = null,
    /** Set to offer the user's own categories and a "New category" tile. */
    onPickCustom: ((com.hisaab.shared.db.CustomCategoryEntity) -> Unit)? = null,
    onCreateCustom: ((name: String, icon: String, color: Int) -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
                placeholder = { Text(t("Search categories")) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                shape = RoundedCornerShape(28.dp),
            )
        }
        val groups = CategoryGroup.entries.map { g -> g to g.members.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) || t(it.label).contains(query.trim(), ignoreCase = true) } }
            .filter { it.second.isNotEmpty() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onPickCustom != null) {
                item(key = "own", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        t("Your categories").uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                val own = custom.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                items(own, key = { "c-${it.id}" }) { c ->
                    com.hisaab.app.ui.category.Tile(IconLibrary.get(c.icon), c.name, Color(c.colorArgb), selected = c.id == currentCustomId) {
                        onPickCustom(c); onDismiss()
                    }
                }
                if (onCreateCustom != null) {
                    item(key = "new") {
                        com.hisaab.app.ui.category.Tile(androidx.compose.material.icons.Icons.Filled.Add, t("New category"), MaterialTheme.colorScheme.primary, false) {
                            creating = true
                        }
                    }
                }
            }
            groups.forEach { (group, members) ->
                item(key = "g-${group.name}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        t(group.label).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(members, key = { it.name }) { c -> CategoryTile(c, selected = c == current) { onPick(c); onDismiss() } }
            }
        }
    }
    if (creating && onCreateCustom != null) {
        com.hisaab.app.ui.category.NewItemDialog(t("New category"), withColor = true, onDismiss = { creating = false }) { name, icon, color ->
            onCreateCustom(name, icon, color); creating = false; onDismiss()
        }
    }
}

@Composable
private fun CategoryTile(c: Category, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.clip(shape)
            .then(if (selected) Modifier.border(2.dp, c.color, shape) else Modifier.border(0.dp, Color.Transparent, shape))
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CategoryBadge(c, size = 48)
        Text(t(c.label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, minLines = 2)
    }
}
