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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.ui.WindowInsetsLayoutHelper
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.fjalarString
import com.muhammadridha.fjalar.ui.theme.glassSurface

@Composable
fun LanguageDropdown(
    selectedLocale: AppLocale,
    onSelectLocale: (AppLocale) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LiquidGlassTheme.colors
    val localeContentDesc = fjalarString(FjalarStringKey.HUD_LOCALE_LABEL)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .height(44.dp)
                .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                .clickable(
                    role = Role.Button,
                    onClick = { expanded = true },
                )
                .semantics {
                    contentDescription = localeContentDesc
                    role = Role.Button
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "🌐 ${selectedLocale.name} ▾",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = fjalarString(FjalarStringKey.LANG_ENGLISH),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selectedLocale == AppLocale.EN) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                onClick = {
                    expanded = false
                    onSelectLocale(AppLocale.EN)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = fjalarString(FjalarStringKey.LANG_INDONESIAN),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selectedLocale == AppLocale.ID) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                onClick = {
                    expanded = false
                    onSelectLocale(AppLocale.ID)
                },
            )
        }
    }
}

@Composable
fun FloatingHudBar(
    onSearchClick: () -> Unit,
    onToggleLocale: () -> Unit = {},
    onToggleTheme: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier,
    locale: AppLocale = LiquidGlassTheme.locale,
    themeMode: ThemeMode = LiquidGlassTheme.themeMode,
    onSelectLocale: ((AppLocale) -> Unit)? = null,
) {
    val colors = LiquidGlassTheme.colors
    val isDark = colors.isDark

    val themeContentDesc = fjalarString(
        if (isDark) FjalarStringKey.HUD_THEME_SWITCH_LIGHT else FjalarStringKey.HUD_THEME_SWITCH_DARK
    )
    val searchPlaceholder = fjalarString(FjalarStringKey.HUD_SEARCH_PLACEHOLDER)
    val recenterDescription = fjalarString(FjalarStringKey.HUD_RECENTER_LOCATION)
    val handleSelectLocale = onSelectLocale ?: { loc ->
        if (loc != locale) onToggleLocale()
    }

    Row(
        modifier = WindowInsetsLayoutHelper.hudTopPaddingModifier(modifier).fillMaxWidth(),
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

        // Locale Dropdown (🌐 EN ▾ / 🌐 ID ▾)
        LanguageDropdown(
            selectedLocale = locale,
            onSelectLocale = handleSelectLocale,
        )

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
