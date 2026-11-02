package com.muhammadridha.fjalar.domain.ports.inbound

import com.muhammadridha.fjalar.domain.model.LocationAlarm

interface SaveAlarmPort {
    /** Creates or updates an alarm. Field validation happens when the [LocationAlarm] is constructed. */
    suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit>

    /**
     * Saves editor fields and an optional enable choice atomically. A null choice preserves
     * the latest stored runtime state, including dismissal and snooze guards.
     * The default keeps simple adapters compatible; stateful implementations override this
     * overload to reconcile against storage within the same write transaction.
     */
    suspend fun saveAlarm(alarm: LocationAlarm, enabled: Boolean?): Result<Unit> = saveAlarm(alarm)

    /** Saves an editor configuration after checking the latest stored state in the same mutation. */
    suspend fun saveAlarmConfiguration(
        alarm: LocationAlarm,
        enabled: Boolean?,
        requireExisting: Boolean,
    ): Result<Unit>
}

class AlarmConfigurationMissingException(alarmId: String) :
    NoSuchElementException("no alarm with id $alarmId")

class AlarmConfigurationRingingException(alarmId: String) :
    IllegalStateException("alarm $alarmId is ringing")
