package com.example.smsspend

import com.example.smsspend.data.TxnEntity
import com.example.smsspend.model.Insights
import com.example.smsspend.model.Period
import com.example.smsspend.model.PeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class InsightsTest {

    private fun d(y: Int, m0: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, m0, day, 12, 0, 0) }.timeInMillis

    private fun debit(amount: Double, merchant: String, category: String, date: Long) =
        TxnEntity(
            key = "$merchant-$date-$amount", type = "DEBIT", amount = amount,
            merchantRaw = merchant, merchantClean = merchant, date = date,
            category = category, body = ""
        )

    @Test fun computesPaceProjectionAndMerchants() {
        // A closed 10-day period so days elapsed == days total (deterministic projection).
        val start = d(2026, Calendar.JANUARY, 1)
        val end = d(2026, Calendar.JANUARY, 11)
        val period = Period(PeriodType.CUSTOM, start, end, "Jan 1 – 10")
        val now = d(2026, Calendar.JANUARY, 20) // after the period closed

        val txns = listOf(
            debit(30.0, "Talabat", "Food Delivery", d(2026, Calendar.JANUARY, 2)),
            debit(20.0, "Talabat", "Food Delivery", d(2026, Calendar.JANUARY, 5)),
            debit(100.0, "Lulu", "Groceries", d(2026, Calendar.JANUARY, 3)),
            // income should never count as spending
            TxnEntity(
                key = "inc", type = "DEPOSIT", amount = 500.0, merchantRaw = "Deposit",
                merchantClean = "Deposit", date = d(2026, Calendar.JANUARY, 4),
                category = "Income", body = ""
            )
        )

        val ins = Insights.compute(txns, period, now, balance = 300.0)

        assertEquals(150.0, ins.spent, 0.0001)
        assertEquals(3, ins.txnCount)
        assertEquals(10, ins.daysTotal)
        assertEquals(10, ins.daysElapsed)
        assertEquals(15.0, ins.perDay, 0.0001)
        assertEquals(150.0, ins.projectedSpend, 0.0001)
        assertEquals(20, ins.runwayDays) // 300 / 15
        assertEquals("Lulu", ins.largest?.merchantClean)

        // granular: Lulu (100) ahead of Talabat (50, 2 items)
        assertEquals("Lulu", ins.topMerchants[0].merchant)
        assertEquals(100.0, ins.topMerchants[0].total, 0.0001)
        assertEquals("Talabat", ins.topMerchants[1].merchant)
        assertEquals(2, ins.topMerchants[1].count)
    }

    @Test fun noRunwayWithoutBalance() {
        val start = d(2026, Calendar.JANUARY, 1)
        val end = d(2026, Calendar.JANUARY, 11)
        val period = Period(PeriodType.CUSTOM, start, end, "Jan")
        val ins = Insights.compute(
            listOf(debit(10.0, "X", "Other", d(2026, Calendar.JANUARY, 2))),
            period, d(2026, Calendar.JANUARY, 20), balance = 0.0
        )
        assertNull(ins.runwayDays)
    }
}
