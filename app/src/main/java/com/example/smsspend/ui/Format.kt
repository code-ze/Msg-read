package com.example.smsspend.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    /** Full precision (3 dp = baisas) — for detailed transaction lists. */
    fun omr(v: Double): String = String.format(Locale.US, "%.3f", v)

    /** High-level dashboards: 2 dp with thousands separators (e.g. 1,399.85) to cut clutter. */
    fun omr2(v: Double): String = String.format(Locale.US, "%,.2f", v)

    /** Hero figures: whole rials with separators when large, else 2 dp (e.g. 8,782 / 42.50). */
    fun money(v: Double): String =
        if (kotlin.math.abs(v) >= 1000) String.format(Locale.US, "%,.0f", v)
        else String.format(Locale.US, "%,.2f", v)

    fun omrSigned(v: Double, positive: Boolean): String =
        (if (positive) "+" else "−") + String.format(Locale.US, "%.3f", v)

    /** Signed 2-dp variant for compact cards. */
    fun omr2Signed(v: Double, positive: Boolean): String =
        (if (positive) "+" else "−") + String.format(Locale.US, "%,.2f", v)

    private val dayFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())
    fun day(ms: Long): String = dayFmt.format(Date(ms))
    fun dayTime(ms: Long): String = timeFmt.format(Date(ms))
}
