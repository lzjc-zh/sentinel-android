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

            // 拉取近 30 天的 Coding Plan 用量明细
            val cal = Calendar.getInstance()
            val endDate = dateFormat.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -30)
            val startDate = dateFormat.format(cal.time)

            val detailsResult = arkApiClient.getUsageDetails(
                startDate = startDate,
                endDate = endDate,
                interval = "Day",
                objectNames = CODING_PLAN_MODELS
            )

            val details = detailsResult.getOrNull()?.details ?: emptyList()

            // 按日期聚合
            val dailyMap = details.groupBy { detail ->
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(detail.time))
                Pair(date, detail.objectName)
            }

            // 计算 5h / 周 / 月 请求次数（不是 token 数）
            val now = System.currentTimeMillis()
            val fiveHoursAgo = now - 5L * 60 * 60 * 1000
            val oneWeekAgo = now - 7L * 24 * 60 * 60 * 1000

            val last5hCount = details.count { it.time >= fiveHoursAgo }
            val lastWeekCount = details.count { it.time >= oneWeekAgo }
            val lastMonthCount = details.size

            // Coding Plan 套餐的请求次数配额（根据 planType 区分 Lite/Pro）
            // Lite: 5h ~1200 次, 周 ~9000 次, 月 ~18000 次
            // Pro: 5h ~6000 次, 周 ~45000 次, 月 ~90000 次
            val (hourQuota, weekQuota, monthQuota) = when (plan.planType.uppercase()) {
                "PRO" -> Triple(6000.0, 45000.0, 90000.0)
                else  -> Triple(1200.0, 9000.0, 18000.0)  // Lite 默认
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
