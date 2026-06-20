package com.example.smsspend

import com.example.smsspend.model.SalaryDetector
import com.example.smsspend.model.SalaryDetector.Income
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SalaryDetectorTest {
    private fun d(y: Int, m0: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, m0, day, 12, 0, 0) }.timeInMillis

    @Test fun detectsVariableDateSalary() {
        val incomes = listOf(
            // salary ~500, lands on different days each month
            Income(500.0, d(2026, Calendar.JANUARY, 20)),
            Income(500.0, d(2026, Calendar.FEBRUARY, 19)),
            Income(515.0, d(2026, Calendar.MARCH, 23)),   // small raise
            Income(515.0, d(2026, Calendar.APRIL, 21)),
            // noise: dividends and a one-off deposit
            Income(70.0, d(2026, Calendar.FEBRUARY, 5)),
            Income(70.0, d(2026, Calendar.MARCH, 6)),
            Income(1000.0, d(2026, Calendar.MARCH, 2))
        )
        val dates = SalaryDetector.detect(incomes)
        assertEquals(4, dates.size)
        // boundary days should be the real salary days
        val days = dates.map { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_MONTH) }
        assertEquals(listOf(20, 19, 23, 21), days)
    }

    @Test fun emptyWhenNotEnough() {
        assertEquals(emptyList<Long>(), SalaryDetector.detect(listOf(Income(500.0, d(2026, Calendar.JANUARY, 20)))))
    }

    @Test fun largeSalaryBeatsFrequentSmallTransfers() {
        // Salary (1679) lands only twice; casual money people send recurs across MORE months.
        // The salary must still win — it's the *largest* recurring deposit, not the most frequent.
        val incomes = listOf(
            Income(1679.0, d(2026, Calendar.APRIL, 23)),
            Income(1679.0, d(2026, Calendar.MAY, 21)),
            Income(50.0, d(2026, Calendar.MARCH, 3)),
            Income(50.0, d(2026, Calendar.APRIL, 9)),
            Income(50.0, d(2026, Calendar.MAY, 4)),
            Income(50.0, d(2026, Calendar.JUNE, 5))
        )
        val dates = SalaryDetector.detect(incomes)
        assertEquals(2, dates.size)
        val days = dates.map { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_MONTH) }
        assertEquals(listOf(23, 21), days)
    }

    @Test fun pinnedAmountIsMatchedExactly() {
        val incomes = listOf(
            Income(1679.0, d(2026, Calendar.APRIL, 23)),
            Income(1679.0, d(2026, Calendar.MAY, 21)),
            Income(2000.0, d(2026, Calendar.MAY, 28)), // a bigger one-off; ignored when pinned
            Income(50.0, d(2026, Calendar.JUNE, 5))
        )
        val dates = SalaryDetector.detect(incomes, expectedAmount = 1679.0)
        val days = dates.map { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_MONTH) }
        assertEquals(listOf(23, 21), days)
    }
}
