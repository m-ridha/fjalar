package com.muhammadridha.fjalar.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme

@Composable
fun BackgroundLocationPermissionDialog(
    settingsOptionLabel: String?,
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LiquidGlassTheme.locale
    val explanation = FjalarStrings.get(FjalarStringKey.PERM_LOCATION_BODY, locale)
    val instructions = settingsOptionLabel?.let {
        FjalarStrings.get(FjalarStringKey.PERM_BACKGROUND_SETTINGS_BODY, locale, it)
    }
    val actionKey = if (settingsOptionLabel == null) {
        FjalarStringKey.PERM_LOCATION_GRANT
    } else {
        FjalarStringKey.PERM_BACKGROUND_SETTINGS_ACTION
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(FjalarStrings.get(FjalarStringKey.PERM_LOCATION_TITLE, locale)) },
        text = { Text(if (instructions == null) explanation else "$explanation\n\n$instructions") },
        confirmButton = {
            TextButton(onClick = onGrant) {
                Text(FjalarStrings.get(actionKey, locale))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(FjalarStrings.get(FjalarStringKey.PERM_LOCATION_DISMISS, locale))
            }
        },
    )
}
