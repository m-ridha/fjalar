package com.muhammadridha.fjalar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muhammadridha.fjalar.ui.FjalarApp
import com.muhammadridha.fjalar.ui.FjalarViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Production composition requires the platform's initialized Koin application. */
@Composable
fun App(
    viewModel: FjalarViewModel = koinViewModel(),
    reducedTransparency: Boolean = false,
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
