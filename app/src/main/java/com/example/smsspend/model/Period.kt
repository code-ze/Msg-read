package com.example.smsspend.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class PeriodType { SALARY, PAY_CYCLE, MONTH, YEAR, CUSTOM }

/**
 * A half-open time window [start, endExclusive). Pure data so it can be unit-tested.
 * [contains] is the single source of truth for "is this transaction in the period".
 */
data class Period(
    val type: PeriodType,
    val start: Long,
    val endExclusive: Long,
    val label: String
) {
    fun contains(date: Long): Boolean = date >= start && date < endExclusive
}

/**
 * Builds periods. The pay-cycle is anchored on a configurable day-of-month (the user's
 * salary lands on the 20th, so the default cycle runs the 20th → the 19th of next month).
 */
object Periods {

    private fun cal(now: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = now }

    private fun startOfDay(c: Calendar) {
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
    }

    /** Clamp an anchor day to a month that may have fewer days (e.g. anchor 31 in Feb). */
    private fun clampDay(c: Calendar, day: Int) {
        val max = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        c.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, max))
    }

    /**
     * Pay-cycle containing [now], shifted by [offset] whole cycles (0 = current,
     * -1 = previous). Cycle starts on [anchorDay] and ends the day before the next anchor.
     */
    fun payCycle(anchorDay: Int, offset: Int, now: Long): Period {
        val ref = cal(now)
        // Find the cycle start that is at/before `now`.
        val start = cal(now)
        startOfDay(start)
        clampDay(start, anchorDay)
        if (ref.timeInMillis < start.timeInMillis) {
            // before this month's anchor -> cycle began last month
            start.add(Calendar.MONTH, -1)
            clampDay(start, anchorDay)
        }
        // apply offset
        start.add(Calendar.MONTH, offset)
        clampDay(start, anchorDay)

        val end = (start.clone() as Calendar)
        end.add(Calendar.MONTH, 1)
        clampDay(end, anchorDay)

        return Period(PeriodType.PAY_CYCLE, start.timeInMillis, end.timeInMillis, rangeLabel(start, end))
    }

    /**
     * Cycle bounded by detected salary deposits. [boundaries] are actual salary dates; each
     * cycle runs from one salary to the next, so the boundary follows the real landing day.
     * [offset] steps through history (0 = current open cycle since the last salary). Returns
     * null if there aren't enough boundaries (caller should fall back to [payCycle]).
     */
    fun salaryCycle(boundaries: List<Long>, offset: Int, now: Long): Period? {
        if (boundaries.isEmpty()) return null
        val bs = boundaries.sorted()
        val curIdx = bs.indexOfLast { it <= now }.let { if (it < 0) 0 else it }
        val idx = (curIdx + offset).coerceIn(0, bs.size - 1)
        val start = bs[idx]
        val open = idx == bs.size - 1
        val end = if (!open) bs[idx + 1] else maxOf(now, start) + DAY_MS
        val startCal = cal(start)
        val label = if (open) {
            "Since " + SimpleDateFormat("MMM d", Locale.getDefault()).format(startCal.time)
        } else {
            rangeLabel(startCal, cal(end))
        }
        return Period(PeriodType.SALARY, start, end, label)
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Calendar month containing [now], shifted by [offset] months. */
    fun month(offset: Int, now: Long): Period {
        val start = cal(now)
        startOfDay(start)
        start.set(Calendar.DAY_OF_MONTH, 1)
        start.add(Calendar.MONTH, offset)
        val end = (start.clone() as Calendar)
        end.add(Calendar.MONTH, 1)
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return Period(PeriodType.MONTH, start.timeInMillis, end.timeInMillis, fmt.format(start.time))
    }

    /** Calendar year containing [now], shifted by [offset] years. */
    fun year(offset: Int, now: Long): Period {
        val start = cal(now)
        startOfDay(start)
        start.set(Calendar.DAY_OF_YEAR, 1)
        start.add(Calendar.YEAR, offset)
        val end = (start.clone() as Calendar)
        end.add(Calendar.YEAR, 1)
        return Period(PeriodType.YEAR, start.timeInMillis, end.timeInMillis, start.get(Calendar.YEAR).toString())
    }

    /**
     * Custom range from the start of [startDayMillis] to the end of [endDayMillis]
     * (inclusive of the end day). Order-tolerant.
     */
    fun custom(startDayMillis: Long, endDayMillis: Long): Period {
        val lo = minOf(startDayMillis, endDayMillis)
        val hi = maxOf(startDayMillis, endDayMillis)
        val start = cal(lo); startOfDay(start)
        val end = cal(hi); startOfDay(end); end.add(Calendar.DAY_OF_MONTH, 1)
        return Period(PeriodType.CUSTOM, start.timeInMillis, end.timeInMillis, rangeLabel(start, end))
    }

    private fun rangeLabel(start: Calendar, endExclusive: Calendar): String {
        val last = (endExclusive.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }
        val sameYear = start.get(Calendar.YEAR) == last.get(Calendar.YEAR)
        val sameMonth = sameYear && start.get(Calendar.MONTH) == last.get(Calendar.MONTH)
        val d = SimpleDateFormat("d", Locale.getDefault())
        val md = SimpleDateFormat("MMM d", Locale.getDefault())
        val mdy = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return when {
            sameMonth -> "${md.format(start.time)} – ${d.format(last.time)}"
            sameYear -> "${md.format(start.time)} – ${md.format(last.time)}"
            else -> "${mdy.format(start.time)} – ${mdy.format(last.time)}"
        }
    }
}
