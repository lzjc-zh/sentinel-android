package com.deepseek.lzjc.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.deepseek.lzjc.MainActivity
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.worker.WidgetRefreshWorker
import java.util.concurrent.TimeUnit

/**
 * Shared widget update logic — called by all three providers and by the refresh worker.
 */
object WidgetUpdater {
    fun update(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
        val data = WidgetDataCache(ctx).getWidgetData()
        val views = RemoteViews(ctx.packageName, R.layout.widget_balance)

        views.setTextViewText(R.id.widget_balance_amount, "¥${data.balance}")
        views.setTextViewText(R.id.widget_daily_amount, "今日 ¥${data.dailyCost}")
        views.setTextViewText(R.id.widget_monthly_amount, "本月 ¥${data.monthlyCost}")

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )

        mgr.updateAppWidget(widgetId, views)
    }
}

/** 2×1 compact */
class BalanceWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        scheduleRefresh(ctx)
        ids.forEach { WidgetUpdater.update(ctx, mgr, it) }
    }
    override fun onEnabled(ctx: Context) = scheduleRefresh(ctx)
}

/** 2×1 classic */
class BalanceWidgetMedium : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        scheduleRefresh(ctx)
        ids.forEach { WidgetUpdater.update(ctx, mgr, it) }
    }
    override fun onEnabled(ctx: Context) = scheduleRefresh(ctx)
}

/** 2×2 wide */
class BalanceWidgetLarge : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        scheduleRefresh(ctx)
        ids.forEach { WidgetUpdater.update(ctx, mgr, it) }
    }
    override fun onEnabled(ctx: Context) = scheduleRefresh(ctx)
}

private fun scheduleRefresh(ctx: Context) {
    WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
        "widget_balance_refresh",
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build()
    )
}
