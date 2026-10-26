package com.muhammadridha.fjalar.di

import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRepositoryPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.domain.service.AlarmService
import kotlinx.datetime.TimeZone

/**
 * Common DI container contract providing singletons for [AlarmService], platform adapters,
 * and persistence ports across Android and iOS application entry points.
 */
interface FjalarPlatformContainer {
    val keyValueStorage: KeyValueStorage
    val alarmRepository: AlarmRepositoryPort
    val preferences: PreferencesPort
    val alarmScheduler: AlarmSchedulerPort
    val locationTracker: LocationTrackerPort
    val alarmRinger: AlarmRingerPort
    val alarmService: AlarmService
    val syncSchedulesPort: SyncSchedulesPort
    val mapRendererPort: MapRendererPort? get() = null

    fun onTimezoneChanged(newTimeZone: TimeZone = TimeZone.currentSystemDefault())
}
