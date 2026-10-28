package com.muhammadridha.fjalar.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.ui.WindowInsetsLayoutHelper
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.fjalarString
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface

@Composable
fun RingingAlertOverlay(
    alarm: LocationAlarm,
    onDismiss: (id: String) -> Unit,
    onSnooze: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    distanceMeters: Int? = null,
    isOperationPending: Boolean = false,
    hasOperationFailure: Boolean = false,
    snoozeDeliveryUnavailable: Boolean = false,
    operationFailureId: Long = 0L,
) {
    val slideState = remember(alarm.id) { SlideToStopState() }
    LaunchedEffect(operationFailureId, hasOperationFailure, isOperationPending) {
        if (hasOperationFailure && !isOperationPending) slideState.resetAfterFailure()
    }
    val colors = LiquidGlassTheme.colors
    val transition = rememberInfiniteTransition()

    val rawProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )
    val pulse1 = rawProgress
    val pulse2 = (rawProgress + 0.333f) % 1f
    val pulse3 = (rawProgress + 0.667f) % 1f

    val distanceText = ringingDistanceText(distanceMeters, LiquidGlassTheme.locale)

    Box(
        modifier = modifier
            .fillMaxSize()
            .glassSurface(level = GlassLevel.MODAL, shape = RoundedCornerShape(0.dp)),
    ) {
        Column(
            modifier = WindowInsetsLayoutHelper.safeDrawingPaddingModifier(Modifier.fillMaxSize())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Main Alert Content & Concentric Radar Pulse
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            ) {
                // Radar Pulse Area
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    listOf(pulse1, pulse2, pulse3).forEach { progress ->
                        val scale = 0.2f + (progress * 0.8f)
                        val alpha = (1f - progress).coerceIn(0f, 0.6f)
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .scale(scale)
                                .alpha(alpha)
                                .clip(CircleShape)
                                .background(colors.armed.copy(alpha = 0.35f)),
                        )
                    }

                    // Center Pin Glow
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(colors.armed.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.armed),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "📍",
                                fontSize = 24.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // In-Zone Distance Pill Badge
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.armed,
                    modifier = Modifier
                        .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Wake-Up Headline
                Text(
                    text = fjalarString(FjalarStringKey.RINGING_HEADLINE, alarm.label),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isOperationPending) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                if (hasOperationFailure) {
                    Text(
                        text = fjalarString(if (snoozeDeliveryUnavailable) FjalarStringKey.RINGING_SNOOZE_NOTIFICATIONS_REQUIRED else FjalarStringKey.RINGING_OPERATION_FAILED),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                // Snooze Button
                Box(
                    modifier = Modifier
                        .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                        .clickable(enabled = !isOperationPending, role = Role.Button) { onSnooze(alarm.id) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = fjalarString(FjalarStringKey.RINGING_SNOOZE_ACTION),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = colors.primary,
                    )
                }
            }

            // Bottom "Slide to Stop Alarm" track (sleepy-eyes dismissal safety)
            SlideToStopTrack(
                onStopAlarm = { onDismiss(alarm.id) },
                state = slideState,
                enabled = !isOperationPending,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}
