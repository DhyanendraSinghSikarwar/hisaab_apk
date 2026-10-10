package com.hisaab.app.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.AccountAvatar
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.theme.Hx
import com.hisaab.parser.model.AccountKind
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountEntity
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.insight.Loans
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A payment the user just filed under EMI & Loans: existing transactions ([txIds]), or one being added for [merchant]. */
data class LoanRequest(val txIds: List<Long> = emptyList(), val merchant: String? = null)

@HiltViewModel
class LoanLinkViewModel @Inject constructor(
    source: LoanSource,
    private val store: LoanLinkStore,
    private val accounts: AccountDao,
    private val transactions: TransactionDao,
) : ViewModel() {
    val snapshot: StateFlow<LoansSnapshot> = source.snapshot

    /** The payee's display name and key, from [r] or its first transaction. */
    suspend fun payee(r: LoanRequest): Pair<String, String?> {
        r.merchant?.trim()?.takeIf { it.length >= 2 }?.let { return it to it.lowercase() }
        val tx = r.txIds.firstNotNullOfOrNull { transactions.getById(it) }
        val name = tx?.let { it.merchant ?: it.upiId }.orEmpty()
        return name to tx?.let(Loans::keyOf)
    }

    /** True (after linking the payments) when the payee already belongs to a loan, so nothing needs asking. */
    suspend fun autoLink(r: LoanRequest, key: String?): Boolean {
        val loan = key?.let { store.current().byPayee[it] } ?: return false
        if (snapshot.value.loans.none { it.accountId == loan }) return false
        store.link(r.txIds, key, loan)
        return true
    }

    /** Until the user chooses, the payee is still listed in Loans (from its first payment). */
    fun hold(key: String?) = viewModelScope.launch { key?.let { store.markManual(it) } }

    fun link(r: LoanRequest, key: String?, loanId: Long) = viewModelScope.launch { store.link(r.txIds, key, loanId) }

    fun notALoan(r: LoanRequest, key: String?) = viewModelScope.launch { store.unlink(r.txIds, key) }

    fun create(r: LoanRequest, key: String?, name: String) = viewModelScope.launch {
        val lender = name.trim().ifEmpty { return@launch }
        val existing = accounts.find(lender, "")
        val id = existing?.id ?: accounts.insert(
            AccountEntity(bankName = lender, last4 = "", kind = AccountKind.ACCOUNT, createdAt = System.currentTimeMillis(), accountType = AccountType.LOAN),
        )
        if (existing != null && existing.accountType != AccountType.LOAN) accounts.setType(id, AccountType.LOAN, null)
        if (id > 0) store.link(r.txIds, key, id)
    }
}

/**
 * Asks which loan [request] belongs to: one of the loans, a new loan named after the payee, or none. The choice is
 * remembered for the payee. Shows nothing while [request] is null; [onDone] runs once it is settled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanLinkPrompt(request: LoanRequest?, onDone: () -> Unit, vm: LoanLinkViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    var info by remember(request) { mutableStateOf<Pair<String, String?>?>(null) }
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        val p = vm.payee(r)
        if (vm.autoLink(r, p.second)) onDone() else { vm.hold(p.second); info = p }
    }
    val r = request ?: return
    val (name, key) = info ?: return
    var creating by remember(request) { mutableStateOf(false) }
    var lender by remember(request) { mutableStateOf(name) }
    val loans = snap.loans.filter { !it.detected }
    fun finish() = onDone()
    ModalBottomSheet(onDismissRequest = { finish() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(t("Which loan is this?"), style = MaterialTheme.typography.titleLarge)
            if (name.isNotBlank()) Text(name, fontSize = 13.sp, color = Hx.text2, modifier = Modifier.padding(bottom = 8.dp))
            if (creating) {
                OutlinedTextField(lender, { lender = it }, Modifier.fillMaxWidth(), label = { Text(t("Lender")) }, singleLine = true)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    TextButton(onClick = { creating = false }) { Text(t("Back")) }
                    Button(enabled = lender.isNotBlank(), onClick = { vm.create(r, key, lender); finish() }) { Text(t("Track as loan")) }
                }
            } else {
                loans.forEach { l ->
                    HRow(
                        l.name,
                        listOfNotNull(l.last4?.takeIf { it.isNotBlank() }?.let { "••$it" }, l.emiMinor?.let { t("EMI {amount}", "amount" to Money.format(it, showPaise = false)) })
                            .joinToString(" · ").ifEmpty { null },
                        leading = { AccountAvatar(l.bankName, AccountKind.ACCOUNT, AccountType.LOAN, size = 36.dp) },
                        onClick = { l.accountId?.let { vm.link(r, key, it) }; finish() },
                    )
                }
                HRow(t("New loan"), name.ifBlank { null }, leading = { Icon(Icons.Filled.Add, null, tint = Hx.accent) }, onClick = { creating = true })
                HRow(t("Not part of a loan"), null, leading = { Icon(Icons.Filled.Block, null, tint = Hx.text2) }, onClick = { vm.notALoan(r, key); finish() })
            }
        }
    }
}
