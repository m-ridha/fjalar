package com.muhammadridha.fjalar.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.ui.WindowInsetsLayoutHelper
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.fjalarString
import com.muhammadridha.fjalar.ui.theme.glassSurface

/**
 * Top-level navigation tabs available in the bottom bar.
 */
enum class NavigationTab {
    MAP,
    ALARMS,
}

/**
 * Resolves the active navigation tab given the current route.
 */
fun resolveActiveTab(route: Any?): NavigationTab? = when {
    isMapRoute(route) -> NavigationTab.MAP
    isAlarmsRoute(route) -> NavigationTab.ALARMS
    else -> null
}

/**
 * Frosted Liquid Glass bottom navigation bar anchored above system navigation bars.
 * Provides tabs for Map and Alarms with a center elevated (+) action button for adding alarms.
 */
@Composable
fun FrostedBottomNavigationBar(
    currentRoute: Any?,
    onNavigateToMap: () -> Unit,
    onNavigateToAlarms: () -> Unit,
    onAddAlarmClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidGlassTheme.colors
    val activeTab = resolveActiveTab(currentRoute)

    val mapLabel = fjalarString(FjalarStringKey.NAV_MAP)
    val alarmsLabel = fjalarString(FjalarStringKey.NAV_ALARMS)
    val addLabel = fjalarString(FjalarStringKey.NAV_ADD_ALARM)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(WindowInsetsLayoutHelper.navigationBottomPaddingModifier())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Frosted dock pill container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .glassSurface(
                    level = GlassLevel.SHEET,
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left Tab: Map
                NavTabItem(
                    label = mapLabel,
                    iconEmoji = "🗺️",
                    isSelected = activeTab == NavigationTab.MAP,
                    onClick = onNavigateToMap,
                    modifier = Modifier.weight(1f),
                )

                // Center placeholder spacer for elevated (+) action button
                Spacer(modifier = Modifier.width(64.dp))

                // Right Tab: Alarms
                NavTabItem(
                    label = alarmsLabel,
                    iconEmoji = "⏰",
                    isSelected = activeTab == NavigationTab.ALARMS,
                    onClick = onNavigateToAlarms,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Center elevated (+) action button
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(
                    role = Role.Button,
                    onClick = onAddAlarmClick,
                )
                .semantics {
                    contentDescription = addLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = colors.onPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NavTabItem(
    label: String,
    iconEmoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidGlassTheme.colors
    val accentColor = if (isSelected) colors.primary else colors.textSecondary

    Column(
        modifier = modifier
            .clickable(
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                this.selected = isSelected
                this.role = Role.Tab
                this.contentDescription = label
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = iconEmoji,
            fontSize = 20.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
