package com.muhammadridha.fjalar.ui.accessibility

import androidx.compose.runtime.Composable
import com.muhammadridha.fjalar.domain.ports.outbound.AccessibilityPort
import org.koin.core.Koin
import org.koin.mp.KoinPlatformTools

/**
 * Returns whether platform-level reduce-transparency or high-contrast accessibility mode is active.
 * Automatically updates when system accessibility preferences change.
 */
@Composable
expect fun isReduceTransparencyActive(): Boolean

internal fun currentKoinOrNull(): Koin? =
    KoinPlatformTools.defaultContext().getOrNull()

/**
 * Resolves the active Koin [AccessibilityPort] singleton when Koin is initialized and has a
 * binding, or invokes [fallback] when Koin is absent (isolated Compose previews and tests).
 */
internal fun resolveAccessibilityPort(
    koin: Koin? = currentKoinOrNull(),
    fallback: () -> AccessibilityPort,
): AccessibilityPort {
    return koin?.getOrNull<AccessibilityPort>() ?: fallback()
}
