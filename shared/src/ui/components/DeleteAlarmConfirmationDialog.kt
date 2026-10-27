package com.muhammadridha.fjalar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface

fun formatDeleteMessage(alarmLabel: String, locale: AppLocale = AppLocale.EN): String {
    return FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_MESSAGE, locale, alarmLabel)
}

@Composable
fun DeleteAlarmConfirmationDialog(
    alarm: LocationAlarm,
    onConfirm: (id: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    locale: AppLocale = LiquidGlassTheme.locale,
) {
    val colors = LiquidGlassTheme.colors
    val titleText = FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_TITLE, locale)
    val messageText = formatDeleteMessage(alarm.label, locale)
    val cancelText = FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CANCEL, locale)
    val confirmText = FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, locale)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .glassSurface(
                        level = GlassLevel.MODAL,
                        shape = RoundedCornerShape(24.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // Prevent closing dialog when clicking inside card
                    )
                    .padding(24.dp),
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                    ) {
                        Text(
                            text = cancelText,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = colors.textSecondary,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onConfirm(alarm.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.danger,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = confirmText,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
