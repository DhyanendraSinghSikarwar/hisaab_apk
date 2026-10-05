package com.hisaab.app.ui.plan

import com.hisaab.app.ApplicationScope
import com.hisaab.app.ui.format.Periods
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountWithActivity
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.insight.Insight
import com.hisaab.shared.insight.Planning
import com.hisaab.shared.insight.Policy
import com.hisaab.shared.insight.Recurring
import com.hisaab.shared.insight.SavingsOutlook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A payment coming up, with whether the account it is paid from can cover it. */
data class Upcoming(
    val name: String,
    val amountMinor: Long,
    val due: LocalDate,
    val kind: Kind,
    val account: AccountWithActivity?,
    /** Known balance of the paying account is below the amount. */
    val short: Boolean,
) {
    enum class Kind { RECURRING, INSURANCE, INCOME }
    val daysLeft: Long get() = ChronoUnit.DAYS.between(LocalDate.now(Periods.zone), due)
}

data class PlanSnapshot(
    val recurring: List<Recurring> = emptyList(),
    val policies: List<Policy> = emptyList(),
    val upcoming: List<Upcoming> = emptyList(),
    val savings: SavingsOutlook? = null,
    val insights: List<Insight> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * Recurring payments, insurance, savings and insights, worked out once off the main thread and shared by
 * Home, Bills & insurance, and Analytics. Recomputed whenever transactions, accounts or holdings change.
 */
@Singleton
class PlanSource @Inject constructor(
    transactions: TransactionDao,
    accounts: AccountDao,
    holdings: HoldingDao,
    manual: com.hisaab.shared.db.RecurringDao,
    @ApplicationScope scope: CoroutineScope,
) {
    private val since = LocalDate.now(Periods.zone).minusDays(400).atStartOfDay(Periods.zone).toInstant().toEpochMilli()

    val snapshot: StateFlow<PlanSnapshot> = combine(
        transactions.observeSince(since),
        accounts.observeWithActivity(Periods.startOfMonth(System.currentTimeMillis())),
        holdings.observeAll(),
        manual.observeAll(),
    ) { txs, accs, held, mine ->
        val today = LocalDate.now(Periods.zone)
        val zone = Periods.zone
        val byId = accs.associateBy { it.id }
        fun payingAccount(id: Long?) = id?.let(byId::get)?.let { a -> a.linkedAccountId?.let(byId::get) ?: a }
        val recurring = Planning.withManual(Planning.recurring(txs, today, zone), mine, today)
        val policies = Planning.policies(txs, today, zone)
        val upcoming = (
            recurring.map { r ->
                val a = payingAccount(r.accountId)
                val bal = a?.takeIf { it.kind == com.hisaab.parser.model.AccountKind.ACCOUNT }?.currentBalanceMinor
                Upcoming(r.name, r.amountMinor, r.nextDue, if (r.income) Upcoming.Kind.INCOME else Upcoming.Kind.RECURRING, a,
                    !r.income && bal != null && bal < r.amountMinor)
            } + policies.filter { !it.monthly }.map { p ->
                Upcoming(p.insurer, p.premiumMinor, p.nextDue, Upcoming.Kind.INSURANCE, payingAccount(p.accountId), false)
            }
        ).filter { it.due >= today }.sortedBy { it.due }
        PlanSnapshot(
            recurring = recurring, policies = policies, upcoming = upcoming,
            savings = Planning.savings(txs, held, today, zone), insights = Planning.insights(txs, recurring.filter { !it.income }, today, zone), loaded = true,
        )
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(10_000), PlanSnapshot())
}
