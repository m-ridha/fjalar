package com.muhammadridha.fjalar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme

/** Flat splash colors shared with the native Android and iOS launch screens (icon master canvases). */
internal val StartupSplashLightBackground = Color(0xFFFFFFFF)
internal val StartupSplashDarkBackground = Color(0xFF000000)

internal const val STARTUP_FADE_MILLIS: Int = 200

/** Reduced transparency also asks for as little motion as possible, so the fade is skipped. */
internal fun startupFadeMillis(reducedTransparency: Boolean): Int =
    if (reducedTransparency) 0 else STARTUP_FADE_MILLIS

/**
 * Solid placeholder that matches the native launch screen until the initial map viewport is known.
 * It uses the already-resolved theme, absorbs touches while visible, and fades out briefly
 * (instantly when the user has asked for reduced transparency).
 */
@Composable
internal fun StartupPlaceholder(
    visible: Boolean,
    reducedTransparency: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = if (LiquidGlassTheme.colors.isDark) {
        StartupSplashDarkBackground
    } else {
        StartupSplashLightBackground
    }
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = startupFadeMillis(reducedTransparency))),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .pointerInput(Unit) { detectTapGestures { } },
        )
    }
}
