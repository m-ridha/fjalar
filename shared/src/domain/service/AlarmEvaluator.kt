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
        ScheduleType.ONE_TIME -> alarm.copy(state = ARMED_MONITORING, suppressedUntil = null, snoozedUntil = null)
        ScheduleType.SCHEDULED -> withWindowState(alarm.copy(suppressedUntil = null, snoozedUntil = null), now)
    }

    fun disable(alarm: LocationAlarm): LocationAlarm = alarm.copy(state = DISABLED, suppressedUntil = null, snoozedUntil = null)

    /** Editing mode keeps disabled/ringing states and any unfinished completion guard. */
    fun prepareSave(previous: LocationAlarm?, edited: LocationAlarm, now: Instant): LocationAlarm {
        if (previous == null || previous.schedule.type == edited.schedule.type) {
            return syncSchedule(edited, now)
        }
        val preserved = edited.copy(state = previous.state, suppressedUntil = previous.suppressedUntil, snoozedUntil = previous.snoozedUntil)
        if (preserved.state == SNOOZED) return syncSchedule(preserved, now)
        if (preserved.state == DISABLED || preserved.state == RINGING) return preserved
        if (preserved.suppressedUntil != null && now < preserved.suppressedUntil) {
            return preserved.copy(state = COMPLETED_FOR_WINDOW)
        }
        return enable(preserved, now)
    }

    /** Moves scheduled alarms in or out of monitoring as windows open and close. */
    fun syncSchedule(alarm: LocationAlarm, now: Instant): LocationAlarm {
        if (alarm.state == SNOOZED) {
            // The original window end survives process death and prevents a late wakeup
            // from ringing in a different window.
            if (alarm.schedule.type == ScheduleType.SCHEDULED &&
                (alarm.suppressedUntil == null || now >= alarm.suppressedUntil ||
                    !scheduleEvaluator.isActive(alarm.schedule, now))) {
                return withWindowState(alarm.copy(snoozedUntil = null, suppressedUntil = null), now)
            }
            val deadline = alarm.snoozedUntil
            if (deadline != null && now < deadline) return alarm
            return alarm.copy(state = RINGING, snoozedUntil = null, suppressedUntil = null)
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
        val inside = HaversineCalculator.distanceMeters(position, alarm.coordinates) <= alarm.radius.meters
        return if (inside) alarm.copy(state = RINGING) else alarm
    }

    fun snooze(alarm: LocationAlarm, now: Instant): LocationAlarm {
        check(alarm.state == RINGING) { "alarm ${alarm.id} is not ringing" }
        return alarm.copy(
            state = SNOOZED,
            snoozedUntil = now + 2.minutes,
            suppressedUntil = if (alarm.schedule.type == ScheduleType.SCHEDULED) {
                scheduleEvaluator.activeWindowAt(alarm.schedule, now)?.end ?: now
            } else null,
        )
    }

    fun dismiss(alarm: LocationAlarm, now: Instant): LocationAlarm {
        check(alarm.state == RINGING) { "alarm ${alarm.id} is not ringing" }
        return when (alarm.schedule.type) {
            ScheduleType.ONE_TIME -> alarm.copy(state = DISABLED)
            ScheduleType.SCHEDULED -> alarm.copy(
                state = COMPLETED_FOR_WINDOW,
                suppressedUntil = scheduleEvaluator.activeWindowAt(alarm.schedule, now)?.end ?: now,
            )
        }
    }

    /** Skips the current window, or the next one if none is open. Repeated skips move further ahead. */
    fun skip(alarm: LocationAlarm, now: Instant): LocationAlarm {
        check(alarm.schedule.type == ScheduleType.SCHEDULED) { "only scheduled alarms can be skipped" }
        check(alarm.state != DISABLED) { "alarm ${alarm.id} is disabled" }
        val from = if (alarm.state == SNOOZED) now else maxOf(now, alarm.suppressedUntil ?: now)
        val window = checkNotNull(scheduleEvaluator.currentOrNextWindow(alarm.schedule, from))
        return alarm.copy(state = COMPLETED_FOR_WINDOW, suppressedUntil = window.end, snoozedUntil = null)
    }

    private fun withWindowState(alarm: LocationAlarm, now: Instant): LocationAlarm {
        val state: AlarmState = if (scheduleEvaluator.isActive(alarm.schedule, now)) ARMED_MONITORING else SCHEDULED_IDLE
        return alarm.copy(state = state)
    }

    companion object {
        const val MAX_ACCURACY_TO_RADIUS_RATIO = 0.8

        /** Rejects coarse fixes (cell-tower spikes) that could falsely place the user inside the radius. */
        fun isAccurateEnough(horizontalAccuracyMeters: Float, radius: TriggerRadius): Boolean =
            horizontalAccuracyMeters >= 0f && horizontalAccuracyMeters <= radius.meters * MAX_ACCURACY_TO_RADIUS_RATIO
    }
}
