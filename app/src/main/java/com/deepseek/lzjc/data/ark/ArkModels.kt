package com.deepseek.lzjc.data.ark

import com.deepseek.lzjc.data.minimax.FlexibleDouble
import com.deepseek.lzjc.data.minimax.FlexibleLong
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

// ===== 通用响应包装 =====

data class ArkResponse<T>(
    @SerializedName("ResponseMetadata") val responseMetadata: ResponseMetadata,
    @SerializedName("Result") val result: T?
)

data class ResponseMetadata(
    @SerializedName("RequestId") val requestId: String,
    @SerializedName("Action") val action: String,
    @SerializedName("Version") val version: String,
    @SerializedName("Service") val service: String,
    @SerializedName("Region") val region: String,
    @SerializedName("Error") val error: ArkError?
)

data class ArkError(
    @SerializedName("Code") val code: String,
    @SerializedName("Message") val message: String
)

// ===== 请求模型 =====

data class GetPersonalPlanRequest(
    @SerializedName("Plan") val plan: String = "AgentPlan"
)

data class GetAFPUsageRequest(
    @SerializedName("Plan") val plan: String = "AgentPlan"
)

data class GetUsageDetailsRequest(
    @SerializedName("QueryInterval") val queryInterval: String,
    @SerializedName("Filter") val filter: UsageFilter
)

data class UsageFilter(
    @SerializedName("StartTime") val startTime: String,
    @SerializedName("EndTime") val endTime: String,
    @SerializedName("ObjectName") val objectName: List<String>? = null,
    @SerializedName("PlanType") val planType: List<Int>? = null
)

// ===== 响应模型 =====

data class PersonalPlanResult(
    @SerializedName("PlanType") val planType: String = "",
    @SerializedName("Status") val status: String = "",
    @SerializedName("StartTime") val startTime: String = "",
    @SerializedName("EndTime") val endTime: String = "",
    @SerializedName("AutoRenew") val autoRenew: Boolean = false
)

data class AFPUsageResult(
    @SerializedName("PlanType") val planType: String,
    @SerializedName("AFPFiveHour") val afpFiveHour: AFPWindow,
    @SerializedName("AFPDaily") val afpDaily: AFPWindow,
    @SerializedName("AFPWeekly") val afpWeekly: AFPWindow,
    @SerializedName("AFPMonthly") val afpMonthly: AFPWindow
)

data class AFPWindow(
    @JsonAdapter(FlexibleDouble::class)
    @SerializedName("Quota") val quota: Double = 0.0,
    @JsonAdapter(FlexibleDouble::class)
    @SerializedName("Used") val used: Double = 0.0,
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("SubscribeTime") val subscribeTime: Long = 0L,
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("ResetTime") val resetTime: Long = 0L
)

data class UsageDetailsResult(
    @SerializedName("Details") val details: List<UsageDetailItem>
)

data class UsageDetailItem(
    @SerializedName("BillingType") val billingType: String = "",
    @SerializedName("ObjectName") val objectName: String = "",
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("Time") val time: Long = 0L,
    @SerializedName("Unit") val unit: String = "",
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("Usage") val usage: Long = 0L
)

// ===== 聚合展示数据 =====

data class ArkPlanOverview(
    val planType: String = "",
    val status: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val autoRenew: Boolean = false,
    val totalAFP: Double = 0.0,
    val usedAFP: Double = 0.0,
    val usagePercentage: Float = 0f,
    val afp5hQuota: Double = 0.0,
    val afp5hUsed: Double = 0.0,
    val afp5hPercent: Double = 0.0,
    val afp1wQuota: Double = 0.0,
    val afp1wUsed: Double = 0.0,
    val afp1wPercent: Double = 0.0,
    val afp1mQuota: Double = 0.0,
    val afp1mUsed: Double = 0.0,
    val afp1mPercent: Double = 0.0,
    val planSource: String = "AgentPlan"
) {
    val remainingAFP: Double get() = totalAFP - usedAFP
}
