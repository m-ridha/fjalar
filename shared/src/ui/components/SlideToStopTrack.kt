package com.muhammadridha.fjalar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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

    val isSatisfied: Boolean
        get() = isSlideToStopSatisfied(dragFraction, threshold)

    fun updateDrag(rawPx: Float, maxPx: Float) {
        if (maxPx <= 0f) {
            dragFraction = 0f
            return
        }
        dragFraction = (rawPx / maxPx).coerceIn(0f, 1f)
    }

    fun checkThresholdAndResolve(onSuccess: () -> Unit): Boolean {
        val satisfied = isSatisfied
        if (satisfied) {
            onSuccess()
        }
        return satisfied
    }
}

@Composable
fun SlideToStopTrack(
    onStopAlarm: () -> Unit,
    modifier: Modifier = Modifier,
    state: SlideToStopState = remember { SlideToStopState() },
) {
    val colors = LiquidGlassTheme.colors
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val trackHeight = 64.dp
    val thumbSize = 52.dp
    val trackPadding = 6.dp

    val offsetAnimatable = remember { Animatable(0f) }
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

        val currentDragFraction = if (maxDragPx > 0f) {
            (offsetAnimatable.value / maxDragPx).coerceIn(0f, 1f)
        } else {
            0f
        }
        state.dragFraction = currentDragFraction

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
                .offset { IntOffset(x = offsetAnimatable.value.roundToInt(), y = 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(colors.danger)
                .pointerInput(maxDragPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (state.checkThresholdAndResolve(onStopAlarm)) {
                                    offsetAnimatable.animateTo(maxDragPx)
                                } else {
                                    offsetAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                    state.dragFraction = 0f
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetAnimatable.animateTo(0f)
                                state.dragFraction = 0f
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val target = (offsetAnimatable.value + dragAmount).coerceIn(0f, maxDragPx)
                                offsetAnimatable.snapTo(target)
                                state.updateDrag(target, maxDragPx)
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
