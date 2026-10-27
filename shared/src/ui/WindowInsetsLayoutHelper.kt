package com.muhammadridha.fjalar.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout helper providing consistent and testable safe area insets modifiers
 * across Fjalar UI components.
 *
 * Ensures map draws 100% edge-to-edge behind system bars while interactive controls
 * (Top HUD, bottom sheets, navigation dock, modal dialogs) are strictly padded
 * away from status bar cutouts, clock/battery readouts, and gesture/3-button navigation bars.
 */
object WindowInsetsLayoutHelper {

    val DefaultHorizontalPadding: Dp = 16.dp
    val DefaultVerticalPadding: Dp = 8.dp

    /**
     * Modifier for Top HUD elements (e.g. FloatingHudBar).
     * Applies statusBarsPadding and standard horizontal/vertical margins.
     */
    fun hudTopPaddingModifier(
        modifier: Modifier = Modifier,
        horizontal: Dp = DefaultHorizontalPadding,
        vertical: Dp = DefaultVerticalPadding,
    ): Modifier = modifier
        .statusBarsPadding()
        .padding(horizontal = horizontal, vertical = vertical)

    /**
     * Modifier for bottom surfaces (e.g. GlassBottomSheet, ConfigureSheet, SearchPlacesSheet).
     * Applies navigationBarsPadding to prevent overlap with gesture pills or 3-button navigation.
     */
    fun navigationBottomPaddingModifier(modifier: Modifier = Modifier): Modifier =
        modifier.navigationBarsPadding()

    /**
     * Modifier applying safe drawing insets around all edges.
     */
    fun safeDrawingPaddingModifier(modifier: Modifier = Modifier): Modifier =
        modifier.safeDrawingPadding()

    /**
     * Modifier for bottom sheets and modal dialogs.
     */
    fun sheetBottomPaddingModifier(modifier: Modifier = Modifier): Modifier =
        modifier.navigationBarsPadding()
}
