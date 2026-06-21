package com.example.smsspend

import com.example.smsspend.model.Fire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FireTest {

    @Test fun requiredNestEggFromYield() {
        // 500/mo = 6000/yr; at 8% need 75,000
        assertEquals(75000.0, Fire.requiredNestEgg(500.0, 8.0), 0.01)
    }

    @Test fun monthlyIncomeFromNestEgg() {
        assertEquals(500.0, Fire.monthlyIncome(75000.0, 8.0), 0.01)
    }

    @Test fun alreadyThereIsZeroMonths() {
        assertEquals(0, Fire.monthsToReach(current = 80000.0, monthly = 100.0, target = 75000.0, annualReturnPct = 8.0))
    }

    @Test fun zeroReturnLinearMonths() {
        // need 1000 more at 100/mo, no growth -> 10 months
        assertEquals(10, Fire.monthsToReach(current = 0.0, monthly = 100.0, target = 1000.0, annualReturnPct = 0.0))
    }

    @Test fun unreachableWhenNoContributionNoGrowth() {
        assertEquals(-1, Fire.monthsToReach(current = 100.0, monthly = 0.0, target = 1000.0, annualReturnPct = 0.0))
    }

    @Test fun growthReachesFasterThanLinear() {
        val withGrowth = Fire.monthsToReach(0.0, 100.0, 20000.0, 10.0)
        val noGrowth = Fire.monthsToReach(0.0, 100.0, 20000.0, 0.0)
        assertTrue(withGrowth in 1 until noGrowth)
    }
}
