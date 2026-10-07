package com.hisaab.app.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.Hx
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class FilterViewModel @Inject constructor(val store: ViewFilterStore) : ViewModel()

/**
 * The global filter chips: Book (Personal, Business, All; kept across launches) and Period ("Oct 2026 ▾"). Each opens
 * a sheet; the choice applies to every tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPeriodChips(modifier: Modifier = Modifier, vm: FilterViewModel = hiltViewModel()) {
    val f by vm.store.filter.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var books by remember { mutableStateOf(false) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(t(f.book.label) + " ▾", leading = Icons.Filled.Work) { books = true }
        Pill(t(f.label) + " ▾", leading = Icons.Filled.CalendarMonth) { sheet = true }
    }
    if (books) {
        ModalBottomSheet(onDismissRequest = { books = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                Text(t("Book"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                listOf(Book.PERSONAL, Book.BUSINESS, Book.ALL).forEach { b ->
                    ListItem(
                        headlineContent = { Text(t(b.label)) }, colors = colors,
                        trailingContent = { if (f.book == b) Icon(Icons.Filled.Check, null, tint = Hx.accent) },
                        modifier = Modifier.fillMaxWidth().clickableRow { vm.store.setBook(b); books = false },
                    )
                }
                Text(t("Applies to every tab."), style = MaterialTheme.typography.bodySmall, color = Hx.text2, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                Text(t("Period"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                run {
                    val now = YearMonth.now(Periods.zone)
                    val options = listOf(
                        t("This week") to { vm.store.setKind(PeriodKind.THIS_WEEK) },
                        Periods.month(now) to { vm.store.setMonth(now) },
                        t("Last month") to { vm.store.setKind(PeriodKind.LAST_MONTH) },
                        t("This FY") to { vm.store.setKind(PeriodKind.FY) },
                        t("Last FY") to { vm.store.setKind(PeriodKind.LAST_FY) },
                        t("Last 12 months") to { vm.store.setKind(PeriodKind.LAST_12) },
                    )
                    options.forEach { (label, act) ->
                        val on = label == t(f.label) || (f.kind == PeriodKind.MONTH && f.month == now && label == Periods.month(now)) ||
                            (label == t("This FY") && f.kind == PeriodKind.FY) || (label == t("Last FY") && f.kind == PeriodKind.LAST_FY)
                        ListItem(
                            headlineContent = { Text(label) }, colors = colors,
                            trailingContent = { if (on) Icon(Icons.Filled.Check, null, tint = Hx.accent) },
                            modifier = Modifier.fillMaxWidth().clickableRow { act(); sheet = false },
                        )
                    }
                    ListItem(
                        headlineContent = { Text(t("Custom…")) }, colors = colors,
                        trailingContent = { if (f.kind == PeriodKind.CUSTOM) Icon(Icons.Filled.Check, null, tint = Hx.accent) },
                        modifier = Modifier.fillMaxWidth().clickableRow { sheet = false; custom = true },
                    )
                }
                Text(t("Applies to every tab."), style = MaterialTheme.typography.bodySmall, color = Hx.text2, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    if (custom) {
        val (a, b) = f.range
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = a.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = b.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { custom = false },
            confirmButton = {
                TextButton(enabled = state.selectedStartDateMillis != null, onClick = {
                    val s = Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                    val e = state.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: s
                    vm.store.setCustom(s, e); custom = false
                }) { Text(t("Apply")) }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { Text(t("Cancel")) } },
        ) { DateRangePicker(state, Modifier.weight(1f), title = { Text(t("Choose dates"), Modifier.padding(start = 24.dp, top = 16.dp)) }) }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
