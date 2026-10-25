package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.model.AlarmState.ARMED_MONITORING
import com.muhammadridha.fjalar.domain.model.AlarmState.COMPLETED_FOR_WINDOW
import com.muhammadridha.fjalar.domain.model.AlarmState.DISABLED
import com.muhammadridha.fjalar.domain.model.AlarmState.RINGING
import com.muhammadridha.fjalar.domain.model.AlarmState.SCHEDULED_IDLE
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.HandleLocationUpdatePort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRepositoryPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Application core: implements every inbound port on top of the pure [AlarmEvaluator] and drives
 * the outbound ports (ringer, GPS tracking, OS wakeups) from the resulting alarm states.
 */
class AlarmService(
    private val repository: AlarmRepositoryPort,
    private val tracker: LocationTrackerPort,
    private val scheduler: AlarmSchedulerPort,
    private val ringer: AlarmRingerPort,
    private val scheduleEvaluator: ScheduleEvaluator,
    private val clock: Clock = Clock.System,
) : ObserveAlarmsPort, SaveAlarmPort, ToggleAlarmPort, HandleLocationUpdatePort,
    DismissAlarmPort, SkipTodayPort, SyncSchedulesPort {

    private val evaluator = AlarmEvaluator(scheduleEvaluator)

    // Location fixes, OS wakeups and UI taps arrive concurrently; serialize read-modify-write cycles.
    private val mutex = Mutex()

    override fun observeAlarms(): Flow<List<LocationAlarm>> = repository.observeAlarms()

    override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = resultOf {
        mutex.withLock {
            val now = clock.now()
            persist(repository.getAlarm(alarm.id), evaluator.syncSchedule(alarm, now))
            applySideEffects(now)
        }
    }

    override suspend fun toggleAlarm(alarmId: String, enabled: Boolean): Result<Unit> =
        mutate(alarmId) { alarm, now -> if (enabled) evaluator.enable(alarm, now) else evaluator.disable(alarm) }

    override suspend fun dismissAlarm(alarmId: String): Result<Unit> = mutate(alarmId, evaluator::dismiss)

    override suspend fun skipToday(alarmId: String): Result<Unit> = mutate(alarmId, evaluator::skip)

    override suspend fun handleLocationUpdate(
        coordinates: GeoCoordinates,
        horizontalAccuracyMeters: Float,
    ): List<LocationAlarm> = mutex.withLock {
        val now = clock.now()
        val triggered = repository.getAlarms().mapNotNull { old ->
            val new = evaluator.evaluateFix(evaluator.syncSchedule(old, now), coordinates, horizontalAccuracyMeters)
            persist(old, new)
            new.takeIf { old.state != RINGING && it.state == RINGING }
        }
        applySideEffects(now)
        triggered
    }

    override suspend fun syncSchedules(): List<LocationAlarm> = mutex.withLock {
        val now = clock.now()
        val alarms = repository.getAlarms().map { old -> evaluator.syncSchedule(old, now).also { persist(old, it) } }
        applySideEffects(now)
        alarms
    }

    private suspend fun mutate(
        alarmId: String,
        transition: (LocationAlarm, Instant) -> LocationAlarm,
    ): Result<Unit> = resultOf {
        mutex.withLock {
            val now = clock.now()
            val old = repository.getAlarm(alarmId) ?: throw NoSuchElementException("no alarm with id $alarmId")
            persist(old, transition(old, now))
            applySideEffects(now)
        }
    }

    private suspend fun persist(old: LocationAlarm?, new: LocationAlarm) {
        if (old == new) return
        repository.upsertAlarm(new)
        if (old?.state != RINGING && new.state == RINGING) ringer.startRinging(new)
        if (old?.state == RINGING && new.state != RINGING) {
            try {
                ringer.stopRinging(new.id)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // Roll back repository state so subsequent calls can retry stopping the ringer
                repository.upsertAlarm(old)
                throw error
            }
        }
    }

    private suspend fun applySideEffects(now: Instant) {
        val alarms = repository.getAlarms()
        // shortcut: fixed interval; adaptive polling by distance-to-target (PRD L4) comes with the location adapters.
        if (alarms.any { it.state == ARMED_MONITORING }) tracker.startTracking(TRACKING_INTERVAL_MILLIS)
        else tracker.stopTracking()

        for (alarm in alarms) {
            val wakeAt = nextWakeup(alarm, now)
            if (wakeAt == null) scheduler.cancelWakeup(alarm.id) else scheduler.scheduleWakeup(alarm.id, wakeAt)
        }
    }

    private fun nextWakeup(alarm: LocationAlarm, now: Instant): Instant? {
        if (alarm.schedule.type != ScheduleType.SCHEDULED) return null
        return when (alarm.state) {
            DISABLED, RINGING -> null
            COMPLETED_FOR_WINDOW -> alarm.suppressedUntil
            SCHEDULED_IDLE -> scheduleEvaluator.currentOrNextWindow(alarm.schedule, now)?.start
            ARMED_MONITORING -> scheduleEvaluator.activeWindowAt(alarm.schedule, now)?.end
        }
    }

    companion object {
        const val TRACKING_INTERVAL_MILLIS = 10_000L
    }
}

/** Like runCatching, but never swallows coroutine cancellation. */
private inline fun resultOf(block: () -> Unit): Result<Unit> =
    try {
        block()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
