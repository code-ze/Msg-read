package com.example.smsspend.model

import java.util.Calendar
import kotlin.math.abs

/**
 * Auto-detects salary deposits from income transactions so pay cycles can follow the real
 * landing date each month (which drifts — e.g. the 19th one month, the 23rd the next).
 *
 * Heuristic: cluster income amounts that are similar (salary is roughly constant, with
 * tolerance for raises), then pick the cluster that recurs across the most distinct months —
 * that's the salary. Returns one boundary date per month (the largest matching deposit that
 * month). Pure and unit-tested.
 */
object SalaryDetector {

    data class Income(val amount: Double, val date: Long)

    fun detect(incomes: List<Income>, tolerance: Double = 0.18): List<Long> {
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
            val dates = byMonth.values.map { group -> group.maxByOrNull { it.amount }!!.date }.sorted()
            val amounts = cl.map { it.amount }.sorted()
            Scored(byMonth.size, amounts[amounts.size / 2], dates)
        }

        // Salary = recurs across the most months; tie-break on the larger amount.
        val best = scored.maxWithOrNull(compareBy({ it.months }, { it.median })) ?: return emptyList()
        return best.dates
    }

    /** Year*12 + month, so consecutive months are adjacent integers. */
    private fun ym(date: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = date }
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }
}
