package com.hisaab.app.ui.loans

import android.net.Uri
import com.hisaab.app.ApplicationScope
import com.hisaab.app.ui.format.Periods
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.insight.Loan
import com.hisaab.shared.insight.Loans
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class LoansSnapshot(
    val loans: List<Loan> = emptyList(),
    val accounts: Map<Long, AccountWithActivity> = emptyMap(),
    val loaded: Boolean = false,
) {
    /** Loans still being repaid. */
    val active: List<Loan> get() = loans.filter { !it.closed }
    val monthlyEmiMinor: Long get() = active.sumOf { it.emiMinor ?: 0 }
    val outstandingMinor: Long get() = active.sumOf { it.outstandingMinor ?: 0 }
}

/** Every loan (loan accounts and EMI series found in the payments), worked out once and shared by Loans, Bills and More. */
@Singleton
class LoanSource @Inject constructor(
    transactions: TransactionDao,
    accounts: AccountDao,
    dismissals: LoanDismissals,
    @ApplicationScope scope: CoroutineScope,
) {
    val snapshot: StateFlow<LoansSnapshot> = combine(
        accounts.observeWithActivity(Periods.startOfMonth(System.currentTimeMillis())),
        transactions.observeLoanPayments(),
        dismissals.payees,
    ) { accs, pays, dismissed ->
        LoansSnapshot(Loans.build(accs, pays, LocalDate.now(Periods.zone), Periods.zone, dismissed), accs.associateBy { it.id }, loaded = true)
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(10_000), LoansSnapshot())
}

/** The screen for a [Loan.key]: "loan/12" for a loan account, "loan/-1?emi=…" for a detected EMI. */
fun loanRoute(key: String): String =
    if (key.startsWith("a")) "loan/${key.drop(1)}" else "loan/-1?emi=${Uri.encode(key.drop(1))}"
