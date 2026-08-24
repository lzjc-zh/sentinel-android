package com.deepseek.lzjc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepseek.lzjc.data.db.DailyUsageSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.deepseek.lzjc.ui.theme.appColors

/**
 * MiMo unified cost trend card.
 * Chart line shows ¥ (estimated cost), stats show both ¥ and Token.
 */
@Composable
fun MiMoCostTrendCard(
    dailyData: List<DailyUsageSummary>,
    modifier: Modifier = Modifier
) {
    val visibleData = remember(dailyData) { rememberLast30Days(dailyData) }
    val totalCost = remember(visibleData) { visibleData.sumOf { it.costAmount } }
    val totalTokens = remember(visibleData) { visibleData.sumOf { it.totalTokens } }
    val avgCost = remember(visibleData, totalCost) {
        if (visibleData.isNotEmpty()) totalCost / visibleData.size else 0.0
    }
    val avgTokens = remember(visibleData, totalTokens) {
        if (visibleData.isNotEmpty()) totalTokens / visibleData.size else 0L
    }
    val peak = remember(visibleData) { visibleData.maxByOrNull { it.costAmount } }

    // Chart uses ¥ values
    val chartData = remember(visibleData) { visibleData.map { it.costAmount.toFloat() } }
    val maxVal = remember(chartData) { chartData.maxOrNull()?.coerceAtLeast(0.01f) ?: 0.01f }

    GlassPanel(modifier = modifier.fillMaxWidth(), radius = 22) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Header: title + total ¥
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        "消耗趋势",
                        color = MaterialTheme.appColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Token × 单价估算",
                        color = MaterialTheme.appColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "¥${String.format("%.2f", totalCost)}",
                        color = Color(0xFFFF6A00),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        formatTokenCompact(totalTokens),
                        color = MaterialTheme.appColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            val dividerColor = MaterialTheme.appColors.divider
            // Chart (¥ values)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                val chartHeight = size.height - 8f
                val chartWidth = size.width
                val stepX = chartWidth / (chartData.size - 1).coerceAtLeast(1)

                repeat(3) { line ->
                    val y = 4f + line * (chartHeight / 3f)
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f
                    )
                }

                if (chartData.size < 2) return@Canvas

                val points = chartData.mapIndexed { index, value ->
                    val x = index * stepX
                    val y = chartHeight - (value / maxVal * (chartHeight - 8f)) + 4f
                    Offset(x, y)
                }

                val linePath = Path()
                val fillPath = Path()
                linePath.moveTo(points.first().x, points.first().y)
                fillPath.moveTo(points.first().x, chartHeight + 4f)
                fillPath.lineTo(points.first().x, points.first().y)

                for (i in 1 until points.size) {
                    linePath.lineTo(points[i].x, points[i].y)
                    fillPath.lineTo(points[i].x, points[i].y)
                }
                fillPath.lineTo(points.last().x, chartHeight + 4f)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFFFF6A00).copy(alpha = 0.2f), Color.Transparent)
                    )
                )

                drawPath(
                    path = linePath,
                    color = Color(0xFFFF6A00),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                points.forEach { point ->
                    drawCircle(color = Color(0xFFFF6A00), radius = 3f, center = point)
                    drawCircle(color = Color.White, radius = 1.5f, center = point)
                }
            }

            Spacer(Modifier.height(4.dp))
            // Stats: daily avg (¥ + Token) | peak
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "日均 ¥${String.format("%.2f", avgCost)}",
                        color = MaterialTheme.appColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "日均 ${formatTokenCompact(avgTokens)}",
                        color = MaterialTheme.appColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (peak != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "峰值 ${peak.date.takeLast(5)}",
                            color = Color(0xFFFF6B6B),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "¥${String.format("%.2f", peak.costAmount)} · ${formatTokenCompact(peak.totalTokens)}",
                            color = Color(0xFFFF6B6B).copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private fun rememberLast30Days(dailyData: List<DailyUsageSummary>): List<DailyUsageSummary> {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    val byDate = dailyData.associateBy { it.date }
    val today = LocalDate.now()

    return (29 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong()).format(formatter)
        byDate[date] ?: DailyUsageSummary(date = date, totalTokens = 0, costAmount = 0.0)
    }
}

private fun formatTokenCompact(count: Long): String = when {
    count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> count.toString()
}
