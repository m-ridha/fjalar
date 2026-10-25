package com.muhammadridha.fjalar.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlin.time.Instant

enum class ScheduleType { ONE_TIME, SCHEDULED }

/**
 * When an alarm may monitor location. For [ScheduleType.SCHEDULED], the window runs from
 * [startMinuteOfDay] (inclusive) to [endMinuteOfDay] (exclusive) in the device time zone; an end
 * before the start crosses midnight. [daysOfWeekMask] selects the days a window may *start* on.
 */
data class AlarmSchedule(
    val type: ScheduleType,
    val startMinuteOfDay: Int? = null,
    val endMinuteOfDay: Int? = null,
    val daysOfWeekMask: Int = 0,
) {
    init {
        if (type == ScheduleType.SCHEDULED) {
            requireNotNull(startMinuteOfDay) { "scheduled alarm needs a start time" }
            requireNotNull(endMinuteOfDay) { "scheduled alarm needs an end time" }
            require(startMinuteOfDay in MINUTES_OF_DAY) { "start minute out of range: $startMinuteOfDay" }
            require(endMinuteOfDay in MINUTES_OF_DAY) { "end minute out of range: $endMinuteOfDay" }
            require(startMinuteOfDay != endMinuteOfDay) { "window must not be empty" }
            require(daysOfWeekMask in 1..EVERY_DAY) { "pick at least one valid day, mask was $daysOfWeekMask" }
        }
    }

    fun isActiveOn(day: DayOfWeek): Boolean = daysOfWeekMask and dayBit(day) != 0

    companion object {
        val MINUTES_OF_DAY = 0..1439

        const val MONDAY = 1
        const val TUESDAY = 2
        const val WEDNESDAY = 4
        const val THURSDAY = 8
        const val FRIDAY = 16
        const val SATURDAY = 32
        const val SUNDAY = 64
        const val WEEKDAYS = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY
        const val EVERY_DAY = WEEKDAYS or SATURDAY or SUNDAY

        val ONE_TIME = AlarmSchedule(ScheduleType.ONE_TIME)

        fun dayBit(day: DayOfWeek): Int = 1 shl (day.isoDayNumber - 1)
    }
}

/** A concrete occurrence of a schedule: [start] inclusive, [end] exclusive. */
data class TimeWindow(val start: Instant, val end: Instant) {
    operator fun contains(instant: Instant): Boolean = instant >= start && instant < end
}
