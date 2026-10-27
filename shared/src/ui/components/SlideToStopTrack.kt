package com.muhammadridha.fjalar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.fjalarString
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface
import kotlin.math.roundToInt

const val SLIDE_TO_STOP_THRESHOLD = 0.80f

fun isSlideToStopSatisfied(
    dragFraction: Float,
    threshold: Float = SLIDE_TO_STOP_THRESHOLD,
): Boolean = dragFraction >= threshold

class SlideToStopState(
    val threshold: Float = SLIDE_TO_STOP_THRESHOLD,
) {
    var dragFraction by mutableStateOf(0f)
        internal set

    private var completionEmitted = false

    fun resetAfterFailure() {
        completionEmitted = false
        dragFraction = 0f
    }

    fun cancelDrag() {
        if (!completionEmitted) dragFraction = 0f
    }

    val isSatisfied: Boolean
        get() = isSlideToStopSatisfied(dragFraction, threshold)

    fun dragBy(deltaPx: Float, maxPx: Float) {
        if (!completionEmitted) updateDrag(dragFraction * maxPx + deltaPx, maxPx)
    }

    fun updateDrag(rawPx: Float, maxPx: Float) {
        if (maxPx <= 0f) {
            dragFraction = 0f
            return
        }
        dragFraction = (rawPx / maxPx).coerceIn(0f, 1f)
    }

    fun checkThresholdAndResolve(onSuccess: () -> Unit): Boolean {
        if (completionEmitted || !isSatisfied) return false
        completionEmitted = true
        onSuccess()
        return true
    }
}

@Composable
fun SlideToStopTrack(
    onStopAlarm: () -> Unit,
    modifier: Modifier = Modifier,
    state: SlideToStopState = remember { SlideToStopState() },
    enabled: Boolean = true,
) {
    val colors = LiquidGlassTheme.colors
    val density = LocalDensity.current
    val currentOnStop by rememberUpdatedState(onStopAlarm)
    val currentEnabled by rememberUpdatedState(enabled)

    val trackHeight = 64.dp
    val thumbSize = 52.dp
    val trackPadding = 6.dp

    val label = fjalarString(FjalarStringKey.RINGING_SLIDE_TO_STOP)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .glassSurface(level = GlassLevel.MODAL, shape = RoundedCornerShape(32.dp))
            .padding(trackPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val thumbSizePx = with(density) { thumbSize.toPx() }
        val maxDragPx = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)

        val currentDragFraction = state.dragFraction
        // Center Instruction Label with fade-out during drag
        val textAlpha = (1f - currentDragFraction * 1.4f).coerceIn(0f, 1f)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = thumbSize + 8.dp)
                    .alpha(textAlpha),
            )
        }

        // Draggable Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(x = (state.dragFraction * maxDragPx).roundToInt(), y = 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(colors.danger)
                .pointerInput(maxDragPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Resolve synchronously: release must see every delta, even before a frame.
                            if (currentEnabled) state.checkThresholdAndResolve { currentOnStop() }
                            state.cancelDrag()
                        },
                        onDragCancel = { state.cancelDrag() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (currentEnabled) {
                                state.dragBy(dragAmount, maxDragPx)
                            }
                        },
                    )
                }
                .semantics {
                    contentDescription = label
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "➔",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
