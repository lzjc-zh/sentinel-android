package com.deepseek.lzjc.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.deepseek.lzjc.data.ark.ArkApiClient
import com.deepseek.lzjc.data.ark.ArkPlanOverview
import com.deepseek.lzjc.data.db.DailyModelBreakdown
import com.deepseek.lzjc.data.db.DailyUsageSummary
import com.deepseek.lzjc.data.db.ModelCostSummary
import com.deepseek.lzjc.data.db.UsageDao
import com.deepseek.lzjc.data.db.UsageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ArkRepository @Inject constructor(
    private val arkApiClient: ArkApiClient,
    private val usageDao: UsageDao,
    @Named("ark") private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_ACCESS_KEY_ID = stringPreferencesKey("ark_access_key_id")
        val KEY_SECRET_ACCESS_KEY = stringPreferencesKey("ark_secret_access_key")

        // Coding Plan 专属模型（用来在 GetUsageDetails 中过滤）
        private val CODING_PLAN_MODELS = listOf(
            "minimax-m3",
            "glm-5.3",
            "glm-5.2",
            "deepseek-v4-flash-ga-260731",
            "deepseek-v4-pro-260425",
            "doubao-embedding-vision-251215",
            "doubao-seed-evolving",
            "doubao-seed-2.0-lite",
            "doubao-seed-code",
            "doubao-seed-2.1-turbo",
            "kimi-k2.6",
            "kimi-k2.7-code"
        )

        // 解析 ISO 8601 时间字符串为毫秒（支持 "2026-08-28T13:37:23Z" 和 "2026-08-28T13:37:23+08:00"）
        private fun parseIso8601Ms(iso: String): Long {
            if (iso.isBlank()) return 0L
            return try {
                val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val s = iso.substringBefore("+").substringBefore("Z").substringBeforeLast(".")
                parser.parse(s)?.time ?: 0L
            } catch (e: Exception) {
                Log.e("ArkFlow", "parseIso8601Ms error: $iso", e)
                0L
            }
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    val accessKeyId: Flow<String> = dataStore.data.map { it[KEY_ACCESS_KEY_ID] ?: "" }
    val secretAccessKey: Flow<String> = dataStore.data.map { it[KEY_SECRET_ACCESS_KEY] ?: "" }

    suspend fun saveCredentials(accessKeyId: String, secretAccessKey: String) {
        dataStore.edit {
            it[KEY_ACCESS_KEY_ID] = accessKeyId
            it[KEY_SECRET_ACCESS_KEY] = secretAccessKey
        }
        arkApiClient.setCredentials(accessKeyId, secretAccessKey)
    }

    suspend fun initCredentials() {
        val keyId = dataStore.data.first()[KEY_ACCESS_KEY_ID] ?: ""
        val secretKey = dataStore.data.first()[KEY_SECRET_ACCESS_KEY] ?: ""
        if (keyId.isNotBlank() && secretKey.isNotBlank()) {
            arkApiClient.setCredentials(keyId, secretKey)
        }
    }

    suspend fun getPlanOverview(): Result<ArkPlanOverview> {
        return try {
            // 尝试同时拉取 Agent Plan 和 Coding Plan
            val agentPlanResult = arkApiClient.getPersonalPlan("AgentPlan")
            val agentAfpResult = arkApiClient.getAFPUsage("AgentPlan")
            val codingPlanResult = arkApiClient.getPersonalPlan("CodingPlan")
            val codingAfpResult = arkApiClient.getAFPUsage("CodingPlan")

            // 优先用 AgentPlan 为主套餐
            val plan = agentPlanResult.getOrNull() ?: codingPlanResult.getOrNull()
            val afp = agentAfpResult.getOrNull() ?: codingAfpResult.getOrNull()

            if (plan == null || afp == null) {
                val err = agentPlanResult.exceptionOrNull()
                    ?: agentAfpResult.exceptionOrNull()
                    ?: codingPlanResult.exceptionOrNull()
                    ?: codingAfpResult.exceptionOrNull()
                return Result.failure(err ?: Exception("No plan data"))
            }

            val planSource = if (agentPlanResult.isSuccess) "AgentPlan" else "CodingPlan"

            val totalAFP = afp.afpMonthly.quota
            val usedAFP = afp.afpMonthly.used
            val usagePercentage = if (totalAFP > 0) (usedAFP / totalAFP).toFloat() else 0f

            Result.success(
                ArkPlanOverview(
                    planType = plan.planType,
                    status = plan.status,
                    startTime = plan.startTime,
                    endTime = plan.endTime,
                    autoRenew = plan.autoRenew,
                    totalAFP = totalAFP,
                    usedAFP = usedAFP,
                    usagePercentage = usagePercentage,
                    afp5hQuota = afp.afpFiveHour.quota,
                    afp5hUsed = afp.afpFiveHour.used,
                    afp5hPercent = if (afp.afpFiveHour.quota > 0) afp.afpFiveHour.used / afp.afpFiveHour.quota * 100 else 0.0,
                    afp1wQuota = afp.afpWeekly.quota,
                    afp1wUsed = afp.afpWeekly.used,
                    afp1wPercent = if (afp.afpWeekly.quota > 0) afp.afpWeekly.used / afp.afpWeekly.quota * 100 else 0.0,
                    afp1mQuota = afp.afpMonthly.quota,
                    afp1mUsed = afp.afpMonthly.used,
                    afp1mPercent = if (afp.afpMonthly.quota > 0) afp.afpMonthly.used / afp.afpMonthly.quota * 100 else 0.0,
                    planSource = planSource
                )
            )
        } catch (e: Exception) {
            Log.e("ArkFlow", "getPlanOverview error", e)
            Result.failure(e)
        }
    }

    /**
     * 获取 Coding Plan 的概览
     * Coding Plan 走另一套逻辑：
     * 1. GetPersonalPlan("CodingPlan") 获取套餐基础信息
     * 2. GetUsageDetails 过滤 Coding Plan 专属模型名，计算用量
     */
    suspend fun getCodingPlanOverview(): Result<ArkPlanOverview> {
        return try {
            val planResult = arkApiClient.getPersonalPlan("CodingPlan")
            val plan = planResult.getOrNull()
                ?: return Result.failure(planResult.exceptionOrNull() ?: Exception("No Coding Plan data"))

            // 拉取从 Coding Plan 订阅开始到现在的全部明细（用于计算 5h/周/月窗口）
            val startMs = parseIso8601Ms(plan.startTime)
            val now = System.currentTimeMillis()

            val cal = Calendar.getInstance()
            cal.timeInMillis = startMs
            val startDate = dateFormat.format(cal.time)
            val endDate = dateFormat.format(Date(now))

            val detailsResult = arkApiClient.getUsageDetails(
                startDate = startDate,
                endDate = endDate,
                interval = "Day",
                objectNames = CODING_PLAN_MODELS
            )

            val details = detailsResult.getOrNull()?.details ?: emptyList()

            // 5h 滑动窗口：Coding Plan 的 5h 周期从首次调用时间开始
            // 用 SubscribeTime（订阅首次调用时间）作为基准，每 5h 一个周期
            val fiveHourMs = 5L * 60 * 60 * 1000
            // 找到 details 里最早的 minimax-m3/glm/doubao-seed 等 Coding Plan 专属模型调用时间
            // auto/deepseek-embedding 不算（可能是 Agent Plan 复用）
            val codingModels = setOf(
                "minimax-m3", "minimax-m2.7", "glm-5.3", "glm-5.2",
                "doubao-seed-2.0-code", "doubao-seed-code", "doubao-seed-2.0-lite",
                "doubao-seed-2.1-turbo", "kimi-k2.6", "kimi-k2.7-code",
                "deepseek-v4-flash-ga-260731", "deepseek-v4-pro-260425"
            )
            // 取 Coding Plan 开通时间之后最早的调用
            val firstCodingCallTime = details
                .filter { it.objectName in codingModels && it.time >= startMs }
                .minOfOrNull { it.time } ?: startMs
            val currentPeriodStart = if (firstCodingCallTime > 0 && firstCodingCallTime <= now) {
                val elapsed = now - firstCodingCallTime
                val periods = elapsed / fiveHourMs
                firstCodingCallTime + periods * fiveHourMs
            } else now
            val currentPeriodEnd = currentPeriodStart + fiveHourMs
            val last5hCount = details.count { it.time in currentPeriodStart until currentPeriodEnd }

            // 周限额：从本周一 00:00 算起（ISO 8601 周一开始）
            val weekStart = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val lastWeekCount = details.count { it.time >= weekStart }

            // 月限额：从本月 1 日 00:00 算起
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val lastMonthCount = details.count { it.time >= monthStart }

            // Coding Plan 套餐的请求次数配额（根据 planType 区分 Lite/Pro）
            val (hourQuota, weekQuota, monthQuota) = when (plan.planType.uppercase()) {
                "PRO" -> Triple(6000.0, 45000.0, 90000.0)
                else  -> Triple(1200.0, 9000.0, 18000.0)
            }

            val hourUsed = last5hCount.toDouble()
            val weekUsed = lastWeekCount.toDouble()
            val monthUsed = lastMonthCount.toDouble()

            // 使用率（百分比）
            val hourPct = if (hourQuota > 0) (hourUsed / hourQuota * 100).coerceIn(0.0, 100.0) else 0.0
            val weekPct = if (weekQuota > 0) (weekUsed / weekQuota * 100).coerceIn(0.0, 100.0) else 0.0
            val monthPct = if (monthQuota > 0) (monthUsed / monthQuota * 100).coerceIn(0.0, 100.0) else 0.0

            Result.success(
                ArkPlanOverview(
                    planType = plan.planType,
                    status = plan.status,
                    startTime = plan.startTime,
                    endTime = plan.endTime,
                    autoRenew = plan.autoRenew,
                    totalAFP = monthQuota,
                    usedAFP = monthUsed,
                    usagePercentage = (monthPct / 100).toFloat(),
                    afp5hQuota = hourQuota,
                    afp5hUsed = hourUsed,
                    afp5hPercent = hourPct,
                    afp1wQuota = weekQuota,
                    afp1wUsed = weekUsed,
                    afp1wPercent = weekPct,
                    afp1mQuota = monthQuota,
                    afp1mUsed = monthUsed,
                    afp1mPercent = monthPct,
                    planSource = "CodingPlan"
                )
            )
        } catch (e: Exception) {
            Log.e("ArkFlow", "getCodingPlanOverview error", e)
            Result.failure(e)
        }
    }

    suspend fun fetchRecentUsage(days: Int = 30): Result<Unit> {
        return try {
            val cal = Calendar.getInstance()
            val endDate = dateFormat.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -days)
            val startDate = dateFormat.format(cal.time)

            val result = arkApiClient.getUsageDetails(startDate, endDate, "Day")
            
            // 即使 API 失败也继续，只是不更新数据库
            if (result.isSuccess) {
                val details = result.getOrNull()?.details ?: emptyList()
                val grouped = details.groupBy { detail ->
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(detail.time))
                    Pair(date, detail.objectName)
                }

                grouped.forEach { (key, items) ->
                    val (date, model) = key
                    val totalTokens = items.sumOf { it.usage }
                    val month = date.substring(0, 7)

                    usageDao.deleteByDateAndModelAndPlatform(date, model, "ark")
                    usageDao.insert(
                        UsageEntity(
                            timestamp = System.currentTimeMillis(),
                            date = date,
                            month = month,
                            model = model,
                            inputTokens = 0,
                            outputTokens = totalTokens,
                            totalTokens = totalTokens,
                            costAmount = 0.0,
                            cacheHitTokens = 0,
                            cacheMissTokens = 0,
                            requestCount = items.size.toLong(),
                            platform = "ark"
                        )
                    )
                }
            } else {
                Log.w("ArkFlow", "fetchRecentUsage failed, but continuing: ${result.exceptionOrNull()?.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ArkFlow", "fetchRecentUsage error", e)
            // 即使出错也返回成功，只是没有历史数据
            Result.success(Unit)
        }
    }

    suspend fun getDailyUsageList(days: Int = 30): List<DailyUsageSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getDailyCostListSinceByPlatform(fromDate, "ark")
    }

    suspend fun getModelUsageSummary(days: Int = 30): List<ModelCostSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getModelCostSinceByPlatform(fromDate, "ark")
    }

    suspend fun getTodayUsage(): Long {
        val today = dateFormat.format(Date())
        return usageDao.getDailyTotalTokensByPlatform(today, "ark")
    }

    suspend fun getMonthlyUsage(): Long {
        val month = monthFormat.format(Date())
        return usageDao.getMonthlyTotalTokensByPlatform(month, "ark")
    }

    suspend fun getDailyModelBreakdowns(days: Int = 7): List<DailyModelBreakdown> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getDailyModelBreakdownSinceByPlatform(fromDate, "ark")
    }
}
