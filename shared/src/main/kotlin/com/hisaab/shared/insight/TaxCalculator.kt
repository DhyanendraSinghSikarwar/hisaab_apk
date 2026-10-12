package com.hisaab.shared.insight

import kotlin.math.max
import kotlin.math.min

/*
 * Indian individual income tax for a resident, table-driven per financial year. All amounts are paise.
 *
 * Rules as of 2026-10-11. Sources: incometax.gov.in "Tax rates for AY 2026-27" (FY 2025-26: new-regime slabs,
 * 87A rebate Rs 60,000 up to Rs 12 lakh, surcharge tiers, new-regime surcharge cap 25%); Union Budget 2025-26
 * (slabs, rebate, 75,000 standard deduction) and Budget 2026-27 (no change to slabs, rebate or deductions; the
 * Income-tax Act 2025 applies from 1 April 2026 and renumbers sections without changing these rates), as
 * reported by BankBazaar, ClearTax and Tata Capital. Not confirmed against the 2026-27 official page: the
 * old-regime rebate and capital-gains rates are carried over from FY 2025-26 (111A 20%, 112A 12.5% above
 * Rs 1.25 lakh, from Finance (No. 2) Act 2024). FY 2026-27 therefore uses the FY 2025-26 rules.
 */

enum class Regime { NEW, OLD }

/** Age on the last day of the year: below 60, senior (60 to 79) or super senior (80 and over). */
enum class AgeBand { BELOW_60, SENIOR, SUPER_SENIOR }

/** Slab up to [upTo] (inclusive), taxed at [ratePct]; [Long.MAX_VALUE] is the open top slab. */
data class TaxSlab(val upTo: Long, val ratePct: Int)

/** One regime's rules for a year. */
data class RegimeRules(
    val slabs: Map<AgeBand, List<TaxSlab>>,
    val standardDeduction: Long,
    /** Family-pension deduction: the lesser of one third and this cap. */
    val familyPensionCap: Long,
    /** Section 87A: nil tax while total income is at most [rebateIncomeLimit]; the rebate is at most [rebateMax]. */
    val rebateIncomeLimit: Long,
    val rebateMax: Long,
    /** Marginal relief just above [rebateIncomeLimit]: tax never exceeds the income over the limit. */
    val rebateMarginalRelief: Boolean,
    /** Total income above the first of each pair is charged the surcharge percent of the second. */
    val surchargeTiers: List<Pair<Long, Int>>,
    /** Chapter VI-A deductions (80C, 80D…) apply; the new regime allows only employer NPS. */
    val deductionsAllowed: Boolean,
)

data class TaxRules(
    val fyStartYear: Int,
    val newRegime: RegimeRules,
    val oldRegime: RegimeRules,
    val cessPct: Int = 4,
    val stcgPct: Double = 20.0,
    val ltcgPct: Double = 12.5,
    val ltcgExempt: Long = 125_000L * 100,
    /** Surcharge on tax from capital gains and dividends is capped at this percent. */
    val specialSurchargeCapPct: Int = 15,
    val limit80C: Long = 150_000L * 100,
    val limit80CCD1B: Long = 50_000L * 100,
    val limit80DBelow60: Long = 25_000L * 100,
    val limit80DSenior: Long = 50_000L * 100,
    val limit80TTA: Long = 10_000L * 100,
    val limit80TTB: Long = 50_000L * 100,
    val limitHomeLoan: Long = 200_000L * 100,
    /** Section 24(a): 30% of rent received is deducted. */
    val rentStandardPct: Int = 30,
) {
    companion object {
        private const val LAKH = 100_000L * 100
        private const val CRORE = 100 * LAKH
        private const val MAX = Long.MAX_VALUE

        private fun slabs(vararg p: Pair<Long, Int>) = p.map { TaxSlab(it.first, it.second) }

        private val OLD_SLABS = mapOf(
            AgeBand.BELOW_60 to slabs(250_000L * 100 to 0, 5 * LAKH to 5, 10 * LAKH to 20, MAX to 30),
            AgeBand.SENIOR to slabs(3 * LAKH to 0, 5 * LAKH to 5, 10 * LAKH to 20, MAX to 30),
            AgeBand.SUPER_SENIOR to slabs(5 * LAKH to 0, 10 * LAKH to 20, MAX to 30),
        )
        private fun same(s: List<TaxSlab>) = AgeBand.entries.associateWith { s }

        private val OLD_SURCHARGE = listOf(50 * LAKH to 10, CRORE to 15, 2 * CRORE to 25, 5 * CRORE to 37)
        private val NEW_SURCHARGE = listOf(50 * LAKH to 10, CRORE to 15, 2 * CRORE to 25)

        private val OLD = RegimeRules(
            OLD_SLABS, 50_000L * 100, 15_000L * 100, 5 * LAKH, 12_500L * 100, false, OLD_SURCHARGE, true,
        )

        /** FY 2024-25 (AY 2025-26): new slabs from Budget July 2024, rebate Rs 25,000 up to Rs 7 lakh. */
        val FY2024_25 = TaxRules(
            2024,
            RegimeRules(
                same(slabs(3 * LAKH to 0, 7 * LAKH to 5, 10 * LAKH to 10, 12 * LAKH to 15, 15 * LAKH to 20, MAX to 30)),
                75_000L * 100, 25_000L * 100, 7 * LAKH, 25_000L * 100, true, NEW_SURCHARGE, false,
            ),
            OLD,
        )

        /** FY 2025-26 (AY 2026-27): Budget 2025, nil tax up to Rs 12 lakh. */
        val FY2025_26 = TaxRules(
            2025,
            RegimeRules(
                same(slabs(4 * LAKH to 0, 8 * LAKH to 5, 12 * LAKH to 10, 16 * LAKH to 15, 20 * LAKH to 20, 24 * LAKH to 25, MAX to 30)),
                75_000L * 100, 25_000L * 100, 12 * LAKH, 60_000L * 100, true, NEW_SURCHARGE, false,
            ),
            OLD,
        )

        /** FY 2026-27 (AY 2027-28): Budget 2026 left the rates unchanged, so these are the FY 2025-26 rules. */
        val FY2026_27 = FY2025_26.copy(fyStartYear = 2026)

        val ALL = listOf(FY2024_25, FY2025_26, FY2026_27)
        val LATEST = FY2026_27

        /** The rules for the year starting in [fyStartYear]; earlier years use FY 2024-25, later ones the latest. */
        fun forYear(fyStartYear: Int): TaxRules = ALL.lastOrNull { it.fyStartYear <= fyStartYear } ?: FY2024_25
    }
}

/** Everything the calculator needs for one year. Salary is the gross figure, before the standard deduction. */
data class TaxInput(
    val salary: Long = 0,
    /** Interest, dividends, freelance and other income taxed at slab rates. */
    val otherIncome: Long = 0,
    /** Rent received, before the 30% deduction. */
    val rent: Long = 0,
    val familyPension: Long = 0,
    /** Short-term gain on equity, section 111A. */
    val stcgEquity: Long = 0,
    /** Long-term gain on equity, section 112A, before the Rs 1.25 lakh exemption. */
    val ltcgEquity: Long = 0,
    val age: AgeBand = AgeBand.BELOW_60,
    /** 80CCD(2): allowed in both regimes. */
    val employerNps: Long = 0,
    val c80: Long = 0,
    val d80Self: Long = 0,
    val d80Parents: Long = 0,
    val parentsSenior: Boolean = false,
    val nps1B: Long = 0,
    val homeLoanInterest: Long = 0,
    /** Savings-account interest, for 80TTA (or 80TTB for seniors, where it is all deposit interest). */
    val savingsInterest: Long = 0,
    /** HRA exemption and other old-regime deductions, taken as entered. */
    val hraOther: Long = 0,
)

/** One regime's working, from gross income to the tax payable. */
data class RegimeTax(
    /** Income taxed at slab rates before deductions: salary, other income, family pension and rent net of 30%. */
    val gross: Long,
    val standardDeduction: Long,
    val deductions: Long,
    /** Taxable income at slab rates. */
    val taxable: Long,
    val slabTax: Long,
    val rebate: Long,
    val cess: Long,
    val surcharge: Long = 0,
    /** Capital gains taxed at special rates, after the exemption. */
    val specialIncome: Long = 0,
    val specialTax: Long = 0,
) {
    val taxBeforeCess: Long get() = slabTax - rebate + specialTax
    val total: Long get() = taxBeforeCess + surcharge + cess
    val totalIncome: Long get() = taxable + specialIncome
}

object TaxCalculator {
    fun compute(rules: TaxRules, regime: Regime, i: TaxInput): RegimeTax {
        val r = if (regime == Regime.NEW) rules.newRegime else rules.oldRegime
        val slabs = r.slabs.getValue(i.age)
        val salary = max(0L, i.salary)
        val std = min(salary, r.standardDeduction)
        val pension = max(0L, i.familyPension)
        val pensionDed = min(pension / 3, r.familyPensionCap)
        val rentNet = max(0L, i.rent) * (100 - rules.rentStandardPct) / 100
        val gross = salary + max(0L, i.otherIncome) + pension + rentNet
        val afterStd = max(0L, gross - std - pensionDed)

        var claimed = max(0L, i.employerNps)
        if (r.deductionsAllowed) {
            val senior = i.age != AgeBand.BELOW_60
            claimed += min(i.c80, rules.limit80C) + min(i.nps1B, rules.limit80CCD1B)
            claimed += min(i.d80Self, if (senior) rules.limit80DSenior else rules.limit80DBelow60)
            claimed += min(i.d80Parents, if (i.parentsSenior) rules.limit80DSenior else rules.limit80DBelow60)
            claimed += min(i.homeLoanInterest, rules.limitHomeLoan)
            claimed += if (senior) min(max(0L, i.savingsInterest), rules.limit80TTB) else min(max(0L, i.savingsInterest), rules.limit80TTA)
            claimed += max(0L, i.hraOther)
        }
        val deductions = min(afterStd, max(0L, claimed))
        val taxable = afterStd - deductions

        // Capital gains: exempt part first, then any unused basic exemption absorbs them.
        val ltcgAfterExempt = max(0L, i.ltcgEquity - rules.ltcgExempt)
        val shortfall = if (slabs.first().ratePct == 0) max(0L, slabs.first().upTo - taxable) else 0L
        val stcgT = max(0L, max(0L, i.stcgEquity) - shortfall)
        val ltcgT = max(0L, ltcgAfterExempt - max(0L, shortfall - max(0L, i.stcgEquity)))

        val core = assemble(rules, r, slabs, taxable, stcgT, ltcgT)
        var surcharge = core.surcharge
        val total = taxable + stcgT + ltcgT
        val tier = r.surchargeTiers.lastOrNull { total > it.first }
        if (tier != null) {
            // Marginal relief: tax plus surcharge never rises by more than the income above the tier's threshold.
            val t = tier.first
            val over = total - t
            val cutNormal = min(taxable, over)
            var left = over - cutNormal
            val cutLtcg = min(ltcgT, left); left -= cutLtcg
            val cutStcg = min(stcgT, left)
            val at = assemble(rules, r, slabs, taxable - cutNormal, stcgT - cutStcg, ltcgT - cutLtcg)
            val ceiling = at.tax + at.surcharge + over
            surcharge = min(surcharge, max(0L, ceiling - core.tax))
        }
        val cess = roundRupee((core.tax + surcharge) * rules.cessPct / 100.0)
        return RegimeTax(
            gross = gross, standardDeduction = std + pensionDed, deductions = deductions, taxable = taxable,
            slabTax = core.slabTax, rebate = core.rebate, cess = cess, surcharge = surcharge,
            specialIncome = stcgT + ltcgT, specialTax = core.specialTax,
        )
    }

    private class Core(val slabTax: Long, val rebate: Long, val specialTax: Long, val surcharge: Long) {
        val tax: Long get() = slabTax - rebate + specialTax
    }

    private fun assemble(rules: TaxRules, r: RegimeRules, slabs: List<TaxSlab>, taxable: Long, stcgT: Long, ltcgT: Long): Core {
        val slabTax = slabTax(taxable, slabs)
        val special = roundRupee(stcgT * rules.stcgPct / 100 + ltcgT * rules.ltcgPct / 100)
        val total = taxable + stcgT + ltcgT
        val rebate = when {
            total <= r.rebateIncomeLimit -> min(slabTax, r.rebateMax)
            r.rebateMarginalRelief && stcgT + ltcgT == 0L -> max(0L, slabTax - (total - r.rebateIncomeLimit))
            else -> 0L
        }
        val normalNet = slabTax - rebate
        val rate = r.surchargeTiers.lastOrNull { total > it.first }?.second ?: 0
        val surcharge = roundRupee(normalNet * rate / 100.0 + special * min(rate, rules.specialSurchargeCapPct) / 100.0)
        return Core(slabTax, rebate, special, surcharge)
    }

    /** Tax on [taxable] at [slabs], before rebate and cess, to the nearest rupee. */
    fun slabTax(taxable: Long, slabs: List<TaxSlab>): Long {
        var lower = 0L
        var tax = 0.0
        for (s in slabs) {
            if (taxable <= lower) break
            tax += (min(taxable, s.upTo) - lower) * s.ratePct / 100.0
            lower = s.upTo
        }
        return roundRupee(tax)
    }

    private fun roundRupee(paise: Double): Long = Math.round(paise / 100.0) * 100
}
