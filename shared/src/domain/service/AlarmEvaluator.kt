package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AlarmState.ARMED_MONITORING
import com.muhammadridha.fjalar.domain.model.AlarmState.COMPLETED_FOR_WINDOW
import com.muhammadridha.fjalar.domain.model.AlarmState.DISABLED
import com.muhammadridha.fjalar.domain.model.AlarmState.RINGING
import com.muhammadridha.fjalar.domain.model.AlarmState.SCHEDULED_IDLE
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes
import com.muhammadridha.fjalar.domain.model.AlarmState.SNOOZED

/**
 * Pure alarm state machine:
 * DISABLED → (enable) → SCHEDULED_IDLE ⇄ ARMED_MONITORING (window opens/closes) → RINGING (fix inside radius)
 * → COMPLETED_FOR_WINDOW (dismiss/skip) → SCHEDULED_IDLE once the window is over.
 * One-time alarms go straight to ARMED_MONITORING and back to DISABLED when dismissed.
 */
class AlarmEvaluator(private val scheduleEvaluator: ScheduleEvaluator) {

    fun enable(alarm: LocationAlarm, now: Instant): LocationAlarm = when (alarm.schedule.type) {
        ScheduleType.ONE_TIME -> alarm.copy(
            state = ARMED_MONITORING,
            suppressedUntil = null,
            snoozedUntil = null,
            mustExitBeforeTrigger = false,
        )
        ScheduleType.SCHEDULED -> withWindowState(
            alarm.copy(suppressedUntil = null, snoozedUntil = null),
            now,
        )
    }

    fun disable(alarm: LocationAlarm): LocationAlarm = alarm.copy(
        state = DISABLED,
        suppressedUntil = null,
        snoozedUntil = null,
        mustExitBeforeTrigger = false,
    )

    /** Editing mode keeps disabled/ringing states and any unfinished completion guard. */
    fun prepareSave(previous: LocationAlarm?, edited: LocationAlarm, now: Instant): LocationAlarm {
        val carried = if (previous?.state == SNOOZED && edited.state == SNOOZED) {
            edited.copy(
                suppressedUntil = previous.suppressedUntil,
                snoozedUntil = previous.snoozedUntil,
                mustExitBeforeTrigger = previous.mustExitBeforeTrigger,
            )
        } else if (previous != null && previous.state == ARMED_MONITORING && edited.state == ARMED_MONITORING) {
            val destinationChanged = previous.coordinates != edited.coordinates || previous.radius != edited.radius
            val mustExit = if (edited.schedule.type == ScheduleType.SCHEDULED) {
                if (destinationChanged) true else previous.mustExitBeforeTrigger
            } else {
                false
            }
            edited.copy(mustExitBeforeTrigger = mustExit)
        } else {
            edited
        }
        if (previous == null || previous.schedule.type == carried.schedule.type) {
            return syncSchedule(carried, now)
        }
        val preserved = carried.copy(
            state = previous.state,
            suppressedUntil = previous.suppressedUntil,
            snoozedUntil = previous.snoozedUntil,
            mustExitBeforeTrigger = if (previous.state == ARMED_MONITORING && carried.schedule.type == ScheduleType.SCHEDULED) {
                true
            } else {
                previous.mustExitBeforeTrigger
            },
        )
        if (preserved.state == SNOOZED) return syncSchedule(preserved, now)
        if (preserved.state == DISABLED || preserved.state == RINGING) return preserved
        if (preserved.suppressedUntil != null && now < preserved.suppressedUntil) {
            return preserved.copy(state = COMPLETED_FOR_WINDOW, mustExitBeforeTrigger = false)
        }
        return enable(preserved, now)
    }

    /** Moves scheduled alarms in or out of monitoring as windows open and close. */
    fun syncSchedule(alarm: LocationAlarm, now: Instant): LocationAlarm {
        if (alarm.state == SNOOZED) {
            // The persisted timer and original cutoff survive process death and field/mode
            // edits. The edited schedule takes over only after that snooze guard expires.
            val originalWindowEnd = alarm.suppressedUntil
            if (originalWindowEnd != null && now >= originalWindowEnd) {
                val cleared = alarm.copy(snoozedUntil = null, suppressedUntil = null)
                return if (alarm.schedule.type == ScheduleType.SCHEDULED) {
                    withWindowState(cleared, now)
                } else {
                    cleared.copy(state = ARMED_MONITORING)
                }
            }
            val deadline = alarm.snoozedUntil
            if (deadline != null && now < deadline) return alarm
            val cleared = alarm.copy(snoozedUntil = null, suppressedUntil = null)
            // A future original cutoff owns the pending snooze even after a schedule edit.
            // Without one, a stale scheduled snooze must still be inside the edited window.
            val windowStillRelevant = if (originalWindowEnd != null) {
                now < originalWindowEnd
            } else {
                alarm.schedule.type != ScheduleType.SCHEDULED || scheduleEvaluator.isActive(alarm.schedule, now)
            }
            return if (windowStillRelevant) cleared.copy(state = RINGING) else withWindowState(cleared, now)
        }
        if (alarm.schedule.type != ScheduleType.SCHEDULED) {
            return if (alarm.state == COMPLETED_FOR_WINDOW &&
                (alarm.suppressedUntil == null || now >= alarm.suppressedUntil)) {
                alarm.copy(state = ARMED_MONITORING, suppressedUntil = null)
            } else alarm
        }
        return when (alarm.state) {
            // A ringing alarm keeps ringing past the window end until the user stops it.
            DISABLED, RINGING, SNOOZED -> alarm
            COMPLETED_FOR_WINDOW ->
                if (alarm.suppressedUntil != null && now < alarm.suppressedUntil) alarm
                else withWindowState(alarm.copy(suppressedUntil = null), now)
            SCHEDULED_IDLE, ARMED_MONITORING -> withWindowState(alarm, now)
        }
    }

    fun evaluateFix(alarm: LocationAlarm, position: GeoCoordinates, horizontalAccuracyMeters: Float): LocationAlarm {
        if (alarm.state != ARMED_MONITORING) return alarm
        if (!isAccurateEnough(horizontalAccuracyMeters, alarm.radius)) return alarm
        val distance = HaversineCalculator.distanceMeters(position, alarm.coordinates)
        val inside = distance <= alarm.radius.meters
        if (inside) {
            if (alarm.mustExitBeforeTrigger) {
                return alarm
            }
            return alarm.copy(state = RINGING)
        }
        if (alarm.mustExitBeforeTrigger) {
            return alarm.copy(mustExitBeforeTrigger = false)
        }
        return alarm
    }

    fun snooze(alarm: LocationAlarm, now: Instant): LocationAlarm {
        check(alarm.state == RINGING) { "alarm ${alarm.id} is not ringing" }
        return alarm.copy(
            state = SNOOZED,
            snoozedUntil = now + 2.minutes,
            suppressedUntil = if (alarm.schedule.type == ScheduleType.SCHEDULED) {
                scheduleEvaluator.activeWindowAt(alarm.schedule, now)?.end ?: now
            } else null,
            mustExitBeforeTrigger = false,
        )
    }

    fun dismiss(alarm: LocationAlarm, now: Instant): LocationAlarm {
        check(alarm.state == RINGING) { "alarm ${alarm.id} is not ringing" }
        return when (alarm.schedule.type) {
            ScheduleType.ONE_TIME -> alarm.copy(state = DISABLED, mustExitBeforeTrigger = false)
            ScheduleType.SCHEDULED -> alarm.copy(
                state = COMPLETED_FOR_WINDOW,
                suppressedUntil = scheduleEvaluator.activeWindowAt(alarm.schedule, now)?.end ?: now,
                mustExitBeforeTrigger = false,
            )
        }
    }

    /** Skips the current window, or the next one if none is open. Repeated skips move further ahead. */
    fun skip(alarm: LocationAlarm, now: Instant): LocationAlarm {
        check(alarm.schedule.type == ScheduleType.SCHEDULED) { "only scheduled alarms can be skipped" }
        check(alarm.state != DISABLED) { "alarm ${alarm.id} is disabled" }
        val from = if (alarm.state == SNOOZED) now else maxOf(now, alarm.suppressedUntil ?: now)
        val window = checkNotNull(scheduleEvaluator.currentOrNextWindow(alarm.schedule, from))
        return alarm.copy(
            state = COMPLETED_FOR_WINDOW,
            suppressedUntil = window.end,
            snoozedUntil = null,
            mustExitBeforeTrigger = false,
        )
    }

    private fun withWindowState(alarm: LocationAlarm, now: Instant): LocationAlarm {
        val isActive = scheduleEvaluator.isActive(alarm.schedule, now)
        val state: AlarmState = if (isActive) ARMED_MONITORING else SCHEDULED_IDLE
        val mustExit = if (alarm.schedule.type == ScheduleType.SCHEDULED) {
            if (alarm.state != ARMED_MONITORING && state == ARMED_MONITORING) {
                true
            } else if (state == ARMED_MONITORING) {
                alarm.mustExitBeforeTrigger
            } else {
                false
            }
        } else {
            false
        }
        return alarm.copy(state = state, mustExitBeforeTrigger = mustExit)
    }

    companion object {
        const val MAX_ACCURACY_TO_RADIUS_RATIO = 0.8

        /** Rejects coarse fixes (cell-tower spikes) that could falsely place the user inside the radius. */
        fun isAccurateEnough(horizontalAccuracyMeters: Float, radius: TriggerRadius): Boolean =
            horizontalAccuracyMeters >= 0f && horizontalAccuracyMeters <= radius.meters * MAX_ACCURACY_TO_RADIUS_RATIO
    }
}
