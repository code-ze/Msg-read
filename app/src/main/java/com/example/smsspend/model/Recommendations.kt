package com.example.smsspend.model

import kotlin.math.roundToInt

/**
 * Turns the raw numbers into a small set of *actionable* recommendations — the "Insights &
 * Action" engine. Pure Kotlin and unit-tested; the UI just renders [Rec]s.
 *
 * Three engines:
 *  - Micro-Leak: high-frequency convenience spends (food delivery / cafés) you can throttle.
 *  - Idle Cash Drag: cash above your safety buffer that could be working (IPOs / dividends).
 *  - Fixed-Cost Audit: recurring bills (utilities/telecom/fuel) creeping above their baseline.
 *
 * Plus a Safe-to-Spend daily allowance so the app guides future behavior, not just reports it.
 */
object Recommendations {

    const val SAFE_TO_SPEND = "safe"
    const val MICRO_LEAK = "leak"
    const val IDLE_CASH = "idle"
    const val FIXED_COST = "fixed"

    data class Rec(val kind: String, val title: String, val body: String, val priority: Int)

    /** A recurring bill compared against its expected baseline for the same period length. */
    data class FixedCost(val category: String, val current: Double, val baseline: Double)

    /** A convenience-spend cluster this cycle (e.g. Food Delivery: 14 orders, 42 OMR). */
    data class Leak(val category: String, val count: Int, val total: Double)

    /** Per-day budget so the balance lasts until the next salary. 0 days left -> whole balance. */
    fun safeDailyAllowance(balance: Double, daysLeftInCycle: Int): Double =
        if (balance <= 0) 0.0 else if (daysLeftInCycle <= 0) balance else balance / daysLeftInCycle

    /** Cash sitting above a [bufferMonths]-month safety buffer of essential spend. */
    fun idleCash(balance: Double, monthlyEssentialSpend: Double, bufferMonths: Int): Double {
        val buffer = monthlyEssentialSpend * bufferMonths
        return (balance - buffer).coerceAtLeast(0.0)
    }

    fun analyze(
        balance: Double,
        perDay: Double,
        daysLeftInCycle: Int,
        monthlyEssentialSpend: Double,
        leaks: List<Leak>,
        fixed: List<FixedCost>,
        bufferMonths: Int = 6,
        leakCountThreshold: Int = 8
    ): List<Rec> {
        val recs = ArrayList<Rec>()

        // Safe-to-spend pace check.
        val safe = safeDailyAllowance(balance, daysLeftInCycle)
        if (safe > 0 && perDay > safe * 1.05) {
            val over = ((perDay - safe) / safe * 100).roundToInt()
            recs.add(
                Rec(
                    SAFE_TO_SPEND,
                    "Slow down to stay on track",
                    "You're spending ${fmt(perDay)} OMR/day but only ${fmt(safe)} OMR/day keeps your " +
                        "balance comfortably to your next salary — about $over% over a safe pace.",
                    priority = 5
                )
            )
        }

        // Micro-Leak velocity.
        val leak = leaks.maxByOrNull { it.count }
        val totalLeakCount = leaks.sumOf { it.count }
        val totalLeak = leaks.sumOf { it.total }
        if (leak != null && totalLeakCount >= leakCountThreshold && totalLeak > 0) {
            val freed = totalLeak * 0.30
            recs.add(
                Rec(
                    MICRO_LEAK,
                    "Micro-leak: ${leak.category}",
                    "You've made $totalLeakCount convenience purchases this cycle, totaling " +
                        "${fmt(totalLeak)} OMR. Cutting this velocity by 30% would free about " +
                        "${fmt(freed)} OMR next cycle to redirect into savings or shares.",
                    priority = 4
                )
            )
        }

        // Idle Cash Drag.
        val idle = idleCash(balance, monthlyEssentialSpend, bufferMonths)
        if (monthlyEssentialSpend > 0 && idle > monthlyEssentialSpend) {
            recs.add(
                Rec(
                    IDLE_CASH,
                    "Idle cash drag",
                    "Your $bufferMonths-month safety buffer is covered. About ${fmt(idle)} OMR is sitting " +
                        "idle in a non-interest account — consider an upcoming MSX IPO or a high-yield " +
                        "dividend listing to put it to work.",
                    priority = 3
                )
            )
        }

        // Fixed-Cost Audit.
        for (f in fixed) {
            if (f.baseline <= 0) continue
            val rise = ((f.current - f.baseline) / f.baseline * 100).roundToInt()
            if (rise >= 10) {
                recs.add(
                    Rec(
                        FIXED_COST,
                        "${f.category} up $rise%",
                        "Your ${f.category.lowercase()} spend (${fmt(f.current)} OMR) is $rise% above its " +
                            "recent average (${fmt(f.baseline)} OMR). Worth auditing for a creeping bill or " +
                            "a changed habit.",
                        priority = 2
                    )
                )
            }
        }

        return recs.sortedByDescending { it.priority }
    }

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%,.2f", v)
}
