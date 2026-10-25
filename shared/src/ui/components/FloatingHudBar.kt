package com.muhammadridha.fjalar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.resources.Res
import com.muhammadridha.fjalar.resources.hud_locale_label
import com.muhammadridha.fjalar.resources.hud_recenter_location
import com.muhammadridha.fjalar.resources.hud_search_placeholder
import com.muhammadridha.fjalar.resources.hud_theme_switch_dark
import com.muhammadridha.fjalar.resources.hud_theme_switch_light
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface
import org.jetbrains.compose.resources.stringResource

@Composable
fun FloatingHudBar(
    onSearchClick: () -> Unit,
    onToggleLocale: () -> Unit,
    onToggleTheme: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier,
    locale: AppLocale = LiquidGlassTheme.locale,
    themeMode: ThemeMode = LiquidGlassTheme.themeMode,
) {
    val colors = LiquidGlassTheme.colors
    val isDark = colors.isDark

    val themeContentDesc = stringResource(
        if (isDark) Res.string.hud_theme_switch_light else Res.string.hud_theme_switch_dark
    )
    val localeContentDesc = stringResource(Res.string.hud_locale_label)
    val searchPlaceholder = stringResource(Res.string.hud_search_placeholder)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Search Pill
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                .clickable(
                    role = Role.Button,
                    onClick = onSearchClick,
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🔍",
                fontSize = 14.sp,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = searchPlaceholder,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Locale Pill (🌐 EN / 🌐 ID)
        Box(
            modifier = Modifier
                .height(44.dp)
                .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                .clickable(
                    role = Role.Button,
                    onClick = onToggleLocale,
                )
                .semantics {
                    contentDescription = localeContentDesc
                    role = Role.Button
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (locale == AppLocale.EN) "🌐 EN" else "🌐 ID",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
            )
        }

        // Theme Toggle Button (☀️ / 🌙)
        Box(
            modifier = Modifier
                .size(44.dp)
                .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                .clickable(
                    role = Role.Button,
                    onClick = onToggleTheme,
                )
                .semantics {
                    contentDescription = themeContentDesc
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isDark) "🌙" else "☀️",
                fontSize = 18.sp,
            )
        }

        // Recenter GPS Button (◎)
        val recenterDescription = stringResource(Res.string.hud_recenter_location)
        Box(
            modifier = Modifier
                .size(44.dp)
                .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                .clickable(
                    role = Role.Button,
                    onClick = onRecenter,
                )
                .semantics {
                    contentDescription = recenterDescription
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "◎",
                fontSize = 20.sp,
                color = colors.primary,
            )
        }
    }
}
