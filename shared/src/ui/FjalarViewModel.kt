package com.muhammadridha.fjalar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Observable UI state representation for the entire Fjalar application.
 */
data class FjalarUiState(
    val alarms: List<LocationAlarm> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLocale: AppLocale = AppLocale.EN,
    val activeRingingAlarm: LocationAlarm? = null,
    val selectedAlarmForEdit: LocationAlarm? = null,
    val isConfigureSheetVisible: Boolean = false,
    val searchQuery: String = "",
    val userLocation: GeoCoordinates? = null,
)

/**
 * Driving adapter (ViewModel) coordinating user interactions, inbound domain ports,
 * and outbound user preferences.
 */
class FjalarViewModel(
    private val observeAlarmsPort: ObserveAlarmsPort,
    private val saveAlarmPort: SaveAlarmPort,
    private val toggleAlarmPort: ToggleAlarmPort,
    private val dismissAlarmPort: DismissAlarmPort,
    private val skipTodayPort: SkipTodayPort,
    private val preferencesPort: PreferencesPort,
    private val syncSchedulesPort: SyncSchedulesPort? = null,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {

    internal val scope: CoroutineScope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(FjalarUiState())
    val uiState: StateFlow<FjalarUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            observeAlarmsPort.observeAlarms().collect { alarmsList ->
                val ringing = alarmsList.firstOrNull { it.state == AlarmState.RINGING }
                _uiState.update { current ->
                    current.copy(
                        alarms = alarmsList,
                        activeRingingAlarm = ringing,
                    )
                }
            }
        }

        scope.launch {
            preferencesPort.observeThemeMode().collect { mode ->
                _uiState.update { current ->
                    current.copy(themeMode = mode)
                }
            }
        }

        scope.launch {
            preferencesPort.observeLocale().collect { locale ->
                _uiState.update { current ->
                    current.copy(appLocale = locale)
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onToggleTheme() {
        val currentMode = _uiState.value.themeMode
        val nextMode = when (currentMode) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        scope.launch {
            preferencesPort.setThemeMode(nextMode)
        }
    }

    fun onToggleLocale() {
        val currentLocale = _uiState.value.appLocale
        val nextLocale = if (currentLocale == AppLocale.EN) {
            AppLocale.ID
        } else {
            AppLocale.EN
        }
        scope.launch {
            preferencesPort.setLocale(nextLocale)
        }
    }

    fun onToggleAlarm(alarmId: String, enabled: Boolean) {
        scope.launch {
            toggleAlarmPort.toggleAlarm(alarmId, enabled)
        }
    }

    fun onSkipToday(alarmId: String) {
        scope.launch {
            skipTodayPort.skipToday(alarmId)
        }
    }

    fun onSaveAlarm(alarm: LocationAlarm) {
        scope.launch {
            val result = saveAlarmPort.saveAlarm(alarm)
            if (result.isSuccess) {
                _uiState.update { current ->
                    current.copy(
                        isConfigureSheetVisible = false,
                        selectedAlarmForEdit = null,
                    )
                }
            }
        }
    }

    fun onDismissRingingAlarm(alarmId: String) {
        scope.launch {
            dismissAlarmPort.dismissAlarm(alarmId)
        }
    }

    fun onOpenNewAlarm() {
        _uiState.update { current ->
            current.copy(
                selectedAlarmForEdit = null,
                isConfigureSheetVisible = true,
            )
        }
    }

    fun onOpenEditAlarm(alarm: LocationAlarm) {
        _uiState.update { current ->
            current.copy(
                selectedAlarmForEdit = alarm,
                isConfigureSheetVisible = true,
            )
        }
    }

    fun onDismissConfigureSheet() {
        _uiState.update { current ->
            current.copy(
                isConfigureSheetVisible = false,
                selectedAlarmForEdit = null,
            )
        }
    }

    /**
     * Re-synchronizes all saved alarms against current time to re-arm commute windows
     * and transition states. Invoked during foreground application entry and system events.
     */
    fun syncSchedules(): Job = scope.launch {
        syncSchedulesPort?.syncSchedules()
    }

    fun onRecenterMap() {
        // Map recenter hook
    }
}
