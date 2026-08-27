package com.deepseek.lzjc.data.glm

import com.google.gson.annotations.SerializedName

// ===== 响应模型 =====

/**
 * 智谱用量查询响应
 */
data class GlmUsageResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String,
    @SerializedName("data") val data: GlmUsageData?,
    @SerializedName("success") val success: Boolean
)

data class GlmUsageData(
    @SerializedName("limits") val limits: List<GlmLimitItem>,
    @SerializedName("level") val level: String // lite, pro, max
)

data class GlmLimitItem(
    @SerializedName("type") val type: String, // TIME_LIMIT, TOKENS_LIMIT
    @SerializedName("percentage") val percentage: Double, // 使用百分比
    @SerializedName("usage") val usage: Long?, // 总配额
    @SerializedName("currentValue") val currentValue: Long?, // 已使用
    @SerializedName("remaining") val remaining: Long?, // 剩余
    @SerializedName("nextResetTime") val nextResetTime: Long? // 下次重置时间
)

// ===== 聚合展示数据 =====

/**
 * 智谱套餐概览
 */
data class GlmPlanOverview(
    val level: String, // lite, pro, max
    val hour5Percentage: Double = 0.0, // 5小时额度使用百分比
    val weeklyPercentage: Double = 0.0, // 每周额度使用百分比
    val mcpUsage: Long = 0, // MCP 已用次数
    val mcpRemaining: Long = 0, // MCP 剩余次数
    val mcpTotal: Long = 0 // MCP 总配额
)
