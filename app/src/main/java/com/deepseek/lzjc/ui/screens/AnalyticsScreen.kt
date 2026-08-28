package com.deepseek.lzjc.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.Platform
import com.deepseek.lzjc.data.glm.GlmPlanOverview
import com.deepseek.lzjc.data.minimax.MiniMaxPlanOverview
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import com.deepseek.lzjc.data.db.DailyUsageSummary

private val MiMoOrange = Color(0xFFFF6A00)
private val GlmBlue = Color(0xFF134CFF)

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
                                        selectedContainerColor = when (platform) {
                                            Platform.MIMO -> MiMoOrange.copy(alpha = 0.15f)
                                            Platform.GLM -> GlmBlue.copy(alpha = 0.15f)
                                            else -> MaterialTheme.appColors.accent.copy(alpha = 0.15f)
                                        },
                                        selectedLabelColor = when (platform) {
                                            Platform.MIMO -> MiMoOrange
                                            Platform.GLM -> GlmBlue
                                            else -> MaterialTheme.appColors.accent
                                        }
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
                        } else {
                            // 显示空状态
                            item(key = "ark_trend_empty") {
                                GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24) {
                                    Column(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "30天趋势",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            text = "暂无趋势数据",
                                            color = MaterialTheme.appColors.textTertiary,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
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
                    Platform.GLM -> {
                        // GLM Plan overview
                        state.glmPlan?.let { plan ->
                            item(key = "glm_overview") {
                                GlmAnalyticsCard(plan = plan)
                            }
                        }

                        if (state.glmPlan == null) {
                            item(key = "glm_no_data") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "暂无 GLM 用量数据，请先在设置中配置 API Key",
                                        color = MaterialTheme.appColors.textTertiary,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                    Platform.MINIMAX -> {
                        // MiniMax Plan detailed overview
                        state.minimaxPlan?.let { plan ->
                            item(key = "minimax_detail") {
                                MiniMaxAnalyticsDetailCard(plan = plan)
                            }
                            // 5h 额度详情
                            item(key = "minimax_5h") {
                                MiniMaxQuotaDetailCard(
                                    title = "5小时额度详情",
                                    used = plan.hourUsed,
                                    limit = plan.hourLimit,
                                    remainingPercent = plan.hourRemainingPercent,
                                    status = plan.hourStatus,
                                    remainingTimeMs = plan.hourRemainingTime
                                )
                            }
                            // 每周额度详情
                            item(key = "minimax_week") {
                                MiniMaxQuotaDetailCard(
                                    title = "每周额度详情",
                                    used = plan.weekUsed,
                                    limit = plan.weekLimit,
                                    remainingPercent = plan.weekRemainingPercent,
                                    status = plan.weekStatus,
                                    remainingTimeMs = plan.weekRemainingTime
                                )
                            }
                            // 套餐说明
                            item(key = "minimax_note") {
                                GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "套餐说明",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.appColors.textPrimary
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "• 套餐等级: ${plan.planType.uppercase()}\n" +
                                                    "• 5小时窗口: 约 ${plan.hourLimit} 次\n" +
                                                    "• 每周窗口: 约 ${plan.weekLimit} 次\n" +
                                                    "• 模型: ${plan.modelName}",
                                            color = MaterialTheme.appColors.textSecondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        // 30天用量趋势折线图
                        if (state.minimaxDailyTrend.isNotEmpty()) {
                            item(key = "minimax_trend") {
                                MiniMaxTrendCard(dailyData = state.minimaxDailyTrend)
                            }
                        }

                        // 模型用量详情
                        if (state.minimaxModelSummary.isNotEmpty()) {
                            item(key = "minimax_models") {
                                MiniMaxModelSummaryCard(modelData = state.minimaxModelSummary)
                            }
                        }

                        if (state.minimaxPlan == null) {
                            item(key = "minimax_no_data") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "暂无 MiniMax 用量数据，请先在设置中配置 API Key",
                                        color = MaterialTheme.appColors.textTertiary,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
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

@Composable
private fun GlmAnalyticsCard(plan: GlmPlanOverview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "GLM Coding Plan 用量",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "套餐等级: ${plan.level.uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary
            )
            Spacer(Modifier.height(16.dp))

            // 5小时额度
            QuotaRow(
                label = "5小时额度",
                percentage = plan.hour5Percentage
            )

            Spacer(Modifier.height(12.dp))

            // 每周额度
            QuotaRow(
                label = "每周额度",
                percentage = plan.weeklyPercentage
            )

            // MCP 次数
            if (plan.mcpTotal > 0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("MCP 每月次数", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${plan.mcpUsage} / ${plan.mcpTotal}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaRow(label: String, percentage: Double) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "已使用 ${String.format("%.1f", percentage)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (percentage / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.appColors.accent
        )
    }
}

private fun formatArkTokens(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000.0)
        else -> tokens.toString()
    }
}

fun formatMiniMaxTokenCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

// ===== MiniMax Analytics Components =====

@Composable
private fun MiniMaxAnalyticsDetailCard(plan: MiniMaxPlanOverview) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "MiniMax Token Plan 用量详情",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.appColors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "套餐等级: ${plan.planType.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.textSecondary
                )
                if (plan.modelName.isNotBlank()) {
                    Text(
                        text = "模型: ${plan.modelName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMaxQuotaDetailCard(
    title: String,
    used: Long,
    limit: Long,
    remainingPercent: Int,
    status: Int,
    remainingTimeMs: Long
) {
    val usedPercent = if (limit > 0) (used.toDouble() / limit) else 0.0
    val isExhausted = status == 2

    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // 标题 + 状态标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.appColors.textPrimary
                )
                if (isExhausted) {
                    Text(
                        text = "已用完",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF6B6B),
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "剩余 $remainingPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MiniMaxPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { usedPercent.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (isExhausted) Color(0xFFFF6B6B) else MiniMaxPurple,
                trackColor = (if (isExhausted) Color(0xFFFF6B6B) else MiniMaxPurple).copy(alpha = 0.15f)
            )

            Spacer(Modifier.height(8.dp))

            // 已用 / 额度
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "已用: ${formatMiniMaxTokenCount(used)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary
                )
                Text(
                    text = "额度: ${formatMiniMaxTokenCount(limit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary
                )
            }

            // 重置时间
            if (remainingTimeMs > 0) {
                Spacer(Modifier.height(6.dp))
                val hours = remainingTimeMs / 3_600_000
                val minutes = (remainingTimeMs % 3_600_000) / 60_000
                Text(
                    text = "重置时间: ${hours}小时${minutes}分钟后",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textTertiary
                )
            }
        }
    }
}

@Composable
private fun MiniMaxStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MiniMaxPurple.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MiniMaxPurple
            )
        }
    }
}

private val MiniMaxPurple = Color(0xFF6C5CE7)

@Composable
private fun MiniMaxTrendCard(dailyData: List<com.deepseek.lzjc.data.db.DailyMaxUsed>) {
    val maxUsed = dailyData.maxOfOrNull { it.usedCount }?.toFloat() ?: 1f

    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "30天用量趋势",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.appColors.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "数据来源：每次刷新时的已用次数记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textTertiary
            )
            Spacer(Modifier.height(16.dp))

            // 折线图
            val pathColor = MiniMaxPurple
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                if (dailyData.isEmpty()) return@Canvas

                val padding = 8.dp.toPx()
                val width = size.width - padding * 2
                val height = size.height - padding * 2

                val xStep = if (dailyData.size > 1) width / (dailyData.size - 1) else 0f

                // 网格线（3条）
                val gridColor = Color(0xFFE0E0E0)
                for (i in 0..3) {
                    val y = padding + (height * i / 3)
                    drawLine(
                        color = gridColor,
                        start = Offset(padding, y),
                        end = Offset(size.width - padding, y),
                        strokeWidth = 1f
                    )
                }

                // 数据点
                val points = dailyData.mapIndexed { index, data ->
                    val x = padding + xStep * index
                    val y = padding + height - (data.usedCount.toFloat() / maxUsed) * height
                    Offset(x, y)
                }

                // 折线
                if (points.size >= 2) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    // 渐变填充
                    val fillPath = Path().apply {
                        moveTo(points.first().x, size.height - padding)
                        lineTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                        lineTo(points.last().x, size.height - padding)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                pathColor.copy(alpha = 0.3f),
                                pathColor.copy(alpha = 0.05f)
                            ),
                            startY = padding,
                            endY = size.height - padding
                        )
                    )
                    drawPath(
                        path = path,
                        color = pathColor,
                        style = Stroke(width = 3f)
                    )
                }

                // 数据点圆圈
                points.forEach { point ->
                    drawCircle(
                        color = pathColor,
                        radius = 3.dp.toPx(),
                        center = point
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // X轴日期标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dailyData.firstOrNull()?.date?.substring(5) ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textTertiary
                )
                Text(
                    text = "${dailyData.size} 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textTertiary
                )
                Text(
                    text = dailyData.lastOrNull()?.date?.substring(5) ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textTertiary
                )
            }

            Spacer(Modifier.height(12.dp))

            // 汇总
            val totalUsed = dailyData.sumOf { it.usedCount }
            val avgUsed = if (dailyData.isNotEmpty()) totalUsed / dailyData.size else 0L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "累计",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textTertiary
                    )
                    Text(
                        text = "$totalUsed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MiniMaxPurple
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "日均",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textTertiary
                    )
                    Text(
                        text = "$avgUsed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MiniMaxPurple
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "峰值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textTertiary
                    )
                    Text(
                        text = "${maxUsed.toLong()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MiniMaxPurple
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMaxModelSummaryCard(modelData: List<com.deepseek.lzjc.data.db.ModelUsageRow>) {
    val totalUsed = modelData.sumOf { it.maxUsed }.coerceAtLeast(1L)

    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "模型用量详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.appColors.textPrimary
            )
            Spacer(Modifier.height(12.dp))

            modelData.forEach { row ->
                val percent = if (row.totalQuota > 0) {
                    (row.maxUsed.toFloat() / row.totalQuota * 100).coerceAtMost(100f)
                } else 0f

                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.modelName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.appColors.textPrimary
                        )
                        Text(
                            text = "${row.maxUsed} / ${row.totalQuota} (${percent.toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textSecondary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (percent / 100).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MiniMaxPurple,
                        trackColor = MiniMaxPurple.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}
