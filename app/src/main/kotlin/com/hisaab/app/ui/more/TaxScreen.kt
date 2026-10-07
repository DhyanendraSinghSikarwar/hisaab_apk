package com.hisaab.app.ui.more

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ApplicationScope
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.SplitBar
import com.hisaab.app.ui.components.Tag
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.ledger.LedgerMath
import com.hisaab.app.ui.ledger.ViewFilter
import com.hisaab.app.ui.theme.Hx
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.HoldingEntity
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.InsuranceKind
import com.hisaab.shared.insight.Planning
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// The arithmetic. All amounts are paise.
// ---------------------------------------------------------------------------------------------

/** One regime's working, from gross income to the tax payable. */
data class RegimeTax(
    val gross: Long,
    val standardDeduction: Long,
    val deductions: Long,
    val taxable: Long,
    val slabTax: Long,
    val rebate: Long,
    val cess: Long,
) {
    val total: Long get() = slabTax - rebate + cess
}

/** One slab of a regime as it applies to a taxable income. [to] is null for the open-ended top slab. */
data class SlabLine(val from: Long, val to: Long?, val rate: Int, val taxedPart: Long, val tax: Long)

/** Indian income tax for a resident individual under 60, without surcharge. */
object TaxMath {
    const val LAKH = 100_000L * 100

    /** Upper bound of each slab and its rate in percent; the last slab is open-ended. */
    val NEW = listOf(4 * LAKH to 0, 8 * LAKH to 5, 12 * LAKH to 10, 16 * LAKH to 15, 20 * LAKH to 20, 24 * LAKH to 25, Long.MAX_VALUE to 30)
    val OLD = listOf(250_000L * 100 to 0, 5 * LAKH to 5, 10 * LAKH to 20, Long.MAX_VALUE to 30)

    const val NEW_STANDARD = 75_000L * 100
    const val OLD_STANDARD = 50_000L * 100
    const val LIMIT_80C = 150_000L * 100
    /** Health cover for yourself and family, under 60. */
    const val LIMIT_80D = 25_000L * 100
    private const val NEW_REBATE_UPTO = 12 * LAKH
    private const val OLD_REBATE_UPTO = 5 * LAKH

    fun slabTax(taxable: Long, slabs: List<Pair<Long, Int>>): Long {
        var lower = 0L
        var tax = 0.0
        for ((upper, rate) in slabs) {
            if (taxable <= lower) break
            tax += (min(taxable, upper) - lower) * rate / 100.0
            lower = upper
        }
        return roundRupee(tax)
    }

    /** Each slab [taxable] reaches: its bounds, the part of income taxed in it, and the tax on that part (before rebate and cess). */
    fun slabLines(taxable: Long, slabs: List<Pair<Long, Int>>): List<SlabLine> = buildList {
        var lower = 0L
        for ((upper, rate) in slabs) {
            if (taxable <= lower) break
            val part = min(taxable, upper) - lower
            add(SlabLine(lower, upper.takeIf { it != Long.MAX_VALUE }, rate, part, roundRupee(part * rate / 100.0)))
            lower = upper
        }
    }

    /** 80CCD(1B), your own NPS contribution over and above 80C. */
    const val LIMIT_NPS = 50_000L * 100
    /** 24(b), interest on a home loan for a self-occupied house. */
    const val LIMIT_HOME_LOAN = 200_000L * 100

    /** [employerNps] is 80CCD(2), the one deduction the new regime allows. */
    fun newRegime(income: Long, employerNps: Long = 0): RegimeTax {
        val afterStd = max(0L, income - NEW_STANDARD)
        val ded = min(afterStd, max(0L, employerNps))
        val taxable = afterStd - ded
        val base = slabTax(taxable, NEW)
        // Section 87A: nil tax up to ₹12 lakh; just above it, marginal relief caps the tax at the income over ₹12 lakh.
        val rebate = if (taxable <= NEW_REBATE_UPTO) base else max(0L, base - (taxable - NEW_REBATE_UPTO))
        val cess = roundRupee((base - rebate) * 0.04)
        return RegimeTax(income, min(income, NEW_STANDARD), ded, taxable, base, rebate, cess)
    }

    fun oldRegime(
        income: Long, c80: Long, d80: Long,
        nps: Long = 0, homeLoan: Long = 0, hraOther: Long = 0, employerNps: Long = 0,
    ): RegimeTax {
        val afterStd = max(0L, income - OLD_STANDARD)
        val claimed = min(c80, LIMIT_80C) + min(d80, LIMIT_80D) + min(nps, LIMIT_NPS) + min(homeLoan, LIMIT_HOME_LOAN) +
            max(0L, hraOther) + max(0L, employerNps)
        val ded = min(afterStd, claimed)
        val taxable = afterStd - ded
        val base = slabTax(taxable, OLD)
        val rebate = if (taxable <= OLD_REBATE_UPTO) base else 0L
        val cess = roundRupee((base - rebate) * 0.04)
        return RegimeTax(income, min(income, OLD_STANDARD), ded, taxable, base, rebate, cess)
    }

    private fun roundRupee(paise: Double): Long = Math.round(paise / 100.0) * 100
}

// ---------------------------------------------------------------------------------------------
// The estimate, worked out only from what Artha tracked: bank credits, investment debits, insurance
// premiums and EPF passbook updates. Figures typed into the what-if calculator live only in TaxViewModel
// for the current visit; nothing typed is ever saved.
// ---------------------------------------------------------------------------------------------

/** One source of a deduction, for the "where this comes from" list. */
data class TaxItem(val label: String, val amountMinor: Long)

data class TaxState(
    val fyStartYear: Int = ViewFilter.fyStart(LocalDate.now()).year,
    val salarySoFar: Long = 0,
    val salaryMonths: Int = 0,
    /** Other income credits so far, as calculated. */
    val otherIncome: Long = 0,
    /** Salary projected to twelve months, as calculated. */
    val projectedSalary: Long = 0,
    /** Salary and other income used for the estimate: calculated, or the what-if values. */
    val income: Long = 0,
    val items80C: List<TaxItem> = emptyList(),
    val items80D: List<TaxItem> = emptyList(),
    /** Investments this year that do not qualify for 80C (equity funds, stocks…), shown for context. */
    val otherInvested: Long = 0,
    /** What-if figures typed in for this visit; empty means the calculated estimate. Never saved. */
    val whatIf: TaxWhatIf = TaxWhatIf(),
    val newRegime: RegimeTax = TaxMath.newRegime(0),
    val oldRegime: RegimeTax = TaxMath.oldRegime(0, 0, 0),
    val loaded: Boolean = false,
) {
    val fyLabel: String get() = "FY $fyStartYear-${(fyStartYear + 1) % 100}"
    /** "FY 2026-27" with a four-digit start and two-digit end, for the shared report. */
    val fyLong: String get() = "FY $fyStartYear-${"%02d".format((fyStartYear + 1) % 100)}"
    val c80Calc: Long get() = items80C.sumOf { it.amountMinor }
    val d80Calc: Long get() = items80D.sumOf { it.amountMinor }
    val c80: Long get() = whatIf.c80 ?: c80Calc
    val d80: Long get() = whatIf.d80 ?: d80Calc
    val salaryUsed: Long get() = whatIf.salary ?: projectedSalary
    val otherIncomeUsed: Long get() = whatIf.otherIncome ?: otherIncome
    val nps: Long get() = whatIf.nps ?: 0
    val homeLoan: Long get() = whatIf.homeLoan ?: 0
    val hraOther: Long get() = whatIf.hraOther ?: 0
    val employerNps: Long get() = whatIf.employerNps ?: 0
    val newIsBetter: Boolean get() = newRegime.total <= oldRegime.total
    val saving: Long get() = kotlin.math.abs(newRegime.total - oldRegime.total)
    val left80C: Long get() = max(0L, TaxMath.LIMIT_80C - c80)
    val hasIncome: Boolean get() = income > 0

    /** This state with the what-if figures [o] applied: income and both regimes recomputed. Empty [o] gives the calculated estimate. */
    fun withWhatIf(o: TaxWhatIf): TaxState {
        val s = copy(whatIf = o)
        val income = s.salaryUsed + s.otherIncomeUsed
        return s.copy(
            income = income,
            newRegime = TaxMath.newRegime(income, s.employerNps),
            oldRegime = TaxMath.oldRegime(income, s.c80, s.d80, s.nps, s.homeLoan, s.hraOther, s.employerNps),
        )
    }
}

/** The current financial year's calculated estimate, shared by the Tax centre and the More list. Never holds what-if figures. */
@Singleton
class TaxSource @Inject constructor(
    transactions: TransactionDao,
    holdings: HoldingDao,
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    init {
        // Earlier versions saved typed tax figures; they are no longer used, so the old file is removed.
        scope.launch(Dispatchers.IO) { runCatching { File(context.filesDir, "datastore/tax_overrides.preferences_pb").delete() } }
    }

    private val fyStart: LocalDate = ViewFilter.fyStart(LocalDate.now(Periods.zone))
    private val fyFrom = millis(fyStart)
    private val fyTo = millis(fyStart.plusYears(1)) - 1

    val state: StateFlow<TaxState> = combine(
        transactions.observeBetween(fyFrom, fyTo), holdings.observeAll(),
    ) { all, held ->
        val txs = all.filter { !it.needsReview }
        val salary = txs.filter { LedgerMath.isIncome(it) && it.category == Category.SALARY }
        val salarySoFar = salary.sumOf(LedgerMath::rupees)
        val months = salary.map { YearMonth.from(Periods.localDate(it.timestamp)) }.distinct().size
        // Salary lands once a month: scale what has arrived up to twelve months. Other income counts as it is.
        val projected = if (months in 1..11) salarySoFar * 12 / months else salarySoFar
        val other = txs.filter { LedgerMath.isIncome(it) && it.category == Category.INCOME }.sumOf(LedgerMath::rupees)
        val income = projected + other

        val invest = txs.filter(LedgerMath::isInvest)
        val qualifying = invest.filter { QUALIFIES_80C.containsMatchIn(text(it)) }
        val premiums = txs.filter { it.category == Category.INSURANCE && it.type != TransactionType.CREDIT && it.type != TransactionType.TRANSFER }
        val life = premiums.filter { Planning.kindOf(text(it)) == InsuranceKind.LIFE }
        val health = premiums.filter { Planning.kindOf(text(it)) == InsuranceKind.HEALTH }

        val items80C = buildList {
            qualifying.groupBy { label80C(text(it)) }.forEach { (l, list) -> add(TaxItem(l, list.sumOf(LedgerMath::rupees))) }
            if (life.isNotEmpty()) add(TaxItem("Life insurance premiums", life.sumOf(LedgerMath::rupees)))
            val epf = held.filter { it.kind == HoldingKind.EPF }.sumOf(::epfShareThisYear)
            if (epf > 0) add(TaxItem("EPF, your share (estimated)", epf))
        }.filter { it.amountMinor > 0 }
        val items80D = listOf(TaxItem("Health insurance premiums", health.sumOf(LedgerMath::rupees))).filter { it.amountMinor > 0 }
        val c80 = items80C.sumOf { it.amountMinor }
        val d80 = items80D.sumOf { it.amountMinor }
        val qualifyingIds = qualifying.map { it.id }.toSet()
        TaxState(
            fyStartYear = fyStart.year, salarySoFar = salarySoFar, salaryMonths = months, otherIncome = other,
            projectedSalary = projected, income = income,
            items80C = items80C, items80D = items80D,
            otherInvested = invest.filter { it.id !in qualifyingIds }.sumOf(LedgerMath::rupees),
            newRegime = TaxMath.newRegime(income), oldRegime = TaxMath.oldRegime(income, c80, d80), loaded = true,
        )
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(10_000), TaxState())

    /**
     * The passbook growth of an EPF account that falls inside this financial year, times the employee's part of
     * each deposit (12 of every 15.67; the rest is the employer's). Only when two passbook figures are known.
     */
    private fun epfShareThisYear(h: HoldingEntity): Long {
        val now = h.valueMinor ?: return 0
        val before = h.previousValueMinor ?: return 0
        val at = h.asOf ?: return 0
        val prevAt = h.previousAsOf ?: return 0
        if (now <= before || at <= prevAt || at < fyFrom) return 0
        val overlap = (min(at, fyTo) - max(prevAt, fyFrom)).coerceAtLeast(0)
        val share = (now - before).toDouble() * overlap / (at - prevAt)
        return (share * 12.0 / 15.67).toLong()
    }

    private fun millis(d: LocalDate) = d.atStartOfDay(Periods.zone).toInstant().toEpochMilli()

    private companion object {
        val QUALIFIES_80C = Regex(
            """elss|tax\s?saver|tax\s?saving|\bppf\b|public provident|sukanya|\bssy\b|\bnsc\b|national savings""",
            RegexOption.IGNORE_CASE,
        )
        val PPF = Regex("""\bppf\b|public provident""", RegexOption.IGNORE_CASE)
        val SSY = Regex("""sukanya|\bssy\b""", RegexOption.IGNORE_CASE)
        val NSC = Regex("""\bnsc\b|national savings""", RegexOption.IGNORE_CASE)

        fun text(t: TransactionEntity) = listOfNotNull(t.merchant, t.note, t.upiId).joinToString(" ")

        fun label80C(text: String): String = when {
            PPF.containsMatchIn(text) -> "PPF deposits"
            SSY.containsMatchIn(text) -> "Sukanya Samriddhi"
            NSC.containsMatchIn(text) -> "NSC"
            else -> "ELSS tax-saver funds"
        }
    }
}

@HiltViewModel
class TaxViewModel @Inject constructor(source: TaxSource) : ViewModel() {
    /** What-if figures for this visit only: they go when the screen is left and are never written anywhere. */
    private val whatIf = MutableStateFlow(TaxWhatIf())

    val state: StateFlow<TaxState> = combine(source.state, whatIf) { s, o -> if (o.any) s.withWhatIf(o) else s }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), source.state.value)

    fun calculate(o: TaxWhatIf) { whatIf.value = o }

    fun backToCalculated() { whatIf.value = TaxWhatIf() }
}

// ---------------------------------------------------------------------------------------------
// The screen.
// ---------------------------------------------------------------------------------------------

@Composable
fun TaxRoute(onBack: () -> Unit, vm: TaxViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    MoreScaffold(
        "Tax centre", onBack,
        actions = {
            if (s.loaded) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Calculate, "What-if calculator") }
                IconButton(onClick = { sharing = true }, enabled = s.hasIncome) { Icon(Icons.Outlined.Share, "Share estimate") }
            }
        },
    ) { inner ->
        if (!s.loaded) return@MoreScaffold
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            if (s.whatIf.any) item("whatif") { WhatIfBanner(onEdit = { editing = true }, onReset = vm::backToCalculated) }
            item("summary") { Summary(s, onShare = { sharing = true }) }
            item("income") { IncomeCard(s) }
            item("deductions") { DeductionsCard(s) }
            item("breakdown") { Breakdown(s) }
        }
    }
    if (editing && s.loaded) TaxEditSheet(s, onDismiss = { editing = false }, onCalculate = vm::calculate, onReset = vm::backToCalculated)
    if (sharing && s.loaded) TaxShareFlow(s, onDone = { sharing = false })
}

/** Shown while what-if figures replace the calculated ones. They last only for this visit. */
@Composable
private fun WhatIfBanner(onEdit: () -> Unit, onReset: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Hx.accentSoft).clickable(onClick = onEdit)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Calculate, null, tint = Hx.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("What-if", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Hx.accent)
            Text("Tax for the figures you entered. Not saved.", fontSize = 12.sp, color = Hx.text2)
        }
        TextButton(onClick = onReset) { Text("Back to calculated") }
    }
}

@Composable
private fun Summary(s: TaxState, onShare: () -> Unit) {
    HCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.fyLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (s.whatIf.any) { Tag("What-if", Hx.accent); Spacer(Modifier.width(6.dp)) }
            Tag("Estimate", Hx.warn)
            com.hisaab.app.ui.components.InfoButton(
                "About this estimate",
                "Worked out from what Artha tracked this year, for a resident individual under 60.",
                "It leaves out surcharge, capital gains and deductions Artha cannot see. Check with a tax professional before you file.",
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RegimeTile("New regime", s.newRegime.total, best = s.newIsBetter && s.hasIncome && s.saving > 0, Modifier.weight(1f))
            RegimeTile("Old regime", s.oldRegime.total, best = !s.newIsBetter && s.hasIncome, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                !s.hasIncome -> "No salary or income credits found for ${s.fyLabel} yet."
                s.saving == 0L -> "Both regimes come to the same tax."
                else -> "${if (s.newIsBetter) "New" else "Old"} regime saves ${Money.format(s.saving, showPaise = false)}"
            },
            color = if (s.hasIncome && s.saving > 0) Hx.pos else Hx.text2, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        )
        val top = max(s.newRegime.total, s.oldRegime.total)
        if (s.hasIncome && top > 0) {
            Spacer(Modifier.height(10.dp))
            BarLine("New", s.newRegime.total.toFloat() / top, Hx.accent)
            Spacer(Modifier.height(4.dp))
            BarLine("Old", s.oldRegime.total.toFloat() / top, Hx.palette[1])
        }
        if (s.hasIncome) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share this estimate")
            }
        }
    }
}

@Composable
private fun BarLine(label: String, fraction: Float, color: Color) {
    val f by animateFloatAsState(fraction, tween(600), label = "bar")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = Hx.text2, modifier = Modifier.width(32.dp))
        SplitBar(listOf(f to color), Modifier.weight(1f), height = 6.dp)
    }
}

@Composable
private fun RegimeTile(label: String, tax: Long, best: Boolean, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(if (best) Hx.accentSoft else Hx.surface2).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = Hx.text2, modifier = Modifier.weight(1f))
            AnimatedVisibility(best) { Tag("Lower", Hx.pos) }
        }
        AnimatedContent(tax, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tax") { t ->
            Text(Money.format(t, showPaise = false), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun IncomeCard(s: TaxState) {
    HCard(title = "Income", titleInfo = {
        com.hisaab.app.ui.components.InfoButton(
            "Salary", "Salary credits are what reached your bank, after TDS and PF, so your taxable salary is likely higher.",
            "Use the calculator (top right) to try your gross salary. What-if figures are not saved.",
        )
    }) {
        ItemRow("Salary received", s.salarySoFar, if (s.salaryMonths > 0) "${s.salaryMonths} month${if (s.salaryMonths == 1) "" else "s"} so far" else "None found yet")
        if (s.salaryMonths in 1..11) ItemRow("Projected for the year", s.salarySoFar * 12 / s.salaryMonths, "At the same monthly pay")
        s.whatIf.salary?.let { ItemRow("Annual gross salary", it, "Calculated: ${Money.format(s.projectedSalary, showPaise = false)}", edited = true) }
        val other = s.whatIf.otherIncome
        if (other != null) ItemRow("Other income", other, "Calculated: ${Money.format(s.otherIncome, showPaise = false)}", edited = true)
        else if (s.otherIncome > 0) ItemRow("Other income", s.otherIncome, null)
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Hx.border)
        ItemRow("Gross income used", s.income, null, bold = true)
    }
}

@Composable
private fun DeductionsCard(s: TaxState) {
    HCard(title = "Deductions · old regime") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("80C", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (s.whatIf.c80 != null) { Spacer(Modifier.width(6.dp)); Tag("What-if", Hx.accent) }
            Spacer(Modifier.weight(1f))
            Text("${Money.format(min(s.c80, TaxMath.LIMIT_80C), showPaise = false)} of ₹1,50,000", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        val used by animateFloatAsState(min(s.c80, TaxMath.LIMIT_80C).toFloat() / TaxMath.LIMIT_80C, tween(700), label = "80c")
        SplitBar(listOf(used to Hx.pos), height = 6.dp)
        Text(
            if (s.left80C > 0) "80C left ${Money.format(s.left80C, showPaise = false)}" else "80C used in full",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (s.left80C > 0) Hx.warn else Hx.pos,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        if (s.whatIf.c80 != null) {
            Text("What-if value. Calculated from tracked payments: ${Money.format(s.c80Calc, showPaise = false)}", fontSize = 12.sp, color = Hx.text2)
        }
        if (s.items80C.isEmpty()) {
            Text("No PPF, ELSS, life cover or EPF payments found this year.", fontSize = 12.sp, color = Hx.text2)
        } else {
            s.items80C.forEach { ItemRow(it.label, it.amountMinor, null) }
        }
        if (s.otherInvested > 0) {
            Text(
                "${Money.format(s.otherInvested, showPaise = false)} of other investments this year does not count for 80C.",
                fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(top = 4.dp),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Hx.border)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("80D", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (s.whatIf.d80 != null) { Spacer(Modifier.width(6.dp)); Tag("What-if", Hx.accent) }
            Spacer(Modifier.weight(1f))
            Text("${Money.format(min(s.d80, TaxMath.LIMIT_80D), showPaise = false)} of ₹25,000", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (s.whatIf.d80 != null) {
            Text(
                "What-if value. Calculated from tracked premiums: ${Money.format(s.d80Calc, showPaise = false)}",
                fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (s.items80D.isEmpty()) {
            Text("No health insurance premiums found this year.", fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(top = 4.dp))
        } else {
            s.items80D.forEach { ItemRow(it.label, it.amountMinor, null) }
        }
        val o = s.whatIf
        if (listOf(o.nps, o.homeLoan, o.hraOther, o.employerNps).any { it != null }) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Hx.border)
            o.nps?.let { ItemRow("NPS 80CCD(1B)", min(it, TaxMath.LIMIT_NPS), capNote(it, TaxMath.LIMIT_NPS, "₹50,000"), edited = true) }
            o.homeLoan?.let { ItemRow("Home-loan interest 24(b)", min(it, TaxMath.LIMIT_HOME_LOAN), capNote(it, TaxMath.LIMIT_HOME_LOAN, "₹2,00,000"), edited = true) }
            o.hraOther?.let { ItemRow("HRA exemption / other", it, "Old regime only", edited = true) }
            o.employerNps?.let { ItemRow("Employer NPS 80CCD(2)", it, "Counts in both regimes", edited = true) }
        }
    }
}

private fun capNote(entered: Long, cap: Long, capText: String): String =
    if (entered > cap) "You entered ${Money.format(entered, showPaise = false)}; capped at $capText" else "Up to $capText · old regime only"

@Composable
private fun ItemRow(label: String, amount: Long, sub: String?, bold: Boolean = false, edited: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f, fill = false))
                if (edited) { Spacer(Modifier.width(6.dp)); Tag("What-if", Hx.accent) }
            }
            if (sub != null) Text(sub, fontSize = 11.sp, color = Hx.text2)
        }
        Text(Money.format(amount, showPaise = false), fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun Breakdown(s: TaxState) {
    HCard(title = "How it adds up") {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.4f))
            Text("New", fontSize = 12.sp, color = Hx.text2, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("Old", fontSize = 12.sp, color = Hx.text2, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        val n = s.newRegime
        val o = s.oldRegime
        Line("Gross income", n.gross, o.gross)
        Line("Standard deduction", -n.standardDeduction, -o.standardDeduction)
        Line(if (s.whatIf.any || n.deductions > 0) "Deductions" else "80C and 80D", -n.deductions, -o.deductions)
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Hx.border)
        Line("Taxable income", n.taxable, o.taxable, bold = true)
        Line("Tax on slabs", n.slabTax, o.slabTax)
        Line("Rebate u/s 87A", -n.rebate, -o.rebate)
        Line("Cess 4%", n.cess, o.cess)
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Hx.border)
        Line("Tax payable", n.total, o.total, bold = true)
        Spacer(Modifier.height(8.dp))
        Text(
            "New: nil to ₹4L, then 5% more for every ₹4L, up to 30% above ₹24L; no tax up to ₹12L taxable. " +
                "Old: nil to ₹2.5L, 5% to ₹5L, 20% to ₹10L, 30% above; no tax up to ₹5L taxable.",
            fontSize = 11.sp, color = Hx.text2,
        )
    }
}

@Composable
private fun Line(label: String, a: Long, b: Long, bold: Boolean = false) {
    val w = if (bold) FontWeight.SemiBold else FontWeight.Normal
    fun fmt(v: Long) = if (v < 0) "−" + Money.format(-v, showPaise = false) else Money.format(v, showPaise = false)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = w, modifier = Modifier.weight(1.4f), color = if (bold) Color.Unspecified else Hx.text2)
        Text(fmt(a), fontSize = 13.sp, fontWeight = w, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(fmt(b), fontSize = 13.sp, fontWeight = w, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}
