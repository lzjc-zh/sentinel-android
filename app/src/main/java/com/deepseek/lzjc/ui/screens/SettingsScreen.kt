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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.lzjc.LocalThemeMode
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.Platform
import com.deepseek.lzjc.data.mimo.MiMoCookieManager
import com.deepseek.lzjc.data.repository.ArkRepository
import com.deepseek.lzjc.data.repository.MiMoRepository
import com.deepseek.lzjc.data.repository.UsageRepository
import com.deepseek.lzjc.util.LanguageOption
import com.deepseek.lzjc.util.findLanguageByCode
import com.deepseek.lzjc.util.languageOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.deepseek.lzjc.ui.theme.appColors

private val MiMoOrange = Color(0xFFFF6A00)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: android.app.Application,
    private val repository: UsageRepository,
    private val mimoRepository: MiMoRepository,
    private val arkRepository: ArkRepository
) : ViewModel() {

    var apiKey by mutableStateOf("")
        private set
    var userToken by mutableStateOf("")
        private set
    var currentPlatform by mutableStateOf(Platform.DEEPSEEK)
        private set
    var mimoLoggedIn by mutableStateOf(false)
        private set
    // Ark fields
    var arkAccessKeyId by mutableStateOf("")
        private set
    var arkSecretAccessKey by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            apiKey = repository.apiKey.first()
            userToken = repository.userToken.first()
            mimoLoggedIn = mimoRepository.isLoggedIn()
            arkAccessKeyId = arkRepository.accessKeyId.first()
            arkSecretAccessKey = arkRepository.secretAccessKey.first()
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

    fun updateArkAccessKeyId(key: String) {
        arkAccessKeyId = key
    }

    fun updateArkSecretAccessKey(key: String) {
        arkSecretAccessKey = key
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
            
            // Save Ark credentials
            val arkKey = arkAccessKeyId.trim()
            val arkSecret = arkSecretAccessKey.trim()
            if (arkKey.isNotBlank() && arkSecret.isNotBlank()) {
                arkRepository.saveCredentials(arkKey, arkSecret)
            }
            
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
    onNavigateToAbout: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val accent = MaterialTheme.appColors.accent

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
            .background(MaterialTheme.appColors.background)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.appColors.textPrimary)
                }
            }
            Text(
                stringResource(R.string.title_settings),
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Platform selector
        SettingsPanel {
            Text(
                "平台",
                color = MaterialTheme.appColors.textPrimary,
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
                    color = MaterialTheme.appColors.textPrimary,
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
                        focusedTextColor = MaterialTheme.appColors.textPrimary,
                        unfocusedTextColor = MaterialTheme.appColors.textPrimary,
                        focusedLabelColor = accent,
                        unfocusedLabelColor = MaterialTheme.appColors.textSecondary,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = MaterialTheme.appColors.border,
                        focusedPlaceholderColor = MaterialTheme.appColors.textTertiary,
                        unfocusedPlaceholderColor = MaterialTheme.appColors.textTertiary,
                        cursorColor = accent
                    )
                )
                Text(
                    stringResource(R.string.threshold_desc),
                    color = MaterialTheme.appColors.textTertiary,
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
                    color = MaterialTheme.appColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.usage_text),
                    color = MaterialTheme.appColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // MiMo settings (shown only when MiMo is selected)
        if (viewModel.currentPlatform == Platform.MIMO) {
            SettingsPanel {
                Text(
                    "MiMo 账号",
                    color = MaterialTheme.appColors.textPrimary,
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
                        color = MaterialTheme.appColors.textTertiary,
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
                    color = MaterialTheme.appColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "点击「登录 MiMo」将打开小米账号登录页面。\n\n登录成功后，系统会自动提取认证 Cookie，之后即可查看 MiMo 平台的用量数据。\n\n数据来源：platform.xiaomimimo.com",
                    color = MaterialTheme.appColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Ark settings (shown only when Ark is selected)
        if (viewModel.currentPlatform == Platform.ARK) {
            SettingsPanel {
                Text(
                    "火山方舟 API 设置",
                    color = MaterialTheme.appColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))

                var showArkKey by remember { mutableStateOf(false) }
                var showArkSecret by remember { mutableStateOf(false) }

                SecretField(
                    value = viewModel.arkAccessKeyId,
                    onValueChange = viewModel::updateArkAccessKeyId,
                    label = "Access Key ID",
                    placeholder = "AKLT...",
                    visible = showArkKey,
                    onToggleVisible = { showArkKey = !showArkKey },
                    accent = Color(0xFFFF6B35)
                )

                Spacer(Modifier.height(14.dp))

                SecretField(
                    value = viewModel.arkSecretAccessKey,
                    onValueChange = viewModel::updateArkSecretAccessKey,
                    label = "Secret Access Key",
                    placeholder = "输入 Secret Access Key",
                    visible = showArkSecret,
                    onToggleVisible = { showArkSecret = !showArkSecret },
                    accent = Color(0xFFFF6B35)
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        val prefs = context.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
                        viewModel.save(onSaveSuccess ?: onBack ?: {}, prefs)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B35),
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.btn_save), fontWeight = FontWeight.SemiBold)
                }
            }

        }

        // Theme settings
        SettingsPanel {
            val ctx = LocalContext.current
            val themeModeState = LocalThemeMode.current
            val themeMode = themeModeState.intValue
            Text(
                stringResource(R.string.title_theme),
                color = MaterialTheme.appColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data class ThemeOption(val label: String, val value: Int)
                val options = listOf(
                    ThemeOption(stringResource(R.string.theme_follow_system), 0),
                    ThemeOption(stringResource(R.string.theme_light), 1),
                    ThemeOption(stringResource(R.string.theme_dark), 2)
                )
                options.forEach { opt ->
                    FilterChip(
                        selected = themeMode == opt.value,
                        onClick = {
                            themeModeState.intValue = opt.value
                            ctx.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
                                .edit().putInt("theme_mode", opt.value).apply()
                        },
                        label = {
                            Text(
                                opt.label,
                                fontWeight = if (themeMode == opt.value) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.appColors.accentLight,
                            selectedLabelColor = MaterialTheme.appColors.accent
                        )
                    )
                }
            }
        }

        // About (clickable)
        SettingsPanel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAbout?.invoke() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.about_title),
                    color = MaterialTheme.appColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    ">",
                    color = MaterialTheme.appColors.textTertiary,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Language settings (always shown)
        SettingsPanel {
            Text(
                stringResource(R.string.title_language),
                color = MaterialTheme.appColors.textPrimary,
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
                        focusedTextColor = MaterialTheme.appColors.textPrimary,
                        unfocusedTextColor = MaterialTheme.appColors.textPrimary,
                        focusedLabelColor = accent,
                        unfocusedLabelColor = MaterialTheme.appColors.textSecondary,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = MaterialTheme.appColors.border,
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
            .background(MaterialTheme.appColors.surface)
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

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    accent: Color = MaterialTheme.appColors.accent
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
                    color = MaterialTheme.appColors.textPrimary
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.appColors.textPrimary,
            unfocusedTextColor = MaterialTheme.appColors.textPrimary,
            focusedLabelColor = accent,
            unfocusedLabelColor = MaterialTheme.appColors.textSecondary,
            focusedBorderColor = accent,
            unfocusedBorderColor = MaterialTheme.appColors.border,
            focusedPlaceholderColor = MaterialTheme.appColors.textTertiary,
            unfocusedPlaceholderColor = MaterialTheme.appColors.textTertiary,
            cursorColor = accent
        )
    )
}


