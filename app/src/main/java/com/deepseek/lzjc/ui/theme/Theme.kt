package com.deepseek.lzjc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentLight: Color,
    val border: Color,
    val error: Color,
    val success: Color,
    val divider: Color
)

val LightColors = AppColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F8FA),
    card = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF666666),
    textTertiary = Color(0xFF999999),
    accent = Color(0xFF4D6BFE),
    accentLight = Color(0xFF4D6BFE).copy(alpha = 0.12f),
    border = Color(0xFFE5E5EA),
    error = Color(0xFFFF6B6B),
    success = Color(0xFF4CAF50),
    divider = Color(0xFFF0F0F0)
)

val DarkColors = AppColors(
    background = Color(0xFF0D0D0D),
    surface = Color(0xFF1A1A1A),
    card = Color(0xFF242424),
    textPrimary = Color(0xFFE8E8E8),
    textSecondary = Color(0xFFAAAAAA),
    textTertiary = Color(0xFF999999),
    accent = Color(0xFF6B8AFF),
    accentLight = Color(0xFF6B8AFF).copy(alpha = 0.15f),
    border = Color(0xFF333333),
    error = Color(0xFFFF6B6B),
    success = Color(0xFF66BB6A),
    divider = Color(0xFF2A2A2A)
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6B8AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A3A5C),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFF5AC8FA),
    surface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF242424),
    onSurface = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFFAAAAAA),
    error = Color(0xFFFF6B6B),
    background = Color(0xFF0D0D0D)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4D6BFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF0EA5E9),
    surface = Color(0xFFF7F8FA),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF666666),
    error = Color(0xFFFF6B6B),
    background = Color(0xFFFFFFFF)
)

/**
 * Theme mode: 0 = follow system, 1 = always light, 2 = always dark
 */
const val THEME_FOLLOW_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

@Composable
fun DeepSeekBalanceTheme(
    themeMode: Int = THEME_FOLLOW_SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        THEME_DARK -> true
        THEME_LIGHT -> false
        else -> systemDark
    }

    val appColors = if (darkTheme) DarkColors else LightColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}

/** Quick access to app colors from any Composable */
val MaterialTheme.appColors: AppColors
    @Composable @ReadOnlyComposable
    get() = LocalAppColors.current
