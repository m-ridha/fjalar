package com.muhammadridha.fjalar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentAlarmRepository
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import com.muhammadridha.fjalar.ui.FjalarApp
import com.muhammadridha.fjalar.ui.FjalarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Creates a default [FjalarViewModel] backed by [PersistentAlarmRepository],
 * [PersistentPreferencesAdapter], and [AlarmService].
 */
fun createDefaultViewModel(
    keyValueStorage: KeyValueStorage = InMemoryKeyValueStorage(),
): FjalarViewModel {
    val repository = PersistentAlarmRepository(keyValueStorage)
    val preferences = PersistentPreferencesAdapter(keyValueStorage)

    val tracker = object : LocationTrackerPort {
        override suspend fun startTracking(updateIntervalMillis: Long) {}
        override suspend fun stopTracking() {}
    }
    val scheduler = object : AlarmSchedulerPort {
        override suspend fun scheduleWakeup(alarmId: String, at: Instant) {}
        override suspend fun cancelWakeup(alarmId: String) {}
    }
    val ringer = object : AlarmRingerPort {
        override suspend fun startRinging(alarm: LocationAlarm) {}
        override suspend fun stopRinging(alarmId: String) {}
    }
    val scheduleEvaluator = ScheduleEvaluator(TimeZone.currentSystemDefault())
    val alarmService = AlarmService(
        repository = repository,
        tracker = tracker,
        scheduler = scheduler,
        ringer = ringer,
        scheduleEvaluator = scheduleEvaluator,
    )

    // Seed sample alarms matching Stitch specs if repository is empty
    CoroutineScope(Dispatchers.Default).launch {
        if (repository.getAlarms().isEmpty()) {
            val sampleAlarms = listOf(
                LocationAlarm(
                    id = "sample-manggarai",
                    label = "Stasiun Manggarai",
                    coordinates = GeoCoordinates(-6.2088, 106.8456),
                    radius = TriggerRadius(500),
                    schedule = AlarmSchedule(
                        type = ScheduleType.SCHEDULED,
                        startMinuteOfDay = 7 * 60,
                        endMinuteOfDay = 9 * 60 + 30,
                        daysOfWeekMask = AlarmSchedule.WEEKDAYS,
                    ),
                    soundConfig = AlarmSoundConfig(soundEnabled = true, vibrateEnabled = true),
                    state = AlarmState.ARMED_MONITORING,
                ),
                LocationAlarm(
                    id = "sample-sudirman",
                    label = "Stasiun Sudirman",
                    coordinates = GeoCoordinates(-6.2024, 106.8232),
                    radius = TriggerRadius(400),
                    schedule = AlarmSchedule(
                        type = ScheduleType.SCHEDULED,
                        startMinuteOfDay = 7 * 60,
                        endMinuteOfDay = 9 * 60 + 30,
                        daysOfWeekMask = AlarmSchedule.WEEKDAYS,
                    ),
                    soundConfig = AlarmSoundConfig(soundEnabled = true, vibrateEnabled = true),
                    state = AlarmState.SCHEDULED_IDLE,
                ),
            )
            sampleAlarms.forEach { repository.upsertAlarm(it) }
        }
    }

    return FjalarViewModel(
        observeAlarmsPort = alarmService,
        saveAlarmPort = alarmService,
        toggleAlarmPort = alarmService,
        dismissAlarmPort = alarmService,
        skipTodayPort = alarmService,
        preferencesPort = preferences,
    )
}

/**
 * Root Composable entry point for Fjalar (Wake me up at!).
 */
@Composable
fun App(
    viewModel: FjalarViewModel = remember { createDefaultViewModel() },
    modifier: Modifier = Modifier,
) {
    FjalarApp(
        viewModel = viewModel,
        modifier = modifier,
    )
}
