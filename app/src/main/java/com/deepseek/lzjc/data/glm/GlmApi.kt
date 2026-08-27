package com.deepseek.lzjc.data.glm

import retrofit2.http.GET
import retrofit2.http.Header

/**
 * 智谱 API
 * Base URL: https://open.bigmodel.cn/
 */
interface GlmApi {

    /**
     * 查询套餐用量额度
     */
    @GET("api/monitor/usage/quota/limit")
    suspend fun getUsageQuota(
        @Header("Authorization") auth: String
    ): GlmUsageResponse
}
