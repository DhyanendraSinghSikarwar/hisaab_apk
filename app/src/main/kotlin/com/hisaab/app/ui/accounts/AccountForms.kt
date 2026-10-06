package com.hisaab.app.ui.accounts

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hisaab.app.ui.components.BrandMark
import com.hisaab.app.ui.components.Brands
import com.hisaab.app.ui.format.Money
import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.CardNetwork
import com.hisaab.shared.db.MaturityAction
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The names the message parsers use, so later SMS land on the same card or account. */
private val BANKS = listOf(
    "HDFC Bank", "SBI", "ICICI Bank", "Axis Bank", "Kotak Mahindra Bank", "IDFC FIRST Bank", "Yes Bank",
    "Bank of Baroda", "Punjab National Bank", "AU Small Finance Bank",
)

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")

fun maturityLine(day: Long, action: MaturityAction?): String {
    val date = LocalDate.ofEpochDay(day)
    val past = date.isBefore(LocalDate.now())
    return when {
        past && action?.closes == false -> "Renewed on ${date.format(DAY)}"
        past -> "Matured on ${date.format(DAY)}"
        else -> "Matures ${date.format(DAY)}" + (action?.let { " · " + if (it.closes) "pays out" else "renews" } ?: "")
    }
}

/** An FD or RD: the maturity date and what the bank does then. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MaturityFields(day: Long?, onDay: (Long?) -> Unit, action: MaturityAction?, onAction: (MaturityAction) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    LaunchedEffect(pressed) { if (pressed) picking = true }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            day?.let { LocalDate.ofEpochDay(it).format(DAY) }.orEmpty(), {}, Modifier.fillMaxWidth(), readOnly = true,
            label = { Text("Maturity date") }, leadingIcon = { Icon(Icons.Filled.Event, null) }, singleLine = true, interactionSource = press,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MaturityAction.entries.forEach { a -> FilterChip(action == a, { onAction(a) }, label = { Text(a.label) }) }
        }
        Text(
            if (action?.closes == false) "Stays in your list after maturity." else "Leaves your list on the maturity date.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (day?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusYears(1)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    onDay(state.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() })
                    picking = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state, Modifier.padding(top = 8.dp)) }
    }
}

/** Adds a card (or account, or deposit) by hand. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAccountSheet(kind: AccountKind, deposit: Boolean, onDismiss: () -> Unit, onAdd: (NewAccount, (Boolean) -> Unit) -> Unit) {
    var k by remember { mutableStateOf(kind) }
    var bank by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(if (kind == AccountKind.CARD) AccountType.CREDIT_CARD else if (deposit) AccountType.FD else AccountType.SAVINGS) }
    var network by remember { mutableStateOf<CardNetwork?>(null) }
    var balance by remember { mutableStateOf("") }
    var exists by remember { mutableStateOf(false) }
    val badBalance = balance.isNotBlank() && Money.parseInput(balance) == null
    val isCard = k == AccountKind.CARD

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isCard) "Add card" else "Add account", style = MaterialTheme.typography.titleLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(AccountKind.CARD to "Card", AccountKind.ACCOUNT to "Account").forEachIndexed { i, (kk, label) ->
                    SegmentedButton(k == kk, {
                        k = kk; type = if (kk == AccountKind.CARD) AccountType.CREDIT_CARD else AccountType.SAVINGS
                    }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
            OutlinedTextField(
                bank, { bank = it; exists = false }, Modifier.fillMaxWidth(), label = { Text("Bank") }, singleLine = true,
                leadingIcon = if (bank.isNotBlank()) ({ BrandMark(Brands.forBank(bank), size = 24.dp) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BANKS.forEach { b -> FilterChip(bank == b, { bank = b; exists = false }, label = { Text(b) }) }
            }
            OutlinedTextField(
                last4, { last4 = it.filter(Char::isDigit).take(4); exists = false }, Modifier.fillMaxWidth(),
                label = { Text(if (isCard) "Last 4 digits of card" else "Last 4 digits of account") }, singleLine = true,
                isError = exists, supportingText = if (exists) ({ Text("Already in your list.") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            Label("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AccountType.forKind(k).forEach { t -> FilterChip(type == t, { type = t }, label = { Text(t.label) }) }
            }
            if (isCard) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardNetwork.entries.forEach { n ->
                        FilterChip(network == n, { network = if (network == n) null else n }, label = { Text(n.label) },
                            leadingIcon = { BrandMark(Brands.forNetwork(n), size = 18.dp) })
                    }
                }
            }
            if (!(isCard && type == AccountType.DEBIT_CARD)) {
                OutlinedTextField(
                    balance, { balance = it }, Modifier.fillMaxWidth(), singleLine = true, isError = badBalance, prefix = { Text("₹") },
                    label = { Text(if (isCard) "Available limit (optional)" else "Balance (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { onAdd(NewAccount(k, bank, last4, type, network.takeIf { isCard }, balance)) { ok -> if (ok) onDismiss() else exists = true } },
                    enabled = bank.isNotBlank() && last4.length == 4 && !badBalance,
                ) { Text("Add") }
            }
        }
    }
}
