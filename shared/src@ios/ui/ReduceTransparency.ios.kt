package com.muhammadridha.fjalar.ui.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.muhammadridha.fjalar.adapters.accessibility.IosAccessibilityAdapter

@Composable
actual fun isReduceTransparencyActive(): Boolean {
    val koin = currentKoinOrNull()
    val port = remember(koin) {
        resolveAccessibilityPort(koin) { IosAccessibilityAdapter() }
    }
    val active by port.reduceTransparencyFlow.collectAsState(initial = port.isReduceTransparencyActive)
    return active
}
