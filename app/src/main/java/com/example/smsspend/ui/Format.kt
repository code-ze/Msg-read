package com.example.smsspend.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    fun omr(v: Double): String = String.format(Locale.US, "%.3f", v)
    fun omrSigned(v: Double, positive: Boolean): String =
        (if (positive) "+" else "−") + String.format(Locale.US, "%.3f", v)

    private val dayFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())
    fun day(ms: Long): String = dayFmt.format(Date(ms))
    fun dayTime(ms: Long): String = timeFmt.format(Date(ms))
}
