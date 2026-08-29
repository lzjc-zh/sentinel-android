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
                    afp1wQuota = afp.afpWeekly.quota,
                    afp1wUsed = afp.afpWeekly.used,
                    afp1mQuota = afp.afpMonthly.quota,
                    afp1mUsed = afp.afpMonthly.used,
                    planSource = planSource
                )
            )
        } catch (e: Exception) {
            Log.e("ArkFlow", "getPlanOverview error", e)
            Result.failure(e)
        }
    }

    /** 获取 Coding Plan 的概览（仅 Coding Plan） */
    suspend fun getCodingPlanOverview(): Result<ArkPlanOverview> {
        return try {
            val planResult = arkApiClient.getPersonalPlan("CodingPlan")
            val afpResult = arkApiClient.getAFPUsage("CodingPlan")

            val plan = planResult.getOrNull()
            val afp = afpResult.getOrNull()

            if (plan == null || afp == null) {
                return Result.failure(planResult.exceptionOrNull() ?: afpResult.exceptionOrNull() ?: Exception("No coding plan data"))
            }

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
                    afp1wQuota = afp.afpWeekly.quota,
                    afp1wUsed = afp.afpWeekly.used,
                    afp1mQuota = afp.afpMonthly.quota,
                    afp1mUsed = afp.afpMonthly.used,
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
