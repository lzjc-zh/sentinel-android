package com.deepseek.lzjc.data.ark

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 方舟 API 客户端封装
 */
@Singleton
class ArkApiClient @Inject constructor(
    private val arkApi: ArkApi,
    private val gson: Gson
) {
    private var accessKeyId: String = ""
    private var secretAccessKey: String = ""

    fun setCredentials(accessKeyId: String, secretAccessKey: String) {
        this.accessKeyId = accessKeyId
        this.secretAccessKey = secretAccessKey
    }

    private fun generateHeaders(action: String, body: String): Map<String, String> {
        return VolcAuth.generateAuthHeaders(accessKeyId, secretAccessKey, action, body)
    }

    suspend fun getPersonalPlan(plan: String = "AgentPlan"): Result<PersonalPlanResult> {
        return try {
            val request = GetPersonalPlanRequest(plan = plan)
            val body = gson.toJson(request)
            val headers = generateHeaders("GetPersonalPlan", body)
            val response = arkApi.getPersonalPlan(headers = headers, request = request)

            if (response.responseMetadata.error != null) {
                Result.failure(Exception(response.responseMetadata.error.message))
            } else {
                Result.success(response.result!!)
            }
        } catch (e: Exception) {
            Log.e("ArkFlow", "getPersonalPlan error", e)
            Result.failure(e)
        }
    }

    suspend fun getAFPUsage(plan: String = "AgentPlan"): Result<AFPUsageResult> {
        return try {
            val request = GetAFPUsageRequest(plan = plan)
            val body = gson.toJson(request)
            val headers = generateHeaders("GetAFPUsage", body)
            val response = arkApi.getAFPUsage(headers = headers, request = request)

            if (response.responseMetadata.error != null) {
                Result.failure(Exception(response.responseMetadata.error.message))
            } else {
                Result.success(response.result!!)
            }
        } catch (e: Exception) {
            Log.e("ArkFlow", "getAFPUsage error", e)
            Result.failure(e)
        }
    }

    suspend fun getUsageDetails(
        startDate: String,
        endDate: String,
        interval: String = "Day",
        objectNames: List<String>? = null
    ): Result<UsageDetailsResult> {
        return try {
            val request = GetUsageDetailsRequest(
                queryInterval = interval,
                filter = UsageFilter(
                    startTime = startDate,
                    endTime = endDate,
                    objectName = objectNames
                )
            )
            val body = gson.toJson(request)
            val headers = generateHeaders("GetUsageDetails", body)
            val response = arkApi.getUsageDetails(headers = headers, request = request)

            if (response.responseMetadata.error != null) {
                Result.failure(Exception(response.responseMetadata.error.message))
            } else {
                Result.success(response.result!!)
            }
        } catch (e: Exception) {
            Log.e("ArkFlow", "getUsageDetails error", e)
            Result.failure(e)
        }
    }
}
