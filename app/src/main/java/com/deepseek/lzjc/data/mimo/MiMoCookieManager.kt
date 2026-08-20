package com.deepseek.lzjc.data.mimo

import android.webkit.CookieManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Manages MiMo platform cookies for API authentication.
 * Adapted from MiMo-Tracker's CookieManager to use Hilt-injected DataStore.
 */
@Singleton
class MiMoCookieManager @Inject constructor(
    @Named("mimo") private val dataStore: DataStore<Preferences>
) {
    suspend fun saveCookies(cookies: String) {
        dataStore.edit { prefs ->
            prefs[COOKIE_KEY] = cookies
        }
    }

    suspend fun getCookies(): String? {
        return dataStore.data.map { prefs ->
            prefs[COOKIE_KEY]
        }.first()
    }

    suspend fun clearCookies() {
        dataStore.edit { prefs ->
            prefs.remove(COOKIE_KEY)
        }
    }

    /**
     * Extract cookies from Android WebView's CookieManager.
     * Returns the cookie string if all required cookies are found, null otherwise.
     */
    fun extractCookiesFromWebView(): String? = extractCookiesFromWebViewStatic()

    companion object {
        private val COOKIE_KEY = stringPreferencesKey("session_cookies")

        const val BASE_URL = "https://platform.xiaomimimo.com"
        const val LOGIN_URL = "$BASE_URL/api/v1/genLoginUrl?currentPath=/console/usage"

        private val requiredCookies = listOf(
            "api-platform_ph",
            "api-platform_serviceToken",
            "api-platform_slh",
            "userId"
        )

        /**
         * Static version for use in WebView callbacks where DI is unavailable.
         */
        fun extractCookiesFromWebViewStatic(): String? {
            val cookieManager = CookieManager.getInstance()
            val allCookies = cookieManager.getCookie(BASE_URL) ?: return null

            val cookieMap = mutableMapOf<String, String>()
            allCookies.split(";").forEach { cookie ->
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    cookieMap[parts[0].trim()] = parts[1].trim()
                }
            }

            val hasRequired = requiredCookies.all { cookieMap.containsKey(it) }
            if (!hasRequired) return null

            return requiredCookies.joinToString("; ") { "$it=${cookieMap[it]}" }
        }

        fun getLoginUrl(): String = LOGIN_URL
    }
}
