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
import com.deepseek.lzjc.ui.components.DayModelBreakdownPopup
import com.deepseek.lzjc.ui.components.RefreshAnimation
import com.deepseek.lzjc.ui.theme.appColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import com.deepseek.lzjc.data.ark.ArkPlanOverview
import com.deepseek.lzjc.data.db.DailyModelBreakdown
import com.deepseek.lzjc.data.db.DailyUsageSummary
import com.deepseek.lzjc.data.db.ModelCostSummary
import com.deepseek.lzjc.data.glm.GlmPlanOverview
import com.deepseek.lzjc.data.minimax.MiniMaxPlanOverview
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.mutableStateOf

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
            .background(MaterialTheme.appColors.background)
    ) {
        when (state.currentPlatform) {
            Platform.DEEPSEEK -> {
                DashboardContent(state = state, viewModel = viewModel)
            }
            Platform.MIMO -> {
                MiMoDashboardContent(state = state, viewModel = viewModel)
            }
            Platform.ARK -> {
                ArkDashboardContent(state = state, viewModel = viewModel)
            }
            Platform.GLM -> {
                GlmDashboardContent(state = state, viewModel = viewModel, onNavigateToSettings = onNavigateToSettings)
            }
            Platform.MINIMAX -> {
                MiniMaxDashboardContent(state = state, viewModel = viewModel, onNavigateToSettings = onNavigateToSettings)
            }
            else -> {
                DashboardContent(state = state, viewModel = viewModel)
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
                    withStyle(SpanStyle(color = MaterialTheme.appColors.accent)) { append("哨") }
                    withStyle(SpanStyle(color = Color(0xFFFF6A00))) { append("兵") }
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.appColors.textPrimary)
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
                        selectedContainerColor = if (platform == Platform.MIMO) MiMoOrange.copy(alpha = 0.15f) else MaterialTheme.appColors.accent.copy(alpha = 0.15f),
                        selectedLabelColor = if (platform == Platform.MIMO) MiMoOrange else MaterialTheme.appColors.accent
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
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 总余额（大字）
            Text(
                "¥${data.totalBalance}",
                color = MaterialTheme.appColors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )

            // 现金余额 + 赠送余额
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("现金余额", color = MaterialTheme.appColors.textTertiary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "¥${data.cashBalance}",
                        color = MaterialTheme.appColors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("赠送余额", color = MaterialTheme.appColors.textTertiary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "¥${data.giftBalance}",
                        color = MaterialTheme.appColors.textPrimary,
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
            color = MaterialTheme.appColors.textTertiary,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            value,
            color = MaterialTheme.appColors.textPrimary,
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
                    color = MaterialTheme.appColors.textPrimary,
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
                    color = MaterialTheme.appColors.textSecondary,
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
                        color = MaterialTheme.appColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "总计 ${formatCompactNumber(data.creditsTotal)}",
                        color = MaterialTheme.appColors.textSecondary,
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
                color = MaterialTheme.appColors.textPrimary,
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
                            color = MaterialTheme.appColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${formatCompactNumber(model.totalToken)} (${String.format("%.1f%%", model.percentage)})",
                            color = MaterialTheme.appColors.textPrimary,
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
private fun MiMoLoginDashboard(
    onLoginSuccess: () -> Unit,
    onSwitchToDeepSeek: () -> Unit = {}
) {
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
                color = MaterialTheme.appColors.textTertiary,
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
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSwitchToDeepSeek,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.appColors.accent.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.appColors.accent
                )
            ) {
                Text("Switch to DeepSeek", fontWeight = FontWeight.SemiBold)
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

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.appColors.background)) {
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
                    Text("取消", color = MaterialTheme.appColors.textSecondary)
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
                color = MaterialTheme.appColors.textSecondary,
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
                color = MaterialTheme.appColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.request_today),
                        color = MaterialTheme.appColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        formatCompactNumber(dailyRequests),
                        color = MaterialTheme.appColors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.request_month),
                        color = MaterialTheme.appColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        formatCompactNumber(monthlyRequests),
                        color = MaterialTheme.appColors.textPrimary,
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
private fun EmptyDashboard(
    onNavigateToSettings: () -> Unit,
    onSwitchToMiMo: () -> Unit = {}
) {
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
                .background(MaterialTheme.appColors.surface, shape = RoundedCornerShape(24.dp))
                .padding(10.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.appColors.accent)) { append("哨") }
                withStyle(SpanStyle(color = Color(0xFFFF6A00))) { append("兵") }
            },
            color = MaterialTheme.appColors.textPrimary,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.empty_dashboard_desc),
            color = MaterialTheme.appColors.textTertiary,
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
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSwitchToMiMo,
            colors = ButtonDefaults.buttonColors(
                containerColor = MiMoOrange.copy(alpha = 0.12f),
                contentColor = MiMoOrange
            )
        ) {
            Text("Switch to MiMo", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ===== Ark Dashboard =====

@Composable
private fun ArkEmptyDashboard(
    onNavigateToSettings: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🌋",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "火山方舟",
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "请先在设置中配置 Access Key",
                color = MaterialTheme.appColors.textTertiary,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onNavigateToSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArkOrange,
                    contentColor = Color.White
                )
            ) {
                Text("前往设置", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val ArkOrange = Color(0xFFFF6B35)

@Composable
private fun ArkDashboardContent(
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
                // Header
                item(key = "header") {
                    PlatformHeaderBar(
                        currentPlatform = Platform.ARK,
                        onPlatformChange = { viewModel.switchPlatform(it) },
                        onRefresh = { viewModel.refresh() }
                    )
                }

                // Error message
                state.errorMessage?.let { error ->
                    item(key = "error") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = error,
                                color = Color(0xFFD32F2F),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Plan info card
                state.arkPlan?.let { plan ->
                    item(key = "plan") {
                        ArkPlanCard(plan = plan)
                    }
                }

                // AFP usage card
                state.arkPlan?.let { plan ->
                    item(key = "afp") {
                        ArkAFPCard(plan = plan)
                    }
                }

                // Today & Monthly usage
                item(key = "usage") {
                    ArkUsageCard(
                        todayUsage = state.arkTodayUsage,
                        monthlyUsage = state.arkMonthlyUsage
                    )
                }

                // 7-day bar chart
                if (state.arkDailyData.isNotEmpty()) {
                    item(key = "chart") {
                        ArkDailyChart(
                            dailyData = state.arkDailyData,
                            onBarTap = { date -> selectedDay = date }
                        )
                    }
                }

                // AFP Usage Details (5h/week/month)
                state.arkPlan?.let { plan ->
                    item(key = "afp_details") {
                        ArkAFPDetailsCard(plan = plan)
                    }
                }
            }

            // Model breakdown popup
            selectedDay?.let { date ->
                val breakdownsForDay = remember(date, state.arkModelBreakdowns) {
                    state.arkModelBreakdowns.filter { it.date == date }
                }
                DayModelBreakdownPopup(
                    date = date,
                    breakdowns = breakdownsForDay,
                    onDismiss = { selectedDay = null }
                )
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
}

@Composable
private fun ArkPlanCard(plan: ArkPlanOverview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ArkOrange.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Agent Plan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (plan.status == "Running") Color(0xFF4CAF50) else Color(0xFFFF5722)
                ) {
                    Text(
                        text = if (plan.status == "Running") "运行中" else plan.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ArkInfoItem("套餐类型", plan.planType)
                ArkInfoItem("到期时间", plan.endTime.substringBefore("T"))
                ArkInfoItem("自动续费", if (plan.autoRenew) "是" else "否")
            }
        }
    }
}

@Composable
private fun ArkInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textTertiary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ArkAFPCard(plan: ArkPlanOverview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "AFP 额度",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { plan.usagePercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = ArkOrange,
                trackColor = ArkOrange.copy(alpha = 0.2f)
            )
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "已用: ${String.format("%.1f", plan.usedAFP)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "剩余: ${String.format("%.1f", plan.remainingAFP)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "总计: ${String.format("%.1f", plan.totalAFP)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Detail breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ArkAFPDetail("5小时", plan.afp5hUsed, plan.afp5hQuota)
                ArkAFPDetail("本周", plan.afp1wUsed, plan.afp1wQuota)
                ArkAFPDetail("本月", plan.afp1mUsed, plan.afp1mQuota)
            }
        }
    }
}

@Composable
private fun ArkAFPDetail(label: String, used: Double, quota: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textTertiary
        )
        Text(
            text = "${String.format("%.0f", used)}/${String.format("%.0f", quota)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ArkUsageCard(todayUsage: Long, monthlyUsage: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "今日用量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textTertiary
                )
                Text(
                    text = formatTokenCount(todayUsage),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
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
                    color = MaterialTheme.appColors.textTertiary
                )
                Text(
                    text = formatTokenCount(monthlyUsage),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
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

@Composable
private fun ArkDailyChart(
    dailyData: List<DailyUsageSummary>,
    onBarTap: (String) -> Unit = {}
) {
    val visibleData = remember(dailyData) { rememberLastSevenDaysData(dailyData) }
    val total = visibleData.sumOf { it.totalTokens }

    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "消耗趋势",
                    color = MaterialTheme.appColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "合计 ${formatArkTokens(total)}",
                    color = MaterialTheme.appColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))

            val maxTokens = visibleData.maxOfOrNull { it.totalTokens }?.coerceAtLeast(1L) ?: 1L
            val themeColors = MaterialTheme.appColors

            // 缓存 Paint 对象
            val valuePaint = remember {
                android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(230, 255, 255, 255)
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
            }
            val labelPaint = remember {
                android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(200, 180, 180, 180)
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

                // 网格线
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

                    // 柱子
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFFFF8A50), Color(0xFFFF6B35))),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight.coerceAtLeast(8f)),
                        cornerRadius = CornerRadius(14f, 14f)
                    )
                    // 高光
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(x + 1f, y),
                        size = Size(barWidth - 2f, 10f),
                        cornerRadius = CornerRadius(14f, 14f)
                    )

                    // 数值和日期
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(formatArkTokens(item.totalTokens), x + barWidth / 2, 28f, valuePaint)
                        val monthDay = item.date.takeLast(5).split("-")
                        drawText("${monthDay[0].toInt()}/${monthDay[1].toInt()}", x + barWidth / 2, size.height - 4f, labelPaint)
                    }
                }
            }
        }
    }
}



private fun formatTokenCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatArkTokens(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000.0)
        else -> tokens.toString()
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

@Composable
private fun ArkAFPDetailsCard(plan: ArkPlanOverview) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "Agent燃料值（AFP）用量",
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "套餐内AFP实时用量统计数据",
                color = MaterialTheme.appColors.textTertiary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(16.dp))

            // 近5小时用量
            AFPUsageRow(
                label = "近5小时用量",
                used = plan.afp5hUsed,
                quota = plan.afp5hQuota
            )

            Spacer(Modifier.height(12.dp))

            // 近一周用量
            AFPUsageRow(
                label = "近一周用量",
                used = plan.afp1wUsed,
                quota = plan.afp1wQuota
            )

            Spacer(Modifier.height(12.dp))

            // 近一月用量
            AFPUsageRow(
                label = "近一月用量",
                used = plan.afp1mUsed,
                quota = plan.afp1mQuota
            )
        }
    }
}

@Composable
private fun AFPUsageRow(
    label: String,
    used: Double,
    quota: Double
) {
    val percentage = if (quota > 0) (used / quota * 100).toInt() else 0

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "已使用 $percentage%",
                color = MaterialTheme.appColors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatArkDouble(used)} / ${formatArkDouble(quota)}",
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (percentage / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = ArkOrange,
            trackColor = ArkOrange.copy(alpha = 0.2f)
        )
    }
}

private fun formatArkDouble(value: Double): String {
    return when {
        value >= 1_000_000 -> String.format("%.1f万", value / 10_000.0)
        value >= 10_000 -> String.format("%.1f万", value / 10_000.0)
        value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
        value >= 1 -> String.format("%.1f", value)
        else -> String.format("%.2f", value)
    }
}

// ===== GLM Dashboard =====

@Composable
private fun GlmEmptyDashboard(
    onNavigateToSettings: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🧠",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "智谱 GLM",
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "请先在设置中配置 API Key",
                color = MaterialTheme.appColors.textTertiary,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onNavigateToSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GlmBlue,
                    contentColor = Color.White
                )
            ) {
                Text("前往设置", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val GlmBlue = Color(0xFF134CFF)

@Composable
private fun GlmDashboardContent(
    state: DashboardState,
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit = {}
) {
    // 每次进入此页面重新检查凭证（设置页保存后返回时生效）
    LaunchedEffect(Unit) {
        viewModel.recheckCredentials()
    }

    if (state.isLoading) {
        LoadingView()
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Header (使用统一的 PlatformHeaderBar)
            item(key = "header") {
                PlatformHeaderBar(
                    currentPlatform = Platform.GLM,
                    onPlatformChange = { viewModel.switchPlatform(it) },
                    onRefresh = { viewModel.refresh() }
                )
            }

            // Error message
            state.errorMessage?.let { error ->
                item(key = "error") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // GLM Plan info
            state.glmPlan?.let { plan ->
                item(key = "glm_plan") {
                    GlmPlanCard(plan = plan)
                }
            }

            // 若未配置 API Key 或报错，显示设置入口
            if (!state.glmHasApiKey) {
                item(key = "glm_setup") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "请先在设置中配置 API Key",
                                color = MaterialTheme.appColors.textSecondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToSettings,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GlmBlue,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("前往设置", fontWeight = FontWeight.SemiBold)
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

@Composable
private fun GlmPlanCard(plan: GlmPlanOverview) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "GLM Coding Plan",
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "套餐等级: ${plan.level.uppercase()}",
                color = MaterialTheme.appColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            // 5小时额度
            GlmUsageRow(
                label = "5小时额度",
                percentage = plan.hour5Percentage
            )

            Spacer(Modifier.height(12.dp))

            // 每周额度
            GlmUsageRow(
                label = "每周额度",
                percentage = plan.weeklyPercentage
            )

            // MCP 次数（如果有）
            if (plan.mcpTotal > 0) {
                Spacer(Modifier.height(12.dp))
                GlmMcpRow(
                    used = plan.mcpUsage,
                    remaining = plan.mcpRemaining,
                    total = plan.mcpTotal
                )
            }
        }
    }
}

@Composable
private fun GlmUsageRow(
    label: String,
    percentage: Double
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "已使用 ${percentage.toInt()}%",
                color = MaterialTheme.appColors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (percentage.toFloat() / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = GlmBlue,
            trackColor = GlmBlue.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun GlmMcpRow(
    used: Long,
    remaining: Long,
    total: Long
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MCP 每月次数",
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "剩余 $remaining / $total",
                color = MaterialTheme.appColors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(4.dp))
        val percentage = if (total > 0) (used.toDouble() / total.toDouble()) else 0.0
        LinearProgressIndicator(
            progress = { percentage.coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = GlmBlue,
            trackColor = GlmBlue.copy(alpha = 0.2f)
        )
    }
}

// ===== MiniMax Dashboard =====

private val MiniMaxPurple = Color(0xFF6C5CE7)

@Composable
private fun MiniMaxDashboardContent(
    state: DashboardState,
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit = {}
) {
    // 每次进入此页面重新检查凭证
    LaunchedEffect(Unit) {
        viewModel.recheckCredentials()
    }

    if (state.isLoading) {
        LoadingView()
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Header
            item(key = "header") {
                PlatformHeaderBar(
                    currentPlatform = Platform.MINIMAX,
                    onPlatformChange = { viewModel.switchPlatform(it) },
                    onRefresh = { viewModel.refresh() }
                )
            }

            // Error message
            state.errorMessage?.let { error ->
                item(key = "error") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // MiniMax Plan info
            state.minimaxPlan?.let { plan ->
                item(key = "minimax_plan") {
                    MiniMaxPlanCard(plan = plan)
                }
            }

            // 若未配置 API Key，显示设置入口
            if (!state.minimaxHasApiKey) {
                item(key = "minimax_setup") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "请先在设置中配置 API Key",
                                color = MaterialTheme.appColors.textSecondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToSettings,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MiniMaxPurple,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("前往设置", fontWeight = FontWeight.SemiBold)
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

@Composable
private fun MiniMaxPlanCard(plan: MiniMaxPlanOverview) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MiniMax Token Plan",
                    color = MaterialTheme.appColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MiniMaxPurple.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = plan.planType.uppercase(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MiniMaxPurple,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 5小时额度 - 进度条
            MiniMaxUsageRow(
                label = "5小时额度",
                used = plan.hourUsed,
                remaining = plan.hourRemaining,
                limit = plan.hourLimit,
                remainingPercent = plan.hourRemainingPercent,
                status = plan.hourStatus,
                remainingTimeMs = plan.hourRemainingTime
            )

            Spacer(Modifier.height(16.dp))

            // 每周额度 - 进度条
            MiniMaxUsageRow(
                label = "每周额度",
                used = plan.weekUsed,
                remaining = plan.weekRemaining,
                limit = plan.weekLimit,
                remainingPercent = plan.weekRemainingPercent,
                status = plan.weekStatus,
                remainingTimeMs = plan.weekRemainingTime
            )

            Spacer(Modifier.height(20.dp))

            // 统计数据卡片
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 今日
                MiniMaxStatCard(
                    modifier = Modifier.weight(1f),
                    label = "今日",
                    value = formatTokenCount(plan.todayUsage)
                )
                // 近7天
                MiniMaxStatCard(
                    modifier = Modifier.weight(1f),
                    label = "近7天",
                    value = formatTokenCount(plan.week7Usage)
                )
                // 近30天
                MiniMaxStatCard(
                    modifier = Modifier.weight(1f),
                    label = "近30天",
                    value = formatTokenCount(plan.week30Usage)
                )
            }
        }
    }
}

@Composable
private fun MiniMaxUsageRow(
    label: String,
    used: Long,
    remaining: Long,
    limit: Long,
    remainingPercent: Int = 0,
    status: Int = 0,
    remainingTimeMs: Long = 0
) {
    val usagePercent = if (limit > 0) (used.toDouble() / limit * 100).toInt() else 0

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "已使用 $usagePercent%",
                color = MaterialTheme.appColors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { usagePercent.coerceIn(0, 100) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = if (usagePercent >= 90) Color(0xFFE53935) else MiniMaxPurple,
            trackColor = MiniMaxPurple.copy(alpha = 0.2f)
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "已用: ${formatTokenCount(used)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textSecondary
            )
            Text(
                "额度: ${formatTokenCount(limit)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textSecondary
            )
        }
        if (remainingTimeMs > 0) {
            val hours = remainingTimeMs / 3600000
            val minutes = (remainingTimeMs % 3600000) / 60000
            Text(
                text = "${hours}小时${minutes}分后重置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textTertiary
            )
        }
    }
}

private fun formatTokenCount2(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
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
        colors = CardDefaults.cardColors(
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

@Composable
private fun MiniMaxTrendChart(dailyData: List<com.deepseek.lzjc.data.minimax.MiniMaxDailyTrend>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (dailyData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无趋势数据",
                        color = MaterialTheme.appColors.textTertiary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                val maxTokens = dailyData.maxOfOrNull { it.tokenCount } ?: 1L

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    val points = dailyData.mapIndexed { index, trend ->
                        val x = (index.toFloat() / (dailyData.size - 1).coerceAtLeast(1)) * size.width
                        val y = size.height - (trend.tokenCount.toFloat() / maxTokens) * size.height * 0.9f
                        Offset(x, y)
                    }

                    // Draw line
                    if (points.size >= 2) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = MiniMaxPurple,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        )
                    }

                    // Draw points
                    points.forEach { point ->
                        drawCircle(
                            color = MiniMaxPurple,
                            radius = 4f,
                            center = point
                        )
                    }
                }

                // Date labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dailyData.forEach { trend ->
                        Text(
                            text = trend.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textTertiary
                        )
                    }
                }
            }
        }
    }
}
