package com.muhammadridha.fjalar.ui.accessibility

import androidx.compose.runtime.Composable

/**
 * Returns whether platform-level reduce-transparency or high-contrast accessibility mode is active.
 * Automatically updates when system accessibility preferences change.
 */
@Composable
expect fun isReduceTransparencyActive(): Boolean
