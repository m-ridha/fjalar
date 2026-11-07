package com.muhammadridha.fjalar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.ui.accessibility.isReduceTransparencyActive

val LocalLiquidGlassColors = staticCompositionLocalOf { LightLiquidGlassColors }
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }
val LocalAppLocale = staticCompositionLocalOf { AppLocale.EN }
val LocalReducedTransparency = staticCompositionLocalOf { false }

fun resolveLiquidGlassColors(
    themeMode: ThemeMode,
    systemInDark: Boolean,
    reducedTransparency: Boolean = false,
): LiquidGlassColors {
    val base = when (themeMode) {
        ThemeMode.LIGHT -> LightLiquidGlassColors
        ThemeMode.DARK -> DarkLiquidGlassColors
        ThemeMode.SYSTEM -> if (systemInDark) DarkLiquidGlassColors else LightLiquidGlassColors
    }
    return if (reducedTransparency) base.withReducedTransparency() else base
}

object LiquidGlassTheme {
    val colors: LiquidGlassColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLiquidGlassColors.current

    val themeMode: ThemeMode
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeMode.current

    val locale: AppLocale
        @Composable
        @ReadOnlyComposable
        get() = LocalAppLocale.current

    val reducedTransparency: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReducedTransparency.current
}

@Composable
fun LiquidGlassTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    locale: AppLocale = AppLocale.EN,
    reducedTransparency: Boolean = isReduceTransparencyActive(),
    content: @Composable () -> Unit,
) {
    val systemInDark = isSystemInDarkTheme()
    val colors = resolveLiquidGlassColors(
        themeMode = themeMode,
        systemInDark = systemInDark,
        reducedTransparency = reducedTransparency,
    )
    val isDark = colors.isDark

    val materialColors = if (isDark) {
        darkColorScheme(
            primary = colors.primary,
            background = colors.canvas,
            surface = colors.canvas,
            onPrimary = Color.Black,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            background = colors.canvas,
            surface = colors.canvas,
            onPrimary = Color.White,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
        )
    }

    CompositionLocalProvider(
        LocalLiquidGlassColors provides colors,
        LocalThemeMode provides themeMode,
        LocalAppLocale provides locale,
        LocalReducedTransparency provides reducedTransparency,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content,
        )
    }
}
