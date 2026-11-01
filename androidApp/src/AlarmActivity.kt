package com.muhammadridha.fjalar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.muhammadridha.fjalar.adapters.ringer.AndroidSystemAlarmRingerAdapter
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.ui.components.RingingAlertOverlay
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.LaunchedEffect
import org.koin.core.parameter.parametersOf

/** Non-exported, isolated alarm task. It has no route to ordinary application navigation. */
class AlarmActivity : ComponentActivity() {
    private val preferences: PreferencesPort by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alarmId = validatedAlarmId(intent)
        if (alarmId == null) {
            finishAndRemoveTask()
            return
        }
        // Keep alarm contents out of screenshots and task previews as well as private navigation.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // Supported compatibility path for the project's pre-27 Android targets.
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        enableEdgeToEdge()
        setContent {
            val controller = koinViewModel<RestrictedAlarmViewModel>(
                key = "alarm:$alarmId",
                parameters = { parametersOf(alarmId) },
            ).controller
            val state by controller.state.collectAsState()
            LaunchedEffect(state.isLoading, state.alarm, state.isOperationPending) {
                // Persistence may emit before scheduler/GPS cleanup; await the action's completion.
                if (!state.isLoading && state.alarm == null && !state.isOperationPending) {
                    finishAndRemoveTask()
                }
            }
            BackHandler(enabled = state.isOperationPending) { /* Finish platform cleanup before leaving. */ }
            val theme by preferences.observeThemeMode().collectAsState(ThemeMode.SYSTEM)
            val locale by preferences.observeLocale().collectAsState(AppLocale.EN)
            LiquidGlassTheme(themeMode = theme, locale = locale) {
                Box(Modifier.fillMaxSize().background(LiquidGlassTheme.colors.canvas)) {
                    state.alarm?.let { alarm ->
                        RingingAlertOverlay(
                            alarm = alarm,
                            onDismiss = { controller.dismiss() },
                            onSnooze = { controller.snooze() },
                            isOperationPending = state.isOperationPending,
                            hasOperationFailure = state.hasOperationFailure,
                            snoozeDeliveryUnavailable = state.snoozeDeliveryUnavailable,
                            operationFailureId = state.operationFailureId,
                            hideDestinationDetails = true,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newAlarmId = validatedAlarmId(intent)
        if (newAlarmId == null) {
            finishAndRemoveTask()
        } else if (newAlarmId != validatedAlarmId(this.intent)) {
            // Recreate the restricted controller; never reuse stale authority from the old alarm.
            setIntent(intent)
            recreate()
        }
    }

    private fun validatedAlarmId(intent: Intent): String? {
        if (intent.action != ACTION_SHOW_ALARM) return null
        return intent.getStringExtra(AndroidSystemAlarmRingerAdapter.EXTRA_ALARM_ID)
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val ACTION_SHOW_ALARM = "com.muhammadridha.fjalar.action.SHOW_ALARM"
    }
}
