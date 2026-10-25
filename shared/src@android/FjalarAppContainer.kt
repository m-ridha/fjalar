package com.muhammadridha.fjalar

import android.content.Context
import com.muhammadridha.fjalar.adapters.location.AndroidFusedLocationAdapter
import com.muhammadridha.fjalar.adapters.ringer.AndroidSystemAlarmRingerAdapter
import com.muhammadridha.fjalar.adapters.scheduler.AndroidAlarmManagerSchedulerAdapter
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentAlarmRepository
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.adapters.storage.SharedPreferencesKeyValueStorage
import com.muhammadridha.fjalar.di.FjalarPlatformContainer
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
    override val keyValueStorage: KeyValueStorage = SharedPreferencesKeyValueStorage(context),
    override val alarmRepository: AlarmRepositoryPort = PersistentAlarmRepository(keyValueStorage),
    override val preferences: PreferencesPort = PersistentPreferencesAdapter(keyValueStorage),
    override val alarmScheduler: AlarmSchedulerPort = AndroidAlarmManagerSchedulerAdapter(context),
    locationTracker: LocationTrackerPort? = null,
    override val alarmRinger: AlarmRingerPort = AndroidSystemAlarmRingerAdapter(context),
) : FjalarPlatformContainer {
    override val locationTracker: LocationTrackerPort = locationTracker ?: AndroidFusedLocationAdapter(
        context = context,
        updatePortProvider = { alarmService },
    )
    @Volatile
    private var _scheduleEvaluator: ScheduleEvaluator = ScheduleEvaluator(TimeZone.currentSystemDefault())

    @Volatile
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
