package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TimeWindow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class ScheduleEvaluator(private val timeZone: TimeZone) {

    /** The window containing [instant], or null. Always null for one-time alarms. */
    fun activeWindowAt(schedule: AlarmSchedule, instant: Instant): TimeWindow? {
        if (schedule.type != ScheduleType.SCHEDULED) return null
        val today = instant.toLocalDateTime(timeZone).date
        // A midnight-crossing window that started yesterday may still be open.
        return listOf(today.minus(1, DateTimeUnit.DAY), today)
            .firstNotNullOfOrNull { day -> windowStartingOn(schedule, day)?.takeIf { instant in it } }
    }

    fun isActive(schedule: AlarmSchedule, instant: Instant): Boolean = activeWindowAt(schedule, instant) != null

    /** The window containing [instant], else the first one starting after it. Null for one-time alarms. */
    fun currentOrNextWindow(schedule: AlarmSchedule, instant: Instant): TimeWindow? {
        if (schedule.type != ScheduleType.SCHEDULED) return null
        activeWindowAt(schedule, instant)?.let { return it }
        val today = instant.toLocalDateTime(timeZone).date
        return (0..7).firstNotNullOfOrNull { offset ->
            windowStartingOn(schedule, today.plus(offset, DateTimeUnit.DAY))?.takeIf { it.start > instant }
        }
    }

    private fun windowStartingOn(schedule: AlarmSchedule, day: LocalDate): TimeWindow? {
        if (!schedule.isActiveOn(day.dayOfWeek)) return null
        val start = schedule.startMinuteOfDay!!
        val end = schedule.endMinuteOfDay!!
        val endDay = if (end < start) day.plus(1, DateTimeUnit.DAY) else day
        return TimeWindow(day.atMinute(start), endDay.atMinute(end))
    }

    private fun LocalDate.atMinute(minuteOfDay: Int): Instant =
        LocalDateTime(this, LocalTime(minuteOfDay / 60, minuteOfDay % 60)).toInstant(timeZone)
}
