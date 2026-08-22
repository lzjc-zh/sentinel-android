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
 * Widget base class — three sizes share refresh scheduling and update logic.
 */
open class BalanceWidgetProvider : AppWidgetProvider() {

    protected open fun getLayoutId(): Int = R.layout.widget_balance

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        schedulePeriodicRefresh(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, getLayoutId())
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicRefresh(context)
    }

    private fun schedulePeriodicRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "widget_balance_refresh", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    companion object {
        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            layoutId: Int
        ) {
            val cache = WidgetDataCache(context)
            val data = cache.getWidgetData()
            val views = RemoteViews(context.packageName, layoutId)

            // Set logo on medium/large
            if (layoutId != R.layout.widget_balance) {
                views.setImageViewResource(R.id.widget_logo, R.mipmap.ic_launcher)
            }

            // Click to open app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Platform name + color
            val platformColor = if (data.platform == "MiMo") 0xFFFF6A00.toInt() else 0xFF4D6BFE.toInt()
            views.setTextViewText(R.id.widget_platform, data.platform)
            views.setTextColor(R.id.widget_platform, platformColor)

            // Balance
            views.setTextViewText(R.id.widget_balance_amount, "¥${data.balance}")

            // Daily cost
            views.setTextViewText(R.id.widget_daily_amount, "¥${data.dailyCost}")

            // Monthly cost
            views.setTextViewText(R.id.widget_monthly_amount, "¥${data.monthlyCost}")

            // Request counts (medium + large only)
            if (layoutId != R.layout.widget_balance) {
                views.setTextViewText(R.id.widget_daily_requests, "${data.dailyRequests} 次请求")
                views.setTextViewText(R.id.widget_monthly_requests, "${data.monthlyRequests} 次请求")
            }

            // Update time (large only)
            if (layoutId == R.layout.widget_balance_large) {
                views.setTextViewText(R.id.widget_update_time, formatUpdateTime(data.lastUpdate))
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun formatUpdateTime(timestamp: Long): String {
            if (timestamp == 0L) return "未更新"
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            return when {
                diff < 60_000 -> "刚刚更新"
                diff < 3600_000 -> "${diff / 60_000} 分钟前"
                diff < 86400_000 -> "${diff / 3600_000} 小时前"
                else -> "${diff / 86400_000} 天前"
            }
        }
    }
}

/** 2×1 compact — platform + balance + daily/monthly */
class BalanceWidgetSmall : BalanceWidgetProvider() {
    override fun getLayoutId() = R.layout.widget_balance
}

/** 2×1 classic — logo + platform + balance + info cards with requests */
class BalanceWidgetMedium : BalanceWidgetProvider() {
    override fun getLayoutId() = R.layout.widget_balance_medium
}

/** 2×2 large — logo + platform + balance + cards + update time */
class BalanceWidgetLarge : BalanceWidgetProvider() {
    override fun getLayoutId() = R.layout.widget_balance_large
}
