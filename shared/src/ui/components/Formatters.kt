package com.muhammadridha.fjalar.ui.components

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import kotlin.math.roundToInt

fun formatDistance(meters: Int, locale: AppLocale): String {
    val text = if (meters >= 1_000) {
        val km = meters / 1_000.0
        val rounded = (km * 10).roundToInt() / 10.0
        if (rounded % 1.0 == 0.0) "${rounded.toInt()} km" else "$rounded km"
    } else {
        "${meters}m"
    }
    return if (locale == AppLocale.ID) text.replace('.', ',') else text
}

fun armedStatusText(alarm: LocationAlarm, distanceMeters: Int?, locale: AppLocale): String {
    val scheduled = alarm.schedule.type == ScheduleType.SCHEDULED
    val start = formatMinuteOfDay(alarm.schedule.startMinuteOfDay ?: 0)
    val end = formatMinuteOfDay(alarm.schedule.endMinuteOfDay ?: 0)
    if (distanceMeters == null) {
        return if (scheduled) {
            FjalarStrings.get(FjalarStringKey.STATUS_ARMED_SCHEDULED_UNKNOWN, locale, alarm.label, start, end)
        } else {
            FjalarStrings.get(FjalarStringKey.STATUS_ARMED_ONE_TIME_UNKNOWN, locale, alarm.label)
        }
    }
    val distance = formatDistance(distanceMeters, locale)
    return if (scheduled) {
        FjalarStrings.get(FjalarStringKey.STATUS_ARMED_SCHEDULED, locale, alarm.label, distance, start, end)
    } else {
        FjalarStrings.get(FjalarStringKey.STATUS_ARMED_ONE_TIME, locale, alarm.label, distance)
    }
}

fun ringingDistanceText(distanceMeters: Int?, locale: AppLocale): String {
    if (distanceMeters == null) return FjalarStrings.get(FjalarStringKey.DISTANCE_UNAVAILABLE, locale)
    return FjalarStrings.get(FjalarStringKey.RINGING_IN_ZONE_BADGE, locale, formatDistance(distanceMeters, locale))
}

fun formatMinuteOfDay(minute: Int): String {
    val h = (minute / 60) % 24
    val m = minute % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

fun dayChipLabels(locale: AppLocale): List<String> = listOf(
    FjalarStringKey.DAY_CHIP_MONDAY,
    FjalarStringKey.DAY_CHIP_TUESDAY,
    FjalarStringKey.DAY_CHIP_WEDNESDAY,
    FjalarStringKey.DAY_CHIP_THURSDAY,
    FjalarStringKey.DAY_CHIP_FRIDAY,
    FjalarStringKey.DAY_CHIP_SATURDAY,
    FjalarStringKey.DAY_CHIP_SUNDAY,
).map { key -> FjalarStrings.get(key, locale) }

fun formatDaysOfWeek(mask: Int, locale: AppLocale): String {
    if (mask == AlarmSchedule.WEEKDAYS) {
        return FjalarStrings.get(FjalarStringKey.DAYS_WEEKDAYS, locale)
    }
    if (mask == AlarmSchedule.EVERY_DAY) {
        return FjalarStrings.get(FjalarStringKey.DAYS_EVERY_DAY, locale)
    }

    val dayNames = listOf(
        FjalarStringKey.DAY_NAME_MONDAY,
        FjalarStringKey.DAY_NAME_TUESDAY,
        FjalarStringKey.DAY_NAME_WEDNESDAY,
        FjalarStringKey.DAY_NAME_THURSDAY,
        FjalarStringKey.DAY_NAME_FRIDAY,
        FjalarStringKey.DAY_NAME_SATURDAY,
        FjalarStringKey.DAY_NAME_SUNDAY,
    ).map { FjalarStrings.get(it, locale) }

    val activeDays = (0..6).filter { i -> (mask and (1 shl i)) != 0 }.map { dayNames[it] }
    return if (activeDays.isEmpty()) "" else activeDays.joinToString(", ")
}
