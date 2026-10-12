package com.hisaab.app.ui.more

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.Pill
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
import com.hisaab.shared.insight.AgeBand
import com.hisaab.shared.insight.InsuranceKind
import com.hisaab.shared.insight.Planning
import com.hisaab.shared.insight.Regime
import com.hisaab.shared.insight.RegimeTax
import com.hisaab.shared.insight.TaxCalculator
import com.hisaab.shared.insight.TaxIncome
import com.hisaab.shared.insight.TaxIncomeEstimate
import com.hisaab.shared.insight.TaxInput
import com.hisaab.shared.insight.TaxRules
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Constants for the screens and the shared report. The arithmetic itself is TaxCalculator in :shared, driven by
// per-year rules (TaxRules); all amounts are paise.
// ---------------------------------------------------------------------------------------------

/** One slab of a regime as it applies to a taxable income. [to] is null for the open-ended top slab. */
data class SlabLine(val from: Long, val to: Long?, val rate: Int, val taxedPart: Long, val tax: Long)

object TaxMath {
    const val LAKH = 100_000L * 100

    val LIMIT_80C: Long get() = TaxRules.LATEST.limit80C
    /** Health cover for yourself and family, under 60. */
    val LIMIT_80D: Long get() = TaxRules.LATEST.limit80DBelow60
    /** 80CCD(1B), your own NPS contribution over and above 80C. */
    val LIMIT_NPS: Long get() = TaxRules.LATEST.limit80CCD1B
    /** 24(b), interest on a home loan for a self-occupied house. */
    val LIMIT_HOME_LOAN: Long get() = TaxRules.LATEST.limitHomeLoan

    /** Upper bound of each slab and its rate in percent for an individual under 60; the last slab is open-ended. */
    fun slabs(rules: TaxRules, regime: Regime): List<Pair<Long, Int>> =
        (if (regime == Regime.NEW) rules.newRegime else rules.oldRegime).slabs.getValue(AgeBand.BELOW_60).map { it.upTo to it.ratePct }

    /** Each slab [taxable] reaches: its bounds, the part of income taxed in it, and the tax on that part (before rebate and cess). */
    fun slabLines(taxable: Long, slabs: List<Pair<Long, Int>>): List<SlabLine> = buildList {
        var lower = 0L
        for ((upper, rate) in slabs) {
            if (taxable <= lower) break
            val part = min(taxable, upper) - lower
            add(SlabLine(lower, upper.takeIf { it != Long.MAX_VALUE }, rate, part, Math.round(part * rate / 100.0 / 100.0) * 100))
            lower = upper
        }
    }

    /** "12", "12.75": a lakh figure without trailing zeros. */
    fun lakhs(minor: Long): String = BigDecimal.valueOf(minor, 2).divide(BigDecimal(100_000)).stripTrailingZeros().toPlainString()
}

/** The period the Tax centre works on. */
enum class TaxPeriod { THIS_FY, LAST_FY, LAST_12 }

// ---------------------------------------------------------------------------------------------
// The estimate, worked out only from what DhanKosh tracked: bank credits, investment debits, insurance
// premiums and EPF passbook updates. Figures typed into the what-if calculator live only in TaxViewModel
// for the current visit; nothing typed is ever saved.
// ---------------------------------------------------------------------------------------------

/** One source of a deduction, for the "where this comes from" list. */
data class TaxItem(val label: String, val amountMinor: Long)

data class TaxState(
    val period: TaxPeriod = TaxPeriod.THIS_FY,
    /** The financial year whose rules apply; for the last 12 months, the current one. */
    val fyStartYear: Int = ViewFilter.fyStart(LocalDate.now()).year,
    /** Income collected from tracked credits, or estimated for the year. */
    val est: TaxIncomeEstimate = TaxIncomeEstimate(),
    val items80C: List<TaxItem> = emptyList(),
    val items80D: List<TaxItem> = emptyList(),
    /** Own NPS contributions this period, for 80CCD(1B). */
    val npsDetected: Long = 0,
    /** Investments this period that do not qualify for 80C (equity funds, stocks…), shown for context. */
    val otherInvested: Long = 0,
    /** What-if figures typed in for this visit; empty means the calculated estimate. Never saved. */
    val whatIf: TaxWhatIf = TaxWhatIf(),
    val newRegime: RegimeTax = RegimeTax(0, 0, 0, 0, 0, 0, 0),
    val oldRegime: RegimeTax = RegimeTax(0, 0, 0, 0, 0, 0, 0),
    val loaded: Boolean = false,
) {
    val rules: TaxRules get() = TaxRules.forYear(fyStartYear)
    val fyLabel: String get() = if (period == TaxPeriod.LAST_12) t("Last 12 months") else t("FY {years}", "years" to "$fyStartYear-${(fyStartYear + 1) % 100}")
    /** "FY 2026-27" with a four-digit start and two-digit end, for the shared report. */
    val fyLong: String get() = if (period == TaxPeriod.LAST_12) t("Last 12 months") else t("FY {years}", "years" to "$fyStartYear-${"%02d".format((fyStartYear + 1) % 100)}")

    val c80Calc: Long get() = items80C.sumOf { it.amountMinor }
    val d80Calc: Long get() = items80D.sumOf { it.amountMinor }
    val c80: Long get() = whatIf.c80 ?: c80Calc
    val d80: Long get() = whatIf.d80 ?: d80Calc
    val salaryUsed: Long get() = whatIf.salary ?: est.salary
    val interestUsed: Long get() = whatIf.interest ?: est.interest
    val dividendsUsed: Long get() = whatIf.dividends ?: est.dividends
    val rentUsed: Long get() = whatIf.rent ?: est.rent
    val otherUsed: Long get() = whatIf.otherIncome ?: est.other
    val stcgUsed: Long get() = whatIf.stcg ?: 0
    val ltcgUsed: Long get() = whatIf.ltcg ?: 0
    val capitalGains: Long get() = stcgUsed + ltcgUsed
    /** Interest, dividends, rent and other credits used for the estimate. */
    val otherIncomeUsed: Long get() = interestUsed + dividendsUsed + rentUsed + otherUsed
    /** Salary and other income used, without capital gains. */
    val income: Long get() = salaryUsed + otherIncomeUsed
    val nps: Long get() = whatIf.nps ?: npsDetected
    val homeLoan: Long get() = whatIf.homeLoan ?: 0
    val hraOther: Long get() = whatIf.hraOther ?: 0
    val employerNps: Long get() = whatIf.employerNps ?: 0
    val newIsBetter: Boolean get() = newRegime.total <= oldRegime.total
    val saving: Long get() = kotlin.math.abs(newRegime.total - oldRegime.total)
    val left80C: Long get() = max(0L, TaxMath.LIMIT_80C - c80)
    val hasIncome: Boolean get() = income + capitalGains > 0
    val newSlabs: List<Pair<Long, Int>> get() = TaxMath.slabs(rules, Regime.NEW)
    val oldSlabs: List<Pair<Long, Int>> get() = TaxMath.slabs(rules, Regime.OLD)

    private fun input() = TaxInput(
        salary = salaryUsed, otherIncome = interestUsed + dividendsUsed + otherUsed, rent = rentUsed,
        stcgEquity = stcgUsed, ltcgEquity = ltcgUsed, employerNps = employerNps, c80 = c80, d80Self = d80, nps1B = nps,
        homeLoanInterest = homeLoan, savingsInterest = min(interestUsed, est.savingsInterest), hraOther = hraOther,
    )

    /** This state with the what-if figures [o] applied and both regimes recomputed. Empty [o] gives the calculated estimate. */
    fun withWhatIf(o: TaxWhatIf): TaxState {
        val s = copy(whatIf = o)
        val i = s.input()
        return s.copy(
            newRegime = TaxCalculator.compute(s.rules, Regime.NEW, i),
            oldRegime = TaxCalculator.compute(s.rules, Regime.OLD, i),
        )
    }
}

/** The calculated estimate for a period, shared by the Tax centre and the More list. Never holds what-if figures. */
@Singleton
class TaxSource @Inject constructor(
    private val transactions: TransactionDao,
    private val holdings: HoldingDao,
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    init {
        // Earlier versions saved typed tax figures; they are no longer used, so the old file is removed.
        scope.launch(Dispatchers.IO) { runCatching { File(context.filesDir, "datastore/tax_overrides.preferences_pb").delete() } }
    }

    /** The current financial year, for the More list. */
    val state: StateFlow<TaxState> = observe(TaxPeriod.THIS_FY).stateIn(scope, SharingStarted.WhileSubscribed(10_000), TaxState())

    fun observe(period: TaxPeriod): Flow<TaxState> {
        val today = LocalDate.now(Periods.zone)
        val fy = ViewFilter.fyStart(today)
        val (from, to) = when (period) {
            TaxPeriod.THIS_FY -> fy to fy.plusYears(1).minusDays(1)
            TaxPeriod.LAST_FY -> fy.minusYears(1) to fy.minusDays(1)
            TaxPeriod.LAST_12 -> today.minusMonths(12).plusDays(1) to today
        }
        val fyYear = if (period == TaxPeriod.LAST_FY) fy.year - 1 else fy.year
        val fromMs = millis(from)
        val toMs = millis(to.plusDays(1)) - 1
        return combine(transactions.observeBetween(0, toMs), holdings.observeAll()) { everything, held ->
            val est = TaxIncome.estimate(everything, from, to, today, Periods.zone)
            val txs = everything.filter { !it.needsReview && it.timestamp in fromMs..toMs }

            val invest = txs.filter(LedgerMath::isInvest)
            val nps = invest.filter { NPS.containsMatchIn(text(it)) }
            val qualifying = invest.filter { QUALIFIES_80C.containsMatchIn(text(it)) }
            val premiums = txs.filter { it.category == Category.INSURANCE && it.type != TransactionType.CREDIT && it.type != TransactionType.TRANSFER }
            val life = premiums.filter { Planning.kindOf(text(it)) == InsuranceKind.LIFE }
            val health = premiums.filter { Planning.kindOf(text(it)) == InsuranceKind.HEALTH }

            val items80C = buildList {
                qualifying.groupBy { label80C(text(it)) }.forEach { (l, list) -> add(TaxItem(l, list.sumOf(LedgerMath::rupees))) }
                if (life.isNotEmpty()) add(TaxItem("Life insurance premiums", life.sumOf(LedgerMath::rupees)))
                val epf = held.filter { it.kind == HoldingKind.EPF }.sumOf { epfShare(it, fromMs, toMs) }
                if (epf > 0) add(TaxItem("EPF, your share (estimated)", epf))
            }.filter { it.amountMinor > 0 }
            val items80D = listOf(TaxItem("Health insurance premiums", health.sumOf(LedgerMath::rupees))).filter { it.amountMinor > 0 }
            val usedIds = (qualifying + nps).map { it.id }.toSet()
            TaxState(
                period = period, fyStartYear = fyYear, est = est, items80C = items80C, items80D = items80D,
                npsDetected = nps.sumOf(LedgerMath::rupees),
                otherInvested = invest.filter { it.id !in usedIds }.sumOf(LedgerMath::rupees), loaded = true,
            ).withWhatIf(TaxWhatIf())
        }.flowOn(Dispatchers.Default)
    }

    /**
     * The passbook growth of an EPF account that falls inside the period, times the employee's part of
     * each deposit (12 of every 15.67; the rest is the employer's). Only when two passbook figures are known.
     */
    private fun epfShare(h: HoldingEntity, fromMs: Long, toMs: Long): Long {
        val now = h.valueMinor ?: return 0
        val before = h.previousValueMinor ?: return 0
        val at = h.asOf ?: return 0
        val prevAt = h.previousAsOf ?: return 0
        if (now <= before || at <= prevAt || at < fromMs) return 0
        val overlap = (min(at, toMs) - max(prevAt, fromMs)).coerceAtLeast(0)
        val share = (now - before).toDouble() * overlap / (at - prevAt)
        return (share * 12.0 / 15.67).toLong()
    }

    private fun millis(d: LocalDate) = d.atStartOfDay(Periods.zone).toInstant().toEpochMilli()

    private companion object {
        val QUALIFIES_80C = Regex(
            """elss|tax\s?saver|tax\s?saving|\bppf\b|public provident|sukanya|\bssy\b|\bnsc\b|national savings""",
            RegexOption.IGNORE_CASE,
        )
        val NPS = Regex("""\bnps\b|national pension""", RegexOption.IGNORE_CASE)
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaxViewModel @Inject constructor(private val source: TaxSource) : ViewModel() {
    /** What-if figures for this visit only: they go when the screen is left and are never written anywhere. */
    private val whatIf = MutableStateFlow(TaxWhatIf())
    private val period = MutableStateFlow(TaxPeriod.THIS_FY)

    val state: StateFlow<TaxState> = combine(period.flatMapLatest { source.observe(it) }, whatIf) { s, o -> if (o.any) s.withWhatIf(o) else s }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), source.state.value)

    fun calculate(o: TaxWhatIf) { whatIf.value = o }

    fun backToCalculated() { whatIf.value = TaxWhatIf() }

    /** Switching period drops what-if figures: they were typed for another year. */
    fun select(p: TaxPeriod) { whatIf.value = TaxWhatIf(); period.value = p }
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
        t("Tax centre"), onBack,
        actions = {
            if (s.loaded) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Calculate, t("What-if calculator")) }
                IconButton(onClick = { sharing = true }, enabled = s.hasIncome) { Icon(Icons.Outlined.Share, t("Share estimate")) }
            }
        },
    ) { inner ->
        if (!s.loaded) return@MoreScaffold
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            item("periods") { PeriodRow(s.period, onSelect = vm::select) }
            if (s.whatIf.any) item("whatif") { WhatIfBanner(onEdit = { editing = true }, onReset = vm::backToCalculated) }
            item("summary") { Summary(s, onShare = { sharing = true }) }
            item("income") { IncomeCard(s) }
            item("deductions") { DeductionsCard(s) }
            item("slabs") { SlabCard(s) }
        }
    }
    if (editing && s.loaded) TaxEditSheet(s, onDismiss = { editing = false }, onCalculate = vm::calculate, onReset = vm::backToCalculated)
    if (sharing && s.loaded) TaxShareFlow(s, onDone = { sharing = false })
}

@Composable
private fun PeriodRow(selected: TaxPeriod, onSelect: (TaxPeriod) -> Unit) {
    val fy = ViewFilter.fyStart(LocalDate.now(Periods.zone)).year
    fun name(y: Int) = t("FY {years}", "years" to "$y-${(y + 1) % 100}")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(name(fy), on = selected == TaxPeriod.THIS_FY) { onSelect(TaxPeriod.THIS_FY) }
        Pill(name(fy - 1), on = selected == TaxPeriod.LAST_FY) { onSelect(TaxPeriod.LAST_FY) }
        Pill(t("Last 12 months"), on = selected == TaxPeriod.LAST_12) { onSelect(TaxPeriod.LAST_12) }
    }
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
            Text(t("What-if"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Hx.accent)
            Text(t("Tax for the figures you entered. Not saved."), fontSize = 12.sp, color = Hx.text2)
        }
        TextButton(onClick = onReset) { Text(t("Back to calculated")) }
    }
}

private fun inr(minor: Long) = Money.format(minor, showPaise = false)
private fun signed(minor: Long) = if (minor > 0) "−" + inr(minor) else inr(0)

@Composable
private fun Summary(s: TaxState, onShare: () -> Unit) {
    HCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.fyLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (s.whatIf.any) { Tag(t("What-if"), Hx.accent); Spacer(Modifier.width(6.dp)) }
            if (s.est.estimated) Tag(t("Estimated"), Hx.warn) else Tag(t("Estimate"), Hx.warn)
            com.hisaab.app.ui.components.InfoButton(
                t("About this estimate"),
                t("Worked out from what DhanKosh tracked, for a resident individual under 60, with the slabs, rebate, surcharge and cess for the year."),
                t("Salary credits are after TDS and PF, so taxable salary may be higher. Check with a tax professional before you file."),
            )
        }
        Text(basisLine(s), fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RegimeCard(
                t("New regime"), s.newRegime, best = s.newIsBetter && s.hasIncome && s.saving > 0,
                note = newRegimeNote(s), modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            RegimeCard(t("Old regime"), s.oldRegime, best = !s.newIsBetter && s.hasIncome, note = null, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                !s.hasIncome -> t("No salary or income credits found for {fy} yet.", "fy" to s.fyLabel)
                s.saving == 0L -> t("Both regimes come to the same tax.")
                else -> t("Recommended: {regime}, saves {amount}", "regime" to (if (s.newIsBetter) t("New regime") else t("Old regime")), "amount" to inr(s.saving))
            },
            color = if (s.hasIncome && s.saving > 0) Hx.pos else Hx.text2, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        )
        if (s.hasIncome) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Share this estimate"))
            }
        }
    }
}

private fun basisLine(s: TaxState): String {
    val n = s.est.monthsOfData
    return when {
        s.whatIf.any -> t("Based on the figures you entered")
        n == 0 -> t("No income data for this period yet")
        s.est.estimated && n == 1 -> t("Based on 1 month of data · estimated for the full year")
        s.est.estimated -> t("Based on {n} months of data · estimated for the full year", "n" to n)
        else -> t("Based on 12 months of data")
    }
}

/** The new regime's headline facts, only when they apply. */
private fun newRegimeNote(s: TaxState): String? {
    val r = s.rules.newRegime
    val n = s.newRegime
    if (n.rebate <= 0 || n.slabTax - n.rebate != 0L) return null
    val line = t("No tax up to {amount} taxable income", "amount" to "₹${TaxMath.lakhs(r.rebateIncomeLimit)} lakh")
    if (s.salaryUsed <= 0) return line
    return line + "\n" + t(
        "Tax-free until {amount} salary (incl. {std} standard deduction)",
        "amount" to "₹${TaxMath.lakhs(r.rebateIncomeLimit + r.standardDeduction)} lakh", "std" to inr(r.standardDeduction),
    )
}

@Composable
private fun RegimeCard(label: String, r: RegimeTax, best: Boolean, note: String?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(if (best) Hx.accentSoft else Hx.surface2).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = Hx.text2, modifier = Modifier.weight(1f))
            if (best) Tag(t("Lower"), Hx.pos)
        }
        Text(inr(r.total), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        Text(t("Total tax"), fontSize = 11.sp, color = Hx.text2)
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Hx.border)
        MiniLine(t("Gross income"), inr(r.gross))
        if (r.specialIncome > 0) MiniLine(t("Capital gains"), inr(r.specialIncome))
        MiniLine(t("Deductions"), signed(r.standardDeduction + r.deductions))
        MiniLine(t("Taxable income"), inr(r.totalIncome), bold = true)
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Hx.border)
        MiniLine(t("Tax before cess"), inr(r.slabTax + r.specialTax))
        MiniLine(t("Rebate 87A"), signed(r.rebate))
        MiniLine(t("Surcharge"), inr(r.surcharge))
        MiniLine(t("Cess 4%"), inr(r.cess))
        MiniLine(t("Effective rate"), effective(r), bold = true)
        if (note != null) Text(note, fontSize = 11.sp, color = Hx.pos, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun effective(r: RegimeTax): String {
    val base = r.gross + r.specialIncome
    return if (base <= 0) "0%" else String.format(Locale.ENGLISH, "%.1f%%", r.total * 100.0 / base)
}

@Composable
private fun MiniLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = if (bold) Color.Unspecified else Hx.text2, modifier = Modifier.weight(1f))
        Text(value, fontSize = 11.5.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun IncomeCard(s: TaxState) {
    val o = s.whatIf
    HCard(title = t("Income"), titleInfo = {
        com.hisaab.app.ui.components.InfoButton(
            t("Income"), t("Collected from salary, interest, dividend, rent and other credits DhanKosh tracked. Refunds, transfers between your accounts and reimbursements are left out."),
            t("Salary credits are what reached your bank, after TDS and PF, so your taxable salary is likely higher. Use the calculator (top right) to try your own figures. What-if figures are not saved."),
        )
    }) {
        val est = s.est
        val salarySub = when {
            o.salary != null -> t("Calculated: {amount}", "amount" to inr(est.salary))
            est.estimated && est.salaryMonths > 0 -> t("Projected from {n} months of credits ({amount} so far)", "n" to est.salaryMonths, "amount" to inr(est.salarySoFar))
            est.salaryMonths == 0 -> t("None found yet")
            else -> null
        }
        ItemRow(t("Salary"), s.salaryUsed, salarySub, edited = o.salary != null)
        ItemRow(t("Interest"), s.interestUsed, calcNote(o.interest, est.interest), edited = o.interest != null)
        ItemRow(t("Dividends"), s.dividendsUsed, calcNote(o.dividends, est.dividends), edited = o.dividends != null)
        ItemRow(t("Rent"), s.rentUsed, calcNote(o.rent, est.rent), edited = o.rent != null)
        ItemRow(t("Other"), s.otherUsed, calcNote(o.otherIncome, est.other), edited = o.otherIncome != null)
        ItemRow(t("Capital gains"), s.capitalGains, if (s.capitalGains > 0) t("Taxed at special rates") else t("Enter in the calculator if you sold equity"), edited = o.stcg != null || o.ltcg != null)
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Hx.border)
        ItemRow(t("Gross income used"), s.income + s.capitalGains, null, bold = true)
    }
}

private fun calcNote(edited: Long?, calculated: Long): String? =
    if (edited != null) t("Calculated: {amount}", "amount" to inr(calculated)) else null

@Composable
private fun DeductionsCard(s: TaxState) {
    HCard(title = t("Deductions · old regime")) {
        val detected = s.items80C.isNotEmpty() || s.items80D.isNotEmpty() || s.npsDetected > 0
        if (detected && !s.whatIf.any) Tag(t("From your data"), Hx.accent, Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("80C", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (s.whatIf.c80 != null) { Spacer(Modifier.width(6.dp)); Tag(t("What-if"), Hx.accent) }
            Spacer(Modifier.weight(1f))
            Text(t("{amount} of {limit}", "amount" to inr(min(s.c80, TaxMath.LIMIT_80C)), "limit" to "₹1,50,000"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        val used by animateFloatAsState(min(s.c80, TaxMath.LIMIT_80C).toFloat() / TaxMath.LIMIT_80C, tween(700), label = "80c")
        SplitBar(listOf(used to Hx.pos), height = 6.dp)
        Text(
            if (s.left80C > 0) t("80C left {amount}", "amount" to inr(s.left80C)) else t("80C used in full"),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (s.left80C > 0) Hx.warn else Hx.pos,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        if (s.whatIf.c80 != null) {
            Text(t("What-if value. Calculated from tracked payments: {amount}", "amount" to inr(s.c80Calc)), fontSize = 12.sp, color = Hx.text2)
        }
        if (s.items80C.isEmpty()) {
            Text(t("No PPF, ELSS, life cover or EPF payments found this year."), fontSize = 12.sp, color = Hx.text2)
        } else {
            s.items80C.forEach { ItemRow(t(it.label), it.amountMinor, null) }
        }
        if (s.otherInvested > 0) {
            Text(
                t("{amount} of other investments this year does not count for 80C.", "amount" to inr(s.otherInvested)),
                fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(top = 4.dp),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Hx.border)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("80D", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (s.whatIf.d80 != null) { Spacer(Modifier.width(6.dp)); Tag(t("What-if"), Hx.accent) }
            Spacer(Modifier.weight(1f))
            Text(t("{amount} of {limit}", "amount" to inr(min(s.d80, TaxMath.LIMIT_80D)), "limit" to "₹25,000"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (s.whatIf.d80 != null) {
            Text(
                t("What-if value. Calculated from tracked premiums: {amount}", "amount" to inr(s.d80Calc)),
                fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (s.items80D.isEmpty()) {
            Text(t("No health insurance premiums found this year."), fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(top = 4.dp))
        } else {
            s.items80D.forEach { ItemRow(t(it.label), it.amountMinor, null) }
        }
        val o = s.whatIf
        if (s.npsDetected > 0 || listOf(o.nps, o.homeLoan, o.hraOther, o.employerNps).any { it != null }) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Hx.border)
            if (s.nps > 0) ItemRow(t("NPS 80CCD(1B)"), min(s.nps, TaxMath.LIMIT_NPS), capNote(s.nps, TaxMath.LIMIT_NPS, "₹50,000"), edited = o.nps != null)
            o.homeLoan?.let { ItemRow(t("Home-loan interest 24(b)"), min(it, TaxMath.LIMIT_HOME_LOAN), capNote(it, TaxMath.LIMIT_HOME_LOAN, "₹2,00,000"), edited = true) }
            o.hraOther?.let { ItemRow(t("HRA exemption / other"), it, t("Old regime only"), edited = true) }
            o.employerNps?.let { ItemRow(t("Employer NPS 80CCD(2)"), it, t("Counts in both regimes"), edited = true) }
        }
    }
}

private fun capNote(entered: Long, cap: Long, capText: String): String =
    if (entered > cap) t("You entered {amount}; capped at {cap}", "amount" to inr(entered), "cap" to capText) else t("Up to {cap} · old regime only", "cap" to capText)

@Composable
private fun ItemRow(label: String, amount: Long, sub: String?, bold: Boolean = false, edited: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f, fill = false))
                if (edited) { Spacer(Modifier.width(6.dp)); Tag(t("What-if"), Hx.accent) }
            }
            if (sub != null) Text(sub, fontSize = 11.sp, color = Hx.text2)
        }
        Text(inr(amount), fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun SlabCard(s: TaxState) {
    HCard(title = t("Slab breakdown")) {
        SlabSection(t("New regime"), s.newRegime, s.newSlabs)
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Hx.border)
        SlabSection(t("Old regime"), s.oldRegime, s.oldSlabs)
    }
}

@Composable
private fun SlabSection(title: String, r: RegimeTax, slabs: List<Pair<Long, Int>>) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(inr(r.total), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = Hx.text2)
    }
    AnimatedVisibility(open) {
        Column {
            val lines = TaxMath.slabLines(r.taxable, slabs)
            if (lines.isEmpty()) Text(t("No taxable income"), fontSize = 12.sp, color = Hx.text2)
            lines.forEach { l ->
                val range = if (l.to == null) t("Above {amount}", "amount" to "₹${TaxMath.lakhs(l.from)}L") else "₹${TaxMath.lakhs(l.from)}L – ₹${TaxMath.lakhs(l.to)}L"
                SlabRow(range, "${l.rate}%", inr(l.tax))
            }
            if (r.specialTax > 0) SlabRow(t("Capital gains"), "", inr(r.specialTax))
            if (r.rebate > 0) SlabRow(t("Rebate 87A"), "", signed(r.rebate))
            if (r.surcharge > 0) SlabRow(t("Surcharge"), "", inr(r.surcharge))
            SlabRow(t("Cess 4%"), "", inr(r.cess))
            SlabRow(t("Total tax"), "", inr(r.total), bold = true)
        }
    }
}

@Composable
private fun SlabRow(label: String, rate: String, amount: String, bold: Boolean = false) {
    val w = if (bold) FontWeight.SemiBold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = w, color = if (bold) Color.Unspecified else Hx.text2, modifier = Modifier.weight(1.4f))
        Text(rate, fontSize = 12.sp, fontWeight = w, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
        Text(amount, fontSize = 12.sp, fontWeight = w, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}
