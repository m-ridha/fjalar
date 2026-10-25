package com.muhammadridha.fjalar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.resources.Res
import com.muhammadridha.fjalar.resources.sheet_active_count
import com.muhammadridha.fjalar.resources.sheet_new_alarm
import com.muhammadridha.fjalar.resources.sheet_saved_title
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface
import org.jetbrains.compose.resources.stringResource

@Composable
fun GlassBottomSheet(
    alarms: List<LocationAlarm>,
    onToggleAlarm: (id: String, enabled: Boolean) -> Unit,
    onEditAlarm: (alarm: LocationAlarm) -> Unit,
    onNewAlarm: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipToday: (id: String) -> Unit = {},
    locale: AppLocale = LiquidGlassTheme.locale,
) {
    val colors = LiquidGlassTheme.colors
    val activeCount = alarms.count { it.isEnabled }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                level = GlassLevel.SHEET,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            )
            .padding(top = 10.dp, bottom = 16.dp),
    ) {
        // Top Grab Handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .background(
                    color = colors.textSecondary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(2.dp),
                ),
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Header Row: Title, Active Count Chip, and + New Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Text(
                    text = stringResource(Res.string.sheet_saved_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )

                // Active count pill
                Text(
                    text = stringResource(Res.string.sheet_active_count, activeCount),
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

            // + New Alarm Button
            Row(
                modifier = Modifier
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .clickable(role = Role.Button, onClick = onNewAlarm)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.sheet_new_alarm),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Scrollable List of AlarmCards
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onToggle = onToggleAlarm,
                    onEdit = onEditAlarm,
                    onSkipToday = onSkipToday,
                    locale = locale,
                )
            }
        }
    }
}
