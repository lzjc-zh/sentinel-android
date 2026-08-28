package com.deepseek.lzjc.data.minimax

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MiniMax API 客户端封装
 */
@Singleton
class MiniMaxApiClient @Inject constructor(
    private val miniMaxApi: MiniMaxApi
) {
    private var apiKey: String = ""

    fun setApiKey(key: String) {
        this.apiKey = key
    }

    suspend fun getTokenPlanRemains(): Result<MiniMaxTokenPlanRemainsResponse> {
        return try {
            val response = miniMaxApi.getTokenPlanRemains("Bearer $apiKey")
            Log.d("MiniMaxFlow", "API Response: baseResp=${response.baseResp}, modelRemains=${response.modelRemains}")
            if (response.baseResp?.statusCode == 0) {
                Result.success(response)
            } else {
                Result.failure(Exception(response.baseResp?.statusMsg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Log.e("MiniMaxFlow", "getTokenPlanRemains error", e)
            Result.failure(e)
        }
    }

}
