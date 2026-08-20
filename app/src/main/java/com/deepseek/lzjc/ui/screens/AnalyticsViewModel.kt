package com.deepseek.lzjc.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.lzjc.data.Platform
import com.deepseek.lzjc.data.db.DailyUsageSummary
import com.deepseek.lzjc.data.db.ModelCostSummary
import com.deepseek.lzjc.data.repository.MiMoRepository
import com.deepseek.lzjc.data.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class AnalyticsState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val currentPlatform: Platform = Platform.DEEPSEEK,
    // DeepSeek fields
    val balance: Double = 0.0,
    val avgDailyCost: Double = 0.0,
    val daysRemaining: Int = 0,
    val trendData: List<DailyUsageSummary> = emptyList(),
    val modelCosts: List<ModelCostSummary> = emptyList(),
    val cacheHitRate: Double = 0.0,
    val cacheHitTokens: Long = 0,
    val cacheMissTokens: Long = 0,
    val cacheEstimatedSaved: Double = 0.0,
    val dailyRequests: Long = 0,
    val monthlyRequests: Long = 0,
    // MiMo fields
    val mimoBalance: Double = 0.0,
    val mimoCashBalance: Double = 0.0,
    val mimoGiftBalance: Double = 0.0,
    val mimoAvgDailyCost: Double = 0.0,
    val mimoDaysRemaining: Int = 0,
    val mimoCacheHitRate: Double = 0.0,
    val mimoCacheHitTokens: Long = 0,
    val mimoCacheMissTokens: Long = 0,
    val mimoTrendData: List<DailyUsageSummary> = emptyList(),
    val mimoCashTrendData: List<DailyUsageSummary> = emptyList(),
    val mimoSubscriptionTokenTrend: List<DailyUsageSummary> = emptyList(),
    val mimoModelCosts: List<ModelCostSummary> = emptyList(),
    val mimoDailyRequests: Long = 0,
    val mimoMonthlyRequests: Long = 0
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: UsageRepository,
    private val mimoRepository: MiMoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    private var hasLoaded = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    init {
        val prefs = appContext.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
        val savedPlatform = Platform.fromKey(prefs.getString("current_platform", "deepseek") ?: "deepseek")
        _state.update { it.copy(currentPlatform = savedPlatform) }
    }

    fun switchPlatform(platform: Platform) {
        _state.update { it.copy(currentPlatform = platform) }
        hasLoaded = false
        refresh()
    }

    fun refreshIfNotLoaded() {
        if (!hasLoaded) refresh()
    }

    fun refresh() {
        when (_state.value.currentPlatform) {
            Platform.DEEPSEEK -> refreshDeepSeek()
            Platform.MIMO -> refreshMiMo()
        }
    }

    private fun refreshDeepSeek() {
        viewModelScope.launch {
            if (hasLoaded) {
                _state.update { it.copy(isRefreshing = true) }
            } else {
                _state.update { it.copy(isLoading = true) }
            }

            try {
                val balanceStr = repository.apiKey.first()
                var balance = 0.0
                if (balanceStr.isNotBlank()) {
                    repository.fetchBalance().onSuccess { resp ->
                        balance = resp.balanceInfos.firstOrNull()?.totalBalance?.toDoubleOrNull() ?: 0.0
                    }
                }

                val avgDailyCost = repository.getAvgDailyCost(7)
                val daysRemaining = if (avgDailyCost > 0.0001) (balance / avgDailyCost).toInt() else 0
                val trendData = repository.getDailyCostList(30)
                val modelCosts = repository.getModelCosts(30)
                val cacheHitRate = repository.getMonthlyCacheHitRate()
                val (cacheHit, cacheMiss) = repository.getMonthlyCacheTokens()
                val dailyRequests = repository.getDailyRequestCount()
                val monthlyRequests = repository.getMonthlyRequestCount()
                val cacheEstimatedSaved = if (cacheHit > 0) {
                    val savedPerToken = (1.0 - 0.02) / 1_000_000.0
                    cacheHit * savedPerToken
                } else 0.0

                hasLoaded = true
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        balance = balance,
                        avgDailyCost = avgDailyCost,
                        daysRemaining = daysRemaining,
                        trendData = trendData,
                        modelCosts = modelCosts,
                        cacheHitRate = cacheHitRate,
                        cacheHitTokens = cacheHit,
                        cacheMissTokens = cacheMiss,
                        cacheEstimatedSaved = cacheEstimatedSaved,
                        dailyRequests = dailyRequests,
                        monthlyRequests = monthlyRequests
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    private fun refreshMiMo() {
        viewModelScope.launch {
            if (hasLoaded) {
                _state.update { it.copy(isRefreshing = true) }
            } else {
                _state.update { it.copy(isLoading = true) }
            }

            try {
                val month = monthFormat.format(Calendar.getInstance().time)
                val today = dateFormat.format(Calendar.getInstance().time)

                // Get persisted balance from MiMoRepository
                val isLoggedIn = mimoRepository.isLoggedIn()
                var mimoBalance = 0.0
                var mimoCashBalance = 0.0
                var mimoGiftBalance = 0.0
                if (isLoggedIn) {
                    val (cash, gift, total) = mimoRepository.getStoredBalance()
                    mimoCashBalance = cash
                    mimoGiftBalance = gift
                    mimoBalance = total
                }

                // Estimated daily cost trend (from per-model costAmount, using costPerToken)
                val estimatedCostTrend = mimoRepository.getDailyEstimatedCostTrend(30)
                // Cash spending trend (balance-delta, real money — supplementary, from 2nd refresh)
                val cashTrendData = mimoRepository.getDailyCashTrend(30)
                // Subscription token trend (token consumption per day)
                val subscriptionTokenTrend = mimoRepository.getDailySubscriptionTokenTrend(30)

                // Average daily estimated cost
                val avgDailyCost = mimoRepository.getAvgDailyCost(7)

                // Model token usage for pie chart (excluding "total" and "balance-delta")
                val modelCosts = mimoRepository.getModelTokenSummary(30)

                // Cache hit rate
                val cacheHitRate = mimoRepository.getMonthlyCacheHitRate(month)
                val (cacheHit, cacheMiss) = mimoRepository.getMonthlyCacheTokens(month)

                // Requests
                val dailyRequests = mimoRepository.getDailyRequestCount(today)
                val monthlyRequests = mimoRepository.getMonthlyRequestCount(month)

                hasLoaded = true
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        mimoBalance = mimoBalance,
                        mimoCashBalance = mimoCashBalance,
                        mimoGiftBalance = mimoGiftBalance,
                        mimoAvgDailyCost = avgDailyCost,
                        mimoDaysRemaining = if (avgDailyCost > 0.0001) (mimoCashBalance / avgDailyCost).toInt() else 0,
                        mimoTrendData = estimatedCostTrend,
                        mimoCashTrendData = cashTrendData,
                        mimoSubscriptionTokenTrend = subscriptionTokenTrend,
                        mimoModelCosts = modelCosts,
                        mimoCacheHitRate = cacheHitRate,
                        mimoCacheHitTokens = cacheHit,
                        mimoCacheMissTokens = cacheMiss,
                        mimoDailyRequests = dailyRequests,
                        mimoMonthlyRequests = monthlyRequests
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }
}
