package com.example.smsspend.model

import kotlin.math.ceil

/**
 * Early-retirement (FIRE) math, pure and unit-tested.
 *
 * The idea: to live off investments you need a nest egg large enough that its yearly return
 * (dividends + growth you withdraw) covers your target yearly spending. Then we project how
 * long it takes to get there given what you already hold plus a monthly contribution.
 */
object Fire {

    data class Result(
        val requiredNestEgg: Double,
        val monthsToReach: Int,      // -1 = not reachable within 100 years at these inputs
        val currentMonthlyIncome: Double,
        val projectedAtTargetMonthly: Double
    )

    /** Nest egg whose annual return at [annualReturnPct] yields [targetMonthlyIncome] per month. */
    fun requiredNestEgg(targetMonthlyIncome: Double, annualReturnPct: Double): Double {
        if (annualReturnPct <= 0.0) return Double.POSITIVE_INFINITY
        return targetMonthlyIncome * 12.0 / (annualReturnPct / 100.0)
    }

    /** Monthly income a [nestEgg] throws off at [annualReturnPct]. */
    fun monthlyIncome(nestEgg: Double, annualReturnPct: Double): Double =
        nestEgg * (annualReturnPct / 100.0) / 12.0

    /** Future value of [current] plus [monthly] contributions after [months], compounded monthly. */
    fun futureValue(current: Double, monthly: Double, annualReturnPct: Double, months: Int): Double {
        if (months <= 0) return current
        val r = annualReturnPct / 100.0 / 12.0
        if (r <= 0.0) return current + monthly * months
        val growth = Math.pow(1 + r, months.toDouble())
        return current * growth + monthly * ((growth - 1) / r)
    }

    /**
     * Monthly contribution needed to grow [current] to [target] within [months] at
     * [annualReturnPct]. 0 if [current] already gets there on its own; ∞ if months <= 0.
     */
    fun requiredMonthlyContribution(
        current: Double,
        target: Double,
        annualReturnPct: Double,
        months: Int
    ): Double {
        if (months <= 0) return Double.POSITIVE_INFINITY
        val r = annualReturnPct / 100.0 / 12.0
        if (r <= 0.0) {
            val need = target - current
            return if (need <= 0) 0.0 else need / months
        }
        val growth = Math.pow(1 + r, months.toDouble())
        val fvCurrent = current * growth
        if (fvCurrent >= target) return 0.0
        return (target - fvCurrent) * r / (growth - 1)
    }

    /**
     * Months until [current] grows to [target] with [monthly] added each month and monthly
     * compounding at [annualReturnPct]. 0 if already there, -1 if it never gets there.
     */
    fun monthsToReach(current: Double, monthly: Double, target: Double, annualReturnPct: Double): Int {
        if (target <= current) return 0
        val r = annualReturnPct / 100.0 / 12.0
        if (r <= 0.0) return if (monthly <= 0.0) -1 else ceil((target - current) / monthly).toInt()
        var balance = current
        var n = 0
        while (balance < target && n < 1200) { // cap at 100 years
            balance = balance * (1 + r) + monthly
            n++
        }
        return if (balance >= target) n else -1
    }

    fun plan(
        targetMonthlyIncome: Double,
        annualReturnPct: Double,
        currentInvested: Double,
        monthlyContribution: Double
    ): Result {
        val need = requiredNestEgg(targetMonthlyIncome, annualReturnPct)
        val months = if (need.isFinite())
            monthsToReach(currentInvested, monthlyContribution, need, annualReturnPct) else -1
        return Result(
            requiredNestEgg = need,
            monthsToReach = months,
            currentMonthlyIncome = monthlyIncome(currentInvested, annualReturnPct),
            projectedAtTargetMonthly = targetMonthlyIncome
        )
    }
}
