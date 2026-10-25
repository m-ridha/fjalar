package com.muhammadridha.fjalar

import android.content.Context
import com.muhammadridha.fjalar.adapters.scheduler.AndroidAlarmManagerSchedulerAdapter
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentAlarmRepository
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.adapters.storage.SharedPreferencesKeyValueStorage
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRepositoryPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import kotlinx.datetime.TimeZone

open class FjalarAppContainer(
    val context: Context,
    val keyValueStorage: KeyValueStorage = SharedPreferencesKeyValueStorage(context),
    val alarmRepository: AlarmRepositoryPort = PersistentAlarmRepository(keyValueStorage),
    val preferences: PreferencesPort = PersistentPreferencesAdapter(keyValueStorage),
    val alarmScheduler: AlarmSchedulerPort = AndroidAlarmManagerSchedulerAdapter(context),
    val locationTracker: LocationTrackerPort = object : LocationTrackerPort {
        override suspend fun startTracking(updateIntervalMillis: Long) {}
        override suspend fun stopTracking() {}
    },
    val alarmRinger: AlarmRingerPort = object : AlarmRingerPort {
        override suspend fun startRinging(alarm: LocationAlarm) {}
        override suspend fun stopRinging(alarmId: String) {}
    },
) {
    @Volatile
    private var _scheduleEvaluator: ScheduleEvaluator = ScheduleEvaluator(TimeZone.currentSystemDefault())

    @Volatile
    private var _alarmService: AlarmService = createAlarmService(_scheduleEvaluator)

    open val syncSchedulesPort: SyncSchedulesPort
        get() = _alarmService

    open val alarmService: AlarmService
        get() = _alarmService

    fun onTimezoneChanged(newTimeZone: TimeZone = TimeZone.currentSystemDefault()) {
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
