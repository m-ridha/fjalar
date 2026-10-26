package com.muhammadridha.fjalar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muhammadridha.fjalar.adapters.search.HybridPlaceSearchAdapter
import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentAlarmRepository
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.di.FjalarPlatformContainer
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import com.muhammadridha.fjalar.ui.FjalarApp
import com.muhammadridha.fjalar.ui.FjalarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Creates a default [FjalarViewModel] backed by [PersistentAlarmRepository],
 * [PersistentPreferencesAdapter], and [AlarmService].
 *
 * Accepts optional custom [tracker], [scheduler], [ringer], [alarmService], or [preferencesPort],
 * falling back to safe in-memory/no-op adapters so unit tests and Composable previews function cleanly.
 */
fun createDefaultViewModel(
    keyValueStorage: KeyValueStorage = InMemoryKeyValueStorage(),
    tracker: LocationTrackerPort? = null,
    scheduler: AlarmSchedulerPort? = null,
    ringer: AlarmRingerPort? = null,
    alarmService: AlarmService? = null,
    preferencesPort: PreferencesPort? = null,
    syncSchedulesPort: SyncSchedulesPort? = null,
    mapRendererPort: MapRendererPort? = null,
    searchPlacesPort: SearchPlacesPort? = null,
    seedSampleAlarms: Boolean = false,
    coroutineScope: CoroutineScope? = null,
): FjalarViewModel {
    val preferences = preferencesPort ?: PersistentPreferencesAdapter(keyValueStorage)
    val fallbackRepository = if (alarmService == null || seedSampleAlarms) {
        PersistentAlarmRepository(keyValueStorage)
    } else {
        null
    }

    val effectiveAlarmService = alarmService ?: run {
        val repo = fallbackRepository ?: PersistentAlarmRepository(keyValueStorage)
        val effectiveTracker = tracker ?: object : LocationTrackerPort {
            override suspend fun startTracking(updateIntervalMillis: Long) {}
            override suspend fun stopTracking() {}
        }
        val effectiveScheduler = scheduler ?: object : AlarmSchedulerPort {
            override suspend fun scheduleWakeup(alarmId: String, at: Instant) {}
            override suspend fun cancelWakeup(alarmId: String) {}
        }
        val effectiveRinger = ringer ?: object : AlarmRingerPort {
            override suspend fun startRinging(alarm: LocationAlarm) {}
            override suspend fun stopRinging(alarmId: String) {}
        }
        val scheduleEvaluator = ScheduleEvaluator(TimeZone.currentSystemDefault())
        AlarmService(
            repository = repo,
            tracker = effectiveTracker,
            scheduler = effectiveScheduler,
            ringer = effectiveRinger,
            scheduleEvaluator = scheduleEvaluator,
        )
    }

    val effectiveSyncSchedulesPort = syncSchedulesPort ?: effectiveAlarmService
    val effectiveSearchPlacesPort = searchPlacesPort ?: HybridPlaceSearchAdapter()

    val vm = FjalarViewModel(
        observeAlarmsPort = effectiveAlarmService,
        saveAlarmPort = effectiveAlarmService,
        toggleAlarmPort = effectiveAlarmService,
        dismissAlarmPort = effectiveAlarmService,
        skipTodayPort = effectiveAlarmService,
        preferencesPort = preferences,
        syncSchedulesPort = effectiveSyncSchedulesPort,
        mapRendererPort = mapRendererPort,
        searchPlacesPort = effectiveSearchPlacesPort,
        coroutineScope = coroutineScope,
    )

    // Seed sample alarms only if explicitly requested (e.g. previews/demos), never in production persistent storage
    if (seedSampleAlarms) {
        vm.scope.launch {
            val repo = fallbackRepository ?: PersistentAlarmRepository(keyValueStorage)
            if (repo.getAlarms().isEmpty()) {
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
                        state = AlarmState.SCHEDULED_IDLE,
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
                sampleAlarms.forEach { repo.upsertAlarm(it) }
            }
        }
    }

    return vm
}

/**
 * Root Composable entry point for Fjalar (Wake me up there).
 *
 * Accepts an optional [FjalarPlatformContainer] (or individual platform adapters)
 * to wire production platform services while falling back to in-memory fakes for previews.
 */
@Composable
fun App(
    container: FjalarPlatformContainer? = null,
    keyValueStorage: KeyValueStorage = remember { container?.keyValueStorage ?: InMemoryKeyValueStorage() },
    tracker: LocationTrackerPort? = container?.locationTracker,
    scheduler: AlarmSchedulerPort? = container?.alarmScheduler,
    ringer: AlarmRingerPort? = container?.alarmRinger,
    alarmService: AlarmService? = container?.alarmService,
    preferencesPort: PreferencesPort? = container?.preferences,
    syncSchedulesPort: SyncSchedulesPort? = container?.syncSchedulesPort,
    mapRendererPort: MapRendererPort? = container?.mapRendererPort,
    searchPlacesPort: SearchPlacesPort? = container?.searchPlacesPort,
    viewModel: FjalarViewModel = viewModel {
        createDefaultViewModel(
            keyValueStorage = keyValueStorage,
            tracker = tracker,
            scheduler = scheduler,
            ringer = ringer,
            alarmService = alarmService,
            preferencesPort = preferencesPort,
            syncSchedulesPort = syncSchedulesPort,
            mapRendererPort = mapRendererPort,
            searchPlacesPort = searchPlacesPort,
        )
    },
    modifier: Modifier = Modifier,
) {
    FjalarApp(
        viewModel = viewModel,
        modifier = modifier,
    )
}
