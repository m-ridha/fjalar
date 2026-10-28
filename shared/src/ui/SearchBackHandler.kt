package com.muhammadridha.fjalar.ui

import androidx.compose.runtime.Composable

@Composable
expect fun SearchBackHandler(enabled: Boolean, onBack: () -> Unit)
