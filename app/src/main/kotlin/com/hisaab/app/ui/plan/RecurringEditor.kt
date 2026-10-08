package com.hisaab.app.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.AccountAvatar
import com.hisaab.app.ui.components.CategoryBadge
import com.hisaab.app.ui.components.CategorySheet
import com.hisaab.app.ui.format.Money
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.RecurringDao
import com.hisaab.shared.db.RecurringEntity
import com.hisaab.shared.insight.Recurring
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Month
import javax.inject.Inject

@HiltViewModel
class RecurringEditorViewModel @Inject constructor(private val dao: RecurringDao, accounts: AccountDao) : ViewModel() {
    val accounts = accounts.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun save(r: RecurringEntity) = viewModelScope.launch { dao.upsert(r) }
    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
}

/**
 * Add or edit a recurring payment or income. [existing] edits one the user added; [prefill] starts from one
 * DhanKosh detected, so it can be corrected and saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringSheet(existing: Recurring?, prefill: Recurring? = null, onDismiss: () -> Unit, vm: RecurringEditorViewModel = hiltViewModel()) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val start = existing ?: prefill
    var name by remember { mutableStateOf(start?.name.orEmpty()) }
    var amount by remember { mutableStateOf(start?.amountMinor?.let { (it / 100.0).toBigDecimal().stripTrailingZeros().toPlainString() }.orEmpty()) }
    var income by remember { mutableStateOf(start?.income ?: false) }
    var yearly by remember { mutableStateOf(start?.yearly ?: false) }
    var day by remember { mutableStateOf((start?.dayOfMonth ?: 1).toString()) }
    var month by remember { mutableStateOf(start?.nextDue?.monthValue ?: java.time.LocalDate.now().monthValue) }
    var category by remember { mutableStateOf(start?.category ?: Category.SUBSCRIPTIONS) }
    var accountId by remember { mutableStateOf(start?.accountId) }
    var picking by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var monthMenu by remember { mutableStateOf(false) }
    val minor = Money.parseInput(amount)?.takeIf { it > 0 }
    val dayNum = day.toIntOrNull()?.takeIf { it in 1..31 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (existing != null) t("Edit recurring") else t("Add a recurring payment or income"), style = MaterialTheme.typography.titleLarge)
            Text(t("A reminder and a plan. The real payment is still recorded from your SMS or email, so it is never counted twice."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!income, { income = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(t("Expense")) }
                SegmentedButton(income, { income = true; if (category == Category.SUBSCRIPTIONS) category = Category.SALARY }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(t("Income")) }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(if (income) t("From (e.g. Salary, Rent received)") else t("Name (e.g. Netflix, Rent, Car EMI)")) }, singleLine = true)
            OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text(t("Amount")) }, prefix = { Text("₹") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!yearly, { yearly = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(t("Every month")) }
                SegmentedButton(yearly, { yearly = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(t("Every year")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text(t("Day")) }, singleLine = true,
                    isError = dayNum == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (yearly) {
                    Column(Modifier.weight(1f)) {
                        OutlinedCard(Modifier.fillMaxWidth().clickable { monthMenu = true }) {
                            Text(t(Month.of(month).name.lowercase().replaceFirstChar { it.uppercase() }), Modifier.padding(16.dp))
                        }
                        DropdownMenu(monthMenu, { monthMenu = false }) {
                            Month.entries.forEach { m ->
                                DropdownMenuItem(text = { Text(t(m.name.lowercase().replaceFirstChar { it.uppercase() })) }, onClick = { month = m.value; monthMenu = false })
                            }
                        }
                    }
                }
            }
            OutlinedCard(Modifier.fillMaxWidth().clickable { picking = true }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category, size = 32)
                    Spacer(Modifier.width(10.dp))
                    Text(t(category.label), Modifier.weight(1f))
                    Text(t("Change"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            Column {
                val acc = accounts.firstOrNull { it.id == accountId }
                OutlinedCard(Modifier.fillMaxWidth().clickable { accountMenu = true }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (acc != null) { AccountAvatar(acc.bankName, acc.kind, acc.accountType, size = 28.dp); Spacer(Modifier.width(10.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(if (income) t("Paid into") else t("Paid from"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(acc?.let { "${it.nickname ?: it.bankName} ••${it.last4}" } ?: t("Any account"))
                        }
                    }
                }
                DropdownMenu(accountMenu, { accountMenu = false }) {
                    DropdownMenuItem(text = { Text(t("Any account")) }, onClick = { accountId = null; accountMenu = false })
                    accounts.filter { !it.hidden && it.mergedIntoId == null }.forEach { a -> DropdownMenuItem(text = { Text("${a.nickname ?: a.bankName} ••${a.last4}") }, onClick = { accountId = a.id; accountMenu = false }) }
                }
                if (!income) Text(t("With an account set, you're warned 3 days before if its balance is too low."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (existing?.manualId != null) TextButton(onClick = { vm.delete(existing.manualId!!); onDismiss() }) { Text(t("Delete"), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(t("Cancel")) }
                Button(
                    enabled = name.isNotBlank() && minor != null && dayNum != null,
                    onClick = {
                        vm.save(RecurringEntity(
                            id = existing?.manualId ?: 0, name = name.trim(), amountMinor = minor!!, income = income,
                            frequency = if (yearly) RecurringEntity.YEARLY else RecurringEntity.MONTHLY, dayOfMonth = dayNum!!,
                            month = if (yearly) month else null, category = category, accountId = accountId, createdAt = System.currentTimeMillis(),
                        ))
                        onDismiss()
                    },
                ) { Text(t("Save")) }
            }
        }
    }
    if (picking) CategorySheet(current = category, onPick = { category = it }, onDismiss = { picking = false })
}
