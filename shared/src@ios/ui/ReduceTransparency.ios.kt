package com.muhammadridha.fjalar.ui.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.muhammadridha.fjalar.adapters.accessibility.IosAccessibilityAdapter

@Composable
actual fun isReduceTransparencyActive(): Boolean {
    val adapter = remember { IosAccessibilityAdapter() }
    val active by adapter.reduceTransparencyFlow.collectAsState(initial = adapter.isReduceTransparencyActive)
    return active
}
