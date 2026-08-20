package com.deepseek.lzjc.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import com.deepseek.lzjc.data.db.DailyModelBreakdown
import com.deepseek.lzjc.data.db.DailyUsageSummary
import com.deepseek.lzjc.data.db.EnhancedDailySummary
import com.deepseek.lzjc.data.db.EnhancedModelCostSummary
import com.deepseek.lzjc.data.db.ModelCostSummary
import com.deepseek.lzjc.data.db.UsageDao
import com.deepseek.lzjc.data.db.UsageEntity
import com.deepseek.lzjc.data.mimo.MiMoCookieManager
import com.deepseek.lzjc.data.mimo.MiMoDailyDataPoint
import com.deepseek.lzjc.data.mimo.MiMoDataScraper
import com.deepseek.lzjc.data.mimo.MiMoModelUsage
import com.deepseek.lzjc.data.mimo.MiMoUsageData
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class MiMoRepository @Inject constructor(
    private val scraper: MiMoDataScraper,
    private val cookieManager: MiMoCookieManager,
    private val usageDao: UsageDao,
    @Named("mimo") private val dataStore: DataStore<Preferences>
) {
    companion object {
        const val PLATFORM_KEY = "mimo"
        private val KEY_LAST_BALANCE = doublePreferencesKey("mimo_last_balance")
        private val KEY_CASH_BALANCE = doublePreferencesKey("mimo_cash_balance")
        private val KEY_GIFT_BALANCE = doublePreferencesKey("mimo_gift_balance")
        private val KEY_TOTAL_BALANCE = doublePreferencesKey("mimo_total_balance")
        private val KEY_COST_PER_TOKEN = doublePreferencesKey("mimo_cost_per_token")
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    suspend fun isLoggedIn(): Boolean {
        return cookieManager.getCookies() != null
    }

    fun getLoginUrl(): String = MiMoCookieManager.getLoginUrl()

    suspend fun saveCookiesFromWebView(): Boolean {
        val cookies = cookieManager.extractCookiesFromWebView() ?: return false
        cookieManager.saveCookies(cookies)
        return true
    }

    suspend fun logout() {
        cookieManager.clearCookies()
    }

    /**
     * Fetch overview data and track balance deltas for daily cost computation.
     */
    suspend fun refreshAndFetch(): Result<MiMoUsageData> {
        val result = scraper.scrapeUsageData()
        result.onSuccess { data ->
            // Compute cost-per-token from cumulative totals
            val totalCost = data.totalCost.replace("¥", "").replace(",", "").toDoubleOrNull() ?: 0.0
            val totalTokens = data.tokenHistory
            val costPerToken = if (totalTokens > 0) totalCost / totalTokens else 0.0

            // Persist costPerToken for stable historical cost calculation
            dataStore.edit {
                it[KEY_COST_PER_TOKEN] = costPerToken
            }

            // Store daily token data in Room with estimated per-model cost
            storeDailyDataInRoom(costPerToken)

            // Track balance delta for real-money tracking (supplementary)
            trackBalanceDelta(data)

            // Persist balance data for AnalyticsScreen
            val cash = data.cashBalance.toDoubleOrNull() ?: 0.0
            val gift = data.giftBalance.toDoubleOrNull() ?: 0.0
            val total = data.totalBalance.toDoubleOrNull() ?: 0.0
            dataStore.edit {
                it[KEY_CASH_BALANCE] = cash
                it[KEY_GIFT_BALANCE] = gift
                it[KEY_TOTAL_BALANCE] = total
            }
        }
        return result
    }

    /**
     * Get persisted MiMo balance (cash, gift, total).
     */
    suspend fun getStoredBalance(): Triple<Double, Double, Double> {
        val prefs = dataStore.data.first()
        return Triple(
            prefs[KEY_CASH_BALANCE] ?: 0.0,
            prefs[KEY_GIFT_BALANCE] ?: 0.0,
            prefs[KEY_TOTAL_BALANCE] ?: 0.0
        )
    }

    /**
     * Track balance changes to compute daily spend (like DeepSeek's balance-delta).
     * MiMo balance decreases as you spend, so: delta = lastBalance - currentBalance.
     */
    private suspend fun trackBalanceDelta(data: MiMoUsageData) {
        val currentBalance = data.totalBalance.toDoubleOrNull() ?: return
        val prefs = dataStore.data.first()
        val lastBalance = prefs[KEY_LAST_BALANCE] ?: currentBalance

        val delta = lastBalance - currentBalance
        if (delta > 0.001) {
            val today = dateFormat.format(Date())
            val month = monthFormat.format(Date())
            usageDao.deleteByDateAndModelAndPlatform(today, "balance-delta", PLATFORM_KEY)
            usageDao.insert(
                UsageEntity(
                    timestamp = System.currentTimeMillis(),
                    date = today,
                    month = month,
                    model = "balance-delta",
                    platform = PLATFORM_KEY,
                    totalTokens = 0,
                    costAmount = delta
                )
            )
        }

        dataStore.edit { it[KEY_LAST_BALANCE] = currentBalance }
    }

    /**
     * Fetch daily data for current + previous month and store in Room.
     */
    suspend fun storeDailyDataInRoom(costPerToken: Double = 0.0) {
        try {
            val cal = Calendar.getInstance()
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH) + 1

            val result1 = scraper.fetchDailyData(currentYear, currentMonth)
            result1.onSuccess { storePoints(it, currentYear, currentMonth, costPerToken) }

            val prevMonth = if (currentMonth > 1) currentMonth - 1 else 12
            val prevYear = if (currentMonth > 1) currentYear else currentYear - 1
            val result2 = scraper.fetchDailyData(prevYear, prevMonth)
            result2.onSuccess { storePoints(it, prevYear, prevMonth, costPerToken) }
        } catch (_: Exception) {}
    }

    private suspend fun storePoints(points: List<MiMoDailyDataPoint>, year: Int, month: Int, costPerToken: Double = 0.0) {
        val monthStr = String.format("%04d-%02d", year, month)
        for (point in points) {
            val fullDate = "$year-${point.dayKey}"
            val dailyCost = point.totalToken * costPerToken

            // "total" record: only carries estimated daily cost.
            // Token fields = 0 to avoid double-counting with per-model records.
            usageDao.deleteByDateAndModelAndPlatform(fullDate, "total", PLATFORM_KEY)
            usageDao.insert(
                UsageEntity(
                    timestamp = System.currentTimeMillis(),
                    date = fullDate,
                    month = monthStr,
                    model = "total",
                    platform = PLATFORM_KEY,
                    inputTokens = 0,
                    outputTokens = 0,
                    totalTokens = 0,
                    costAmount = dailyCost,
                    cacheHitTokens = 0,
                    cacheMissTokens = 0,
                    requestCount = 0
                )
            )

            // Per-model records: carry token data AND proportional cost
            // costAmount = modelToken / dailyTotalToken * dailyCost
            val dailyTotalForProportion = point.totalToken.coerceAtLeast(1)
            for (modelData in point.models) {
                val modelCost = if (dailyTotalForProportion > 0 && costPerToken > 0) {
                    modelData.data.totalToken.toDouble() / dailyTotalForProportion * dailyCost
                } else 0.0

                usageDao.deleteByDateAndModelAndPlatform(fullDate, modelData.name, PLATFORM_KEY)
                usageDao.insert(
                    UsageEntity(
                        timestamp = System.currentTimeMillis(),
                        date = fullDate,
                        month = monthStr,
                        model = modelData.name,
                        platform = PLATFORM_KEY,
                        inputTokens = modelData.data.inputToken,
                        outputTokens = modelData.data.outputToken,
                        totalTokens = modelData.data.totalToken,
                        costAmount = modelCost,
                        cacheHitTokens = modelData.data.cacheToken,
                        cacheMissTokens = modelData.data.uncachedInput,
                        requestCount = modelData.data.requestCount
                    )
                )
            }
        }
    }

    // ===== Query methods =====

    suspend fun getDailyRequestCount(date: String): Long {
        return usageDao.getDailyRequestCountByPlatform(date, PLATFORM_KEY)
    }

    suspend fun getMonthlyRequestCount(month: String): Long {
        return usageDao.getMonthlyRequestCountByPlatform(month, PLATFORM_KEY)
    }

    /**
     * Get daily cost list including balance-delta records.
     * These represent actual money spent per day.
     */
    suspend fun getDailyCostList(days: Int = 30): List<DailyUsageSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getDailyCostListSinceByPlatform(fromDate, PLATFORM_KEY)
    }

    /**
     * Get average daily cost from estimated per-model costs.
     */
    suspend fun getAvgDailyCost(days: Int = 7): Double {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getAvgDailyEstimatedCostSinceByPlatform(fromDate, PLATFORM_KEY)
    }

    /**
     * Get model token usage for pie chart (excluding balance-delta, total, and zero-token models).
     * Returns token-based model summaries for the last N days.
     */
    suspend fun getModelTokenSummary(days: Int = 30): List<ModelCostSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        val enhanced = usageDao.getEnhancedModelCostSinceByPlatform(fromDate, PLATFORM_KEY)
        return enhanced
            .filter { it.model != "total" && it.model != "balance-delta" && it.totalTokens > 0 }
            .map { e ->
                ModelCostSummary(
                    model = e.model,
                    costAmount = 0.0,
                    totalTokens = e.totalTokens
                )
            }
    }

    /**
     * Get daily estimated cost trend (from per-model costAmount aggregation).
     * Uses costPerToken computed from cumulative totals for historical stability.
     */
    suspend fun getDailyEstimatedCostTrend(days: Int = 30): List<DailyUsageSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getDailyEstimatedCostSinceByPlatform(fromDate, PLATFORM_KEY)
    }

    /**
     * Get daily cash spending trend (balance-delta records, real money).
     * Supplementary: only has data from 2nd refresh onward.
     */
    suspend fun getDailyCashTrend(days: Int = 30): List<DailyUsageSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getDailyBalanceDeltaSinceByPlatform(fromDate, PLATFORM_KEY)
    }

    /**
     * Get daily subscription token trend (from per-model records).
     */
    suspend fun getDailySubscriptionTokenTrend(days: Int = 30): List<DailyUsageSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getDailyTokenTrendSinceByPlatform(fromDate, PLATFORM_KEY)
    }

    suspend fun getMonthlyCacheHitRate(month: String): Double {
        val hit = usageDao.getMonthlyCacheHitTokensByPlatform(month, PLATFORM_KEY)
        val miss = usageDao.getMonthlyCacheMissTokensByPlatform(month, PLATFORM_KEY)
        val total = hit + miss
        return if (total > 0) hit.toDouble() / total else 0.0
    }

    suspend fun getMonthlyCacheTokens(month: String): Pair<Long, Long> {
        val hit = usageDao.getMonthlyCacheHitTokensByPlatform(month, PLATFORM_KEY)
        val miss = usageDao.getMonthlyCacheMissTokensByPlatform(month, PLATFORM_KEY)
        return hit to miss
    }

    suspend fun getEnhancedDailyData(days: Int = 30): List<EnhancedDailySummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getEnhancedDailySinceByPlatform(fromDate, PLATFORM_KEY)
    }

    suspend fun getDailyModelBreakdowns(days: Int = 7): List<DailyModelBreakdown> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getDailyModelBreakdownSinceByPlatform(fromDate, PLATFORM_KEY)
    }
}
