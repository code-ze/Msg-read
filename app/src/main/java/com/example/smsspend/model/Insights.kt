package com.example.smsspend.model

import com.example.smsspend.data.TxnEntity
import com.example.smsspend.parser.Categorizer
import com.example.smsspend.parser.TxnType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Spend rolled up for a single merchant within a period (the granular "where it goes"). */
data class MerchantLine(
    val merchant: String,
    val category: String,
    val total: Double,
    val count: Int
)

/**
 * Derived analytics for a period: pace, projection, runway and the granular merchant view.
 * Pure Kotlin (no Android types beyond [TxnEntity], which is a plain data class) so the math
 * is unit-tested. "Projection" and "runway" only mean something for the current open period;
 * for closed past periods [daysElapsed] == [daysTotal] so projection equals actual spend.
 */
data class Insights(
    val spent: Double,
    val txnCount: Int,
    val daysElapsed: Int,
    val daysTotal: Int,
    val perDay: Double,
    val projectedSpend: Double,
    val runwayDays: Int?,       // balance / perDay, null if no balance or no spend
    val largest: TxnEntity?,
    val busiestDayLabel: String?,
    val topMerchants: List<MerchantLine>
) {
    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        private fun type(t: TxnEntity): TxnType =
            runCatching { TxnType.valueOf(t.type) }.getOrDefault(TxnType.DEBIT)

        /** True spending only (a learned rule may have moved a debit into an income category). */
        fun isSpending(t: TxnEntity): Boolean {
            val ty = type(t)
            if (ty.isIncome) return false
            if (ty.isInvestment) return false
            return ty.isSpending && t.category !in Categorizer.incomeCategories
        }

        fun compute(
            txns: List<TxnEntity>,
            period: Period,
            now: Long,
            balance: Double,
            topN: Int = 6
        ): Insights {
            val spending = txns.filter { isSpending(it) }
            val spent = spending.sumOf { it.amount }

            val daysTotal = ((period.endExclusive - period.start) / DAY_MS).toInt().coerceAtLeast(1)
            val effectiveEnd = now.coerceIn(period.start, period.endExclusive)
            val daysElapsed = (((effectiveEnd - period.start) / DAY_MS).toInt() + 1)
                .coerceIn(1, daysTotal)

            val perDay = spent / daysElapsed
            val projectedSpend = perDay * daysTotal
            val runwayDays = if (balance > 0 && perDay > 0) (balance / perDay).toInt() else null

            val largest = spending.maxByOrNull { it.amount }

            val byMerchant = LinkedHashMap<String, MerchantLine>()
            for (t in spending) {
                val cur = byMerchant[t.merchantClean]
                byMerchant[t.merchantClean] =
                    if (cur == null) MerchantLine(t.merchantClean, t.category, t.amount, 1)
                    else cur.copy(total = cur.total + t.amount, count = cur.count + 1)
            }
            val topMerchants = byMerchant.values.sortedByDescending { it.total }.take(topN)

            val busiestDayLabel = spending
                .groupBy { dayOfWeek(it.date) }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .maxByOrNull { it.value }
                ?.key
                ?.let { dowName(it) }

            return Insights(
                spent = spent,
                txnCount = spending.size,
                daysElapsed = daysElapsed,
                daysTotal = daysTotal,
                perDay = perDay,
                projectedSpend = projectedSpend,
                runwayDays = runwayDays,
                largest = largest,
                busiestDayLabel = busiestDayLabel,
                topMerchants = topMerchants
            )
        }

        private fun dayOfWeek(ms: Long): Int =
            Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_WEEK)

        private fun dowName(dow: Int): String {
            val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, dow) }
            return SimpleDateFormat("EEEE", Locale.getDefault()).format(c.time) + "s"
        }
    }
}
