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
            val planResult = arkApiClient.getPersonalPlan()
            val afpResult = arkApiClient.getAFPUsage()

            if (planResult.isFailure) {
                return Result.failure(planResult.exceptionOrNull()!!)
            }
            if (afpResult.isFailure) {
                return Result.failure(afpResult.exceptionOrNull()!!)
            }

            val plan = planResult.getOrNull()!!
            val afp = afpResult.getOrNull()!!

            val totalAFP = afp.afpMonthly.quota
            val usedAFP = afp.afpMonthly.used
            val usagePercentage = if (totalAFP > 0) (usedAFP / totalAFP).toFloat() else 0f

            Result.success(
                ArkPlanOverview(
                    planType = plan.planType,
                    status = plan.status,
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
                    afp1mUsed = afp.afpMonthly.used
                )
            )
        } catch (e: Exception) {
            Log.e("ArkFlow", "getPlanOverview error", e)
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
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull()!!)
            }

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
                        outputTokens = totalTokens, // 存到 outputTokens 用于显示
                        totalTokens = totalTokens,
                        costAmount = 0.0,
                        cacheHitTokens = 0,
                        cacheMissTokens = 0,
                        requestCount = items.size.toLong(), // 请求数量
                        platform = "ark"
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ArkFlow", "fetchRecentUsage error", e)
            Result.failure(e)
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
