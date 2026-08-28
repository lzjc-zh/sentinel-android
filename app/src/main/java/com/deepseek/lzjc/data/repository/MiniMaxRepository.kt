package com.deepseek.lzjc.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.deepseek.lzjc.data.db.DailyMaxUsed
import com.deepseek.lzjc.data.db.MiniMaxSnapshotDao
import com.deepseek.lzjc.data.db.MiniMaxSnapshotEntity
import com.deepseek.lzjc.data.db.ModelUsageRow
import com.deepseek.lzjc.data.minimax.MiniMaxApiClient
import com.deepseek.lzjc.data.minimax.MiniMaxModelRemain
import com.deepseek.lzjc.data.minimax.MiniMaxPlanOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class MiniMaxRepository @Inject constructor(
    private val miniMaxApiClient: MiniMaxApiClient,
    @Named("minimax") private val dataStore: DataStore<Preferences>,
    private val snapshotDao: MiniMaxSnapshotDao
) {
    companion object {
        val KEY_API_KEY = stringPreferencesKey("minimax_api_key")
        private const val TAG = "MiniMaxFlow"
        // MiniMax Plus 套餐官方额度
        private const val HOUR_LIMIT = 1500L
        private const val WEEK_LIMIT = 3000L
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
                    Log.d(TAG, "Model remains: $modelRemains")

                    if (modelRemains.isNullOrEmpty()) {
                        Log.w(TAG, "No model remains data")
                        return Result.failure(Exception("No data returned"))
                    }

                    // 获取 general 模型（主模型）
                    val primaryModel = modelRemains.firstOrNull { it.modelName == "general" }
                        ?: modelRemains.first()

                    Log.d(TAG, "Model: ${primaryModel.modelName}")

                    val remainingHourPct = primaryModel.currentIntervalRemainingPercent ?: 100
                    val remainingWeekPct = primaryModel.currentWeeklyRemainingPercent ?: 100

                    // 根据 remainingPercent 估算已用次数
                    val hourUsed = if (remainingHourPct < 100) {
                        ((100 - remainingHourPct) * HOUR_LIMIT / 100).toLong()
                    } else 0L

                    val weekUsed = if (remainingWeekPct < 100) {
                        ((100 - remainingWeekPct) * WEEK_LIMIT / 100).toLong()
                    } else 0L

                    // 保存所有模型的快照
                    val now = System.currentTimeMillis()
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                    modelRemains.forEach { model ->
                        saveSnapshot(model, "hour", today, now)
                        saveSnapshot(model, "week", today, now)
                    }

                    Result.success(
                        MiniMaxPlanOverview(
                            planType = "plus",
                            status = "active",
                            expireTime = "",
                            hourUsed = hourUsed,
                            hourLimit = HOUR_LIMIT,
                            hourRemaining = HOUR_LIMIT - hourUsed,
                            hourRemainingPercent = remainingHourPct,
                            hourStatus = primaryModel.currentIntervalStatus ?: 1,
                            weekUsed = weekUsed,
                            weekLimit = WEEK_LIMIT,
                            weekRemaining = WEEK_LIMIT - weekUsed,
                            weekRemainingPercent = remainingWeekPct,
                            weekStatus = primaryModel.currentWeeklyStatus ?: 1,
                            modelName = primaryModel.modelName ?: "",
                            hourRemainingTime = primaryModel.remainsTime ?: 0,
                            weekRemainingTime = primaryModel.weeklyRemainsTime ?: 0
                        )
                    )
                },
                onFailure = { e ->
                    Log.e(TAG, "API call failed", e)
                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "getPlanOverview error", e)
            Result.failure(e)
        }
    }

    private suspend fun saveSnapshot(
        model: MiniMaxModelRemain,
        window: String,
        date: String,
        timestamp: Long
    ) {
        try {
            val usedCount = when (window) {
                "hour" -> model.currentIntervalUsageCount ?: 0L
                "week" -> model.currentWeeklyUsageCount ?: 0L
                else -> 0L
            }
            val remainingPercent = when (window) {
                "hour" -> model.currentIntervalRemainingPercent ?: 100
                "week" -> model.currentWeeklyRemainingPercent ?: 100
                else -> 100
            }
            val status = when (window) {
                "hour" -> model.currentIntervalStatus ?: 1
                "week" -> model.currentWeeklyStatus ?: 1
                else -> 1
            }
            val limit = if (window == "hour") HOUR_LIMIT else WEEK_LIMIT
            val remainingCount = if (remainingPercent < 100) {
                (remainingPercent * limit / 100).toLong()
            } else limit

            snapshotDao.insert(
                MiniMaxSnapshotEntity(
                    timestamp = timestamp,
                    date = date,
                    modelName = model.modelName ?: "general",
                    window = window,
                    usedCount = usedCount,
                    remainingCount = remainingCount,
                    remainingPercent = remainingPercent,
                    status = status,
                    totalQuota = limit
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "saveSnapshot error", e)
        }
    }

    /** 获取每日最大已用次数（用于折线图） */
    suspend fun getDailyMaxUsed(fromDate: String, window: String = "hour"): List<DailyMaxUsed> {
        return try {
            snapshotDao.getDailyMaxUsed(fromDate, window)
        } catch (e: Exception) {
            Log.e(TAG, "getDailyMaxUsed error", e)
            emptyList()
        }
    }

    /** 获取模型用量汇总 */
    suspend fun getModelUsageSummary(fromDate: String, window: String = "hour"): List<ModelUsageRow> {
        return try {
            snapshotDao.getModelUsageSummary(fromDate, window)
        } catch (e: Exception) {
            Log.e(TAG, "getModelUsageSummary error", e)
            emptyList()
        }
    }
}
