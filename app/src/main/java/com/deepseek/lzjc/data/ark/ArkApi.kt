package com.deepseek.lzjc.data.ark

import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 火山方舟 Agent Plan API
 * Base URL: https://ark.cn-beijing.volcengineapi.com/
 */
interface ArkApi {

    /**
     * 查询个人版套餐信息
     */
    @POST("/")
    suspend fun getPersonalPlan(
        @HeaderMap headers: Map<String, String>,
        @Query("Action") action: String = "GetPersonalPlan",
        @Query("Version") version: String = "2024-01-01",
        @Body request: GetPersonalPlanRequest
    ): ArkResponse<PersonalPlanResult>

    /**
     * 获取套餐 AFP 额度用量
     */
    @POST("/")
    suspend fun getAFPUsage(
        @HeaderMap headers: Map<String, String>,
        @Query("Action") action: String = "GetAFPUsage",
        @Query("Version") version: String = "2024-01-01",
        @Body request: GetAFPUsageRequest
    ): ArkResponse<AFPUsageResult>

    /**
     * 获取套餐用量详情
     */
    @POST("/")
    suspend fun getUsageDetails(
        @HeaderMap headers: Map<String, String>,
        @Query("Action") action: String = "GetUsageDetails",
        @Query("Version") version: String = "2024-01-01",
        @Body request: GetUsageDetailsRequest
    ): ArkResponse<UsageDetailsResult>
}
