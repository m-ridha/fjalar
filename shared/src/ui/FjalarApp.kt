package com.muhammadridha.fjalar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.ui.components.ArmedStatusPill
import com.muhammadridha.fjalar.ui.components.ConfigureAlarmSheet
import com.muhammadridha.fjalar.ui.components.FloatingHudBar
import com.muhammadridha.fjalar.ui.components.GlassBottomSheet
import com.muhammadridha.fjalar.ui.components.PlatformMapView
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

        val isDarkTheme = uiState.themeMode == ThemeMode.DARK ||
            (uiState.themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())

        val activeAlarm = uiState.selectedAlarmForEdit
            ?: uiState.alarms.firstOrNull { it.state == AlarmState.ARMED_MONITORING }

        val pinLocation = uiState.selectedAlarmForEdit?.coordinates
            ?: uiState.selectedCoordinates
            ?: activeAlarm?.coordinates

        val geofenceCenter = uiState.selectedAlarmForEdit?.coordinates
            ?: uiState.selectedCoordinates
            ?: activeAlarm?.coordinates

        val geofenceRadius = uiState.selectedAlarmForEdit?.radius
            ?: (if (uiState.selectedCoordinates != null) uiState.selectedRadius else activeAlarm?.radius)

        val center = uiState.mapViewportCenter
            ?: pinLocation
            ?: uiState.userLocation
            ?: GeoCoordinates.DEFAULT_JAKARTA

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.canvas),
        ) {
            // Layer 1: Living Map Canvas (100% viewport edge-to-edge)
            PlatformMapView(
                center = center,
                zoom = uiState.mapViewportZoom,
                pinLocation = pinLocation,
                geofenceCenter = geofenceCenter,
                geofenceRadius = geofenceRadius,
                isDarkTheme = isDarkTheme,
                onMapClick = viewModel::onMapClicked,
                onPinDrag = viewModel::onPinDragged,
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
                        selectedCoordinates = uiState.selectedCoordinates,
                        onRadiusChanged = viewModel::onRadiusChanged,
                        onSave = viewModel::onSaveAlarm,
                        onCancel = viewModel::onDismissConfigureSheet,
                        locale = uiState.appLocale,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
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
                    // shortcut: dedicated snooze port deferred to milestone 2; keep snooze inert to prevent single-tap dismissal (Invariant 3)
                    onSnooze = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
