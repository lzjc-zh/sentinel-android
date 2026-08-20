package com.deepseek.lzjc.data.mimo

/**
 * Data models for MiMo Platform usage data.
 * Renamed from MiMo-Tracker to avoid conflicts with SeekFlow's PlatformModels.
 */
data class MiMoUsageData(
    // Token usage (from /api/v1/usage)
    val totalCost: String = "¥0.00",
    val tokenHistory: Long = 0,
    val inputCached: Long = 0,
    val inputUncached: Long = 0,
    val output: Long = 0,
    val requestCount: Long = 0,

    // Subscription plan (from /api/v1/tokenPlan/detail + usage)
    val planName: String = "",
    val creditsUsed: Long = 0,
    val creditsTotal: Long = 0,
    val usagePercentage: Double = 0.0,
    val expireDate: String = "",

    // Account balance (from /api/v1/balance)
    val totalBalance: String = "0.00",
    val giftBalance: String = "0.00",
    val cashBalance: String = "0.00",

    // Charts
    val monthlyUsage: List<MiMoMonthlyUsage> = emptyList(),
    val modelUsage: List<MiMoModelUsage> = emptyList()
)

data class MiMoMonthlyUsage(
    val month: String,         // "2026-08"
    val inputToken: Long,
    val outputToken: Long,
    val totalToken: Long,
    val cacheToken: Long,
    val requestCount: Long
)

data class MiMoModelUsage(
    val modelName: String,
    val inputToken: Long,
    val outputToken: Long,
    val totalToken: Long,
    val cacheToken: Long,
    val requestCount: Long,
    val percentage: Double = 0.0
)

// Daily API data (from /api/v1/usage/detail?year=YYYY&month=MM)

data class MiMoDailyDataPoint(
    val dayKey: String,        // "08-17"
    val inputToken: Long,
    val outputToken: Long,
    val totalToken: Long,
    val cacheToken: Long,
    val requestCount: Long,
    val models: List<MiMoDayModelData> = emptyList()
) {
    val uncachedInput: Long get() = (inputToken - cacheToken).coerceAtLeast(0)
    val cacheHitRate: Double get() = if (inputToken > 0) cacheToken.toDouble() / inputToken else 0.0
}

data class MiMoDayModelData(
    val name: String,
    val data: MiMoModelDayData
)

data class MiMoModelDayData(
    val inputToken: Long = 0,
    val outputToken: Long = 0,
    val totalToken: Long = 0,
    val cacheToken: Long = 0,
    val requestCount: Long = 0
) {
    val uncachedInput: Long get() = (inputToken - cacheToken).coerceAtLeast(0)
}
