package com.muhammadridha.fjalar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.muhammadridha.fjalar.ui.LocalControlOcclusionInsets

@Composable
internal fun FoldAwareControls(activity: ComponentActivity, content: @Composable () -> Unit) {
    val layoutInfo by remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
    }.collectAsState(initial = WindowLayoutInfo(emptyList()))

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fold = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull {
            it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL
        }
        val insets = if (fold == null) {
            FoldControlInsets()
        } else {
            foldControlInsets(
                width = constraints.maxWidth,
                height = constraints.maxHeight,
                foldLeft = fold.bounds.left,
                foldTop = fold.bounds.top,
                foldRight = fold.bounds.right,
                foldBottom = fold.bounds.bottom,
                isVertical = fold.orientation == FoldingFeature.Orientation.VERTICAL,
                clearance = with(density) { 16.dp.roundToPx() },
            )
        }
        CompositionLocalProvider(
            LocalControlOcclusionInsets provides WindowInsets(insets.left, insets.top, insets.right, insets.bottom),
            content = content,
        )
    }
}
