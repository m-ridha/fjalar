package com.muhammadridha.fjalar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.ui.components.ArmedStatusPill
import com.muhammadridha.fjalar.ui.components.ConfigureAlarmSheet
import com.muhammadridha.fjalar.ui.components.FloatingHudBar
import com.muhammadridha.fjalar.ui.components.GlassBottomSheet
import com.muhammadridha.fjalar.ui.components.RingingAlertOverlay
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme

/**
 * Main Composable representing the root Fjalar application screen.
 * Integrates the map living canvas, Floating HUD, Armed Status Pill,
 * Glass Bottom Sheet, Configure Modal Sheet, and Ringing Alert Overlay.
 */
@Composable
fun FjalarApp(
    viewModel: FjalarViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LiquidGlassTheme(
        themeMode = uiState.themeMode,
        locale = uiState.appLocale,
    ) {
        val colors = LiquidGlassTheme.colors

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.canvas),
        ) {
            // Layer 1: Living Map Canvas Placeholder (100% viewport edge-to-edge)
            MapCanvasPlaceholder(
                hasArmedAlarms = uiState.alarms.any { it.state == AlarmState.ARMED_MONITORING },
                modifier = Modifier.fillMaxSize(),
            )

            // Layer 2: Top Floating HUD & Armed Status Pill
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FloatingHudBar(
                    onSearchClick = { /* Open search */ },
                    onToggleLocale = viewModel::onToggleLocale,
                    onToggleTheme = viewModel::onToggleTheme,
                    onRecenter = viewModel::onRecenterMap,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    locale = uiState.appLocale,
                    themeMode = uiState.themeMode,
                )

                val armedAlarm = uiState.alarms.firstOrNull { it.state == AlarmState.ARMED_MONITORING }
                if (armedAlarm != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ArmedStatusPill(
                        alarm = armedAlarm,
                        onClick = { viewModel.onOpenEditAlarm(armedAlarm) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            // Layer 3: Glass Bottom Sheet anchored to bottom
            GlassBottomSheet(
                alarms = uiState.alarms,
                onToggleAlarm = viewModel::onToggleAlarm,
                onEditAlarm = viewModel::onOpenEditAlarm,
                onNewAlarm = viewModel::onOpenNewAlarm,
                onSkipToday = viewModel::onSkipToday,
                locale = uiState.appLocale,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )

            // Layer 4: Configure Alarm Modal Sheet (animated visibility)
            AnimatedVisibility(
                visible = uiState.isConfigureSheetVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = viewModel::onDismissConfigureSheet),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ConfigureAlarmSheet(
                        initialAlarm = uiState.selectedAlarmForEdit,
                        onSave = viewModel::onSaveAlarm,
                        onCancel = viewModel::onDismissConfigureSheet,
                        locale = uiState.appLocale,
                        modifier = Modifier
                            .clickable(enabled = false) {}
                            .navigationBarsPadding(),
                    )
                }
            }

            // Layer 5: Full-screen Ringing Alert Overlay (Topmost critical safety layer)
            val ringingAlarm = uiState.activeRingingAlarm
            if (ringingAlarm != null) {
                RingingAlertOverlay(
                    alarm = ringingAlarm,
                    onDismiss = viewModel::onDismissRingingAlarm,
                    onSnooze = viewModel::onDismissRingingAlarm,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Living map canvas placeholder rendering a subtle coordinate grid, crosshairs,
 * and circular geofence overlays for active alarms.
 */
@Composable
private fun MapCanvasPlaceholder(
    hasArmedAlarms: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidGlassTheme.colors

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2f, height * 0.42f)

        // Subtle transit coordinate grid
        val gridStep = 48.dp.toPx()
        val gridColor = colors.textSecondary.copy(alpha = 0.08f)

        var x = 0f
        while (x <= width) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f,
            )
            x += gridStep
        }

        var y = 0f
        while (y <= height) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f,
            )
            y += gridStep
        }

        // Circular geofence overlays for armed alarms
        if (hasArmedAlarms) {
            val outerRadius = 160.dp.toPx()
            val innerRadius = 80.dp.toPx()

            // Outer geofence boundary
            drawCircle(
                color = colors.armed.copy(alpha = 0.08f),
                radius = outerRadius,
                center = center,
            )
            drawCircle(
                color = colors.armed.copy(alpha = 0.35f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )

            // Inner geofence trigger zone
            drawCircle(
                color = colors.armed.copy(alpha = 0.16f),
                radius = innerRadius,
                center = center,
            )
            drawCircle(
                color = colors.armed.copy(alpha = 0.75f),
                radius = innerRadius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }

        // Center location crosshair / transit indicator
        val pinColor = if (hasArmedAlarms) colors.armed else colors.primary
        drawCircle(
            color = pinColor.copy(alpha = 0.25f),
            radius = 24.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = pinColor,
            radius = 6.dp.toPx(),
            center = center,
        )
    }
}
