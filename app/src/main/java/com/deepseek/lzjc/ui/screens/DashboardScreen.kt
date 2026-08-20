package com.deepseek.lzjc.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.Platform
import com.deepseek.lzjc.data.mimo.MiMoCookieManager
import com.deepseek.lzjc.data.mimo.MiMoUsageData
import com.deepseek.lzjc.ui.components.BalanceCard
import com.deepseek.lzjc.ui.components.DailyBarChart
import com.deepseek.lzjc.ui.components.DayModelBreakdownPopup
import com.deepseek.lzjc.ui.components.GlassPanel
import com.deepseek.lzjc.ui.components.ModelTokenRow
import com.deepseek.lzjc.ui.components.RefreshAnimation

// MiMo accent color
private val MiMoOrange = Color(0xFFFF6A00)

@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        when (state.currentPlatform) {
            Platform.DEEPSEEK -> {
                if (!state.hasApiKey) {
                    EmptyDashboard(onNavigateToSettings = onNavigateToSettings)
                } else {
                    DashboardContent(state = state, viewModel = viewModel)
                }
            }
            Platform.MIMO -> {
                if (!state.mimoLoggedIn) {
                    MiMoLoginDashboard(onLoginSuccess = { viewModel.onMiMoLoginSuccess() })
                } else {
                    MiMoDashboardContent(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardState,
    viewModel: DashboardViewModel
) {
    if (state.isLoading) {
        LoadingView()
    } else {
        var selectedDay by remember { mutableStateOf<String?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item(key = "header") {
                    PlatformHeaderBar(
                        currentPlatform = state.currentPlatform,
                        onPlatformChange = { viewModel.switchPlatform(it) },
                        onRefresh = { viewModel.refresh() }
                    )
                }

                if (state.errorMessage != null) {
                    item(key = "error") {
                        ErrorStrip(message = state.errorMessage)
                    }
                }

                item(key = "balance") {
                    BalanceCard(
                        totalBalance = state.totalBalance,
                        grantedBalance = state.grantedBalance,
                        toppedUpBalance = state.toppedUpBalance,
                        dailyCost = state.dailyCost,
                        monthlyCost = state.monthlyCost,
                        isLoading = false
                    )
                }

                item(key = "token_summary") {
                    val maxTokens = maxOf(state.flashTokens, state.proTokens, 1L)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModelTokenRow(
                            modelName = "V4 Flash",
                            tokens = state.flashTokens,
                            progress = state.flashTokens.toFloat() / maxTokens,
                            accent = Color(0xFF19C9FF)
                        )
                        ModelTokenRow(
                            modelName = "V4 Pro",
                            tokens = state.proTokens,
                            progress = state.proTokens.toFloat() / maxTokens,
                            accent = Color(0xFFB84DFF)
                        )
                    }
                }

                if (state.dailyRequests > 0 || state.monthlyRequests > 0) {
                    item(key = "requests") {
                        RequestCountSummary(
                            dailyRequests = state.dailyRequests,
                            monthlyRequests = state.monthlyRequests
                        )
                    }
                }

                item(key = "chart") {
                    DailyBarChart(
                        dailyData = state.dailyData,
                        onBarTap = { date -> selectedDay = date }
                    )
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

            selectedDay?.let { date ->
                val breakdownsForDay = remember(date, state.modelBreakdowns) {
                    state.modelBreakdowns.filter { it.date == date }
                }
                DayModelBreakdownPopup(
                    date = date,
                    breakdowns = breakdownsForDay,
                    onDismiss = { selectedDay = null }
                )
            }
        }
    }
}

@Composable
private fun MiMoDashboardContent(
    state: DashboardState,
    viewModel: DashboardViewModel
) {
    if (state.isLoading) {
        LoadingView()
    } else {
        var selectedDay by remember { mutableStateOf<String?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item(key = "header") {
                    PlatformHeaderBar(
                        currentPlatform = state.currentPlatform,
                        onPlatformChange = { viewModel.switchPlatform(it) },
                        onRefresh = { viewModel.refresh() }
                    )
                }

                if (state.errorMessage != null) {
                    item(key = "error") {
                        ErrorStrip(message = state.errorMessage)
                    }
                }

                // MiMo plan info + balance
                state.mimoData?.let { data ->
                    item(key = "mimo_overview") {
                        MiMoOverviewCard(data = data)
                    }

                    item(key = "mimo_credits") {
                        MiMoCreditsCard(data = data)
                    }

                    item(key = "mimo_tokens") {
                        MiMoTokenSummaryCard(data = data)
                    }
                }

                if (state.mimoDailyRequests > 0 || state.mimoMonthlyRequests > 0) {
                    item(key = "requests") {
                        RequestCountSummary(
                            dailyRequests = state.mimoDailyRequests,
                            monthlyRequests = state.mimoMonthlyRequests
                        )
                    }
                }

                if (state.mimoDailyData.isNotEmpty()) {
                    item(key = "chart") {
                        DailyBarChart(
                            dailyData = state.mimoDailyData,
                            onBarTap = { date -> selectedDay = date }
                        )
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

            selectedDay?.let { date ->
                val breakdownsForDay = remember(date, state.mimoModelBreakdowns) {
                    state.mimoModelBreakdowns.filter { it.date == date }
                }
                DayModelBreakdownPopup(
                    date = date,
                    breakdowns = breakdownsForDay,
                    onDismiss = { selectedDay = null }
                )
            }
        }
    }
}

// ===== Platform selector header =====

@Composable
private fun PlatformHeaderBar(
    currentPlatform: Platform,
    onPlatformChange: (Platform) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = "哨兵",
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFF4D6BFE))) { append("哨") }
                    withStyle(SpanStyle(color = Color(0xFFFF6A00))) { append("兵") }
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF333333))
            }
        }

        // Platform selector chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Platform.entries.forEach { platform ->
                FilterChip(
                    selected = currentPlatform == platform,
                    onClick = { onPlatformChange(platform) },
                    label = {
                        Text(
                            platform.displayName,
                            fontWeight = if (currentPlatform == platform) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (platform == Platform.MIMO) MiMoOrange.copy(alpha = 0.15f) else Color(0xFF4D6BFE).copy(alpha = 0.15f),
                        selectedLabelColor = if (platform == Platform.MIMO) MiMoOrange else Color(0xFF4D6BFE)
                    )
                )
            }
        }
    }
}

// ===== MiMo UI components =====

/**
 * MiMo 账户余额卡片：显示总余额、现金余额、赠送余额。
 * 不显示有效期和总消耗。
 */
@Composable
private fun MiMoOverviewCard(data: MiMoUsageData) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "账户余额",
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 总余额（大字）
            Text(
                "¥${data.totalBalance}",
                color = Color(0xFF1A1A1A),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )

            // 现金余额 + 赠送余额
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("现金余额", color = Color(0xFF999999), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "¥${data.cashBalance}",
                        color = Color(0xFF1A1A1A),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("赠送余额", color = Color(0xFF999999), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "¥${data.giftBalance}",
                        color = Color(0xFF1A1A1A),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MiMoStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = Color(0xFF999999),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            value,
            color = Color(0xFF1A1A1A),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * MiMo 订阅额度卡片：显示计划名称(Lite标识)、有效期、额度进度。
 */
@Composable
private fun MiMoCreditsCard(data: MiMoUsageData) {
    if (data.creditsTotal <= 0 && data.planName.isBlank()) return

    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题行：计划名称 + Lite 标识
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "订阅额度",
                    color = Color(0xFF1A1A1A),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (data.planName.isNotBlank()) {
                    Text(
                        data.planName,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(MiMoOrange, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 有效期
            if (data.expireDate.isNotBlank()) {
                Text(
                    "有效期至 ${data.expireDate}",
                    color = Color(0xFF666666),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (data.creditsTotal > 0) {
                val progress = data.creditsUsed.toFloat() / data.creditsTotal

                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MiMoOrange,
                    trackColor = MiMoOrange.copy(alpha = 0.15f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "已用 ${formatCompactNumber(data.creditsUsed)}",
                        color = Color(0xFF666666),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "总计 ${formatCompactNumber(data.creditsTotal)}",
                        color = Color(0xFF666666),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    String.format("使用率 %.1f%%", data.usagePercentage),
                    color = MiMoOrange,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * MiMo Token 概要卡片：显示输入/输出/缓存 token 和按模型分组。
 */
@Composable
private fun MiMoTokenSummaryCard(data: MiMoUsageData) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Token 使用概要",
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiMoStatItem("输入(缓存)", formatCompactNumber(data.inputCached))
                MiMoStatItem("输入(未缓存)", formatCompactNumber(data.inputUncached))
                MiMoStatItem("输出", formatCompactNumber(data.output))
            }

            if (data.modelUsage.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                data.modelUsage.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            model.modelName,
                            color = Color(0xFF666666),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${formatCompactNumber(model.totalToken)} (${String.format("%.1f%%", model.percentage)})",
                            color = Color(0xFF1A1A1A),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ===== MiMo Login (WebView) =====

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiMoLoginDashboard(onLoginSuccess: () -> Unit) {
    var showWebView by remember { mutableStateOf(false) }

    if (showWebView) {
        MiMoWebViewLogin(onLoginSuccess = onLoginSuccess, onCancel = { showWebView = false })
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "MiMo",
                color = MiMoOrange,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "登录小米账号以查看 MiMo 用量数据",
                color = Color(0xFF888888),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { showWebView = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MiMoOrange,
                    contentColor = Color.White
                )
            ) {
                Text("登录 MiMo", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiMoWebViewLogin(
    onLoginSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "MiMo 登录",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiMoOrange
                    )
                    Text(
                        "登录小米账号后自动获取认证",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("取消", color = Color(0xFF666666))
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MiMoOrange
            )
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(false)
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }

                    val cookieMgr = android.webkit.CookieManager.getInstance()
                    cookieMgr.setAcceptCookie(true)
                    cookieMgr.setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                            isLoading = newProgress < 100
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url != null && url.contains("/console")) {
                                view?.postDelayed({
                                    val cookies = com.deepseek.lzjc.data.mimo.MiMoCookieManager.extractCookiesFromWebViewStatic()
                                    if (cookies != null) {
                                        onLoginSuccess()
                                    }
                                }, 500)
                            }
                        }
                    }

                    loadUrl(MiMoCookieManager.LOGIN_URL)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

// ===== Common UI =====

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RefreshAnimation(size = 52.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.loading_refreshing),
                color = Color(0xFF666666),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ErrorStrip(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFFFF6B6B).copy(alpha = 0.18f), shape = RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(message, color = Color(0xFFFFD8D8), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RequestCountSummary(
    dailyRequests: Long,
    monthlyRequests: Long
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.request_title),
                color = Color(0xFF666666),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.request_today),
                        color = Color(0xFF999999),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        formatCompactNumber(dailyRequests),
                        color = Color(0xFF1A1A1A),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.request_month),
                        color = Color(0xFF999999),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        formatCompactNumber(monthlyRequests),
                        color = Color(0xFF1A1A1A),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatCompactNumber(n: Long): String {
    return when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}

@Composable
private fun EmptyDashboard(onNavigateToSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = "哨兵",
            modifier = Modifier
                .size(86.dp)
                .background(Color(0xFFF0F0F5), shape = RoundedCornerShape(24.dp))
                .padding(10.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF4D6BFE))) { append("哨") }
                withStyle(SpanStyle(color = Color(0xFFFF6A00))) { append("兵") }
            },
            color = Color(0xFF1A1A1A),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.empty_dashboard_desc),
            color = Color(0xFF888888),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onNavigateToSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF74D9FF),
                contentColor = Color(0xFF06222C)
            )
        ) {
            Text(stringResource(R.string.go_to_settings), fontWeight = FontWeight.SemiBold)
        }
    }
}
