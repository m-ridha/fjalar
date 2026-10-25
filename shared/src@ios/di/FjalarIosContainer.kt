package com.muhammadridha.fjalar.di

import com.muhammadridha.fjalar.adapters.location.IosCLLocationAdapter
import com.muhammadridha.fjalar.adapters.ringer.IosCriticalAudioRingerAdapter
import com.muhammadridha.fjalar.adapters.scheduler.IosBackgroundSchedulerAdapter
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentAlarmRepository
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.adapters.storage.UserDefaultsKeyValueStorage
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRepositoryPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import kotlinx.datetime.TimeZone

open class FjalarIosContainer(
    override val keyValueStorage: KeyValueStorage = UserDefaultsKeyValueStorage(),
    override val alarmRepository: AlarmRepositoryPort = PersistentAlarmRepository(keyValueStorage),
    override val preferences: PreferencesPort = PersistentPreferencesAdapter(keyValueStorage),
    override val alarmScheduler: AlarmSchedulerPort = IosBackgroundSchedulerAdapter(),
    locationTracker: LocationTrackerPort? = null,
    override val alarmRinger: AlarmRingerPort = IosCriticalAudioRingerAdapter(),
) : FjalarPlatformContainer {

    override val locationTracker: LocationTrackerPort = locationTracker ?: IosCLLocationAdapter(
        updatePortProvider = { alarmService },
    )

    @kotlin.concurrent.Volatile
    private var _scheduleEvaluator: ScheduleEvaluator = ScheduleEvaluator(TimeZone.currentSystemDefault())

    @kotlin.concurrent.Volatile
    private var _alarmService: AlarmService = createAlarmService(_scheduleEvaluator)

    override val syncSchedulesPort: SyncSchedulesPort
        get() = _alarmService

    override val alarmService: AlarmService
        get() = _alarmService

    override fun onTimezoneChanged(newTimeZone: TimeZone) {
        _scheduleEvaluator = ScheduleEvaluator(newTimeZone)
        _alarmService = createAlarmService(_scheduleEvaluator)
    }

    private fun createAlarmService(scheduleEvaluator: ScheduleEvaluator): AlarmService {
        return AlarmService(
            repository = alarmRepository,
            tracker = locationTracker,
            scheduler = alarmScheduler,
            ringer = alarmRinger,
            scheduleEvaluator = scheduleEvaluator,
        )
    }
}
