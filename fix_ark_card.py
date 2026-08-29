import sys

path = r'E:\dev\projects\sentinel\app\src\main\java\com\deepseek\lzjc\ui\screens\DashboardScreen.kt'

with open(path, 'rb') as f:
    raw = f.read()

start = raw.find(b'private fun ArkCodingPlanCard(')
lines = raw[start:].split(b'\n')
end_offset = start
brace_count = 0
in_function = False
for i, line in enumerate(lines):
    for c in line:
        if c == ord('{'):
            brace_count += 1
            in_function = True
        elif c == ord('}'):
            brace_count -= 1
    end_offset += len(line) + 1
    if in_function and brace_count == 0:
        break

new_func = '''@Composable
private fun ArkCodingPlanCard(plan: com.deepseek.lzjc.data.ark.ArkPlanOverview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4A6FFF).copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Coding Plan 套餐",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF4A6FFF)
                    ) {
                        Text(
                            text = plan.planType,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (plan.status == "Running") Color(0xFF4CAF50) else Color(0xFFFF5722)
                ) {
                    Text(
                        text = if (plan.status == "Running") "生效中" else plan.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 当前会话（5h）
            CodingPlanUsageRow(
                label = "当前会话",
                percent = plan.afp5hPercent,
                used = plan.afp5hUsed,
                quota = plan.afp5hQuota,
                resetHint = "5 小时滑动窗口"
            )
            Spacer(Modifier.height(12.dp))

            // 近 1 周
            CodingPlanUsageRow(
                label = "近 1 周",
                percent = plan.afp1wPercent,
                used = plan.afp1wUsed,
                quota = plan.afp1wQuota,
                resetHint = "每周一 00:00 刷新"
            )
            Spacer(Modifier.height(12.dp))

            // 近 1 月
            CodingPlanUsageRow(
                label = "近 1 月",
                percent = plan.afp1mPercent,
                used = plan.afp1mUsed,
                quota = plan.afp1mQuota,
                resetHint = "订阅月第 1 日 00:00 刷新"
            )
            Spacer(Modifier.height(16.dp))

            // 套餐基础信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "开始时间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textTertiary
                    )
                    Text(
                        text = plan.startTime.substringBefore("T"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.textPrimary
                    )
                }
                Column {
                    Text(
                        text = "到期时间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textTertiary
                    )
                    Text(
                        text = plan.endTime.substringBefore("T"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.textPrimary
                    )
                }
                Column {
                    Text(
                        text = "自动续费",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textTertiary
                    )
                    Text(
                        text = if (plan.autoRenew) "已开启" else "未开启",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun CodingPlanUsageRow(
    label: String,
    percent: Double,
    used: Double,
    quota: Double,
    resetHint: String?
) {
    val pct = percent.coerceIn(0.0, 100.0)
    val pctText = String.format("%.2f", pct)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = pctText + "%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4A6FFF)
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (pct / 100.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = Color(0xFF4A6FFF),
            trackColor = Color(0xFF4A6FFF).copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "已用: " + String.format("%.0f", used) + " / " + String.format("%.0f", quota) +
                   "    " + (resetHint ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textTertiary
        )
    }
}
'''

new_func_bytes = new_func.encode('utf-8')
new_content = raw[:start] + new_func_bytes + raw[end_offset:]

with open(path, 'wb') as f:
    f.write(new_content)

print(f'Done. Original={len(raw)}, New={len(new_content)}')
