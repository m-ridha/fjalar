package com.muhammadridha.fjalar.di

import com.muhammadridha.fjalar.adapters.location.IosCLLocationAdapter
import com.muhammadridha.fjalar.adapters.ringer.IosCriticalAudioRingerAdapter
import com.muhammadridha.fjalar.adapters.scheduler.IosBackgroundSchedulerAdapter
import com.muhammadridha.fjalar.adapters.search.HybridPlaceSearchAdapter
import com.muhammadridha.fjalar.adapters.search.IosAppleGeocoderEngine
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentAlarmRepository
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.adapters.storage.UserDefaultsKeyValueStorage
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRepositoryPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import kotlinx.datetime.TimeZone

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSystemTimeZoneDidChangeNotification

open class FjalarIosContainer(
    override val keyValueStorage: KeyValueStorage = UserDefaultsKeyValueStorage(),
    override val alarmRepository: AlarmRepositoryPort = PersistentAlarmRepository(keyValueStorage),
    override val preferences: PreferencesPort = PersistentPreferencesAdapter(keyValueStorage),
    override val alarmScheduler: AlarmSchedulerPort = IosBackgroundSchedulerAdapter(),
    locationTracker: LocationTrackerPort? = null,
    override val alarmRinger: AlarmRingerPort = IosCriticalAudioRingerAdapter(),
    override val mapRendererPort: MapRendererPort? = null,
    searchPlacesPort: SearchPlacesPort? = null,
) : FjalarPlatformContainer {

    override val searchPlacesPort: SearchPlacesPort = searchPlacesPort ?: HybridPlaceSearchAdapter(
        onlineEngine = IosAppleGeocoderEngine(),
    )

    override val locationTracker: LocationTrackerPort = locationTracker ?: IosCLLocationAdapter(
        updatePortProvider = { alarmService },
    )

    @kotlin.concurrent.Volatile
    private var currentTimeZone: TimeZone = TimeZone.currentSystemDefault()

    val scheduleEvaluator: ScheduleEvaluator = ScheduleEvaluator(timeZoneProvider = { currentTimeZone })

    override val alarmService: AlarmService = AlarmService(
        repository = alarmRepository,
        tracker = this.locationTracker,
        scheduler = alarmScheduler,
        ringer = alarmRinger,
        scheduleEvaluator = scheduleEvaluator,
    )

    override val syncSchedulesPort: SyncSchedulesPort
        get() = alarmService

    init {
        try {
            (alarmScheduler as? IosBackgroundSchedulerAdapter)?.requestAuthorization()
        } catch (_: Throwable) {
            // Defensive in headless test environments
        }

        try {
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = NSSystemTimeZoneDidChangeNotification,
                `object` = null,
                queue = null,
            ) { _ ->
                onTimezoneChanged(TimeZone.currentSystemDefault())
            }
        } catch (_: Throwable) {
            // Defensive in headless test environments
        }
    }

    override fun onTimezoneChanged(newTimeZone: TimeZone) {
        currentTimeZone = newTimeZone
    }
}
