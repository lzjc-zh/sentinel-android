package com.deepseek.lzjc.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.deepseek.lzjc.data.minimax.MiniMaxApiClient
import com.deepseek.lzjc.data.minimax.MiniMaxPlanOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        if (key.isNotBlank()) {
            miniMaxApiClient.setApiKey(key)
        }
    }

    suspend fun getPlanOverview(): Result<MiniMaxPlanOverview> {
        return try {
            val result = miniMaxApiClient.getTokenPlanRemains()
            result.fold(
                onSuccess = { response ->
                    val data = response.data
                    if (data == null) {
                        return Result.failure(Exception("No data returned"))
                    }

                    val subscription = data.subscription
                    val usage = data.usage

                    Result.success(
                        MiniMaxPlanOverview(
                            planType = subscription?.planType ?: "",
                            status = subscription?.status ?: "",
                            expireTime = subscription?.expireTime ?: "",
                            hourUsed = usage?.currentHour?.used ?: 0,
                            hourLimit = usage?.currentHour?.limit ?: 0,
                            hourRemaining = usage?.currentHour?.remaining ?: 0,
                            weekUsed = usage?.currentWeek?.used ?: 0,
                            weekLimit = usage?.currentWeek?.limit ?: 0,
                            weekRemaining = usage?.currentWeek?.remaining ?: 0
                        )
                    )
                },
                onFailure = { e -> Result.failure(e) }
            )
        } catch (e: Exception) {
            Log.e("MiniMaxFlow", "getPlanOverview error", e)
            Result.failure(e)
        }
    }
}
