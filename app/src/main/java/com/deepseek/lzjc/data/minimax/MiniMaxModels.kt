package com.deepseek.lzjc.data.minimax

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

// ===== 通用反序列化适配器（处理 API 返回 String/Number 混用的情况） =====

class FlexibleLong : JsonDeserializer<Long?> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Long? {
        if (json == null || json.isJsonNull) return null
        return try {
            when {
                json.isJsonPrimitive -> {
                    val prim = json.asJsonPrimitive
                    if (prim.isNumber) prim.asLong
                    else if (prim.isString) prim.asString.toLongOrNull()
                    else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

class FlexibleInt : JsonDeserializer<Int?> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Int? {
        if (json == null || json.isJsonNull) return null
        return try {
            when {
                json.isJsonPrimitive -> {
                    val prim = json.asJsonPrimitive
                    if (prim.isNumber) prim.asInt
                    else if (prim.isString) prim.asString.toIntOrNull()
                    else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

class FlexibleDouble : JsonDeserializer<Double?> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Double? {
        if (json == null || json.isJsonNull) return null
        return try {
            when {
                json.isJsonPrimitive -> {
                    val prim = json.asJsonPrimitive
                    if (prim.isNumber) prim.asDouble
                    else if (prim.isString) prim.asString.toDoubleOrNull()
                    else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

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
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("start_time") val startTime: Long?,
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("end_time") val endTime: Long?,
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("remains_time") val remainsTime: Long?,
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("current_interval_total_count") val currentIntervalTotalCount: Long?,
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("current_interval_usage_count") val currentIntervalUsageCount: Long?,
    @SerializedName("model_name") val modelName: String?,
    // Weekly fields (optional, may not exist)
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("current_weekly_total_count") val currentWeeklyTotalCount: Long?,
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("current_weekly_usage_count") val currentWeeklyUsageCount: Long?,
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("weekly_start_time") val weeklyStartTime: Long?,
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("weekly_end_time") val weeklyEndTime: Long?,
    @JsonAdapter(FlexibleLong::class)
    @SerializedName("weekly_remains_time") val weeklyRemainsTime: Long?,
    @JsonAdapter(FlexibleInt::class)
    @SerializedName("current_interval_status") val currentIntervalStatus: Int?,
    @JsonAdapter(FlexibleInt::class)
    @SerializedName("current_interval_remaining_percent") val currentIntervalRemainingPercent: Int?,
    @JsonAdapter(FlexibleInt::class)
    @SerializedName("current_weekly_status") val currentWeeklyStatus: Int?,
    @JsonAdapter(FlexibleInt::class)
    @SerializedName("current_weekly_remaining_percent") val currentWeeklyRemainingPercent: Int?
)

// ===== 聚合展示数据 =====

data class MiniMaxPlanOverview(
    val planType: String = "",
    val status: String = "",
    val expireTime: String = "",
    val hourUsed: Long = 0,
    val hourLimit: Long = 0,
    val hourRemaining: Long = 0,
    val hourRemainingPercent: Int = 0,
    val hourStatus: Int = 0,
    val weekUsed: Long = 0,
    val weekLimit: Long = 0,
    val weekRemaining: Long = 0,
    val weekRemainingPercent: Int = 0,
    val weekStatus: Int = 0,
    val modelName: String = "",
    val hourRemainingTime: Long = 0,
    val weekRemainingTime: Long = 0
)

data class MiniMaxDailyTrend(
    val date: String,
    val usageCount: Long,
    val tokenCount: Long
)
