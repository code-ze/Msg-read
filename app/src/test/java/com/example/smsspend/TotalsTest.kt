package com.example.smsspend

import com.example.smsspend.data.TxnEntity
import com.example.smsspend.model.Totals
import com.example.smsspend.parser.Categorizer
import com.example.smsspend.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test

class TotalsTest {

    private fun txn(type: TxnType, amount: Double, category: String) = TxnEntity(
        key = "$type-$amount-$category",
        type = type.name,
        amount = amount,
        merchantRaw = "x",
        merchantClean = "x",
        date = 0L,
        category = category,
        body = ""
    )

    private val sample = listOf(
        txn(TxnType.DEBIT, 10.0, "Groceries"),
        txn(TxnType.DEPOSIT, 100.0, Categorizer.INCOME),
        txn(TxnType.IPO, 50.0, Categorizer.INVESTMENTS),
        txn(TxnType.DIVIDEND, 5.0, Categorizer.DIVIDENDS)
    )

    @Test fun investmentsExcludedFromSpending() {
        val t = Totals.from(sample, investAsSpending = false)
        assertEquals(10.0, t.spent, 0.0001)
        assertEquals(105.0, t.income, 0.0001)   // deposit + dividend
        assertEquals(50.0, t.invested, 0.0001)
        assertEquals(95.0, t.net, 0.0001)
        assertEquals(1, t.byCategory.size)       // Groceries only
    }

    @Test fun investmentsIncludedInSpending() {
        val t = Totals.from(sample, investAsSpending = true)
        assertEquals(60.0, t.spent, 0.0001)
        assertEquals(50.0, t.invested, 0.0001)
        assertEquals(2, t.byCategory.size)       // Groceries + Investments
    }
}
