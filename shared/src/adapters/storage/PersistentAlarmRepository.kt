package com.muhammadridha.fjalar.adapters.storage

import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Concrete outbound persistence adapter for [AlarmRepositoryPort] backed by [KeyValueStorage].
 * Maintains an in-memory reactive cache backed by serialized JSON storage.
 */
class PersistentAlarmRepository(
    private val storage: KeyValueStorage,
) : AlarmRepositoryPort {

    private val alarms = MutableStateFlow<Map<String, LocationAlarm>>(loadAlarms())

    override fun observeAlarms(): Flow<List<LocationAlarm>> =
        alarms.asStateFlow().map { it.values.toList() }

    override suspend fun getAlarms(): List<LocationAlarm> =
        alarms.value.values.toList()

    override suspend fun getAlarm(id: String): LocationAlarm? =
        alarms.value[id]

    override suspend fun upsertAlarm(alarm: LocationAlarm) {
        val updated = alarms.value + (alarm.id to alarm)
        persist(updated)
        alarms.value = updated
    }

    override suspend fun deleteAlarm(id: String) {
        val updated = alarms.value - id
        persist(updated)
        alarms.value = updated
    }

    private fun persist(updatedAlarms: Map<String, LocationAlarm>) {
        val json = LocationAlarmJsonCodec.toJsonList(updatedAlarms.values)
        storage.putString(KEY_ALARMS, json)
    }

    private fun loadAlarms(): Map<String, LocationAlarm> {
        val json = storage.getString(KEY_ALARMS)
        if (!json.isNullOrBlank()) {
            val list = LocationAlarmJsonCodec.fromJsonList(json)
            if (list.isNotEmpty()) {
                return list.associateBy { it.id }
            }
        }
        return emptyMap()
    }

    companion object {
        const val KEY_ALARMS = "fjalar_stored_alarms"
    }
}
