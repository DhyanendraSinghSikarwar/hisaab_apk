package com.hisaab.shared

import com.hisaab.shared.insight.AgeBand
import com.hisaab.shared.insight.Regime
import com.hisaab.shared.insight.TaxCalculator
import com.hisaab.shared.insight.TaxInput
import com.hisaab.shared.insight.TaxRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invented incomes only. All amounts in the helpers are rupees; the calculator works in paise. */
class TaxCalculatorTest {
    private val rules = TaxRules.FY2026_27
    private fun r(v: Long) = v * 100

    private fun new(salary: Long, other: Long = 0, init: (TaxInput) -> TaxInput = { it }) =
        TaxCalculator.compute(rules, Regime.NEW, init(TaxInput(salary = r(salary), otherIncome = r(other))))

    private fun old(salary: Long, init: (TaxInput) -> TaxInput = { it }) =
        TaxCalculator.compute(rules, Regime.OLD, init(TaxInput(salary = r(salary))))

    @Test fun newRegimeNoTaxUpTo12LakhTaxable() {
        val t = new(1_275_000)
        assertEquals(r(1_200_000), t.taxable)
        assertEquals(0L, t.total)
    }

    @Test fun marginalReliefJustAbove12Lakh() {
        val t = new(1_285_000)
        assertEquals(r(1_210_000), t.taxable)
        assertEquals(r(10_000), t.slabTax - t.rebate)
        assertEquals(r(10_400), t.total)
        assertTrue(t.slabTax - t.rebate <= t.taxable - r(1_200_000))
    }

    @Test fun sevenLakhSalaryNewRegimeIsNil() {
        assertEquals(0L, new(700_000).total)
    }

    @Test fun fifteenLakh() {
        val t = new(1_500_000)
        assertEquals(r(93_750), t.slabTax)
        assertEquals(0L, t.rebate)
        assertEquals(r(97_500), t.total)
    }

    @Test fun twentyFiveLakh() {
        val t = new(2_500_000)
        assertEquals(r(307_500), t.slabTax)
        assertEquals(r(319_800), t.total)
    }

    @Test fun sixtyLakhSurcharge() {
        val t = new(6_075_000)
        assertEquals(r(1_380_000), t.slabTax)
        assertEquals(r(138_000), t.surcharge)
        assertEquals(r(60_720), t.cess)
        assertEquals(r(1_578_720), t.total)
    }

    @Test fun surchargeMarginalReliefAbove50Lakh() {
        val t = new(5_085_000)
        assertEquals(r(5_010_000), t.taxable)
        assertEquals(r(7_000), t.surcharge)
    }

    @Test fun oneCroreTwentyLakhSurcharge15() {
        val t = new(12_075_000)
        assertEquals(r(3_180_000), t.slabTax)
        assertEquals(r(477_000), t.surcharge)
        assertEquals(r(3_803_280), t.total)
    }

    @Test fun newRegimeSurchargeCappedAt25() {
        val t = new(60_075_000)
        val tax = t.slabTax
        assertEquals(tax * 25 / 100, t.surcharge)
    }

    @Test fun oldRegimeSurchargeReaches37() {
        val t = old(60_050_000)
        assertEquals(t.slabTax * 37 / 100, t.surcharge)
    }

    @Test fun oldRegimeFiveLakhIsNil() {
        val t = old(500_000)
        assertEquals(r(450_000), t.taxable)
        assertEquals(r(10_000), t.slabTax)
        assertEquals(0L, t.total)
    }

    @Test fun oldRegimeTenLakhWithAndWithoutDeductions() {
        assertEquals(r(106_600), old(1_000_000).total)
        val d = old(1_000_000) { it.copy(c80 = r(150_000), d80Self = r(25_000)) }
        assertEquals(r(775_000), d.taxable)
        assertEquals(r(70_200), d.total)
    }

    @Test fun oldRegimeDeductionsAreCapped() {
        val d = old(1_000_000) { it.copy(c80 = r(400_000), d80Self = r(90_000), nps1B = r(80_000), homeLoanInterest = r(500_000)) }
        assertEquals(r(150_000 + 25_000 + 50_000 + 200_000), d.deductions)
    }

    @Test fun oldRegimeFifteenLakh() {
        assertEquals(r(257_400), old(1_500_000).total)
    }

    @Test fun newRegimeIgnoresOldDeductions() {
        val a = new(1_500_000)
        val b = new(1_500_000) { it.copy(c80 = r(150_000), d80Self = r(25_000)) }
        assertEquals(a.total, b.total)
        val e = new(1_500_000) { it.copy(employerNps = r(100_000)) }
        assertEquals(r(100_000), e.deductions)
    }

    @Test fun seniorAndSuperSeniorOldRegime() {
        val senior = TaxCalculator.compute(rules, Regime.OLD, TaxInput(salary = r(1_000_000), age = AgeBand.SENIOR))
        assertEquals(r(104_000), senior.total)
        val superSenior = TaxCalculator.compute(rules, Regime.OLD, TaxInput(salary = r(1_000_000), age = AgeBand.SUPER_SENIOR))
        assertEquals(r(93_600), superSenior.total)
        val seniorRebate = TaxCalculator.compute(rules, Regime.OLD, TaxInput(salary = r(550_000), age = AgeBand.SENIOR))
        assertEquals(0L, seniorRebate.total)
    }

    @Test fun capitalGainsAreSeparateAndNotRebated() {
        // Taxable slab income Rs 12 lakh plus Rs 1 lakh of taxable LTCG: the rebate no longer applies.
        val t = new(1_275_000) { it.copy(ltcgEquity = r(225_000)) }
        assertEquals(r(100_000), t.specialIncome)
        assertEquals(r(12_500), t.specialTax)
        assertEquals(0L, t.rebate)
        assertEquals(r(75_400), t.total)
    }

    @Test fun lowIncomeKeepsRebateOnSlabTaxButNotOnGains() {
        val t = new(500_000) { it.copy(stcgEquity = r(100_000)) }
        assertEquals(r(1_250), t.rebate)
        assertEquals(r(20_000), t.specialTax)
        assertEquals(r(20_800), t.total)
    }

    @Test fun unusedBasicExemptionAbsorbsGains() {
        val t = TaxCalculator.compute(rules, Regime.NEW, TaxInput(stcgEquity = r(300_000)))
        assertEquals(0L, t.total)
    }

    @Test fun ltcgUnderExemptionIsFree() {
        val t = new(2_500_000) { it.copy(ltcgEquity = r(125_000)) }
        assertEquals(0L, t.specialTax)
    }

    @Test fun rulesPerYear() {
        assertEquals(2026, TaxRules.forYear(2027).fyStartYear)
        assertEquals(2025, TaxRules.forYear(2025).fyStartYear)
        assertEquals(2024, TaxRules.forYear(2020).fyStartYear)
        val t = TaxCalculator.compute(TaxRules.FY2024_25, Regime.NEW, TaxInput(salary = r(775_000)))
        assertEquals(0L, t.total)
        val above = TaxCalculator.compute(TaxRules.FY2024_25, Regime.NEW, TaxInput(salary = r(975_000)))
        assertTrue(above.total > 0)
    }

    @Test fun rentGetsThirtyPercentDeduction() {
        val t = TaxCalculator.compute(rules, Regime.NEW, TaxInput(rent = r(1_000_000)))
        assertEquals(r(700_000), t.gross)
    }
}
