package com.deepseek.lzjc.data.minimax

import com.google.gson.annotations.SerializedName

// ===== Token Plan 剩余用量 =====

data class MiniMaxTokenPlanRemainsResponse(
    @SerializedName("request_id") val requestId: String?,
    @SerializedName("code") val code: Int?,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: MiniMaxTokenPlanData?
)

data class MiniMaxTokenPlanData(
    @SerializedName("subscription") val subscription: MiniMaxSubscription?,
    @SerializedName("usage") val usage: MiniMaxUsage?
)

data class MiniMaxSubscription(
    @SerializedName("status") val status: String?, // active, expired
    @SerializedName("plan_type") val planType: String?, // plus, max, ultra
    @SerializedName("expire_time") val expireTime: String? // ISO 8601
)

data class MiniMaxUsage(
    @SerializedName("current_hour") val currentHour: MiniMaxWindowUsage?,
    @SerializedName("current_week") val currentWeek: MiniMaxWindowUsage?
)

data class MiniMaxWindowUsage(
    @SerializedName("used") val used: Long,
    @SerializedName("limit") val limit: Long,
    @SerializedName("remaining") val remaining: Long
)

// ===== 聚合展示数据 =====

data class MiniMaxPlanOverview(
    val planType: String = "", // plus, max, ultra
    val status: String = "", // active, expired
    val expireTime: String = "", // ISO 8601
    val hourUsed: Long = 0,
    val hourLimit: Long = 0,
    val hourRemaining: Long = 0,
    val weekUsed: Long = 0,
    val weekLimit: Long = 0,
    val weekRemaining: Long = 0
)
