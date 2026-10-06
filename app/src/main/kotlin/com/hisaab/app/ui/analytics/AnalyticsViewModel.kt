package com.hisaab.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.settings.TabLayoutStore
import com.hisaab.app.settings.TabLayouts
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.LedgerMath
import com.hisaab.app.ui.ledger.LedgerSlice
import com.hisaab.app.ui.ledger.LedgerSource
import com.hisaab.app.ui.ledger.MonthFlow
import com.hisaab.app.ui.ledger.PeriodKind
import com.hisaab.app.ui.ledger.SpendGroup
import com.hisaab.app.ui.ledger.ViewFilterStore
import com.hisaab.app.ui.plan.PlanSnapshot
import com.hisaab.app.ui.plan.PlanSource
import com.hisaab.app.ui.plan.Upcoming
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.BudgetDao
import com.hisaab.shared.db.BudgetEntity
import com.hisaab.shared.db.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs

/** One category's spend now and in the period before. [percent] is +100 for a new category, −100 for one that stopped. */
data class CategoryChange(val category: Category, val now: Long, val before: Long, val percent: Int)

/** Where this month's spend is heading: actual so far, the expected path from today, and its range. */
data class Forecast(
    val month: YearMonth,
    val actual: List<Long>,
    val expected: List<Long>,
    val low: List<Long>,
    val high: List<Long>,
    val likely: Long,
    val lowEnd: Long,
    val highEnd: Long,
    val billsLeft: Long,
    val dailyRate: Long,
)

/** Liquid cash projected day by day for the next 30 days (index 0 = today). */
data class CashProjection(
    val series: List<Long>,
    val start: Long,
    val lowest: Long,
    val lowestOn: LocalDate,
    val bills: Int,
    val incomes: Int,
    val dailyRate: Long,
)

/** One category budget measured over the selected period: [limit] is the monthly limit times the months the period spans. */
data class BudgetUse(val category: Category, val limit: Long, val spent: Long)

/** Budgets for the selected period. [pace] is the share of the current month gone (day/days), or null for a past period. */
data class BudgetSummary(
    val lines: List<BudgetUse> = emptyList(),
    val months: Int = 1,
    val alertPercent: Int = 80,
    val pace: Float? = null,
) {
    val limit: Long get() = lines.sumOf { it.limit }
    val spent: Long get() = lines.sumOf { it.spent }
}

data class AnalyticsData(
    val slice: LedgerSlice = LedgerSlice(),
    val spent: Long = 0,
    val income: Long = 0,
    val invested: Long = 0,
    val perDay: Long = 0,
    val categories: List<Pair<Category, Long>> = emptyList(),
    val groups: Map<SpendGroup, Long> = emptyMap(),
    val monthly: List<MonthFlow> = emptyList(),
    val salary: Long = 0,
    val otherIncome: Long = 0,
    val refunds: Long = 0,
    /** Savings rate (%) for each of the last 12 months that had income, oldest first. */
    val savingsRates: List<Pair<YearMonth, Float>> = emptyList(),
    val changes: List<CategoryChange> = emptyList(),
    val previousSpent: Long = 0,
    val previousLabel: String = "",
    val isCurrentMonth: Boolean = false,
    val budget: Long = 0,
    val budgets: BudgetSummary = BudgetSummary(),
    val forecast: Forecast? = null,
    val cash: CashProjection? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    ledger: LedgerSource,
    private val filters: ViewFilterStore,
    plans: PlanSource,
    layout: TabLayoutStore,
    budgets: BudgetDao,
    settings: AppSettingsStore,
) : ViewModel() {

    /** The Spending cards, in the order and visibility set under Settings → Customize tabs. */
    val sections: StateFlow<List<String>> = layout.settings.map { it.visible(TabLayouts.ANALYTICS) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TabLayouts.DEFAULTS.getValue(TabLayouts.ANALYTICS).map { it.key })

    val data: StateFlow<AnalyticsData> = combine(
        ledger.slice, plans.snapshot, budgets.observeAll(), settings.settings.map { it.budgetAlertPercent },
    ) { s, plan, b, alertAt ->
        AnalyticsMath.build(s, plan, b, alertAt, LocalDate.now(Periods.zone))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsData())

    fun showThisMonth() = filters.setMonth(YearMonth.now(Periods.zone))
}

/** Pure sums behind every Analysis card. Amounts are in paise. */
object AnalyticsMath {
    /** Spends that recur on a schedule; the forecast adds them on their due days rather than spreading them. */
    private val FIXED = setOf(
        Category.RENT, Category.BILLS, Category.EMI_LOAN, Category.INSURANCE, Category.SUBSCRIPTIONS, Category.TAXES, Category.EDUCATION,
    )
    private val MONTH = DateTimeFormatter.ofPattern("MMM yyyy")
    private val SHORT = DateTimeFormatter.ofPattern("d MMM")

    fun build(s: LedgerSlice, plan: PlanSnapshot, budgetList: List<BudgetEntity>, alertPercent: Int, today: LocalDate): AnalyticsData {
        if (!s.loaded) return AnalyticsData()
        val budget = budgetList.sumOf { it.monthlyLimitMinor }
        val spent = LedgerMath.spent(s.txs)
        val income = LedgerMath.income(s.txs)
        val days = (ChronoUnit.DAYS.between(s.from, minOf(s.to, today)) + 1).coerceAtLeast(1)
        val monthly = LedgerMath.monthly(s.year, s.months)

        var salary = 0L; var refunds = 0L; var other = 0L
        s.txs.filter(LedgerMath::isIncome).forEach { t ->
            val v = LedgerMath.rupees(t)
            when {
                t.category == Category.SALARY -> salary += v
                t.category == Category.REFUND || (t.merchant ?: "").contains("interest", ignoreCase = true) -> refunds += v
                else -> other += v
            }
        }

        val now = LedgerMath.byCategory(s.txs).toMap()
        val before = LedgerMath.byCategory(s.previous).toMap()
        val changes = (now.keys + before.keys).map { c ->
            val a = now[c] ?: 0L; val b = before[c] ?: 0L
            val pct = when { b == 0L -> 100; a == 0L -> -100; else -> ((a - b) * 100 / b).coerceIn(-999, 999).toInt() }
            CategoryChange(c, a, b, pct)
        }.sortedByDescending { abs(it.now - it.before) }

        val (pFrom, pTo) = s.filter.previous
        val prevLabel = if (s.filter.kind == PeriodKind.MONTH || s.filter.kind == PeriodKind.LAST_MONTH) pFrom.format(MONTH)
        else "${pFrom.format(SHORT)} – ${pTo.format(SHORT)}"

        val current = s.filter.kind == PeriodKind.MONTH && s.filter.month == YearMonth.from(today)
        val rate = if (current) discretionaryRate(s.year, today) else 0L

        return AnalyticsData(
            slice = s, spent = spent, income = income, invested = LedgerMath.invested(s.txs), perDay = spent / days,
            categories = now.toList().sortedByDescending { it.second }, groups = LedgerMath.byGroup(s.txs), monthly = monthly,
            salary = salary, otherIncome = other, refunds = refunds,
            savingsRates = monthly.filter { it.income > 0 }.map { it.month to ((it.income - it.spent) * 100f / it.income).coerceIn(-100f, 100f) },
            changes = changes, previousSpent = LedgerMath.spent(s.previous), previousLabel = prevLabel,
            isCurrentMonth = current, budget = budget,
            budgets = budgetSummary(s, now, budgetList, alertPercent, today),
            forecast = if (current) forecast(s.txs, plan, rate, today) else null,
            cash = if (current) cash(s, plan, rate, today) else null,
            loaded = true,
        )
    }

    /** Each budget against the period's spend in its category; limits scale with the calendar months the period spans. */
    private fun budgetSummary(
        s: LedgerSlice, spent: Map<Category, Long>, list: List<BudgetEntity>, alertPercent: Int, today: LocalDate,
    ): BudgetSummary {
        val months = (ChronoUnit.MONTHS.between(YearMonth.from(s.from), YearMonth.from(s.to)) + 1).toInt().coerceAtLeast(1)
        val lines = list.filter { it.monthlyLimitMinor > 0 }
            .map { BudgetUse(it.category, it.monthlyLimitMinor * months, spent[it.category] ?: 0L) }
            .sortedByDescending { it.spent.toDouble() / it.limit }
        val single = months == 1 && YearMonth.from(s.from) == YearMonth.from(today) && !s.to.isBefore(today)
        val pace = if (single) today.dayOfMonth.toFloat() / today.lengthOfMonth() else null
        return BudgetSummary(lines, months, alertPercent.takeIf { it in 1..100 } ?: 80, pace)
    }

    /** Average day's spend outside scheduled bills: the last three months blended with this month's pace. */
    fun discretionaryRate(year: List<TransactionEntity>, today: LocalDate): Long {
        val cur = YearMonth.from(today)
        val byMonth = year.filter { LedgerMath.isSpend(it) && it.category !in FIXED }
            .groupBy { YearMonth.from(Periods.localDate(it.timestamp)) }
        val prior = (1L..3L).map { cur.minusMonths(it) }.filter { byMonth[it].orEmpty().isNotEmpty() }
        val priorRate = if (prior.isEmpty()) null
        else prior.sumOf { m -> byMonth[m].orEmpty().sumOf(LedgerMath::rupees) } / prior.sumOf { it.lengthOfMonth() }
        val curRate = byMonth[cur].orEmpty().sumOf(LedgerMath::rupees) / today.dayOfMonth
        return when {
            priorRate == null -> curRate
            today.dayOfMonth < 7 -> priorRate
            else -> (priorRate + curRate) / 2
        }
    }

    private fun forecast(txs: List<TransactionEntity>, plan: PlanSnapshot, rate: Long, today: LocalDate): Forecast {
        val month = YearMonth.from(today)
        val d0 = today.dayOfMonth
        val actual = LedgerMath.cumulative(txs, month).take(d0)
        val base = actual.lastOrNull() ?: 0L
        val bills = plan.upcoming.filter { it.kind != Upcoming.Kind.INCOME && YearMonth.from(it.due) == month && !it.due.isBefore(today) }
        val remaining = month.lengthOfMonth() - d0
        fun billsBy(i: Int) = if (i == 0) 0L else bills.filter { it.due.dayOfMonth <= d0 + i }.sumOf { it.amountMinor }
        val expected = (0..remaining).map { i -> base + billsBy(i) + rate * i }
        val low = (0..remaining).map { i -> base + billsBy(i) + rate * i * 8 / 10 }
        val high = (0..remaining).map { i -> base + billsBy(i) + rate * i * 12 / 10 }
        return Forecast(
            month, actual, expected, low, high, expected.last(), low.last(), high.last(),
            billsLeft = billsBy(remaining), dailyRate = rate,
        )
    }

    private fun cash(s: LedgerSlice, plan: PlanSnapshot, rate: Long, today: LocalDate): CashProjection? {
        val liquid = s.accounts.filter { it.isLiquid && it.currentBalanceMinor != null }
        if (liquid.isEmpty()) return null
        val start = liquid.sumOf { it.currentBalanceMinor!! }
        val end = today.plusDays(30)
        val byId = s.accounts.associateBy { it.id }
        val events = LongArray(31)
        var bills = 0; var incomes = 0
        fun add(d: LocalDate, amount: Long) {
            if (d.isBefore(today) || d.isAfter(end)) return
            val i = ChronoUnit.DAYS.between(today, d).toInt().coerceAtLeast(1)
            events[i] += amount
            if (amount < 0) bills++ else incomes++
        }
        for (r in plan.recurring) {
            val acc = r.accountId?.let(byId::get)
            // A credit card's charges reach the bank only when the card bill is paid.
            if (!r.income && acc != null && acc.kind == AccountKind.CARD && !acc.isDebitCard) continue
            var d = r.nextDue
            while (!d.isAfter(end)) {
                add(d, if (r.income) r.amountMinor else -r.amountMinor)
                if (r.yearly) break
                d = d.plusMonths(1)
            }
        }
        plan.upcoming.filter { it.kind == Upcoming.Kind.INSURANCE }.forEach { add(it.due, -it.amountMinor) }
        var bal = start
        val series = (0..30).map { i -> if (i > 0) bal += events[i] - rate; bal }
        val low = series.indices.minBy { series[it] }
        return CashProjection(series, start, series[low], today.plusDays(low.toLong()), bills, incomes, rate)
    }
}
