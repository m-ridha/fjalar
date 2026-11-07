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
    val adapter = remember(context) { AndroidAccessibilityAdapter(context.applicationContext) }
    val active by adapter.reduceTransparencyFlow.collectAsState(initial = adapter.isReduceTransparencyActive)
    return active
}
