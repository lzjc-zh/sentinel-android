package com.deepseek.lzjc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.db.DailyUsageSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.deepseek.lzjc.ui.theme.appColors

@Composable
fun DailyBarChart(
    dailyData: List<DailyUsageSummary>,
    modifier: Modifier = Modifier,
    onBarTap: (String) -> Unit = {}
) {
    val visibleData = remember(dailyData) { rememberLastSevenDaysData(dailyData) }
    val total = visibleData.sumOf { it.totalTokens }

    GlassPanel(modifier = modifier.fillMaxWidth(), radius = 24) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chart_consumption_trend), color = MaterialTheme.appColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${stringResource(R.string.chart_total)} ${formatChartTokens(total)}", color = MaterialTheme.appColors.textPrimary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))

            val maxTokens = visibleData.maxOfOrNull { it.totalTokens }?.coerceAtLeast(1L) ?: 1L
            val themeColors = MaterialTheme.appColors
            val labelArgb = android.graphics.Color.argb(200,
                themeColors.textSecondary.red.toInt().coerceIn(0,255),
                themeColors.textSecondary.green.toInt().coerceIn(0,255),
                themeColors.textSecondary.blue.toInt().coerceIn(0,255))
            val valueArgb = android.graphics.Color.argb(230,
                themeColors.textPrimary.red.toInt().coerceIn(0,255),
                themeColors.textPrimary.green.toInt().coerceIn(0,255),
                themeColors.textPrimary.blue.toInt().coerceIn(0,255))

            // 缓存 Paint 对象，theme 变化时重建
            val valuePaint = remember(valueArgb) {
                android.graphics.Paint().apply {
                    color = valueArgb
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
            }
            val labelPaint = remember(labelArgb) {
                android.graphics.Paint().apply {
                    color = labelArgb
                    textSize = 31f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(184.dp)
                    .pointerInput(visibleData) {
                        detectTapGestures { offset ->
                            val slotWidth = size.width.toFloat() / visibleData.size
                            val index = (offset.x / slotWidth).toInt().coerceIn(0, visibleData.size - 1)
                            onBarTap(visibleData[index].date)
                        }
                    }
            ) {
                val chartHeight = size.height - 54f
                val slotWidth = size.width / visibleData.size
                val barWidth = slotWidth * 0.56f
                val baseY = chartHeight + 12f

                repeat(3) { line ->
                    val y = 28f + line * (chartHeight / 3f)
                    drawLine(
                        color = themeColors.divider,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2f
                    )
                }

                visibleData.forEachIndexed { index, item ->
                    val barHeight = (item.totalTokens.toFloat() / maxTokens) * (chartHeight - 28f)
                    val x = index * slotWidth + (slotWidth - barWidth) / 2
                    val y = baseY - barHeight

                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFF8EEAFF), Color(0xFF355BFF))),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight.coerceAtLeast(8f)),
                        cornerRadius = CornerRadius(14f, 14f)
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(x + 1f, y),
                        size = Size(barWidth - 2f, 10f),
                        cornerRadius = CornerRadius(14f, 14f)
                    )

                    drawContext.canvas.nativeCanvas.apply {
                        drawText(formatChartTokens(item.totalTokens), x + barWidth / 2, 28f, valuePaint)
                        val monthDay = item.date.takeLast(5).split("-")
                        drawText("${monthDay[0].toInt()}/${monthDay[1].toInt()}", x + barWidth / 2, size.height - 4f, labelPaint)
                    }
                }
            }
        }
    }
}

private fun rememberLastSevenDaysData(dailyData: List<DailyUsageSummary>): List<DailyUsageSummary> {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    val byDate = dailyData.associateBy { it.date }
    val today = LocalDate.now()

    return (6 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong()).format(formatter)
        byDate[date] ?: DailyUsageSummary(
            date = date,
            totalTokens = 0,
            costAmount = 0.0
        )
    }
}

private fun formatChartTokens(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000.0)
        else -> tokens.toString()
    }
}
