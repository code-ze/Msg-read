package com.example.smsspend.model

import com.example.smsspend.data.TxnEntity
import com.example.smsspend.parser.Categorizer
import com.example.smsspend.parser.TxnType

data class CategoryLine(val category: String, val total: Double, val count: Int)

/**
 * Period totals derived from a list of transactions. [investAsSpending] flips whether IPO
 * subscriptions count toward [spent] or are reported separately as [invested].
 */
data class Totals(
    val spent: Double,
    val income: Double,
    val invested: Double,
    val net: Double,
    val byCategory: List<CategoryLine>
) {
    companion object {
        private fun type(t: TxnEntity): TxnType =
            runCatching { TxnType.valueOf(t.type) }.getOrDefault(TxnType.DEBIT)

        fun from(txns: List<TxnEntity>, investAsSpending: Boolean): Totals {
            var spent = 0.0
            var income = 0.0
            var invested = 0.0
            val byCat = LinkedHashMap<String, CategoryLine>()

            for (t in txns) {
                val ty = type(t)
                when {
                    ty.isIncome -> income += t.amount
                    ty.isInvestment -> {
                        invested += t.amount
                        if (investAsSpending) {
                            spent += t.amount
                            accumulate(byCat, t.category, t.amount)
                        }
                    }
                    ty.isSpending -> {
                        // a learned rule could have moved a debit into an income category
                        if (t.category in Categorizer.incomeCategories) {
                            income += t.amount
                        } else {
                            spent += t.amount
                            accumulate(byCat, t.category, t.amount)
                        }
                    }
                }
            }

            val lines = byCat.values.sortedByDescending { it.total }
            return Totals(spent, income, invested, income - spent, lines)
        }

        private fun accumulate(map: MutableMap<String, CategoryLine>, cat: String, amt: Double) {
            val cur = map[cat]
            map[cat] = if (cur == null) CategoryLine(cat, amt, 1)
            else cur.copy(total = cur.total + amt, count = cur.count + 1)
        }
    }
}
