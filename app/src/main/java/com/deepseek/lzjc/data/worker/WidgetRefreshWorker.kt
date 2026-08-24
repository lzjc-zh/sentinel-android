package com.deepseek.lzjc.data.worker

import android.content.ComponentName
import android.content.Context
import android.appwidget.AppWidgetManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deepseek.lzjc.data.repository.MiMoRepository
import com.deepseek.lzjc.data.repository.UsageRepository
import com.deepseek.lzjc.ui.widget.BalanceWidgetLarge
import com.deepseek.lzjc.ui.widget.BalanceWidgetMedium
import com.deepseek.lzjc.ui.widget.BalanceWidgetSmall
import com.deepseek.lzjc.ui.widget.WidgetDataCache
import com.deepseek.lzjc.ui.widget.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Locale

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: UsageRepository,
    private val mimoRepository: MiMoRepository,
    private val widgetDataCache: WidgetDataCache
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val today = dateFormat.format(System.currentTimeMillis())
            val month = monthFormat.format(System.currentTimeMillis())

            // Refresh DeepSeek
            try {
                val balanceResult = repository.refreshAndRecord()
                val totalBalance = balanceResult.getOrNull()
                    ?.balanceInfos?.firstOrNull()?.totalBalance ?: "0.00"
                val dsDaily = repository.getDailyCost(today)
                val dsMonthly = repository.getMonthlyCost(month)
                val dsDailyReq = repository.getDailyRequestCount()
                val dsMonthlyReq = repository.getMonthlyRequestCount()

                widgetDataCache.saveDeepSeekData(
                    balance = totalBalance,
                    dailyCost = String.format("%.2f", dsDaily),
                    monthlyCost = String.format("%.2f", dsMonthly),
                    dailyRequests = dsDailyReq,
                    monthlyRequests = dsMonthlyReq
                )
            } catch (_: Exception) { }

            // Refresh MiMo
            try {
                if (mimoRepository.isLoggedIn()) {
                    mimoRepository.refreshAndFetch()
                    val (_, _, total) = mimoRepository.getStoredBalance()
                    val mimoDaily = mimoRepository.getTodayCost()
                    val mimoMonthly = mimoRepository.getMonthCost()
                    val mimoDailyReq = mimoRepository.getDailyRequestCount(today)
                    val mimoMonthlyReq = mimoRepository.getMonthlyRequestCount(month)

                    widgetDataCache.saveMiMoData(
                        balance = String.format("%.2f", total),
                        dailyCost = if (mimoDaily < 0.01) String.format("%.4f", mimoDaily) else String.format("%.2f", mimoDaily),
                        monthlyCost = String.format("%.2f", mimoMonthly),
                        dailyRequests = mimoDailyReq,
                        monthlyRequests = mimoMonthlyReq
                    )
                }
            } catch (_: Exception) { }

            // Update all widget instances via shared updater
            val mgr = AppWidgetManager.getInstance(appContext)
            for (cls in listOf(BalanceWidgetSmall::class.java, BalanceWidgetMedium::class.java, BalanceWidgetLarge::class.java)) {
                val ids = mgr.getAppWidgetIds(ComponentName(appContext, cls))
                ids.forEach { WidgetUpdater.update(appContext, mgr, it) }
            }

            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
