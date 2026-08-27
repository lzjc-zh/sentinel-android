package com.deepseek.lzjc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepseek.lzjc.ui.theme.appColors

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColors.background)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.appColors.textPrimary
                )
            }
            Text(
                "关于",
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // App info card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.appColors.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "哨兵",
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "v$versionName",
                color = MaterialTheme.appColors.textTertiary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "AI 用量监控 Android 应用",
                color = MaterialTheme.appColors.textSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "支持 DeepSeek、MiMo 和火山方舟",
                color = MaterialTheme.appColors.textSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }

        // Links card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.appColors.surface)
        ) {
            // Check for updates
            AboutLinkItem(
                label = "检查更新",
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/lzjc-zh/sentinel-android/releases")
                    )
                    context.startActivity(intent)
                }
            )

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .height(1.dp)
                    .background(MaterialTheme.appColors.divider)
            )

            // Privacy policy
            AboutLinkItem(
                label = "隐私声明",
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://lzjc-zh.github.io/sentinel-android/privacy-policy.html")
                    )
                    context.startActivity(intent)
                }
            )
        }

        // Credits card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.appColors.surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "致谢",
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            CreditItem(
                name = "SeekFlow",
                author = "DavidBlon",
                desc = "DeepSeek API 余额与用量监控",
                url = "github.com/DavidBlon/SeekFlow"
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "本应用基于上述开源项目整合开发，感谢原作者的杰出贡献。",
                color = MaterialTheme.appColors.textTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AboutLinkItem(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = MaterialTheme.appColors.textPrimary,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            ">",
            color = MaterialTheme.appColors.textTertiary
        )
    }
}

@Composable
private fun CreditItem(
    name: String,
    author: String,
    desc: String,
    url: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.appColors.background)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "by $author",
                color = MaterialTheme.appColors.textTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            desc,
            color = MaterialTheme.appColors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            url,
            color = MaterialTheme.appColors.accent,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
