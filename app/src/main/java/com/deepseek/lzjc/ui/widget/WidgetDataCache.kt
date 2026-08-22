package com.deepseek.lzjc.ui.widget

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetDataCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ===== Per-platform save =====

    fun saveDeepSeekData(
        balance: String,
        dailyCost: String,
        monthlyCost: String,
        dailyRequests: Long,
        monthlyRequests: Long
    ) {
        prefs.edit()
            .putString(KEY_DS_BALANCE, balance)
            .putString(KEY_DS_DAILY, dailyCost)
            .putString(KEY_DS_MONTHLY, monthlyCost)
            .putLong(KEY_DS_DAILY_REQ, dailyRequests)
            .putLong(KEY_DS_MONTHLY_REQ, monthlyRequests)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }

    fun saveMiMoData(
        balance: String,
        dailyCost: String,
        monthlyCost: String,
        dailyRequests: Long,
        monthlyRequests: Long
    ) {
        prefs.edit()
            .putString(KEY_MIMO_BALANCE, balance)
            .putString(KEY_MIMO_DAILY, dailyCost)
            .putString(KEY_MIMO_MONTHLY, monthlyCost)
            .putLong(KEY_MIMO_DAILY_REQ, dailyRequests)
            .putLong(KEY_MIMO_MONTHLY_REQ, monthlyRequests)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }

    /**
     * Get widget display data for the currently selected platform.
     * Reads the platform preference from the main app's SharedPreferences.
     */
    fun getWidgetData(): WidgetDisplayData {
        val mainPrefs = context.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
        val platform = mainPrefs.getString("current_platform", "deepseek") ?: "deepseek"

        return if (platform == "mimo") {
            WidgetDisplayData(
                platform = "MiMo",
                balance = prefs.getString(KEY_MIMO_BALANCE, "--") ?: "--",
                dailyCost = prefs.getString(KEY_MIMO_DAILY, "0.0000") ?: "0.0000",
                monthlyCost = prefs.getString(KEY_MIMO_MONTHLY, "0.00") ?: "0.00",
                dailyRequests = prefs.getLong(KEY_MIMO_DAILY_REQ, 0),
                monthlyRequests = prefs.getLong(KEY_MIMO_MONTHLY_REQ, 0),
                lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
            )
        } else {
            WidgetDisplayData(
                platform = "DeepSeek",
                balance = prefs.getString(KEY_DS_BALANCE, "--") ?: "--",
                dailyCost = prefs.getString(KEY_DS_DAILY, "0.00") ?: "0.00",
                monthlyCost = prefs.getString(KEY_DS_MONTHLY, "0.00") ?: "0.00",
                dailyRequests = prefs.getLong(KEY_DS_DAILY_REQ, 0),
                monthlyRequests = prefs.getLong(KEY_DS_MONTHLY_REQ, 0),
                lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "widget_balance_cache"

        // DeepSeek keys
        private const val KEY_DS_BALANCE = "ds_balance"
        private const val KEY_DS_DAILY = "ds_daily"
        private const val KEY_DS_MONTHLY = "ds_monthly"
        private const val KEY_DS_DAILY_REQ = "ds_daily_req"
        private const val KEY_DS_MONTHLY_REQ = "ds_monthly_req"

        // MiMo keys
        private const val KEY_MIMO_BALANCE = "mimo_balance"
        private const val KEY_MIMO_DAILY = "mimo_daily"
        private const val KEY_MIMO_MONTHLY = "mimo_monthly"
        private const val KEY_MIMO_DAILY_REQ = "mimo_daily_req"
        private const val KEY_MIMO_MONTHLY_REQ = "mimo_monthly_req"

        private const val KEY_LAST_UPDATE = "last_update"
    }
}

data class WidgetDisplayData(
    val platform: String,
    val balance: String,
    val dailyCost: String,
    val monthlyCost: String,
    val dailyRequests: Long,
    val monthlyRequests: Long,
    val lastUpdate: Long
)
