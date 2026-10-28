package com.muhammadridha.fjalar.ui.components

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AppLocale
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentFormattersTest {

    @Test
    fun formatDistanceBelowKilometerFormatsMeters() {
        assertEquals("100m", formatDistance(100, AppLocale.EN))
        assertEquals("420m", formatDistance(420, AppLocale.EN))
        assertEquals("999m", formatDistance(999, AppLocale.EN))
    }

    @Test
    fun formatDistanceKilometersFormatsRounded() {
        assertEquals("1 km", formatDistance(1_000, AppLocale.EN))
        assertEquals("1.2 km", formatDistance(1_200, AppLocale.EN))
        assertEquals("2 km", formatDistance(2_000, AppLocale.EN))
        assertEquals("5 km", formatDistance(5_000, AppLocale.EN))
        assertEquals("10 km", formatDistance(10_000, AppLocale.EN))
    }

    @Test
    fun formatDistanceUsesIndonesianDecimalSeparator() {
        assertEquals("420m", formatDistance(420, AppLocale.ID))
        assertEquals("1 km", formatDistance(1_000, AppLocale.ID))
        assertEquals("1,2 km", formatDistance(1_200, AppLocale.ID))
    }

    @Test
    fun formatMinuteOfDayFormatsHoursAndMinutesWithPadding() {
        assertEquals("00:00", formatMinuteOfDay(0))
        assertEquals("07:00", formatMinuteOfDay(420))
        assertEquals("09:30", formatMinuteOfDay(570))
        assertEquals("23:59", formatMinuteOfDay(1439))
    }

    @Test
    fun dayChipLabelsReturnsLanguageSpecificAbbreviations() {
        assertEquals(
            listOf("M", "T", "W", "T", "F", "S", "S"),
            dayChipLabels(AppLocale.EN),
        )
        assertEquals(
            listOf("Sn", "Sl", "Rb", "Km", "Jm", "Sb", "Mg"),
            dayChipLabels(AppLocale.ID),
        )
    }

    @Test
    fun formatDaysOfWeekHandlesPresetAndCustomBitmasks() {
        assertEquals("Mon–Fri", formatDaysOfWeek(AlarmSchedule.WEEKDAYS, AppLocale.EN))
        assertEquals("Sn–Jm", formatDaysOfWeek(AlarmSchedule.WEEKDAYS, AppLocale.ID))

        assertEquals("Every day", formatDaysOfWeek(AlarmSchedule.EVERY_DAY, AppLocale.EN))
        assertEquals("Setiap hari", formatDaysOfWeek(AlarmSchedule.EVERY_DAY, AppLocale.ID))

        // Mon (1) + Wed (4)
        val monWed = AlarmSchedule.MONDAY or AlarmSchedule.WEDNESDAY
        assertEquals("Mon, Wed", formatDaysOfWeek(monWed, AppLocale.EN))
        assertEquals("Sn, Rb", formatDaysOfWeek(monWed, AppLocale.ID))
    }
}
