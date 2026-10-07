package com.hisaab.app.ui.settings

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.shared.db.ForexDao
import com.hisaab.shared.db.ForexRateEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ForexRatesViewModel @Inject constructor(private val dao: ForexDao) : ViewModel() {
    val rates = dao.observeRates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val foreign = dao.observeForeign().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(currency: String, day: LocalDate, rate: Double) = viewModelScope.launch {
        dao.upsert(ForexRateEntity(currency.uppercase(), day.toEpochDay(), rate))
        dao.recompute()
    }

    fun delete(r: ForexRateEntity) = viewModelScope.launch { dao.delete(r.currency, r.day); dao.recompute() }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Rupee rates for spends in other currencies. Each spend uses the rate of its own date, or the nearest one set. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ForexRatesRoute(onBack: () -> Unit, vm: ForexRatesViewModel = hiltViewModel()) {
    val rates by vm.rates.collectAsStateWithLifecycle()
    val foreign by vm.foreign.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text("Forex rates") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = foreign.firstOrNull()?.currency ?: "USD" }) { Icon(Icons.Filled.Add, "Add rate") } },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp, bottom = 96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (foreign.isNotEmpty()) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Your spends abroad", style = MaterialTheme.typography.titleSmall)
                            foreign.forEach { f ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${f.currency} · ${f.count}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    if (f.unpriced > 0) TextButton(onClick = { adding = f.currency }) { Text("${f.unpriced} need a rate") }
                                    else Text("All counted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Each spend uses the rate of its date, or the nearest date you set. Card markup is set on each card.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(rates, key = { "${it.currency}-${it.day}" }) { r ->
                ListItem(
                    headlineContent = { Text("1 ${r.currency} = ₹${"%.2f".format(r.inrPerUnit)}") },
                    supportingContent = { Text(LocalDate.ofEpochDay(r.day).format(DAY)) },
                    trailingContent = { IconButton(onClick = { vm.delete(r) }) { Icon(Icons.Filled.Delete, "Delete") } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    adding?.let { start ->
        RateDialog(start, foreign.map { it.currency }, onDismiss = { adding = null }) { c, d, r -> vm.save(c, d, r); adding = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RateDialog(start: String, known: List<String>, onDismiss: () -> Unit, onSave: (String, LocalDate, Double) -> Unit) {
    var currency by remember { mutableStateOf(start) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var rate by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    LaunchedEffect(pressed) { if (pressed) picking = true }
    val value = rate.toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add rate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (known + listOf("USD", "EUR", "GBP", "AED", "SGD")).distinct().forEach { c ->
                        FilterChip(currency == c, { currency = c }, label = { Text(c) })
                    }
                }
                OutlinedTextField(
                    currency, { currency = it.uppercase().filter(Char::isLetter).take(3) }, Modifier.fillMaxWidth(), label = { Text("Currency") },
                    singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    day.format(DAY), {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text("Date") },
                    leadingIcon = { Icon(Icons.Filled.Event, null) }, interactionSource = press, singleLine = true,
                )
                OutlinedTextField(
                    rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, Modifier.fillMaxWidth(),
                    label = { Text("Rupees for 1 $currency") }, prefix = { Text("₹") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(enabled = currency.length == 3 && value != null, onClick = { onSave(currency, day, value!!) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
