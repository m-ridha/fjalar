package com.muhammadridha.fjalar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muhammadridha.fjalar.ui.FjalarApp
import com.muhammadridha.fjalar.ui.FjalarViewModel
import com.muhammadridha.fjalar.ui.accessibility.isReduceTransparencyActive
import org.koin.compose.viewmodel.koinViewModel

/** Production composition requires the platform's initialized Koin application. */
@Composable
fun App(
    viewModel: FjalarViewModel = koinViewModel(),
    reducedTransparency: Boolean = isReduceTransparencyActive(),
    onRequestPermission: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    FjalarApp(
        viewModel = viewModel,
        modifier = modifier,
        reducedTransparency = reducedTransparency,
        onRequestPermission = onRequestPermission,
    )
}
