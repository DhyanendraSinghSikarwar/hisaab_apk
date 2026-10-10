package com.hisaab.app.ui.accounts

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.theme.Hx
import com.hisaab.shared.repo.CardOverride
import com.hisaab.shared.repo.CardOverrideStore
import com.hisaab.shared.repo.CardPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class CardAmountsViewModel @Inject constructor(private val store: CardOverrideStore) : ViewModel() {
    fun save(cardId: Long, bank: String, last4: String, o: CardOverride, done: () -> Unit) = viewModelScope.launch {
        store.save(bank, last4, o.copy(cardId = cardId)); done()
    }

    fun reset(bank: String, last4: String, done: () -> Unit) = viewModelScope.launch { store.clear(bank, last4); done() }
}

private val SHEET_DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun rupees(minor: Long?) = minor?.let { if (it % 100 == 0L) (it / 100).toString() else "%.2f".format(it / 100.0) }.orEmpty()

/** The figures the user has set by hand: those of [p] flagged as edited, else null. */
private fun currentOverride(p: CardPosition) = Four(
    p.billedMinor.takeIf { p.billedEdited }, p.minDueMinor.takeIf { p.minDueEdited },
    p.dueDay?.toEpochDay().takeIf { p.dueEdited }, p.unbilledMinor.takeIf { p.unbilledEdited },
)

private data class Four(val billed: Long?, val minDue: Long?, val due: Long?, val unbilled: Long?)

/**
 * "Edit amounts": billed, minimum due, due date and unbilled for one card. A value left equal to the calculated one is
 * not stored; the rest override the calculated figures until a newer statement or a payment arrives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardAmountsSheet(cardId: Long, bank: String, last4: String, position: CardPosition, onDismiss: () -> Unit, vm: CardAmountsViewModel = hiltViewModel()) {
    val calc = position.calculated ?: position
    var billed by remember { mutableStateOf(rupees(position.billedMinor)) }
    var minDue by remember { mutableStateOf(rupees(position.minDueMinor)) }
    var due by remember { mutableStateOf(position.dueDay?.toEpochDay()) }
    var unbilled by remember { mutableStateOf(rupees(position.unbilledMinor)) }
    var picking by remember { mutableStateOf(false) }

    fun parse(s: String): Long? = if (s.isBlank()) null else Money.parseInput(s)
    fun bad(s: String) = s.isNotBlank() && (Money.parseInput(s)?.let { it < 0 } ?: true)
    val invalid = bad(billed) || bad(minDue) || bad(unbilled)
    // Equal to the calculated value: nothing to override.
    fun keep(v: Long?, c: Long?) = v?.takeIf { it != c }
    val next = Four(
        keep(parse(billed), calc.billedMinor), keep(parse(minDue), calc.minDueMinor),
        keep(due, calc.dueDay?.toEpochDay()), keep(parse(unbilled), calc.unbilledMinor),
    )
    val changed = next != currentOverride(position)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(t("Edit amounts"), style = MaterialTheme.typography.titleLarge)
            AmountField(t("Billed amount (₹)"), billed, { billed = it }, calc.billedMinor?.let { Money.format(it, showPaise = false) }, bad(billed), parse(billed) != calc.billedMinor) { billed = rupees(calc.billedMinor) }
            AmountField(t("Minimum due (₹, optional)"), minDue, { minDue = it }, calc.minDueMinor?.let { Money.format(it, showPaise = false) }, bad(minDue), parse(minDue) != calc.minDueMinor) { minDue = rupees(calc.minDueMinor) }
            // Payment due date.
            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            LaunchedEffect(pressed) { if (pressed) picking = true }
            Column {
                OutlinedTextField(
                    due?.let { LocalDate.ofEpochDay(it).format(SHEET_DATE) }.orEmpty(), {}, Modifier.fillMaxWidth(), readOnly = true,
                    label = { Text(t("Payment due date")) }, leadingIcon = { Icon(Icons.Filled.Event, null) }, singleLine = true, interactionSource = press,
                )
                NoteRow(calc.dueDay?.format(SHEET_DATE), due != calc.dueDay?.toEpochDay()) { due = calc.dueDay?.toEpochDay() }
            }
            AmountField(t("Unbilled amount (₹)"), unbilled, { unbilled = it }, calc.unbilledMinor?.let { Money.format(it, showPaise = false) }, bad(unbilled), parse(unbilled) != calc.unbilledMinor) { unbilled = rupees(calc.unbilledMinor) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onDismiss, Modifier.weight(1f)) { Text(t("Cancel")) }
                Button(
                    onClick = {
                        vm.save(cardId, bank, last4, CardOverride(cardId, next.billed, next.minDue, next.due, next.unbilled, System.currentTimeMillis()), onDismiss)
                    },
                    Modifier.weight(1f), enabled = changed && !invalid,
                ) { Text(t("Save")) }
            }
            if (position.edited) TextButton(onClick = { vm.reset(bank, last4, onDismiss) }, Modifier.align(Alignment.CenterHorizontally)) { Text(t("Reset all to calculated")) }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (due?.let(LocalDate::ofEpochDay) ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { due = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    picking = false
                }) { Text(t("Done")) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(t("Cancel")) } },
        ) { DatePicker(state, Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun AmountField(label: String, value: String, onValue: (String) -> Unit, calculated: String?, error: Boolean, differs: Boolean, onReset: () -> Unit) {
    Column {
        OutlinedTextField(
            value, onValue, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, isError = error,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = if (error) ({ Text(t("Enter a valid amount.")) }) else null,
        )
        NoteRow(calculated, differs, onReset)
    }
}

/** "Calculated ₹4,210" with a "Reset to calculated" action while the field differs. */
@Composable
private fun NoteRow(calculated: String?, differs: Boolean, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (calculated != null) t("Calculated {amount}", "amount" to calculated) else t("Not calculated"),
            Modifier.weight(1f), fontSize = 12.sp, color = Hx.text2, maxLines = 1,
        )
        if (differs) TextButton(onClick = onReset) { Text(t("Reset to calculated"), fontSize = 12.sp) }
    }
}
