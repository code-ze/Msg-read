package com.example.smsspend

import com.example.smsspend.model.Periods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PeriodsTest {

    private fun millis(year: Int, month0: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear(); set(year, month0, day, 12, 0, 0)
        }.timeInMillis

    private fun field(ms: Long, f: Int): Int =
        Calendar.getInstance().apply { timeInMillis = ms }.get(f)

    @Test fun payCycleAfterAnchor() {
        // now = 25 Jun 2025, anchor 20 -> cycle 20 Jun .. 20 Jul (exclusive)
        val p = Periods.payCycle(20, 0, millis(2025, Calendar.JUNE, 25))
        assertEquals(Calendar.JUNE, field(p.start, Calendar.MONTH))
        assertEquals(20, field(p.start, Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JULY, field(p.endExclusive, Calendar.MONTH))
        assertEquals(20, field(p.endExclusive, Calendar.DAY_OF_MONTH))
        assertTrue(p.contains(millis(2025, Calendar.JULY, 1)))
        assertTrue(!p.contains(millis(2025, Calendar.JULY, 20)))
    }

    @Test fun payCycleBeforeAnchor() {
        // now = 10 Jun 2025, anchor 20 -> cycle 20 May .. 20 Jun
        val p = Periods.payCycle(20, 0, millis(2025, Calendar.JUNE, 10))
        assertEquals(Calendar.MAY, field(p.start, Calendar.MONTH))
        assertEquals(20, field(p.start, Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JUNE, field(p.endExclusive, Calendar.MONTH))
    }

    @Test fun payCycleOffset() {
        val p = Periods.payCycle(20, -1, millis(2025, Calendar.JUNE, 25))
        assertEquals(Calendar.MAY, field(p.start, Calendar.MONTH))
        assertEquals(20, field(p.start, Calendar.DAY_OF_MONTH))
    }

    @Test fun anchorClampsInShortMonths() {
        // anchor 31 in February should not crash and stays a valid ordered range
        val p = Periods.payCycle(31, 0, millis(2025, Calendar.FEBRUARY, 15))
        assertTrue(p.start < p.endExclusive)
    }

    @Test fun monthAndYear() {
        val m = Periods.month(0, millis(2025, Calendar.JUNE, 15))
        assertEquals(1, field(m.start, Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JUNE, field(m.start, Calendar.MONTH))

        val y = Periods.year(0, millis(2025, Calendar.JUNE, 15))
        assertEquals(2025, field(y.start, Calendar.YEAR))
        assertEquals(2026, field(y.endExclusive, Calendar.YEAR))
    }

    @Test fun customRangeIsInclusiveOfEndDay() {
        val p = Periods.custom(millis(2025, Calendar.JUNE, 1), millis(2025, Calendar.JUNE, 20))
        assertTrue(p.contains(millis(2025, Calendar.JUNE, 20)))
        assertTrue(!p.contains(millis(2025, Calendar.JUNE, 21)))
    }

    @Test fun salaryCycleUsesActualBoundaries() {
        val boundaries = listOf(
            millis(2026, Calendar.JANUARY, 20),
            millis(2026, Calendar.FEBRUARY, 19),
            millis(2026, Calendar.MARCH, 23)
        )
        val now = millis(2026, Calendar.MARCH, 25)

        val current = Periods.salaryCycle(boundaries, 0, now)!!
        assertEquals(23, field(current.start, Calendar.DAY_OF_MONTH))
        assertTrue(current.contains(millis(2026, Calendar.MARCH, 24)))
        assertTrue(!current.contains(millis(2026, Calendar.FEBRUARY, 28)))

        val prev = Periods.salaryCycle(boundaries, -1, now)!!
        assertEquals(19, field(prev.start, Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.FEBRUARY, field(prev.start, Calendar.MONTH))
        assertTrue(prev.contains(millis(2026, Calendar.MARCH, 1)))
        assertTrue(!prev.contains(millis(2026, Calendar.MARCH, 23)))
    }

    @Test fun salaryCycleNullWhenNoBoundaries() {
        assertEquals(null, Periods.salaryCycle(emptyList(), 0, millis(2026, Calendar.MARCH, 25)))
    }
}
