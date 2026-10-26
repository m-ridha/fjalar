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
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
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
    override val mapRendererPort: MapRendererPort? = null,
) : FjalarPlatformContainer {
    override val locationTracker: LocationTrackerPort = locationTracker ?: AndroidFusedLocationAdapter(
        context = context,
        updatePortProvider = { alarmService },
    )
    @Volatile
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

    override fun onTimezoneChanged(newTimeZone: TimeZone) {
        currentTimeZone = newTimeZone
    }
}
