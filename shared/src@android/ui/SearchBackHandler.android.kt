package com.muhammadridha.fjalar.ui

import androidx.compose.runtime.Composable

@Composable
actual fun SearchBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
