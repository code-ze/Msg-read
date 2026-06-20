package com.example.smsspend

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SpendingWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(c, mgr, id)
    }

    companion object {
        fun render(c: Context, mgr: AppWidgetManager, id: Int) {
            val (total, text) = Store.summary(c)
            val rv = RemoteViews(c.packageName, R.layout.widget_spending)
            rv.setTextViewText(R.id.w_total, total)
            rv.setTextViewText(R.id.w_text, text)
            val pi = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.w_root, pi)
            mgr.updateAppWidget(id, rv)
        }
    }
}
