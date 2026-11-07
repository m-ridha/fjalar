package com.muhammadridha.fjalar.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Extra platform occlusions (such as a separating Android hinge), in window pixels. */
val LocalControlOcclusionInsets = staticCompositionLocalOf { WindowInsets(0, 0, 0, 0) }

/**
 * Layout helper providing consistent and testable safe area insets modifiers
 * across Fjalar UI components.
 *
 * Ensures map draws 100% edge-to-edge behind system bars while interactive controls
 * (Top HUD, bottom sheets, navigation dock, modal dialogs) are strictly padded
 * away from status bar cutouts, clock/battery readouts, and gesture/3-button navigation bars.
 */
object WindowInsetsLayoutHelper {

    @Composable
    private fun controlSafeInsets(): WindowInsets =
        WindowInsets.safeDrawing.union(LocalControlOcclusionInsets.current)

    val DefaultHorizontalPadding: Dp = 16.dp
    val DefaultVerticalPadding: Dp = 8.dp

    /**
     * Modifier for Top HUD elements (e.g. FloatingHudBar).
     * Applies top and side safe areas, including landscape cutouts and reserved regions.
     */
    @Composable
    fun hudTopPaddingModifier(
        modifier: Modifier = Modifier,
        horizontal: Dp = DefaultHorizontalPadding,
        vertical: Dp = DefaultVerticalPadding,
    ): Modifier = modifier
        .windowInsetsPadding(controlSafeInsets().only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .padding(horizontal = horizontal, vertical = vertical)

    /**
     * Modifier for bottom surfaces (e.g. SearchPlacesSheet, bottom action bars).
     * Applies bottom and side safe areas to avoid system navigation and cutouts.
     */
    @Composable
    fun navigationBottomPaddingModifier(modifier: Modifier = Modifier): Modifier =
        modifier.windowInsetsPadding(controlSafeInsets().only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))

    @Composable
    fun horizontalSafeDrawingPaddingModifier(modifier: Modifier = Modifier): Modifier =
        modifier.windowInsetsPadding(controlSafeInsets().only(WindowInsetsSides.Horizontal))

    /**
     * Modifier applying safe drawing insets around all edges.
     */
    @Composable
    fun safeDrawingPaddingModifier(modifier: Modifier = Modifier): Modifier =
        modifier.windowInsetsPadding(controlSafeInsets())

    /**
     * Modifier for bottom sheets and modal dialogs.
     */
    @Composable
    fun sheetBottomPaddingModifier(modifier: Modifier = Modifier): Modifier =
        modifier.windowInsetsPadding(controlSafeInsets().only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
}
