package com.hisaab.app.ui.more

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
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

/** Indian income tax for a resident individual under 60, without surcharge. */
object TaxMath {
    const val LAKH = 100_000L * 100

    /** Upper bound of each slab and its rate in percent; the last slab is open-ended. */
    private val NEW = listOf(4 * LAKH to 0, 8 * LAKH to 5, 12 * LAKH to 10, 16 * LAKH to 15, 20 * LAKH to 20, 24 * LAKH to 25, Long.MAX_VALUE to 30)
    private val OLD = listOf(250_000L * 100 to 0, 5 * LAKH to 5, 10 * LAKH to 20, Long.MAX_VALUE to 30)

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

    fun newRegime(income: Long): RegimeTax {
        val taxable = max(0L, income - NEW_STANDARD)
        val base = slabTax(taxable, NEW)
        // Section 87A: nil tax up to ₹12 lakh; just above it, marginal relief caps the tax at the income over ₹12 lakh.
        val rebate = if (taxable <= NEW_REBATE_UPTO) base else max(0L, base - (taxable - NEW_REBATE_UPTO))
        val cess = roundRupee((base - rebate) * 0.04)
        return RegimeTax(income, min(income, NEW_STANDARD), 0, taxable, base, rebate, cess)
    }

    fun oldRegime(income: Long, c80: Long, d80: Long): RegimeTax {
        val afterStd = max(0L, income - OLD_STANDARD)
        val ded = min(afterStd, min(c80, LIMIT_80C) + min(d80, LIMIT_80D))
        val taxable = afterStd - ded
        val base = slabTax(taxable, OLD)
        val rebate = if (taxable <= OLD_REBATE_UPTO) base else 0L
        val cess = roundRupee((base - rebate) * 0.04)
        return RegimeTax(income, min(income, OLD_STANDARD), ded, taxable, base, rebate, cess)
    }

    private fun roundRupee(paise: Double): Long = Math.round(paise / 100.0) * 100
}

// ---------------------------------------------------------------------------------------------
// The estimate, worked out only from what Hisaab tracked: bank credits, investment debits, insurance
// premiums and EPF passbook updates. Nothing is typed in.
// ---------------------------------------------------------------------------------------------

/** One source of a deduction, for the "where this comes from" list. */
data class TaxItem(val label: String, val amountMinor: Long)

data class TaxState(
    val fyStartYear: Int = ViewFilter.fyStart(LocalDate.now()).year,
    val salarySoFar: Long = 0,
    val salaryMonths: Int = 0,
    val otherIncome: Long = 0,
    /** Salary projected to twelve months, plus other income so far. */
    val income: Long = 0,
    val items80C: List<TaxItem> = emptyList(),
    val items80D: List<TaxItem> = emptyList(),
    /** Investments this year that do not qualify for 80C (equity funds, stocks…), shown for context. */
    val otherInvested: Long = 0,
    val newRegime: RegimeTax = TaxMath.newRegime(0),
    val oldRegime: RegimeTax = TaxMath.oldRegime(0, 0, 0),
    val loaded: Boolean = false,
) {
    val fyLabel: String get() = "FY $fyStartYear-${(fyStartYear + 1) % 100}"
    val c80: Long get() = items80C.sumOf { it.amountMinor }
    val d80: Long get() = items80D.sumOf { it.amountMinor }
    val newIsBetter: Boolean get() = newRegime.total <= oldRegime.total
    val saving: Long get() = kotlin.math.abs(newRegime.total - oldRegime.total)
    val left80C: Long get() = max(0L, TaxMath.LIMIT_80C - c80)
    val hasIncome: Boolean get() = income > 0
}

/** The current financial year's estimate, shared by the Tax centre and the More list. */
@Singleton
class TaxSource @Inject constructor(
    transactions: TransactionDao,
    holdings: HoldingDao,
    @ApplicationScope scope: CoroutineScope,
) {
    private val fyStart: LocalDate = ViewFilter.fyStart(LocalDate.now(Periods.zone))
    private val fyFrom = millis(fyStart)
    private val fyTo = millis(fyStart.plusYears(1)) - 1

    val state: StateFlow<TaxState> = combine(transactions.observeBetween(fyFrom, fyTo), holdings.observeAll()) { all, held ->
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
            fyStartYear = fyStart.year, salarySoFar = salarySoFar, salaryMonths = months, otherIncome = other, income = income,
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
    val state: StateFlow<TaxState> = source.state
}

// ---------------------------------------------------------------------------------------------
// The screen.
// ---------------------------------------------------------------------------------------------

@Composable
fun TaxRoute(onBack: () -> Unit, vm: TaxViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    MoreScaffold("Tax centre", onBack) { inner ->
        if (!s.loaded) return@MoreScaffold
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            item("summary") { Summary(s) }
            item("income") { IncomeCard(s) }
            item("deductions") { DeductionsCard(s) }
            item("breakdown") { Breakdown(s) }
            item("note") {
                Text(
                    "Worked out from what Hisaab tracked this year, for a resident individual under 60. Salary credits are what " +
                        "reached your bank, after TDS and PF, so your taxable salary is likely higher. It leaves out surcharge, " +
                        "capital gains, HRA and deductions Hisaab cannot see. Check with a tax professional before you file.",
                    style = MaterialTheme.typography.bodySmall, color = Hx.text2, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Summary(s: TaxState) {
    HCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.fyLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Tag("Estimate", Hx.warn)
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
    HCard(title = "Income") {
        ItemRow("Salary received", s.salarySoFar, if (s.salaryMonths > 0) "${s.salaryMonths} month${if (s.salaryMonths == 1) "" else "s"} so far" else "None found yet")
        if (s.salaryMonths in 1..11) ItemRow("Projected for the year", s.salarySoFar * 12 / s.salaryMonths, "At the same monthly pay")
        if (s.otherIncome > 0) ItemRow("Other income", s.otherIncome, null)
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Hx.border)
        ItemRow("Gross income used", s.income, null, bold = true)
    }
}

@Composable
private fun DeductionsCard(s: TaxState) {
    HCard(title = "Deductions · old regime") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("80C", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
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
            Text("80D", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${Money.format(min(s.d80, TaxMath.LIMIT_80D), showPaise = false)} of ₹25,000", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (s.items80D.isEmpty()) {
            Text("No health insurance premiums found this year.", fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(top = 4.dp))
        } else {
            s.items80D.forEach { ItemRow(it.label, it.amountMinor, null) }
        }
    }
}

@Composable
private fun ItemRow(label: String, amount: Long, sub: String?, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
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
        Line("80C and 80D", -n.deductions, -o.deductions)
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
