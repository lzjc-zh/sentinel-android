package com.deepseek.lzjc.data.glm

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 智谱 API 客户端封装
 */
@Singleton
class GlmApiClient @Inject constructor(
    private val glmApi: GlmApi
) {
    private var apiKey: String = ""

    fun setApiKey(key: String) {
        this.apiKey = key
    }

    suspend fun getUsageQuota(): Result<GlmUsageResponse> {
        return try {
            val response = glmApi.getUsageQuota("Bearer $apiKey")
            if (response.success) {
                Result.success(response)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Log.e("GlmFlow", "getUsageQuota error", e)
            Result.failure(e)
        }
    }
}
