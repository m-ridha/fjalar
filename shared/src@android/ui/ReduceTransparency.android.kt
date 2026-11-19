package com.muhammadridha.fjalar.ui.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.muhammadridha.fjalar.adapters.accessibility.AndroidAccessibilityAdapter

@Composable
actual fun isReduceTransparencyActive(): Boolean {
    val context = LocalContext.current
    val koin = currentKoinOrNull()
    val port = remember(context, koin) {
        resolveAccessibilityPort(koin) {
            AndroidAccessibilityAdapter(context.applicationContext ?: context)
        }
    }
    val active by port.reduceTransparencyFlow.collectAsState(initial = port.isReduceTransparencyActive)
    return active
}
