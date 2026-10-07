package com.hisaab.app.ui.invest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.AccountAvatar
import com.hisaab.app.ui.components.CollapsibleCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.loans.LoansViewModel
import com.hisaab.app.ui.theme.Hx
import com.hisaab.shared.insight.Loan
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val DUE = DateTimeFormatter.ofPattern("d MMM")

/** The loans in Portfolio: a collapsed card like Equity, one row per loan account (EMI and next due, outstanding on the right). */
@Composable
internal fun PortfolioLoansCard(accounts: List<com.hisaab.shared.db.AccountWithActivity>, onOpenLoan: (Long) -> Unit, modifier: Modifier = Modifier) {
    val loans = remember(accounts) { accounts.filter { it.isLoan && !it.hidden } }
    if (loans.isEmpty()) return
    val snap by hiltViewModel<LoansViewModel>().snapshot.collectAsStateWithLifecycle()
    val byAccount: Map<Long, Loan> = remember(snap) { snap.loans.mapNotNull { l -> l.accountId?.let { it to l } }.toMap() }
    fun owed(a: com.hisaab.shared.db.AccountWithActivity): Long? = byAccount[a.id]?.outstandingMinor ?: a.currentBalanceMinor?.let(::abs)
    val total = loans.sumOf { owed(it) ?: 0L }
    CollapsibleCard(t("Loans · {n}", "n" to loans.size), modifier, trailing = "−" + Money.format(total, showPaise = false), initiallyExpanded = false) {
        loans.forEachIndexed { i, a ->
            if (i > 0) HorizontalDivider(color = Hx.border.copy(alpha = 0.6f))
            val l = byAccount[a.id]
            HRow(
                title = a.displayName,
                subtitle = listOfNotNull(
                    l?.emiMinor?.let { t("EMI {amount}", "amount" to Money.format(it, showPaise = false)) },
                    l?.nextDue?.let { t("next {date}", "date" to it.format(DUE)) },
                    a.last4.takeIf { it.isNotBlank() }?.let { "••$it" },
                ).joinToString(" · ").ifEmpty { t("Loan") },
                leading = { AccountAvatar(a.bankName, a.kind, a.accountType, size = 36.dp) },
                onClick = { onOpenLoan(a.id) },
            ) {
                Text(owed(a)?.let { "−" + Money.format(it, showPaise = false) } ?: "—", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Hx.neg)
            }
        }
    }
}
