package com.muhammadridha.fjalar.ui.components

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AppLocale
import kotlin.math.roundToInt

fun formatDistance(meters: Int): String {
    return if (meters >= 1_000) {
        val km = meters / 1_000.0
        val rounded = (km * 10).roundToInt() / 10.0
        if (rounded % 1.0 == 0.0) "${rounded.toInt()} km" else "$rounded km"
    } else {
        "${meters}m"
    }
}

fun formatMinuteOfDay(minute: Int): String {
    val h = (minute / 60) % 24
    val m = minute % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

val ENGLISH_DAY_CHIPS = listOf("M", "T", "W", "T", "F", "S", "S")
val INDONESIAN_DAY_CHIPS = listOf("Sn", "Sl", "Rb", "Km", "Jm", "Sb", "Mg")

fun dayChipLabels(locale: AppLocale): List<String> = when (locale) {
    AppLocale.EN -> ENGLISH_DAY_CHIPS
    AppLocale.ID -> INDONESIAN_DAY_CHIPS
}

fun formatDaysOfWeek(mask: Int, locale: AppLocale): String {
    if (mask == AlarmSchedule.WEEKDAYS) {
        return if (locale == AppLocale.ID) "Sn–Jm" else "Mon–Fri"
    }
    if (mask == AlarmSchedule.EVERY_DAY) {
        return if (locale == AppLocale.ID) "Setiap hari" else "Every day"
    }

    val dayNames = if (locale == AppLocale.ID) {
        listOf("Sn", "Sl", "Rb", "Km", "Jm", "Sb", "Mg")
    } else {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }

    val activeDays = (0..6).filter { i -> (mask and (1 shl i)) != 0 }.map { dayNames[it] }
    return if (activeDays.isEmpty()) "" else activeDays.joinToString(", ")
}
