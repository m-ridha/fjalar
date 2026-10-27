package com.muhammadridha.fjalar.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.ui.WindowInsetsLayoutHelper
import com.muhammadridha.fjalar.ui.components.AlarmCard
import com.muhammadridha.fjalar.ui.components.DeleteAlarmConfirmationDialog
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface

fun calculateActiveCount(alarms: List<LocationAlarm>): Int {
    return alarms.count { it.isEnabled }
}

fun formatActiveCountBadge(activeCount: Int, locale: AppLocale = AppLocale.EN): String {
    return FjalarStrings.get(FjalarStringKey.SHEET_ACTIVE_COUNT, locale, activeCount)
}

@Composable
fun AlarmsScreen(
    alarms: List<LocationAlarm>,
    onToggleAlarm: (id: String, enabled: Boolean) -> Unit,
    onSkipToday: (id: String) -> Unit,
    onDeleteAlarm: (id: String) -> Unit,
    onNavigateToConfigure: (alarmId: String?) -> Unit,
    modifier: Modifier = Modifier,
    locale: AppLocale = LiquidGlassTheme.locale,
    hasOperationFailure: Boolean = false,
    bottomDockClearance: Dp = 100.dp,
) {
    val colors = LiquidGlassTheme.colors
    var alarmToDelete by remember { mutableStateOf<LocationAlarm?>(null) }
    val activeCount = calculateActiveCount(alarms)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .then(WindowInsetsLayoutHelper.safeDrawingPaddingModifier()),
    ) {
        if (hasOperationFailure) {
            Text(
                text = FjalarStrings.get(FjalarStringKey.OPERATION_FAILED, locale),
                color = colors.danger,
                modifier = WindowInsetsLayoutHelper.hudTopPaddingModifier(),
            )
        }
        // Safe-area Top Header
        Row(
            modifier = WindowInsetsLayoutHelper
                .hudTopPaddingModifier(Modifier)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.SHEET_SAVED_TITLE, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )

                // Active count pill
                Text(
                    text = formatActiveCountBadge(activeCount, locale),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.armed,
                    modifier = Modifier
                        .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            // "+ New" action button
            Row(
                modifier = Modifier
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .clickable(role = Role.Button, onClick = { onNavigateToConfigure(null) })
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.SHEET_NEW_ALARM, locale),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (alarms.isEmpty()) {
            // Empty State View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 24.dp, end = 24.dp, bottom = bottomDockClearance)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(level = GlassLevel.SHEET, shape = RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "⏰",
                        style = MaterialTheme.typography.displaySmall,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = FjalarStrings.get(FjalarStringKey.ALARMS_EMPTY_TITLE, locale),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = FjalarStrings.get(FjalarStringKey.ALARMS_EMPTY_BODY, locale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { onNavigateToConfigure(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "+ " + FjalarStrings.get(FjalarStringKey.NAV_ADD_ALARM, locale),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        } else {
            // Populated Alarms List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomDockClearance + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onToggle = onToggleAlarm,
                        onEdit = { onNavigateToConfigure(it.id) },
                        onSkipToday = onSkipToday,
                        onDelete = { alarmToDelete = it },
                        locale = locale,
                    )
                }
            }
        }
    }

    if (alarmToDelete != null) {
        DeleteAlarmConfirmationDialog(
            alarm = alarmToDelete!!,
            onConfirm = { id ->
                alarmToDelete = null
                onDeleteAlarm(id)
            },
            onDismiss = {
                alarmToDelete = null
            },
            locale = locale,
        )
    }
}
