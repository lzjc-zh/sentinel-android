package com.deepseek.lzjc.data.minimax

import com.google.gson.annotations.SerializedName

// ===== Token Plan 剩余用量 =====

data class MiniMaxTokenPlanRemainsResponse(
    @SerializedName("model_remains") val modelRemains: List<MiniMaxModelRemain>?,
    @SerializedName("base_resp") val baseResp: MiniMaxBaseResp?
)

data class MiniMaxBaseResp(
    @SerializedName("status_code") val statusCode: Int?,
    @SerializedName("status_msg") val statusMsg: String?
)

data class MiniMaxModelRemain(
    @SerializedName("start_time") val startTime: Long?,
    @SerializedName("end_time") val endTime: Long?,
    @SerializedName("remains_time") val remainsTime: Long?,
    @SerializedName("current_interval_total_count") val currentIntervalTotalCount: Long?, // 剩余次数（不是总次数！）
    @SerializedName("current_interval_usage_count") val currentIntervalUsageCount: Long?, // 已用次数
    @SerializedName("model_name") val modelName: String?,
    @SerializedName("current_weekly_total_count") val currentWeeklyTotalCount: Long?, // 剩余次数
    @SerializedName("current_weekly_usage_count") val currentWeeklyUsageCount: Long?, // 已用次数
    @SerializedName("weekly_start_time") val weeklyStartTime: Long?,
    @SerializedName("weekly_end_time") val weeklyEndTime: Long?,
    @SerializedName("weekly_remains_time") val weeklyRemainsTime: Long?,
    @SerializedName("current_interval_status") val currentIntervalStatus: Int?,
    @SerializedName("current_interval_remaining_percent") val currentIntervalRemainingPercent: Int?,
    @SerializedName("current_weekly_status") val currentWeeklyStatus: Int?,
    @SerializedName("current_weekly_remaining_percent") val currentWeeklyRemainingPercent: Int?
)

// ===== Token Plan 用量详情 =====

data class MiniMaxTokenPlanUsageResponse(
    @SerializedName("model_usage") val modelUsage: List<MiniMaxModelUsage>?,
    @SerializedName("base_resp") val baseResp: MiniMaxBaseResp?
)

data class MiniMaxModelUsage(
    @SerializedName("model_name") val modelName: String?,
    @SerializedName("daily_usage") val dailyUsage: List<MiniMaxDailyUsage>?
)

data class MiniMaxDailyUsage(
    @SerializedName("date") val date: String?, // YYYY-MM-DD
    @SerializedName("usage_count") val usageCount: Long?, // 调用次数
    @SerializedName("token_count") val tokenCount: Long? // Token 数量
)

// ===== 聚合展示数据 =====

data class MiniMaxPlanOverview(
    val planType: String = "", // plus, max, ultra
    val status: String = "", // active, expired
    val expireTime: String = "", // ISO 8601
    val hourUsed: Long = 0,
    val hourLimit: Long = 0,
    val hourRemaining: Long = 0,
    val hourRemainingPercent: Int = 0,
    val hourStatus: Int = 0, // 1=正常, 2=已用完
    val weekUsed: Long = 0,
    val weekLimit: Long = 0,
    val weekRemaining: Long = 0,
    val weekRemainingPercent: Int = 0,
    val weekStatus: Int = 0, // 1=正常, 2=已用完
    val modelName: String = "", // 主要模型名称
    val hourRemainingTime: Long = 0, // 剩余时间（毫秒）
    val weekRemainingTime: Long = 0, // 剩余时间（毫秒）
    // 用量趋势数据
    val dailyTrend: List<MiniMaxDailyTrend> = emptyList(),
    val todayUsage: Long = 0,
    val week7Usage: Long = 0,
    val week30Usage: Long = 0
)

data class MiniMaxDailyTrend(
    val date: String, // MM-dd
    val usageCount: Long,
    val tokenCount: Long
)
