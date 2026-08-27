package com.deepseek.lzjc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.Platform
import com.deepseek.lzjc.ui.components.BalanceForecast
import com.deepseek.lzjc.ui.components.CacheHitRateCard
import com.deepseek.lzjc.ui.components.GlassPanel
import com.deepseek.lzjc.ui.components.MiMoCostTrendCard
import com.deepseek.lzjc.ui.components.ModelPieChart
import com.deepseek.lzjc.ui.components.RefreshAnimation
import com.deepseek.lzjc.ui.components.RequestCountCard
import com.deepseek.lzjc.ui.components.TrendLineChart
import com.deepseek.lzjc.ui.theme.appColors
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.Card
import com.deepseek.lzjc.data.db.DailyUsageSummary

private val MiMoOrange = Color(0xFFFF6A00)

@Composable
private fun MiMoBalanceOverviewCard(
    cashBalance: Double,
    giftBalance: Double,
    totalBalance: Double,
    todayCost: Double,
    monthCost: Double,
    avgDailyCost: Double
) {
    GlassPanel(radius = 22) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "MiMo 余额概览",
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            // Balance row: cash + gift
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("现金余额", color = MaterialTheme.appColors.textTertiary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "¥${String.format("%.2f", cashBalance)}",
                        color = MiMoOrange,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("赠送余额", color = MaterialTheme.appColors.textTertiary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "¥${String.format("%.2f", giftBalance)}",
                        color = Color(0xFF51F0AE),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Spending row: today + month
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("今日消耗", color = MaterialTheme.appColors.textTertiary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "¥${String.format("%.4f", todayCost)}",
                        color = Color(0xFFFF9232),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("本月消耗", color = MaterialTheme.appColors.textTertiary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "¥${String.format("%.2f", monthCost)}",
                        color = Color(0xFFFF9232),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "总计 ¥${String.format("%.2f", totalBalance)}",
                    color = MaterialTheme.appColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (avgDailyCost > 0.0001 && cashBalance > 0) {
                    val daysLeft = (cashBalance / avgDailyCost).toInt()
                    Text(
                        "预计可用 ${daysLeft} 天",
                        color = if (daysLeft < 7) Color(0xFFFF6B6B) else MaterialTheme.appColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshIfNotLoaded()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColors.background)
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RefreshAnimation(size = 52.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.loading_analytics_data),
                        color = MaterialTheme.appColors.textSecondary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "header") {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.title_analytics),
                                color = MaterialTheme.appColors.textPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.appColors.textPrimary)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Platform.entries.forEach { platform ->
                                FilterChip(
                                    selected = state.currentPlatform == platform,
                                    onClick = { viewModel.switchPlatform(platform) },
                                    label = {
                                        Text(
                                            platform.displayName,
                                            fontWeight = if (state.currentPlatform == platform) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (platform == Platform.MIMO) MiMoOrange.copy(alpha = 0.15f) else MaterialTheme.appColors.accent.copy(alpha = 0.15f),
                                        selectedLabelColor = if (platform == Platform.MIMO) MiMoOrange else MaterialTheme.appColors.accent
                                    )
                                )
                            }
                        }
                    }
                }

                when (state.currentPlatform) {
                    Platform.DEEPSEEK -> {
                        item(key = "forecast") {
                            BalanceForecast(
                                balance = state.balance,
                                avgDailyCost = state.avgDailyCost
                            )
                        }

                        item(key = "cache") {
                            CacheHitRateCard(
                                cacheHitRate = state.cacheHitRate,
                                cacheHitTokens = state.cacheHitTokens,
                                cacheMissTokens = state.cacheMissTokens,
                                estimatedSaved = state.cacheEstimatedSaved,
                                periodLabel = "本月 · 全部模型"
                            )
                        }

                        item(key = "requests") {
                            RequestCountCard(
                                dailyRequests = state.dailyRequests,
                                monthlyRequests = state.monthlyRequests
                            )
                        }

                        item(key = "trend") {
                            TrendLineChart(dailyData = state.trendData)
                        }

                        item(key = "pie") {
                            ModelPieChart(modelCosts = state.modelCosts)
                        }
                    }
                    Platform.MIMO -> {
                        // MiMo balance overview (cash + gift)
                        if (state.mimoBalance > 0) {
                            item(key = "mimo_balance") {
                                MiMoBalanceOverviewCard(
                                    cashBalance = state.mimoCashBalance,
                                    giftBalance = state.mimoGiftBalance,
                                    totalBalance = state.mimoBalance,
                                    todayCost = state.mimoTodayCost,
                                    monthCost = state.mimoMonthCost,
                                    avgDailyCost = state.mimoAvgDailyCost
                                )
                            }
                        }

                        // Cache hit rate with period label
                        item(key = "mimo_cache") {
                            CacheHitRateCard(
                                cacheHitRate = state.mimoCacheHitRate,
                                cacheHitTokens = state.mimoCacheHitTokens,
                                cacheMissTokens = state.mimoCacheMissTokens,
                                estimatedSaved = 0.0,
                                periodLabel = "本月 · 全部模型"
                            )
                        }

                        // Request counts
                        item(key = "mimo_requests") {
                            RequestCountCard(
                                dailyRequests = state.mimoDailyRequests,
                                monthlyRequests = state.mimoMonthlyRequests
                            )
                        }

                        // Unified cost trend (¥ + Token, from costPerToken × tokens)
                        if (state.mimoTrendData.isNotEmpty() && state.mimoTrendData.any { it.costAmount > 0 }) {
                            item(key = "mimo_cost_trend") {
                                MiMoCostTrendCard(dailyData = state.mimoTrendData)
                            }
                        }

                        // Model token usage pie chart (per-model breakdown)
                        if (state.mimoModelCosts.isNotEmpty()) {
                            item(key = "mimo_pie") {
                                ModelPieChart(
                                    modelCosts = state.mimoModelCosts,
                                    useTokens = true,
                                    title = "模型 Token 占比"
                                )
                            }
                        }
                    }
                    Platform.ARK -> {
                        // Ark AFP overview
                        item(key = "ark_afp") {
                            ArkAFPOverviewCard(
                                todayUsage = state.arkTodayUsage,
                                monthlyUsage = state.arkMonthlyUsage
                            )
                        }

                        // 30-day trend chart (using existing TrendLineChart)
                        if (state.arkTrendData.isNotEmpty()) {
                            item(key = "ark_trend") {
                                TrendLineChart(dailyData = state.arkTrendData)
                            }
                        }

                        // Model token usage pie chart
                        if (state.arkModelCosts.isNotEmpty()) {
                            item(key = "ark_pie") {
                                ModelPieChart(
                                    modelCosts = state.arkModelCosts,
                                    useTokens = true,
                                    title = "模型 Token 占比"
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                RefreshAnimation(size = 36.dp, isAnimating = true)
            }
        }
    }
}

// ===== Ark Analytics Components =====

@Composable
private fun ArkAFPOverviewCard(
    todayUsage: Long,
    monthlyUsage: Long
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "AFP 用量概览",
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "今日用量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary
                    )
                    Text(
                        text = formatArkTokens(todayUsage),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.appColors.textPrimary
                    )
                    Text(
                        text = "Tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textTertiary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "本月用量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary
                    )
                    Text(
                        text = formatArkTokens(monthlyUsage),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.appColors.textPrimary
                    )
                    Text(
                        text = "Tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textTertiary
                    )
                }
            }
        }
    }
}

private fun formatArkTokens(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000.0)
        else -> tokens.toString()
    }
}
