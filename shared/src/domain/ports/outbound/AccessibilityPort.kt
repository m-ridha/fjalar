package com.muhammadridha.fjalar.domain.ports.outbound

import kotlinx.coroutines.flow.Flow

/**
 * Outbound port for detecting and observing platform-level accessibility visual preferences.
 *
 * Implements detection and live observation of reduce-transparency and high-contrast
 * accessibility features to satisfy WCAG AAA visual contrast invariants.
 */
interface AccessibilityPort {
    /** Immediate snapshot of whether reduce-transparency or high-contrast is active. */
    val isReduceTransparencyActive: Boolean

    /** Flow emitting updates when platform accessibility display settings change. */
    val reduceTransparencyFlow: Flow<Boolean>
}
