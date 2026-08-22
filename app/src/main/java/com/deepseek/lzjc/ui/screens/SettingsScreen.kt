package com.deepseek.lzjc.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.Platform
import com.deepseek.lzjc.data.mimo.MiMoCookieManager
import com.deepseek.lzjc.data.repository.MiMoRepository
import com.deepseek.lzjc.data.repository.UsageRepository
import com.deepseek.lzjc.util.LanguageOption
import com.deepseek.lzjc.util.findLanguageByCode
import com.deepseek.lzjc.util.languageOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private val MiMoOrange = Color(0xFFFF6A00)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: android.app.Application,
    private val repository: UsageRepository,
    private val mimoRepository: MiMoRepository
) : ViewModel() {

    var apiKey by mutableStateOf("")
        private set
    var userToken by mutableStateOf("")
        private set
    var currentPlatform by mutableStateOf(Platform.DEEPSEEK)
        private set
    var mimoLoggedIn by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            apiKey = repository.apiKey.first()
            userToken = repository.userToken.first()
            mimoLoggedIn = mimoRepository.isLoggedIn()
            val prefs = application.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
            currentPlatform = Platform.fromKey(prefs.getString("current_platform", "deepseek") ?: "deepseek")
        }
    }

    fun updateApiKey(key: String) {
        apiKey = key
    }

    fun updateUserToken(token: String) {
        userToken = token
    }

    fun switchPlatform(platform: Platform) {
        currentPlatform = platform
    }

    fun save(onSuccess: () -> Unit, prefs: android.content.SharedPreferences) {
        viewModelScope.launch {
            val key = apiKey.trim()
            val token = userToken.trim()
            if (key.isNotBlank()) repository.saveApiKey(key)
            if (token.isNotBlank()) repository.saveUserToken(token)
            prefs.edit().putString("current_platform", currentPlatform.key).apply()
            onSuccess()
        }
    }

    fun onMiMoLoginSuccess() {
        viewModelScope.launch {
            val success = mimoRepository.saveCookiesFromWebView()
            mimoLoggedIn = success
        }
    }

    fun miMoLogout() {
        viewModelScope.launch {
            mimoRepository.logout()
            mimoLoggedIn = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    onSaveSuccess: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val accent = Color(0xFF4D6BFE)

    var showKey by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var threshold by remember {
        mutableStateOf(
            context.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
                .getString("balance_threshold", "") ?: ""
        )
    }
    var selectedLang by remember {
        mutableStateOf(
            findLanguageByCode(
                context.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
                    .getString("app_language", "zh") ?: "zh"
            )
        )
    }
    var langExpanded by remember { mutableStateOf(false) }
    var showMiMoWebView by remember { mutableStateOf(false) }

    if (showMiMoWebView) {
        MiMoLoginWebView(
            onLoginSuccess = {
                viewModel.onMiMoLoginSuccess()
                showMiMoWebView = false
            },
            onCancel = { showMiMoWebView = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF333333))
                }
            }
            Text(
                stringResource(R.string.title_settings),
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Platform selector
        SettingsPanel {
            Text(
                "平台",
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Platform.entries.forEach { platform ->
                    FilterChip(
                        selected = viewModel.currentPlatform == platform,
                        onClick = { viewModel.switchPlatform(platform) },
                        label = {
                            Text(
                                platform.displayName,
                                fontWeight = if (viewModel.currentPlatform == platform) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (platform == Platform.MIMO) MiMoOrange.copy(alpha = 0.15f) else accent.copy(alpha = 0.15f),
                            selectedLabelColor = if (platform == Platform.MIMO) MiMoOrange else accent
                        )
                    )
                }
            }
        }

        // DeepSeek settings (shown only when DeepSeek is selected)
        if (viewModel.currentPlatform == Platform.DEEPSEEK) {
            SettingsPanel {
                Text(
                    stringResource(R.string.api_settings),
                    color = Color(0xFF1A1A1A),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))

                SecretField(
                    value = viewModel.apiKey,
                    onValueChange = viewModel::updateApiKey,
                    label = stringResource(R.string.label_api_key),
                    placeholder = "sk-...",
                    visible = showKey,
                    onToggleVisible = { showKey = !showKey },
                    accent = accent
                )

                Spacer(Modifier.height(14.dp))

                SecretField(
                    value = viewModel.userToken,
                    onValueChange = viewModel::updateUserToken,
                    label = stringResource(R.string.label_user_token),
                    placeholder = "eyJ...",
                    visible = showToken,
                    onToggleVisible = { showToken = !showToken },
                    accent = accent
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text(stringResource(R.string.label_threshold)) },
                    placeholder = { Text(stringResource(R.string.threshold_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("\u00a5") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF000000),
                        unfocusedTextColor = Color(0xFF000000),
                        focusedLabelColor = accent,
                        unfocusedLabelColor = Color(0xFF666666),
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color(0xFFCCCCCC),
                        focusedPlaceholderColor = Color(0xFF999999),
                        unfocusedPlaceholderColor = Color(0xFF999999),
                        cursorColor = accent
                    )
                )
                Text(
                    stringResource(R.string.threshold_desc),
                    color = Color(0xFF999999),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        val prefs = context.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("balance_threshold", threshold).apply()
                        viewModel.save(onSaveSuccess ?: onBack ?: {}, prefs)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.btn_save), fontWeight = FontWeight.SemiBold)
                }
            }

            SettingsPanel {
                Text(
                    stringResource(R.string.title_usage),
                    color = Color(0xFF1A1A1A),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.usage_text),
                    color = Color(0xFF666666),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // MiMo settings (shown only when MiMo is selected)
        if (viewModel.currentPlatform == Platform.MIMO) {
            SettingsPanel {
                Text(
                    "MiMo 账号",
                    color = Color(0xFF1A1A1A),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))

                if (viewModel.mimoLoggedIn) {
                    Text(
                        "已登录",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.miMoLogout() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("退出登录", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text(
                        "未登录",
                        color = Color(0xFF999999),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showMiMoWebView = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MiMoOrange,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("登录 MiMo", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            SettingsPanel {
                Text(
                    "使用说明",
                    color = Color(0xFF1A1A1A),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "点击「登录 MiMo」将打开小米账号登录页面。\n\n登录成功后，系统会自动提取认证 Cookie，之后即可查看 MiMo 平台的用量数据。\n\n数据来源：platform.xiaomimimo.com",
                    color = Color(0xFF666666),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // About
        SettingsPanel {
            val ctx = LocalContext.current
            val versionName = remember {
                try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
                } catch (_: Exception) { "?" }
            }
            Text(
                stringResource(R.string.about_title),
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "哨兵",
                    color = Color(0xFF1A1A1A),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "v$versionName",
                    color = Color(0xFF999999),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.about_description),
                color = Color(0xFF666666),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Open-source credits
        SettingsPanel {
            Text(
                "致谢",
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            CreditItem(
                name = "SeekFlow",
                author = "DavidBlon",
                desc = "DeepSeek API 余额与用量监控",
                url = "github.com/DavidBlon/SeekFlow"
            )
            Spacer(Modifier.height(8.dp))
            CreditItem(
                name = "MiMo-Tracker",
                author = "TheMoDev",
                desc = "小米 MiMo 平台用量追踪器",
                url = "github.com/TheMoDev/MiMo-Tracker"
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "本应用基于上述开源项目整合开发，\n感谢原作者的杰出贡献。",
                color = Color(0xFF999999),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Language settings (always shown)
        SettingsPanel {
            Text(
                stringResource(R.string.title_language),
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLang.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF000000),
                        unfocusedTextColor = Color(0xFF000000),
                        focusedLabelColor = accent,
                        unfocusedLabelColor = Color(0xFF666666),
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color(0xFFCCCCCC),
                        cursorColor = accent
                    )
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false }
                ) {
                    languageOptions.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.displayName) },
                            onClick = {
                                selectedLang = lang
                                langExpanded = false
                                context.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
                                    .edit().putString("app_language", lang.localeCode).apply()
                                (context as? Activity)?.recreate()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ===== MiMo WebView Login =====

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiMoLoginWebView(
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
                                    val cookies = MiMoCookieManager.extractCookiesFromWebViewStatic()
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

// ===== Shared UI =====

@Composable
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF5F7FA))
            .padding(18.dp),
        content = content
    )
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
            .background(Color.White)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "by $author",
                color = Color(0xFF999999),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            desc,
            color = Color(0xFF666666),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            url,
            color = Color(0xFF4D6BFE),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    accent: Color = Color(0xFF4D6BFE)
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onToggleVisible) {
                Text(
                    if (visible) stringResource(R.string.btn_hide) else stringResource(R.string.btn_show),
                    color = Color(0xFF333333)
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color(0xFF000000),
            unfocusedTextColor = Color(0xFF000000),
            focusedLabelColor = accent,
            unfocusedLabelColor = Color(0xFF666666),
            focusedBorderColor = accent,
            unfocusedBorderColor = Color(0xFFCCCCCC),
            focusedPlaceholderColor = Color(0xFF999999),
            unfocusedPlaceholderColor = Color(0xFF999999),
            cursorColor = accent
        )
    )
}
