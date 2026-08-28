package com.deepseek.lzjc.data.minimax

import retrofit2.http.GET
import retrofit2.http.Header

/**
 * MiniMax API
 * Base URL: https://www.minimaxi.com/
 */
interface MiniMaxApi {

    /**
     * 查询 Token Plan 剩余用量
     */
    @GET("v1/api/openplatform/coding_plan/remains")
    suspend fun getTokenPlanRemains(
        @Header("Authorization") auth: String
    ): MiniMaxTokenPlanRemainsResponse
}
