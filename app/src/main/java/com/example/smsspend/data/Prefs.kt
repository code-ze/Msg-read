package com.example.smsspend.data

import android.content.Context

/**
 * Lightweight settings store (SharedPreferences — no extra dependency). Holds the pay-cycle
 * anchor day, the salary amount + manual balance the user pins, feature toggles, and the
 * cached snapshots home-screen widgets read without opening the app.
 *
 * The user's hand-entered values (salary, manual balance) live here rather than in the Room
 * database on purpose: SharedPreferences survives database schema changes, so this data is
 * never lost when the app updates.
 */
object Prefs {
    private const val FILE = "smsspend"
    private const val KEY_ANCHOR = "anchor_day"
    private const val KEY_INVEST_AS_SPEND = "invest_as_spend"
    private const val KEY_LIVE_MSX = "live_msx_prices"
    private const val KEY_SALARY = "salary_amount"
    private const val KEY_MANUAL_BALANCE = "manual_balance"
    private const val KEY_MANUAL_BALANCE_AT = "manual_balance_at"

    fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getAnchorDay(c: Context): Int = sp(c).getInt(KEY_ANCHOR, 20)
    fun setAnchorDay(c: Context, v: Int) = sp(c).edit().putInt(KEY_ANCHOR, v.coerceIn(1, 31)).apply()
    fun getInvestAsSpending(c: Context): Boolean = sp(c).getBoolean(KEY_INVEST_AS_SPEND, false)
    fun setInvestAsSpending(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_INVEST_AS_SPEND, v).apply()

    /** Pinned salary amount; 0 = auto-detect from deposits. */
    fun getSalaryAmount(c: Context): Double = sp(c).getFloat(KEY_SALARY, 0f).toDouble()
    fun setSalaryAmount(c: Context, v: Double) = sp(c).edit().putFloat(KEY_SALARY, v.toFloat()).apply()

    /** Manually entered current balance; 0 = use the latest balance scraped from SMS. */
    fun getManualBalance(c: Context): Double = sp(c).getFloat(KEY_MANUAL_BALANCE, 0f).toDouble()
    fun getManualBalanceAt(c: Context): Long = sp(c).getLong(KEY_MANUAL_BALANCE_AT, 0L)
    fun setManualBalance(c: Context, v: Double) = sp(c).edit()
        .putFloat(KEY_MANUAL_BALANCE, v.toFloat())
        .putLong(KEY_MANUAL_BALANCE_AT, if (v > 0) System.currentTimeMillis() else 0L)
        .apply()

    /** Opt-in: fetch live prices from MSX (adds network use). Off by default. */
    fun getLiveMsxPrices(c: Context): Boolean = sp(c).getBoolean(KEY_LIVE_MSX, false)
    fun setLiveMsxPrices(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_LIVE_MSX, v).apply()

    // --- per-widget config: which metric this widget shows ---
    val metricLabels = linkedMapOf(
        "spent" to "Spent",
        "income" to "Income",
        "net" to "Net",
        "invested" to "Invested",
        "balance" to "Balance",
        "perday" to "Per day",
        "projected" to "Projected"
    )

    fun setWidgetMetric(c: Context, widgetId: Int, metric: String) =
        sp(c).edit().putString("w_${widgetId}_metric", metric).apply()
    fun getWidgetMetric(c: Context, widgetId: Int): String =
        sp(c).getString("w_${widgetId}_metric", "spent") ?: "spent"
    fun clearWidgetMetric(c: Context, widgetId: Int) =
        sp(c).edit().remove("w_${widgetId}_metric").apply()

    // --- cached snapshot of the current period (set by the app, read by widgets) ---
    fun setWidgetSnapshot(
        c: Context,
        values: Map<String, String>,
        breakdown: String,
        trend: String,
        label: String
    ) {
        val e = sp(c).edit()
        for (key in metricLabels.keys) e.putString("w_$key", values[key] ?: "—")
        e.putString("w_breakdown", breakdown)
        e.putString("w_trend", trend)
        e.putString("w_label", label)
        e.apply()
    }
    fun widgetValue(c: Context, metric: String): String =
        sp(c).getString("w_$metric", "—") ?: "—"
    fun widgetBreakdown(c: Context): String = sp(c).getString("w_breakdown", "Open app to load") ?: "Open app to load"
    fun widgetTrend(c: Context): String = sp(c).getString("w_trend", "") ?: ""
    fun widgetLabel(c: Context): String = sp(c).getString("w_label", "This cycle") ?: "This cycle"
}
