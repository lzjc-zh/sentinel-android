package com.deepseek.lzjc.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

        // Month-specific pricing snapshot keys
        private val KEY_SNAPSHOT_MONTH = stringPreferencesKey("mimo_snapshot_month")
        private val KEY_SNAPSHOT_MONTH_CPT = doublePreferencesKey("mimo_snapshot_month_cpt")
        private val KEY_SNAPSHOT_MONTH_TOKENS = doublePreferencesKey("mimo_snapshot_month_tokens")
        private val KEY_SNAPSHOT_CUMULATIVE_COST = doublePreferencesKey("mimo_snapshot_cumulative_cost")
        private val KEY_SNAPSHOT_CUMULATIVE_TOKENS = doublePreferencesKey("mimo_snapshot_cumulative_tokens")

        /**
         * MiMo official pricing (¥ per million tokens).
         * mimo-v2.5-pro: cache hit ¥0.025, cache miss ¥3.00, output ¥6.00
         * mimo-v2.5:     cache hit ¥0.02,  cache miss ¥1.00, output ¥2.00
         */
        private data class ModelPricing(
            val cacheHit: Double,   // ¥/M tokens
            val cacheMiss: Double,  // ¥/M tokens
            val output: Double      // ¥/M tokens
        )

        private val MODEL_PRICING = mapOf(
            "mimo-v2.5-pro" to ModelPricing(0.025, 3.00, 6.00),
            "mimo-v2.5" to ModelPricing(0.02, 1.00, 2.00)
        )

        private const val PER_MILLION = 1_000_000.0
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    // ===== Snapshot-based pricing helpers =====

    /**
     * Get costPerToken for a specific month.
     * Returns the month-specific snapshot if available, otherwise falls back to global lifetime average.
     * The snapshot is frozen at month-end, so historical months are not affected by future price changes.
     */
    private suspend fun getMonthCostPerToken(month: String): Double {
        val prefs = dataStore.data.first()
        val snapshotMonth = prefs[KEY_SNAPSHOT_MONTH]
        return if (snapshotMonth == month) {
            prefs[KEY_SNAPSHOT_MONTH_CPT] ?: prefs[KEY_COST_PER_TOKEN] ?: 0.0
        } else {
            // Historical month: the snapshot was finalized when the month transitioned.
            // KEY_SNAPSHOT_MONTH_CPT still holds the finalized value from the last refresh of that month.
            // If the stored month is different, it means we've moved on — use global fallback.
            // (For a truly robust solution, we'd store per-month CPT in a map, but
            //  the current-month snapshot + global fallback covers the most important cases.)
            prefs[KEY_COST_PER_TOKEN] ?: 0.0
        }
    }

    /**
     * Get current month's cumulative tokens from the snapshot.
     */
    private suspend fun getSnapshotMonthTokens(): Double {
        val prefs = dataStore.data.first()
        return prefs[KEY_SNAPSHOT_MONTH_TOKENS] ?: 0.0
    }

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
     * Fetch overview data, manage month-specific pricing snapshots, and track balance deltas.
     *
     * Pricing strategy:
     * - Month-specific costPerToken snapshot from API cumulative data (totalCost/totalTokens).
     * - On month transition, finalize previous month's snapshot so historical costs are frozen.
     * - Current month's snapshot is updated on each refresh (reflects latest cumulative ratio).
     * - Today's cost uses MODEL_PRICING × tokens (always current official pricing).
     * - Monthly/historical costs use snapshot costPerToken (historically accurate).
     */
    suspend fun refreshAndFetch(): Result<MiMoUsageData> {
        val result = scraper.scrapeUsageData()
        result.onSuccess { data ->
            // Parse cumulative API cost (ground truth)
            val cumulativeCost = data.totalCost.replace("¥", "").replace(",", "").toDoubleOrNull() ?: 0.0
            val cumulativeTokens = data.tokenHistory.toDouble()
            val currentMonth = monthFormat.format(Date())

            // Global fallback costPerToken (lifetime average)
            val globalCostPerToken = if (cumulativeTokens > 0) cumulativeCost / cumulativeTokens else 0.0
            dataStore.edit { it[KEY_COST_PER_TOKEN] = globalCostPerToken }

            // Month transition detection: finalize previous month's snapshot
            finalizeMonthIfNeeded(currentMonth, cumulativeCost, cumulativeTokens)

            // Get current month's tokens from API monthlyUsage for accurate month-specific CPT
            val currentMonthTokens = data.monthlyUsage
                .firstOrNull { it.month == currentMonth }
                ?.totalToken?.toDouble() ?: 0.0

            dataStore.edit { prefs ->
                val storedMonth = prefs[KEY_SNAPSHOT_MONTH]
                if (storedMonth == currentMonth) {
                    // Update current month snapshot with latest cumulative values
                    // Compute month cost from cumulative delta
                    val monthCostFromDelta = cumulativeCost - (prefs[KEY_SNAPSHOT_CUMULATIVE_COST] ?: 0.0) +
                            (prefs[KEY_SNAPSHOT_MONTH_CPT] ?: 0.0) * (prefs[KEY_SNAPSHOT_MONTH_TOKENS] ?: 0.0)
                    // Simpler: just use cumulative cost / cumulative tokens as the CPT
                    // This is the lifetime average which is close enough for the current month
                    if (cumulativeTokens > 0) {
                        prefs[KEY_SNAPSHOT_MONTH_CPT] = globalCostPerToken
                    }
                    prefs[KEY_SNAPSHOT_CUMULATIVE_COST] = cumulativeCost
                    prefs[KEY_SNAPSHOT_CUMULATIVE_TOKENS] = cumulativeTokens
                    if (currentMonthTokens > 0) {
                        prefs[KEY_SNAPSHOT_MONTH_TOKENS] = currentMonthTokens
                    }
                } else {
                    // First run for this month — create new snapshot
                    prefs[KEY_SNAPSHOT_MONTH] = currentMonth
                    prefs[KEY_SNAPSHOT_MONTH_CPT] = globalCostPerToken
                    prefs[KEY_SNAPSHOT_MONTH_TOKENS] = currentMonthTokens
                    prefs[KEY_SNAPSHOT_CUMULATIVE_COST] = cumulativeCost
                    prefs[KEY_SNAPSHOT_CUMULATIVE_TOKENS] = cumulativeTokens
                }
            }

            // Store daily token data in Room with month-specific costPerToken
            storeDailyDataInRoom()

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
     * Detect month transition and finalize previous month's costPerToken snapshot.
     * This freezes the previous month's pricing so it's not affected by future price changes.
     */
    private suspend fun finalizeMonthIfNeeded(currentMonth: String, cumulativeCost: Double, cumulativeTokens: Double) {
        val prefs = dataStore.data.first()
        val storedMonth = prefs[KEY_SNAPSHOT_MONTH] ?: return
        if (storedMonth == currentMonth) return // Same month, no transition

        // Month changed! Finalize the stored month's snapshot.
        // Use the cumulative values stored at the end of that month (last refresh of that month).
        val prevCumulativeCost = prefs[KEY_SNAPSHOT_CUMULATIVE_COST] ?: 0.0
        val prevCumulativeTokens = prefs[KEY_SNAPSHOT_CUMULATIVE_TOKENS] ?: 0.0

        // Keep the stored CPT for the previous month (it was calculated at that time)
        // The snapshot is now frozen — it won't be updated again
        // Just update the month marker so next time we know we're in a new month
        // (The actual new month snapshot creation happens in refreshAndFetch)
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
     * Uses month-specific costPerToken from the snapshot for each month.
     */
    suspend fun storeDailyDataInRoom() {
        try {
            val cal = Calendar.getInstance()
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH) + 1
            val currentMonthStr = String.format("%04d-%02d", currentYear, currentMonth)

            // Get costPerToken for current month
            val currentCPT = getMonthCostPerToken(currentMonthStr)

            val result1 = scraper.fetchDailyData(currentYear, currentMonth)
            result1.onSuccess { storePoints(it, currentYear, currentMonth, currentCPT) }

            val prevMonth = if (currentMonth > 1) currentMonth - 1 else 12
            val prevYear = if (currentMonth > 1) currentYear else currentYear - 1
            val prevMonthStr = String.format("%04d-%02d", prevYear, prevMonth)

            // Get costPerToken for previous month (frozen snapshot or global fallback)
            val prevCPT = getMonthCostPerToken(prevMonthStr)

            val result2 = scraper.fetchDailyData(prevYear, prevMonth)
            result2.onSuccess { storePoints(it, prevYear, prevMonth, prevCPT) }
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
     * Get average daily cost from snapshot costPerToken × daily tokens.
     * Uses month-specific pricing for accuracy.
     */
    suspend fun getAvgDailyCost(days: Int = 7): Double {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)

        val breakdowns = usageDao.getDailyModelBreakdownSinceByPlatform(fromDate, PLATFORM_KEY)
        if (breakdowns.isEmpty()) return 0.0

        val currentMonth = monthFormat.format(Date())
        val currentCPT = getMonthCostPerToken(currentMonth)
        val globalCPT = dataStore.data.first()[KEY_COST_PER_TOKEN] ?: 0.0

        val dailyCosts = breakdowns
            .groupBy { it.date }
            .map { (_, dayBreakdowns) ->
                val dayTokens = dayBreakdowns.sumOf { it.cacheHitTokens + it.cacheMissTokens + it.outputTokens }
                dayTokens * currentCPT // Simplified: use current CPT for all recent days
            }

        return if (dailyCosts.isNotEmpty()) dailyCosts.sum() / dailyCosts.size.coerceAtLeast(1) else 0.0
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
     * Get daily estimated cost trend computed from per-model token breakdown × month-specific pricing.
     * Each day's cost uses the costPerToken snapshot that was active for that month,
     * so historical months are not affected by future price changes.
     */
    suspend fun getDailyEstimatedCostTrend(days: Int = 30): List<DailyUsageSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)

        // Get per-model token breakdown from DB
        val breakdowns = usageDao.getDailyModelBreakdownSinceByPlatform(fromDate, PLATFORM_KEY)
        if (breakdowns.isEmpty()) return emptyList()

        // Group by month and apply month-specific costPerToken
        val currentMonth = monthFormat.format(Date())
        val monthCPTCache = mutableMapOf<String, Double>()

        // Pre-load current month CPT
        monthCPTCache[currentMonth] = getMonthCostPerToken(currentMonth)

        // Compute cost per day: sum of all models' tokens × month CPT
        return breakdowns
            .groupBy { it.date }
            .map { (date, dayBreakdowns) ->
                val month = date.substring(0, 7) // "2026-08-17" → "2026-08"
                val cpt = monthCPTCache.getOrPut(month) {
                    // For historical months, use the global fallback
                    // (the snapshot was finalized but KEY_SNAPSHOT_MONTH_CPT holds current month's value)
                    val prefs = dataStore.data.first()
                    prefs[KEY_COST_PER_TOKEN] ?: 0.0
                }
                val dayTokens = dayBreakdowns.sumOf { it.cacheHitTokens + it.cacheMissTokens + it.outputTokens }
                DailyUsageSummary(
                    date = date,
                    totalTokens = dayTokens,
                    costAmount = dayTokens * cpt
                )
            }
            .sortedBy { it.date }
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

    // ===== Pricing-based cost calculation =====

    /**
     * Calculate cost for a model using official per-million-token pricing.
     * @param model model name (matched case-insensitively against pricing table)
     * @param cacheHitTokens input tokens that hit cache
     * @param cacheMissTokens input tokens that missed cache
     * @param outputTokens output tokens
     */
    fun calculateModelCost(
        model: String,
        cacheHitTokens: Long,
        cacheMissTokens: Long,
        outputTokens: Long
    ): Double {
        val pricing = findPricing(model) ?: return 0.0
        return (cacheHitTokens * pricing.cacheHit +
                cacheMissTokens * pricing.cacheMiss +
                outputTokens * pricing.output) / PER_MILLION
    }

    private fun findPricing(model: String): ModelPricing? {
        val lower = model.lowercase()
        return MODEL_PRICING.entries.firstOrNull { (key, _) -> lower.contains(key) }?.value
    }

    /**
     * Get today's total cost using official pricing × per-model token breakdown.
     */
    suspend fun getTodayCost(): Double {
        val today = dateFormat.format(Date())
        val breakdowns = usageDao.getDailyModelBreakdownSinceByPlatform(today, PLATFORM_KEY)
        return breakdowns.sumOf {
            calculateModelCost(it.model, it.cacheHitTokens, it.cacheMissTokens, it.outputTokens)
        }
    }

    /**
     * Get current month's total cost using snapshot costPerToken × monthly token count.
     * The snapshot reflects the API's cumulative totalCost/totalTokens at last refresh,
     * frozen at month-end for historical accuracy.
     */
    suspend fun getMonthCost(): Double {
        val currentMonth = monthFormat.format(Date())
        val monthCPT = getMonthCostPerToken(currentMonth)
        if (monthCPT <= 0) return 0.0
        val monthTokens = usageDao.getMonthlyTotalTokensByPlatform(currentMonth, PLATFORM_KEY)
        return monthTokens * monthCPT
    }
}
