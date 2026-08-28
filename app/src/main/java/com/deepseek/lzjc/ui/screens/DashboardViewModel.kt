package com.deepseek.lzjc.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.Platform
import com.deepseek.lzjc.data.ark.ArkPlanOverview
import com.deepseek.lzjc.data.db.DailyModelBreakdown
import com.deepseek.lzjc.data.db.DailyUsageSummary
import com.deepseek.lzjc.data.db.ModelCostSummary
import com.deepseek.lzjc.data.glm.GlmPlanOverview
import com.deepseek.lzjc.data.mimo.MiMoUsageData
import com.deepseek.lzjc.data.minimax.MiniMaxPlanOverview
import com.deepseek.lzjc.data.repository.ArkRepository
import com.deepseek.lzjc.data.repository.GlmRepository
import com.deepseek.lzjc.data.repository.MiMoRepository
import com.deepseek.lzjc.data.repository.MiniMaxRepository
import com.deepseek.lzjc.data.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class DashboardState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val currentPlatform: Platform = Platform.DEEPSEEK,
    // DeepSeek fields
    val totalBalance: String = "0.00",
    val grantedBalance: String = "0.00",
    val toppedUpBalance: String = "0.00",
    val dailyCost: String = "0.00",
    val monthlyCost: String = "0.00",
    val flashTokens: Long = 0,
    val proTokens: Long = 0,
    val dailyData: List<DailyUsageSummary> = emptyList(),
    val hasApiKey: Boolean = false,
    val hasUserToken: Boolean = false,
    val dailyRequests: Long = 0,
    val monthlyRequests: Long = 0,
    val modelBreakdowns: List<DailyModelBreakdown> = emptyList(),
    // MiMo fields
    val mimoLoggedIn: Boolean = false,
    val mimoData: MiMoUsageData? = null,
    val mimoDailyData: List<DailyUsageSummary> = emptyList(),
    val mimoDailyRequests: Long = 0,
    val mimoMonthlyRequests: Long = 0,
    val mimoModelBreakdowns: List<DailyModelBreakdown> = emptyList(),
    // Ark fields
    val arkHasCredentials: Boolean = false,
    val arkPlan: ArkPlanOverview? = null,
    val arkDailyData: List<DailyUsageSummary> = emptyList(),
    val arkModelCosts: List<ModelCostSummary> = emptyList(),
    val arkModelBreakdowns: List<DailyModelBreakdown> = emptyList(),
    val arkTodayUsage: Long = 0,
    val arkMonthlyUsage: Long = 0,
    // GLM fields
    val glmHasApiKey: Boolean = false,
    val glmPlan: GlmPlanOverview? = null,
    // MiniMax fields
    val minimaxHasApiKey: Boolean = false,
    val minimaxPlan: MiniMaxPlanOverview? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val application: Application,
    private val repository: UsageRepository,
    private val mimoRepository: MiMoRepository,
    private val arkRepository: ArkRepository,
    private val glmRepository: GlmRepository,
    private val miniMaxRepository: MiniMaxRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val prefs = application.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    init {
        viewModelScope.launch {
            // 初始化所有仓库
            miniMaxRepository.initApiKey()

            // Check credential availability
            val mimoLoggedIn = mimoRepository.isLoggedIn()
            val hasDeepSeekKey = repository.apiKey.first().isNotBlank()
            val hasArkKey = arkRepository.accessKeyId.first().isNotBlank()
            val hasGlmKey = glmRepository.apiKey.first().isNotBlank()
            val hasMiniMaxKey = miniMaxRepository.apiKey.first().isNotBlank()

            // Auto-select platform
            val isFirstLaunch = !prefs.getBoolean(Platform.PREF_FIRST_LAUNCH, false)
            val platform = if (isFirstLaunch) {
                when {
                    mimoLoggedIn -> Platform.MIMO
                    hasDeepSeekKey -> Platform.DEEPSEEK
                    hasArkKey -> Platform.ARK
                    hasGlmKey -> Platform.GLM
                    hasMiniMaxKey -> Platform.MINIMAX
                    else -> Platform.DEEPSEEK
                }.also {
                    prefs.edit()
                        .putString(Platform.PREF_KEY, it.key)
                        .putBoolean(Platform.PREF_FIRST_LAUNCH, true)
                        .apply()
                }
            } else {
                val saved = Platform.fromKey(prefs.getString(Platform.PREF_KEY, "deepseek") ?: "deepseek")
                when {
                    saved == Platform.DEEPSEEK && hasDeepSeekKey -> saved
                    saved == Platform.MIMO && mimoLoggedIn -> saved
                    saved == Platform.ARK && hasArkKey -> saved
                    saved == Platform.GLM && hasGlmKey -> saved
                    saved == Platform.MINIMAX && hasMiniMaxKey -> saved
                    mimoLoggedIn -> Platform.MIMO
                    hasDeepSeekKey -> Platform.DEEPSEEK
                    hasArkKey -> Platform.ARK
                    hasGlmKey -> Platform.GLM
                    hasMiniMaxKey -> Platform.MINIMAX
                    else -> saved
                }
            }

            _state.update { it.copy(
                currentPlatform = platform,
                mimoLoggedIn = mimoLoggedIn,
                arkHasCredentials = hasArkKey,
                glmHasApiKey = hasGlmKey,
                minimaxHasApiKey = hasMiniMaxKey
            ) }

            // Auto-refresh based on platform
            when (platform) {
                Platform.DEEPSEEK -> {
                    if (hasDeepSeekKey) refreshDeepSeek()
                    else _state.update { it.copy(isLoading = false) }
                }
                Platform.MIMO -> {
                    if (mimoLoggedIn) refreshMiMo()
                    else _state.update { it.copy(isLoading = false) }
                }
                Platform.ARK -> {
                    if (hasArkKey) refreshArk()
                    else _state.update { it.copy(isLoading = false) }
                }
                Platform.GLM -> {
                    val hasGlmKey = glmRepository.apiKey.first().isNotBlank()
                    _state.update { it.copy(glmHasApiKey = hasGlmKey) }
                    if (hasGlmKey) refreshGlm()
                    else _state.update { it.copy(isLoading = false) }
                }
                Platform.MINIMAX -> {
                    if (hasMiniMaxKey) refreshMiniMax()
                    else _state.update { it.copy(isLoading = false) }
                }
            }

            // Listen for DeepSeek credential changes
            combine(repository.apiKey, repository.userToken) { key, token -> key to token }
                .distinctUntilChanged()
                .collect { (key, token) ->
                    _state.update {
                        it.copy(hasApiKey = key.isNotBlank(), hasUserToken = token.isNotBlank())
                    }
                    if (_state.value.currentPlatform == Platform.DEEPSEEK && key.isNotBlank()) {
                        yield()
                        refreshDeepSeek()
                    }
                }
        }
    }

    fun switchPlatform(platform: Platform) {
        prefs.edit().putString(Platform.PREF_KEY, platform.key).apply()
        _state.update { it.copy(currentPlatform = platform, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (platform) {
                Platform.DEEPSEEK -> {
                    val key = repository.apiKey.first()
                    if (key.isNotBlank()) refreshDeepSeek()
                    else _state.update { it.copy(isLoading = false) }
                }
                Platform.MIMO -> {
                    val loggedIn = mimoRepository.isLoggedIn()
                    _state.update { it.copy(mimoLoggedIn = loggedIn) }
                    if (loggedIn) refreshMiMo()
                    else _state.update { it.copy(isLoading = false) }
                }
                Platform.ARK -> {
                    val hasKey = arkRepository.accessKeyId.first().isNotBlank()
                    _state.update { it.copy(arkHasCredentials = hasKey) }
                    if (hasKey) refreshArk()
                    else _state.update { it.copy(isLoading = false) }
                }
                Platform.GLM -> {
                    val hasKey = glmRepository.apiKey.first().isNotBlank()
                    _state.update { it.copy(glmHasApiKey = hasKey) }
                    if (hasKey) refreshGlm()
                    else _state.update { it.copy(isLoading = false) }
                }
                Platform.MINIMAX -> {
                    val hasKey = miniMaxRepository.apiKey.first().isNotBlank()
                    _state.update { it.copy(minimaxHasApiKey = hasKey) }
                    if (hasKey) refreshMiniMax()
                    else _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun onMiMoLoginSuccess() {
        viewModelScope.launch {
            val success = mimoRepository.saveCookiesFromWebView()
            if (success) {
                _state.update { it.copy(mimoLoggedIn = true, errorMessage = null) }
                refreshMiMo()
            } else {
                _state.update { it.copy(errorMessage = "登录失败，无法获取认证信息") }
            }
        }
    }

    fun miMoLogout() {
        viewModelScope.launch {
            mimoRepository.logout()
            _state.update {
                it.copy(
                    mimoLoggedIn = false,
                    mimoData = null,
                    mimoDailyData = emptyList()
                )
            }
        }
    }

    fun refresh() {
        when (_state.value.currentPlatform) {
            Platform.DEEPSEEK -> refreshDeepSeek()
            Platform.MIMO -> refreshMiMo()
            Platform.ARK -> refreshArk()
            Platform.GLM -> refreshGlm()
            Platform.MINIMAX -> refreshMiniMax()
        }
    }

    private fun refreshDeepSeek() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }

            runCatching {
                var totalBalance = _state.value.totalBalance
                var grantedBalance = _state.value.grantedBalance
                var toppedUpBalance = _state.value.toppedUpBalance
                var errorMsg: String? = null

                repository.refreshAndRecord()
                    .onSuccess { response ->
                        val info = response.balanceInfos.firstOrNull()
                        totalBalance = info?.totalBalance ?: "0.00"
                        grantedBalance = info?.grantedBalance ?: "0.00"
                        toppedUpBalance = info?.toppedUpBalance ?: "0.00"
                    }
                    .onFailure { e ->
                        errorMsg = e.message ?: application.getString(R.string.network_error)
                    }

                val today = dateFormat.format(System.currentTimeMillis())
                val month = monthFormat.format(System.currentTimeMillis())
                val dailyCost = repository.getDailyCost(today)
                val monthlyCost = repository.getMonthlyCost(month)
                val flashTokens = repository.getMonthlyModelTokens(UsageRepository.MODEL_FLASH, month)
                val proTokens = repository.getMonthlyModelTokens(UsageRepository.MODEL_PRO, month)
                val dailyRequests = repository.getDailyRequestCount(today)
                val monthlyRequests = repository.getMonthlyRequestCount(month)

                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -30)
                val fromDate = dateFormat.format(cal.time)
                val data = repository.getDailyUsageSince(fromDate).first()
                val modelBreakdowns = repository.getDailyModelBreakdowns(7)

                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = errorMsg,
                        totalBalance = totalBalance,
                        grantedBalance = grantedBalance,
                        toppedUpBalance = toppedUpBalance,
                        dailyCost = String.format("%.2f", dailyCost),
                        monthlyCost = String.format("%.2f", monthlyCost),
                        flashTokens = flashTokens,
                        proTokens = proTokens,
                        dailyData = data,
                        dailyRequests = dailyRequests,
                        monthlyRequests = monthlyRequests,
                        modelBreakdowns = modelBreakdowns
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: application.getString(R.string.network_error)
                    )
                }
            }
        }
    }

    private fun refreshMiMo() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }

            runCatching {
                val result = mimoRepository.refreshAndFetch()
                result.onSuccess { data ->
                    val today = dateFormat.format(System.currentTimeMillis())
                    val month = monthFormat.format(System.currentTimeMillis())

                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            mimoData = data,
                            mimoDailyData = mimoRepository.getDailyCostList(30),
                            mimoDailyRequests = mimoRepository.getDailyRequestCount(today),
                            mimoMonthlyRequests = mimoRepository.getMonthlyRequestCount(month),
                            mimoModelBreakdowns = mimoRepository.getDailyModelBreakdowns(7)
                        )
                    }
                }
                result.onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = e.message ?: application.getString(R.string.network_error)
                        )
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: application.getString(R.string.network_error)
                    )
                }
            }
        }
    }

    private fun refreshArk() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }

            runCatching {
                arkRepository.initCredentials()

                val planResult = arkRepository.getPlanOverview()
                val usageResult = arkRepository.fetchRecentUsage(30)

                var plan: ArkPlanOverview? = null
                var errorMsg: String? = null

                planResult.onSuccess { plan = it }
                planResult.onFailure { e -> errorMsg = e.message }

                usageResult.onFailure { e -> if (errorMsg == null) errorMsg = e.message }

                val dailyData = arkRepository.getDailyUsageList(30)
                val modelCosts = arkRepository.getModelUsageSummary(30)
                val modelBreakdowns = arkRepository.getDailyModelBreakdowns(7)
                val todayUsage = arkRepository.getTodayUsage()
                val monthlyUsage = arkRepository.getMonthlyUsage()

                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = errorMsg,
                        arkPlan = plan,
                        arkDailyData = dailyData,
                        arkModelCosts = modelCosts,
                        arkModelBreakdowns = modelBreakdowns,
                        arkTodayUsage = todayUsage,
                        arkMonthlyUsage = monthlyUsage
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: application.getString(R.string.network_error)
                    )
                }
            }
        }
    }

    /** 重新检查凭证状态（设置页保存后调用） */
    fun recheckCredentials() {
        viewModelScope.launch {
            val hasGlmKey = glmRepository.apiKey.first().isNotBlank()
            val hasArkKey = arkRepository.accessKeyId.first().isNotBlank()
            val hasDeepSeekKey = repository.apiKey.first().isNotBlank()
            val hasMiniMaxKey = miniMaxRepository.apiKey.first().isNotBlank()
            val mimoLoggedIn = mimoRepository.isLoggedIn()
            _state.update {
                it.copy(
                    glmHasApiKey = hasGlmKey,
                    arkHasCredentials = hasArkKey,
                    hasApiKey = hasDeepSeekKey,
                    mimoLoggedIn = mimoLoggedIn,
                    minimaxHasApiKey = hasMiniMaxKey
                )
            }
            
            // 如果当前平台有凭证，自动刷新
            when (_state.value.currentPlatform) {
                Platform.MINIMAX -> {
                    if (hasMiniMaxKey && _state.value.minimaxPlan == null) {
                        refreshMiniMax()
                    }
                }
                Platform.GLM -> {
                    if (hasGlmKey && _state.value.glmPlan == null) {
                        refreshGlm()
                    }
                }
                else -> {}
            }
        }
    }

    private fun refreshGlm() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }

            runCatching {
                glmRepository.initApiKey()

                // 重新检查凭证
                val hasKey = glmRepository.apiKey.first().isNotBlank()
                _state.update { it.copy(glmHasApiKey = hasKey) }

                if (!hasKey) {
                    _state.update { it.copy(isRefreshing = false, isLoading = false) }
                    return@runCatching
                }

                val result = glmRepository.getPlanOverview()
                result.onSuccess { plan ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            glmPlan = plan
                        )
                    }
                }
                result.onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = e.message ?: application.getString(R.string.network_error)
                        )
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: application.getString(R.string.network_error)
                    )
                }
            }
        }
    }

    private fun refreshMiniMax() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }

            runCatching {
                miniMaxRepository.initApiKey()

                // 重新检查凭证
                val hasKey = miniMaxRepository.apiKey.first().isNotBlank()
                _state.update { it.copy(minimaxHasApiKey = hasKey) }

                if (!hasKey) {
                    _state.update { it.copy(isRefreshing = false, isLoading = false) }
                    return@runCatching
                }

                val result = miniMaxRepository.getPlanOverview()
                result.onSuccess { plan ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            minimaxPlan = plan
                        )
                    }
                }
                result.onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = e.message ?: application.getString(R.string.network_error)
                        )
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: application.getString(R.string.network_error)
                    )
                }
            }
        }
    }
}
