package com.coinglass.intel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.coinglass.intel.MainActivity
import com.coinglass.intel.R
import com.coinglass.intel.data.db.AppDb
import kotlinx.coroutines.runBlocking

class IntelWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                paint(context, manager, ids)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, IntelWidget::class.java))
            if (ids.isEmpty()) return
            paint(ctx, mgr, ids)
        }

        private fun paint(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val snaps = runBlocking { AppDb.get(ctx).snap().all() }
            val top = WidgetPicks.top(snaps, 3)
            val views = RemoteViews(ctx.packageName, R.layout.intel_widget)
            val openApp = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            if (top.isEmpty()) {
                views.setTextViewText(R.id.widget_title, "COINGLASS INTEL")
                views.setTextViewText(R.id.widget_line1, "watchlist bos")
                views.setTextViewText(R.id.widget_line2, "uygulamada pair yaz, yildiza bas")
                views.setTextViewText(R.id.widget_line3, "")
            } else {
                views.setTextViewText(R.id.widget_title, "WATCHLIST  |skor|")
                val lines = intArrayOf(R.id.widget_line1, R.id.widget_line2, R.id.widget_line3)
                lines.forEachIndexed { i, id ->
                    val s = top.getOrNull(i)
                    if (s == null) {
                        views.setTextViewText(id, "")
                        views.setOnClickPendingIntent(id, openApp)
                    } else {
                        views.setTextViewText(
                            id,
                            "${s.symbol}  ${"%+.1f".format(s.score)}  ${s.direction}  R${s.risk}",
                        )
                        val tap = PendingIntent.getActivity(
                            ctx, s.symbol.hashCode(),
                            Intent(ctx, MainActivity::class.java).putExtra("symbol", s.symbol),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        views.setOnClickPendingIntent(id, tap)
                    }
                }
            }
            ids.forEach { mgr.updateAppWidget(it, views) }
        }
    }
}
