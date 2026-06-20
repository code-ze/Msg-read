package com.example.smsspend.data

import android.content.Context

/**
 * Lightweight settings store (SharedPreferences — no extra dependency). Holds the
 * pay-cycle anchor day, the "count investments as spending" toggle, and the cached
 * snapshots that home-screen widgets read without opening the app.
 */
object Prefs {
    private const val FILE = "smsspend"
    private const val KEY_ANCHOR = "anchor_day"
    private const val KEY_INVEST_AS_SPEND = "invest_as_spend"

    fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getAnchorDay(c: Context): Int = sp(c).getInt(KEY_ANCHOR, 20)
    fun setAnchorDay(c: Context, v: Int) = sp(c).edit().putInt(KEY_ANCHOR, v.coerceIn(1, 31)).apply()
    fun getInvestAsSpending(c: Context): Boolean = sp(c).getBoolean(KEY_INVEST_AS_SPEND, false)
    fun setInvestAsSpending(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_INVEST_AS_SPEND, v).apply()

    // --- per-widget config: which metric this widget shows (spent/income/net/invested) ---
    val metricLabels = linkedMapOf(
        "spent" to "Spent",
        "income" to "Income",
        "net" to "Net",
        "invested" to "Invested"
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
        spent: String, income: String, net: String, invested: String,
        breakdown: String, label: String
    ) {
        sp(c).edit()
            .putString("w_spent", spent)
            .putString("w_income", income)
            .putString("w_net", net)
            .putString("w_invested", invested)
            .putString("w_breakdown", breakdown)
            .putString("w_label", label)
            .apply()
    }
    fun widgetValue(c: Context, metric: String): String =
        sp(c).getString("w_$metric", "—") ?: "—"
    fun widgetBreakdown(c: Context): String = sp(c).getString("w_breakdown", "Open app to load") ?: "Open app to load"
    fun widgetLabel(c: Context): String = sp(c).getString("w_label", "This cycle") ?: "This cycle"
}
