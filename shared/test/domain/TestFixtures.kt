package com.muhammadridha.fjalar.domain

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

val JAKARTA: TimeZone = TimeZone.of("Asia/Jakarta")

/** Wall-clock time in Jakarta. 2026-10-26 is a Monday. */
fun jakarta(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
    LocalDateTime(year, month, day, hour, minute).toInstant(JAKARTA)

val SUDIRMAN = GeoCoordinates(-6.202444, 106.823194)
val MANGGARAI = GeoCoordinates(-6.209944, 106.850306)

fun minuteOfDay(hour: Int, minute: Int = 0) = hour * 60 + minute

/** Office commute: 07:00–09:30, Mon–Fri. */
val WEEKDAY_COMMUTE = AlarmSchedule(
    type = ScheduleType.SCHEDULED,
    startMinuteOfDay = minuteOfDay(7),
    endMinuteOfDay = minuteOfDay(9, 30),
    daysOfWeekMask = AlarmSchedule.WEEKDAYS,
)

fun manggaraiAlarm(
    schedule: AlarmSchedule = WEEKDAY_COMMUTE,
    radius: TriggerRadius = TriggerRadius(500),
) = LocationAlarm(
    id = "manggarai",
    label = "Stasiun Manggarai",
    coordinates = MANGGARAI,
    radius = radius,
    schedule = schedule,
)
