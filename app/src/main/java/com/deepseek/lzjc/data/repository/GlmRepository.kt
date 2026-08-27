package com.deepseek.lzjc.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.deepseek.lzjc.data.glm.GlmApiClient
import com.deepseek.lzjc.data.glm.GlmPlanOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class GlmRepository @Inject constructor(
    private val glmApiClient: GlmApiClient,
    @Named("glm") private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_API_KEY = stringPreferencesKey("glm_api_key")
    }

    val apiKey: Flow<String> = dataStore.data.map { it[KEY_API_KEY] ?: "" }

    suspend fun saveApiKey(key: String) {
        dataStore.edit { it[KEY_API_KEY] = key }
        glmApiClient.setApiKey(key)
    }

    suspend fun initApiKey() {
        val key = dataStore.data.first()[KEY_API_KEY] ?: ""
        if (key.isNotBlank()) {
            glmApiClient.setApiKey(key)
        }
    }

    suspend fun getPlanOverview(): Result<GlmPlanOverview> {
        return try {
            val result = glmApiClient.getUsageQuota()
            result.fold(
                onSuccess = { response ->
                    val data = response.data ?: return Result.failure(Exception("No data"))
                    val limits = data.limits

                    var hour5Percentage = 0.0
                    var weeklyPercentage = 0.0
                    var mcpUsage = 0L
                    var mcpRemaining = 0L
                    var mcpTotal = 0L

                    limits.forEach { limit ->
                        when (limit.type) {
                            "TOKENS_LIMIT" -> {
                                // 第一个 TOKENS_LIMIT 是 5 小时，第二个是每周
                                if (hour5Percentage == 0.0) {
                                    hour5Percentage = limit.percentage
                                } else {
                                    weeklyPercentage = limit.percentage
                                }
                            }
                            "TIME_LIMIT" -> {
                                mcpUsage = limit.currentValue ?: 0L
                                mcpRemaining = limit.remaining ?: 0L
                                mcpTotal = limit.usage ?: 0L
                            }
                        }
                    }

                    Result.success(
                        GlmPlanOverview(
                            level = data.level,
                            hour5Percentage = hour5Percentage,
                            weeklyPercentage = weeklyPercentage,
                            mcpUsage = mcpUsage,
                            mcpRemaining = mcpRemaining,
                            mcpTotal = mcpTotal
                        )
                    )
                },
                onFailure = { e -> Result.failure(e) }
            )
        } catch (e: Exception) {
            Log.e("GlmFlow", "getPlanOverview error", e)
            Result.failure(e)
        }
    }
}
