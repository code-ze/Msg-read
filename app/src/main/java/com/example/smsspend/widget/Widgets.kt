package com.example.smsspend.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.smsspend.MainActivity
import com.example.smsspend.R
import com.example.smsspend.data.Prefs

/** Re-renders every placed widget across all providers using the latest cached snapshot. */
object WidgetUpdater {
    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        renderProvider(context, mgr, TotalWidget::class.java) { c, m, id -> TotalWidget.render(c, m, id) }
        renderProvider(context, mgr, BreakdownWidget::class.java) { c, m, id -> BreakdownWidget.render(c, m, id) }
    }

    private fun renderProvider(
        context: Context,
        mgr: AppWidgetManager,
        cls: Class<*>,
        render: (Context, AppWidgetManager, Int) -> Unit
    ) {
        val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
        for (id in ids) render(context, mgr, id)
    }

    fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
}

/** Compact widget: a single chosen metric (Spent/Income/Net/Invested) for the period. */
class TotalWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(c, mgr, id)
    }
    override fun onDeleted(c: Context, ids: IntArray) {
        for (id in ids) Prefs.clearWidgetMetric(c, id)
    }
    companion object {
        fun render(c: Context, mgr: AppWidgetManager, id: Int) {
            val metric = Prefs.getWidgetMetric(c, id)
            val title = Prefs.metricLabels[metric] ?: "Spent"
            val rv = RemoteViews(c.packageName, R.layout.widget_total)
            rv.setTextViewText(R.id.w_title, "$title · ${Prefs.widgetLabel(c)}")
            rv.setTextViewText(R.id.w_value, Prefs.widgetValue(c, metric))
            rv.setOnClickPendingIntent(R.id.w_root, WidgetUpdater.openAppIntent(c))
            mgr.updateAppWidget(id, rv)
        }
    }
}

/** Larger widget: chosen metric plus a top-categories breakdown. */
class BreakdownWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(c, mgr, id)
    }
    override fun onDeleted(c: Context, ids: IntArray) {
        for (id in ids) Prefs.clearWidgetMetric(c, id)
    }
    companion object {
        fun render(c: Context, mgr: AppWidgetManager, id: Int) {
            val metric = Prefs.getWidgetMetric(c, id)
            val title = Prefs.metricLabels[metric] ?: "Spent"
            val rv = RemoteViews(c.packageName, R.layout.widget_breakdown)
            rv.setTextViewText(R.id.w_title, "$title · ${Prefs.widgetLabel(c)}")
            rv.setTextViewText(R.id.w_value, Prefs.widgetValue(c, metric))
            rv.setTextViewText(R.id.w_trend, Prefs.widgetTrend(c))
            rv.setTextViewText(R.id.w_breakdown, Prefs.widgetBreakdown(c))
            rv.setOnClickPendingIntent(R.id.w_root, WidgetUpdater.openAppIntent(c))
            mgr.updateAppWidget(id, rv)
        }
    }
}
