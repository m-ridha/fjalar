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

/**
 * Pure alarm state machine:
 * DISABLED → (enable) → SCHEDULED_IDLE ⇄ ARMED_MONITORING (window opens/closes) → RINGING (fix inside radius)
 * → COMPLETED_FOR_WINDOW (dismiss/skip) → SCHEDULED_IDLE once the window is over.
 * One-time alarms go straight to ARMED_MONITORING and back to DISABLED when dismissed.
 */
class AlarmEvaluator(private val scheduleEvaluator: ScheduleEvaluator) {

    fun enable(alarm: LocationAlarm, now: Instant): LocationAlarm = when (alarm.schedule.type) {
        ScheduleType.ONE_TIME -> alarm.copy(state = ARMED_MONITORING, suppressedUntil = null)
        ScheduleType.SCHEDULED -> withWindowState(alarm.copy(suppressedUntil = null), now)
    }

    fun disable(alarm: LocationAlarm): LocationAlarm = alarm.copy(state = DISABLED, suppressedUntil = null)

    /** Moves scheduled alarms in or out of monitoring as windows open and close. */
    fun syncSchedule(alarm: LocationAlarm, now: Instant): LocationAlarm {
        if (alarm.schedule.type != ScheduleType.SCHEDULED) return alarm
        return when (alarm.state) {
            // A ringing alarm keeps ringing past the window end until the user stops it.
            DISABLED, RINGING -> alarm
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
        val from = maxOf(now, alarm.suppressedUntil ?: now)
        val window = checkNotNull(scheduleEvaluator.currentOrNextWindow(alarm.schedule, from))
        return alarm.copy(state = COMPLETED_FOR_WINDOW, suppressedUntil = window.end)
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
