package com.deepseek.lzjc.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.deepseek.lzjc.data.minimax.MiniMaxApiClient
import com.deepseek.lzjc.data.minimax.MiniMaxDailyTrend
import com.deepseek.lzjc.data.minimax.MiniMaxPlanOverview
import com.deepseek.lzjc.data.minimax.MiniMaxDailyUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class MiniMaxRepository @Inject constructor(
    private val miniMaxApiClient: MiniMaxApiClient,
    @Named("minimax") private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_API_KEY = stringPreferencesKey("minimax_api_key")
    }

    val apiKey: Flow<String> = dataStore.data.map { it[KEY_API_KEY] ?: "" }

    suspend fun saveApiKey(key: String) {
        dataStore.edit { it[KEY_API_KEY] = key }
        miniMaxApiClient.setApiKey(key)
    }

    suspend fun initApiKey() {
        val key = dataStore.data.first()[KEY_API_KEY] ?: ""
        miniMaxApiClient.setApiKey(key)
    }

    suspend fun getPlanOverview(): Result<MiniMaxPlanOverview> {
        return try {
            val remainsResult = miniMaxApiClient.getTokenPlanRemains()

            remainsResult.fold(
                onSuccess = { response ->
                    val modelRemains = response.modelRemains
                    Log.d("MiniMaxFlow", "Model remains: $modelRemains")

                    if (modelRemains.isNullOrEmpty()) {
                        Log.w("MiniMaxFlow", "No model remains data")
                        return Result.failure(Exception("No data returned"))
                    }

                    // 获取 general 模型（主模型）的数据
                    val primaryModel = modelRemains.firstOrNull { it.modelName == "general" }

                    if (primaryModel == null) {
                        Log.w("MiniMaxFlow", "No general model found, using first model")
                    }

                    val model = primaryModel ?: modelRemains.first()

                    Log.d("MiniMaxFlow", "Model: ${model.modelName}")
                    Log.d("MiniMaxFlow", "raw: currentIntervalTotalCount=${model.currentIntervalTotalCount}, currentIntervalUsageCount=${model.currentIntervalUsageCount}, remainingPercent=${model.currentIntervalRemainingPercent}")
                    Log.d("MiniMaxFlow", "raw: currentWeeklyTotalCount=${model.currentWeeklyTotalCount}, currentWeeklyUsageCount=${model.currentWeeklyUsageCount}, weeklyRemainingPercent=${model.currentWeeklyRemainingPercent}")

                    val usedHour = model.currentIntervalUsageCount ?: 0L
                    val usedWeek = model.currentWeeklyUsageCount ?: 0L
                    val remainingHourPct = model.currentIntervalRemainingPercent ?: 100
                    val remainingWeekPct = model.currentWeeklyRemainingPercent ?: 100
                    val hourStatus = model.currentIntervalStatus ?: 1
                    val weekStatus = model.currentWeeklyStatus ?: 1

                    // MiniMax Plus 套餐官方额度参考
                    // 来自官方文档: Plus约12,000次/月, 5h约1500次, 周约3000次
                    val hourLimit = 1500L
                    val weekLimit = 3000L

                    // 根据 remainingPercent 计算实际已用
                    val hourUsedCalculated = if (remainingHourPct < 100) {
                        ((100 - remainingHourPct) * hourLimit / 100).toLong()
                    } else {
                        hourLimit // 已用完
                    }

                    val weekUsedCalculated = if (remainingWeekPct < 100) {
                        ((100 - remainingWeekPct) * weekLimit / 100).toLong()
                    } else {
                        weekLimit
                    }

                    Result.success(
                        MiniMaxPlanOverview(
                            planType = "plus",
                            status = "active",
                            expireTime = "",
                            hourUsed = hourUsedCalculated,
                            hourLimit = hourLimit,
                            hourRemaining = hourLimit - hourUsedCalculated,
                            hourRemainingPercent = remainingHourPct,
                            hourStatus = hourStatus,
                            weekUsed = weekUsedCalculated,
                            weekLimit = weekLimit,
                            weekRemaining = weekLimit - weekUsedCalculated,
                            weekRemainingPercent = remainingWeekPct,
                            weekStatus = weekStatus,
                            modelName = model.modelName ?: "",
                            hourRemainingTime = model.remainsTime ?: 0,
                            weekRemainingTime = model.weeklyRemainsTime ?: 0,
                            dailyTrend = emptyList(),
                            todayUsage = 0,
                            week7Usage = 0,
                            week30Usage = 0
                        )
                    )
                },
                onFailure = { e ->
                    Log.e("MiniMaxFlow", "API call failed", e)
                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            Log.e("MiniMaxFlow", "getPlanOverview error", e)
            Result.failure(e)
        }
    }
}
