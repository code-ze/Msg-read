package com.example.smsspend

import java.text.SimpleDateFormat
import java.util.*

data class Insight(
    val emoji: String,
    val title: String,
    val value: String,
    val sub: String,
    val detail: String,
    val highlight: Boolean = false
)

object InsightsEngine {

    private val mf = SimpleDateFormat("MMM yyyy", Locale.US)
    private val df = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val dayFmt = SimpleDateFormat("MMM d", Locale.US)

    fun compute(all: List<Txn>): List<Insight> {
        if (all.isEmpty()) return listOf(Insight("📭", "No Data", "Refresh to load", "Pull SMS transactions", ""))
        val insights = mutableListOf<Insight>()

        val expenses = all.filter { it.type == "debit" || it.type == "walletOut" }
        val income = all.filter { it.type == "deposit" || it.type == "walletIn" }

        // 1. Average daily spend
        val totalSpent = expenses.sumOf { it.amount }
        val days = daySpan(all)
        val perDay = if (days > 0) totalSpent / days else 0.0
        insights += Insight("📅", "Average Daily Spend",
            "%.3f OMR".format(perDay),
            "over $days days tracked",
            "You spend an average of %.3f OMR every day. Over a month that's %.0f OMR, over a year %.0f OMR.".format(
                perDay, perDay * 30, perDay * 365))

        // 2. Biggest single day
        val byDay = expenses.groupBy { dayKey(it.date) }
        val biggestDay = byDay.maxByOrNull { (_, txns) -> txns.sumOf { it.amount } }
        if (biggestDay != null) {
            val dayAmt = biggestDay.value.sumOf { it.amount }
            val dayDate = Date(biggestDay.value.first().date)
            val merchants = biggestDay.value.sortedByDescending { it.amount }.take(3)
                .joinToString(", ") { it.merchant }
            insights += Insight("💸", "Biggest Spending Day",
                "%.3f OMR".format(dayAmt),
                dayFmt.format(dayDate),
                "On ${df.format(dayDate)} you spent %.3f OMR. Top transactions: $merchants.".format(dayAmt),
                highlight = true)
        }

        // 3. Top category
        val byCat = expenses.groupBy { it.category }
        val topCat = byCat.maxByOrNull { (_, v) -> v.sumOf { it.amount } }
        if (topCat != null) {
            val catAmt = topCat.value.sumOf { it.amount }
            val pct = if (totalSpent > 0) (catAmt / totalSpent * 100).toInt() else 0
            insights += Insight("🏆", "Top Spending Category",
                topCat.key,
                "%.0f%% of total spend (%.0f OMR)".format(pct.toDouble(), catAmt),
                "${topCat.key} accounts for $pct% of your tracked spending (%.3f OMR). You made ${topCat.value.size} transactions in this category.".format(catAmt))
        }

        // 4. Most frequent merchant
        val byMerchant = expenses.groupBy { it.merchant }
        val topMerchant = byMerchant.maxByOrNull { (_, v) -> v.size }
        if (topMerchant != null) {
            val mAmt = topMerchant.value.sumOf { it.amount }
            insights += Insight("🏪", "Most Visited",
                topMerchant.key,
                "${topMerchant.value.size} visits · %.0f OMR total".format(mAmt),
                "You visited ${topMerchant.key} ${topMerchant.value.size} times, spending a total of %.3f OMR (avg %.3f per visit).".format(
                    mAmt, mAmt / topMerchant.value.size))
        }

        // 5. Best and worst months
        val byMonth = expenses.groupBy { monthKey(it.date) }
        val monthData = byMonth.mapValues { (_, v) -> v.sumOf { it.amount } }
        if (monthData.size >= 2) {
            val best = monthData.minByOrNull { it.value }
            val worst = monthData.maxByOrNull { it.value }
            if (best != null) {
                insights += Insight("✅", "Best Month (Lowest Spend)",
                    best.key,
                    "%.0f OMR".format(best.value),
                    "Your most frugal month was ${best.key} with %.3f OMR spent.".format(best.value))
            }
            if (worst != null) {
                insights += Insight("🔥", "Worst Month (Highest Spend)",
                    worst.key,
                    "%.0f OMR".format(worst.value),
                    "Your highest spending month was ${worst.key} with %.3f OMR spent.".format(worst.value), highlight = true)
            }
        }

        // 6. Savings rate
        val totalIncome = income.sumOf { it.amount }
        if (totalIncome > 0) {
            val savedAmt = totalIncome - totalSpent
            val saveRate = (savedAmt / totalIncome * 100).toInt()
            val emoji = when {
                saveRate > 30 -> "💰"
                saveRate > 10 -> "💵"
                saveRate < 0 -> "⚠️"
                else -> "📊"
            }
            insights += Insight(emoji, "Savings Rate",
                "$saveRate%",
                "%.0f saved of %.0f OMR earned".format(savedAmt, totalIncome),
                "You earned %.3f OMR and spent %.3f OMR. Your savings rate is $saveRate%${if (saveRate < 0) " — you're spending more than you earn!" else "."}".format(
                    totalIncome, totalSpent),
                highlight = saveRate < 0)
        }

        // 7. Unusual transactions (>2x daily average)
        val unusual = expenses.filter { it.amount > perDay * 2 }
            .sortedByDescending { it.amount }
        if (unusual.isNotEmpty()) {
            val top = unusual.first()
            insights += Insight("🚨", "Largest Single Transaction",
                "%.3f OMR".format(top.amount),
                "${top.merchant} · ${dayFmt.format(Date(top.date))}",
                "Your largest single transaction was %.3f OMR at ${top.merchant} on ${df.format(Date(top.date))}. You had ${unusual.size} transactions above %.0f OMR (2× daily average).".format(
                    top.amount, perDay * 2))
        }

        // 8. Month-over-month trend (last 2 months)
        val sorted = byMonth.entries.sortedBy { it.key }
        if (sorted.size >= 2) {
            val prev = sorted[sorted.size - 2].value
            val curr = sorted[sorted.size - 1].value
            val diff = curr - prev
            val pct = if (prev > 0) (diff / prev * 100).toInt() else 0
            val emoji = if (diff < 0) "📉" else "📈"
            insights += Insight(emoji, "Month-over-Month",
                "${if (diff >= 0) "+" else ""}$pct%",
                "vs last month",
                "This month you spent %.3f OMR vs %.3f OMR last month — a change of ${if (diff >= 0) "+" else ""}%.3f OMR ($pct%%).".format(
                    curr, prev, diff))
        }

        // 9. Income sources
        val incomeBySource = income.groupBy { it.merchant }
        if (incomeBySource.size > 1) {
            val srcs = incomeBySource.entries.sortedByDescending { (_, v) -> v.sumOf { it.amount } }
                .take(3).joinToString(", ") { "${it.key} (%.0f)".format(it.value.sumOf { t -> t.amount }) }
            insights += Insight("💼", "Income Sources",
                "${incomeBySource.size} sources",
                srcs,
                "You have ${incomeBySource.size} income sources: $srcs")
        }

        // 10. Subscription burn
        val subs = byCat["Subscriptions"]
        if (subs != null && subs.isNotEmpty()) {
            val subTotal = subs.sumOf { it.amount }
            val monthlyEst = if (days > 0) subTotal / days * 30 else 0.0
            insights += Insight("📱", "Subscriptions",
                "%.0f OMR/month".format(monthlyEst),
                "${subs.size} charges tracked",
                "You've spent %.3f OMR on subscriptions (%.0f OMR/month estimated). Services: ${subs.map { it.merchant }.distinct().joinToString(", ")}.".format(
                    subTotal, monthlyEst))
        }

        // 11. Projected month end (current month)
        val now = Calendar.getInstance()
        val thisMonthExpenses = expenses.filter {
            val c = Calendar.getInstance().also { c -> c.timeInMillis = it.date }
            c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        }
        if (thisMonthExpenses.isNotEmpty()) {
            val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
            val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
            val monthSoFar = thisMonthExpenses.sumOf { it.amount }
            val projected = monthSoFar / dayOfMonth * daysInMonth
            insights += Insight("🔮", "Month-End Projection",
                "%.0f OMR".format(projected),
                "based on ${dayOfMonth}d of ${daysInMonth}d",
                "You've spent %.3f OMR in %d days. At this pace, you'll spend ~%.0f OMR by month end.".format(
                    monthSoFar, dayOfMonth, projected))
        }

        return insights
    }

    private fun dayKey(ts: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ts))
    private fun monthKey(ts: Long): String = mf.format(Date(ts))

    private fun daySpan(txns: List<Txn>): Int {
        if (txns.isEmpty()) return 1
        val min = txns.minOf { it.date }
        val max = txns.maxOf { it.date }
        val d = ((max - min) / 86400000L).toInt()
        return maxOf(d, 1)
    }
}
