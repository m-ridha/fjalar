package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.model.AlarmState.ARMED_MONITORING
import com.muhammadridha.fjalar.domain.model.AlarmState.COMPLETED_FOR_WINDOW
import com.muhammadridha.fjalar.domain.model.AlarmState.DISABLED
import com.muhammadridha.fjalar.domain.model.AlarmState.RINGING
import com.muhammadridha.fjalar.domain.model.AlarmState.SCHEDULED_IDLE
import com.muhammadridha.fjalar.domain.model.AlarmState.SNOOZED
import com.muhammadridha.fjalar.domain.ports.inbound.SnoozeAlarmPort
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.ports.inbound.DeleteAlarmPort
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    DismissAlarmPort, SnoozeAlarmPort, SkipTodayPort, SyncSchedulesPort, DeleteAlarmPort {

    private val evaluator = AlarmEvaluator(scheduleEvaluator)

    // Location fixes, OS wakeups and UI taps arrive concurrently; serialize read-modify-write cycles.
    private val mutex = Mutex()
    private var ringingOwnerId: String? = null

    override fun observeAlarms(): Flow<List<LocationAlarm>> = repository.observeAlarms()

    override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = resultOf {
        mutex.withLock {
            val now = clock.now()
            val previous = repository.getAlarm(alarm.id)
            persist(previous, evaluator.prepareSave(previous, alarm, now))
            applySideEffects(now)
        }
    }

    override suspend fun toggleAlarm(alarmId: String, enabled: Boolean): Result<Unit> =
        mutate(alarmId) { alarm, now -> if (enabled) evaluator.enable(alarm, now) else evaluator.disable(alarm) }

    override suspend fun dismissAlarm(alarmId: String): Result<Unit> = mutate(alarmId, evaluator::dismiss)

    override suspend fun snoozeAlarm(alarmId: String): Result<Unit> = resultOf {
        mutex.withLock {
            val now = clock.now()
            val old = repository.getAlarm(alarmId) ?: throw NoSuchElementException("no alarm with id $alarmId")
            val snoozed = evaluator.snooze(old, now)
            // Reserve the wakeup before stopping playback. A scheduling failure leaves
            // the original ringing presentation available for retry.
            val wakeAt = checkNotNull(nextWakeup(snoozed, now))
            try {
                scheduleWakeup(snoozed, wakeAt)
                persist(old, snoozed)
                applySideEffects(now, alreadyScheduledId = alarmId)
            } catch (error: Throwable) {
                // No successful Snooze is published on failure. Restore the ringing
                // record and reconcile playback/wakeups so the same action can retry.
                recoverAfterFailure(error) { repository.upsertAlarm(old) }
                recoverAfterFailure(error) { scheduler.cancelWakeup(alarmId) }
                // A failed rollback write or cancellation must not prevent playback
                // reconciliation, nor replace the original operation failure.
                recoverAfterFailure(error) { applySideEffects(now) }
                throw error
            }
        }
    }

    override suspend fun skipToday(alarmId: String): Result<Unit> = mutate(alarmId, evaluator::skip)

    override suspend fun deleteAlarm(alarmId: String): Result<Unit> = resultOf {
        mutex.withLock {
            val existing = repository.getAlarm(alarmId)
            if (existing != null) {
                if (existing.state == RINGING) {
                    ringer.stopRinging(alarmId)
                    if (ringingOwnerId == alarmId) ringingOwnerId = null
                }
                scheduler.cancelWakeup(alarmId)
                repository.deleteAlarm(alarmId)
                applySideEffects(clock.now())
            }
        }
    }

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
        if (old?.state != RINGING || new.state == RINGING) {
            repository.upsertAlarm(new)
            return
        }

        // Snapshot the displayed owner before stopping. Recovery must not depend
        // on another storage read or a successful rollback write.
        val previousOwner = repository.getAlarms()
            .filter { it.state == RINGING }
            .minByOrNull { it.id }
        ringer.stopRinging(old.id)
        if (ringingOwnerId == old.id) ringingOwnerId = null
        try {
            repository.upsertAlarm(new)
        } catch (error: Throwable) {
            recoverAfterFailure(error) { reconcileRingingOwner(previousOwner) }
            throw error
        }
    }

    private suspend fun recoverAfterFailure(error: Throwable, recovery: suspend () -> Unit) {
        withContext(NonCancellable) {
            try {
                recovery()
            } catch (recoveryError: Throwable) {
                if (recoveryError !== error) error.addSuppressed(recoveryError)
            }
        }
    }

    private suspend fun reconcileRingingOwner(owner: LocationAlarm?) {
        if (ringingOwnerId != owner?.id) {
            ringingOwnerId?.let { ringer.stopRinging(it) }
            ringingOwnerId = null
            if (owner != null) {
                ringer.startRinging(owner)
                ringingOwnerId = owner.id
            }
        }
    }

    private suspend fun applySideEffects(now: Instant, alreadyScheduledId: String? = null) {
        val alarms = repository.getAlarms()
        val owner = alarms.filter { it.state == RINGING }.minByOrNull { it.id }
        reconcileRingingOwner(owner)
        // shortcut: fixed interval; adaptive polling by distance-to-target (PRD L4) comes with the location adapters.
        if (alarms.any { it.state == ARMED_MONITORING }) tracker.startTracking(TRACKING_INTERVAL_MILLIS)
        else tracker.stopTracking()

        for (alarm in alarms) {
            if (alarm.id == alreadyScheduledId) continue
            val wakeAt = nextWakeup(alarm, now)
            if (wakeAt == null) scheduler.cancelWakeup(alarm.id) else scheduleWakeup(alarm, wakeAt)
        }
    }

    private suspend fun scheduleWakeup(alarm: LocationAlarm, at: Instant) {
        if (alarm.state == SNOOZED && at == alarm.snoozedUntil &&
            (alarm.suppressedUntil == null || at < alarm.suppressedUntil)) {
            scheduler.scheduleSnoozeWakeup(alarm, at)
        } else {
            scheduler.scheduleWakeup(alarm.id, at)
        }
    }

    private fun nextWakeup(alarm: LocationAlarm, now: Instant): Instant? {
        if (alarm.state == SNOOZED) {
            val deadline = alarm.snoozedUntil ?: return now
            return alarm.suppressedUntil?.let { minOf(deadline, it) } ?: deadline
        }
        if (alarm.state == COMPLETED_FOR_WINDOW) return alarm.suppressedUntil
        if (alarm.schedule.type != ScheduleType.SCHEDULED) return null
        return when (alarm.state) {
            DISABLED, RINGING, SNOOZED -> null
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
