package com.muhammadridha.fjalar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.ui.WindowInsetsLayoutHelper
import com.muhammadridha.fjalar.ui.components.PlatformMapView
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface

@Composable
fun MapPickerScreen(
    initialCoordinates: GeoCoordinates,
    onConfirmCoordinates: (GeoCoordinates) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    locale: AppLocale = LiquidGlassTheme.locale,
    initialZoom: Double = 15.0,
) {
    val colors = LiquidGlassTheme.colors
    val isDarkTheme = LiquidGlassTheme.themeMode == ThemeMode.DARK ||
        (LiquidGlassTheme.themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())

    var currentCoordinates by remember(initialCoordinates) {
        mutableStateOf(initialCoordinates)
    }

    var viewportRequestId by remember { mutableStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        // Layer 1: 100% Viewport Edge-to-Edge Map
        PlatformMapView(
            center = currentCoordinates,
            zoom = initialZoom,
            pinLocation = currentCoordinates,
            geofenceCenter = currentCoordinates,
            geofenceRadius = TriggerRadius(500),
            isDarkTheme = isDarkTheme,
            onMapClick = {
                currentCoordinates = it
                viewportRequestId++
            },
            onPinDrag = {
                currentCoordinates = it
                viewportRequestId++
            },
            onCameraCenterChanged = { currentCoordinates = it },
            viewportRequestId = viewportRequestId,
            modifier = Modifier.fillMaxSize(),
        )

        // Layer 2: Dead-Center Crosshair Target Indicator
        Box(
            modifier = Modifier
                .align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⌖",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        lineHeight = 28.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Layer 3: Safe-Area Top Header (Back + Hint)
        Row(
            modifier = WindowInsetsLayoutHelper
                .hudTopPaddingModifier(Modifier)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back Button
            Row(
                modifier = Modifier
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.NAV_BACK, locale),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    ),
                )
            }

            // Hint Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.PICKER_HINT, locale),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Layer 4: Safe-Area Bottom Floating Action Pill (Confirm)
        Box(
            modifier = WindowInsetsLayoutHelper
                .navigationBottomPaddingModifier(Modifier)
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Button(
                onClick = { onConfirmCoordinates(currentCoordinates) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            ) {
                Text(
                    text = "✓ ${FjalarStrings.get(FjalarStringKey.PICKER_CONFIRM, locale)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
