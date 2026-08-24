package com.deepseek.lzjc

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.deepseek.lzjc.util.applyLocale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.deepseek.lzjc.ui.screens.MainTabScreen
import com.deepseek.lzjc.ui.screens.SplashScreen
import com.deepseek.lzjc.ui.theme.DeepSeekBalanceTheme
import com.deepseek.lzjc.ui.theme.THEME_FOLLOW_SYSTEM
import com.deepseek.lzjc.ui.theme.appColors
import dagger.hilt.android.AndroidEntryPoint

/** Shared mutable theme mode state — Settings writes, MainActivity reads */
val LocalThemeMode = compositionLocalOf { mutableIntStateOf(THEME_FOLLOW_SYSTEM) }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context?) {
        val code = newBase?.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
            ?.getString("app_language", "zh") ?: "zh"
        super.attachBaseContext(applyLocale(newBase ?: return, code))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
            val themeModeState = remember { mutableIntStateOf(prefs.getInt("theme_mode", THEME_FOLLOW_SYSTEM)) }

            CompositionLocalProvider(LocalThemeMode provides themeModeState) {
                DeepSeekBalanceTheme(themeMode = themeModeState.intValue) {
                    var showSplash by remember { mutableIntStateOf(1) }
                    val colors = MaterialTheme.appColors

                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = colors.background
                        ) {
                            MainTabScreen()
                        }

                        if (showSplash == 1) {
                            SplashScreen(
                                onSplashFinished = { showSplash = 0 }
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun DeepSeekNavHost() {
    MainTabScreen()
}
