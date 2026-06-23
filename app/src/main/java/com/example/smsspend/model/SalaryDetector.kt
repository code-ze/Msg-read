package com.example.smsspend.model

import java.util.Calendar
import kotlin.math.abs

/**
 * Auto-detects salary deposits from income transactions so pay cycles can follow the real
 * landing date each month (which drifts — e.g. the 19th one month, the 23rd the next).
 *
 * The salary is the *largest deposit that recurs roughly once a month*. Earlier logic picked
 * the cluster that spanned the most months, but casual "people sending me money" deposits
 * recur across more months than the handful of captured salary payments — so the salary lost.
 * We now cluster by similar amount (salary is near-constant, with tolerance for raises) and
 * pick the recurring cluster with the **largest** amount; ad-hoc transfers are smaller and
 * lose to the salary. If the user pins their exact salary amount we match that directly.
 *
 * Returns one boundary date per month (the deposit closest to the salary amount that month).
 * Pure and unit-tested.
 */
object SalaryDetector {

    data class Income(val amount: Double, val date: Long)

    /**
     * @param expectedAmount if > 0, the user-pinned salary; deposits within [pinTolerance]
     *        of it are treated as salary (most reliable). 0 means auto-detect.
     */
    fun detect(
        incomes: List<Income>,
        expectedAmount: Double = 0.0,
        tolerance: Double = 0.18,
        pinTolerance: Double = 0.08
    ): List<Long> {
        if (expectedAmount > 0.0) {
            val matches = incomes.filter {
                abs(it.amount - expectedAmount) / expectedAmount <= pinTolerance
            }
            return oneBoundaryPerMonth(matches, expectedAmount)
        }

        if (incomes.size < 2) return emptyList()

        // Greedily cluster by similar amount (sorted so near amounts sit together).
        val sorted = incomes.sortedBy { it.amount }
        val clusters = mutableListOf<MutableList<Income>>()
        for (inc in sorted) {
            val c = clusters.lastOrNull()
            val ref = c?.last()?.amount ?: 0.0
            if (c != null && ref > 0 && abs(inc.amount - ref) / ref <= tolerance) {
                c.add(inc)
            } else {
                clusters.add(mutableListOf(inc))
            }
        }

        data class Scored(val months: Int, val median: Double, val dates: List<Long>)

        val scored = clusters.mapNotNull { cl ->
            val byMonth = cl.groupBy { ym(it.date) }
            if (byMonth.size < 2) return@mapNotNull null // must recur to be a salary
            val amounts = cl.map { it.amount }.sorted()
            val median = amounts[amounts.size / 2]
            Scored(byMonth.size, median, oneBoundaryPerMonth(cl, median))
        }

        // Salary = the largest recurring deposit; tie-break on the cluster spanning more months.
        val best = scored.maxWithOrNull(compareBy({ it.median }, { it.months })) ?: return emptyList()
        return best.dates
    }

    /** One date per calendar month: the deposit nearest [target], to keep boundaries clean. */
    private fun oneBoundaryPerMonth(incomes: List<Income>, target: Double): List<Long> =
        incomes.groupBy { ym(it.date) }
            .values
            .map { group -> group.minByOrNull { abs(it.amount - target) }!!.date }
            .sorted()

    /** Year*12 + month, so consecutive months are adjacent integers. */
    private fun ym(date: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = date }
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }
}
