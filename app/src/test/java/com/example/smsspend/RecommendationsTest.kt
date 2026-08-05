package com.example.smsspend

import com.example.smsspend.model.Recommendations
import com.example.smsspend.model.Recommendations.FixedCost
import com.example.smsspend.model.Recommendations.Leak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationsTest {

    @Test fun safeDailyAllowanceDividesBalanceByDaysLeft() {
        assertEquals(50.0, Recommendations.safeDailyAllowance(500.0, 10), 0.0001)
        assertEquals(300.0, Recommendations.safeDailyAllowance(300.0, 0), 0.0001) // no days -> whole
        assertEquals(0.0, Recommendations.safeDailyAllowance(0.0, 10), 0.0001)
    }

    @Test fun idleCashIsAboveBuffer() {
        // buffer = 200 * 6 = 1200; balance 5000 -> 3800 idle
        assertEquals(3800.0, Recommendations.idleCash(5000.0, 200.0, 6), 0.0001)
        assertEquals(0.0, Recommendations.idleCash(800.0, 200.0, 6), 0.0001) // below buffer
    }

    @Test fun microLeakFiresOnHighFrequency() {
        val recs = Recommendations.analyze(
            balance = 5000.0, perDay = 10.0, daysLeftInCycle = 20,
            monthlyEssentialSpend = 300.0,
            leaks = listOf(Leak("Food Delivery", 14, 42.0), Leak("Cafes & Tea", 6, 18.0)),
            fixed = emptyList()
        )
        val leak = recs.firstOrNull { it.kind == Recommendations.MICRO_LEAK }
        assertNotNull(leak)
        assertTrue(leak!!.body.contains("20 convenience purchases")) // 14 + 6
    }

    @Test fun fixedCostFlagsRiseOverTenPercent() {
        val recs = Recommendations.analyze(
            balance = 1000.0, perDay = 5.0, daysLeftInCycle = 30,
            monthlyEssentialSpend = 150.0,
            leaks = emptyList(),
            fixed = listOf(
                FixedCost("Fuel & Transport", current = 56.0, baseline = 50.0), // +12%
                FixedCost("Telecom", current = 20.0, baseline = 20.0)           // flat -> no rec
            )
        )
        val fixed = recs.filter { it.kind == Recommendations.FIXED_COST }
        assertEquals(1, fixed.size)
        assertTrue(fixed[0].title.contains("12%"))
    }

    @Test fun noMicroLeakBelowThreshold() {
        val recs = Recommendations.analyze(
            balance = 1000.0, perDay = 5.0, daysLeftInCycle = 30,
            monthlyEssentialSpend = 150.0,
            leaks = listOf(Leak("Food Delivery", 3, 12.0)),
            fixed = emptyList()
        )
        assertNull(recs.firstOrNull { it.kind == Recommendations.MICRO_LEAK })
    }
}
