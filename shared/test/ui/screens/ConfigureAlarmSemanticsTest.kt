package com.muhammadridha.fjalar.ui.screens

import com.muhammadridha.fjalar.domain.model.AppLocale
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigureAlarmSemanticsTest {

    @Test
    fun radiusSliderSemanticDescription_formatsMetersAndKilometersEnglish() {
        assertEquals(
            "Trigger radius: 100m",
            configureAlarmRadiusSemanticDescription(100, AppLocale.EN),
        )
        assertEquals(
            "Trigger radius: 500m",
            configureAlarmRadiusSemanticDescription(500, AppLocale.EN),
        )
        assertEquals(
            "Trigger radius: 1 km",
            configureAlarmRadiusSemanticDescription(1000, AppLocale.EN),
        )
        assertEquals(
            "Trigger radius: 1.5 km",
            configureAlarmRadiusSemanticDescription(1500, AppLocale.EN),
        )
    }

    @Test
    fun radiusSliderSemanticDescription_formatsMetersAndKilometersIndonesian() {
        assertEquals(
            "Radius pemantauan: 100m",
            configureAlarmRadiusSemanticDescription(100, AppLocale.ID),
        )
        assertEquals(
            "Radius pemantauan: 500m",
            configureAlarmRadiusSemanticDescription(500, AppLocale.ID),
        )
        assertEquals(
            "Radius pemantauan: 1 km",
            configureAlarmRadiusSemanticDescription(1000, AppLocale.ID),
        )
        assertEquals(
            "Radius pemantauan: 1,5 km",
            configureAlarmRadiusSemanticDescription(1500, AppLocale.ID),
        )
    }

    @Test
    fun dayChipContentDescription_resolvesAllDaysOfWeekEnglish() {
        val expected = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        for (i in 0..6) {
            assertEquals(
                expected[i],
                configureAlarmDayChipContentDescription(i, AppLocale.EN),
                "Day index $i content description mismatch in English",
            )
        }
    }

    @Test
    fun dayChipContentDescription_resolvesAllDaysOfWeekIndonesian() {
        val expected = listOf("Sn", "Sl", "Rb", "Km", "Jm", "Sb", "Mg")
        for (i in 0..6) {
            assertEquals(
                expected[i],
                configureAlarmDayChipContentDescription(i, AppLocale.ID),
                "Day index $i content description mismatch in Indonesian",
            )
        }
    }

    @Test
    fun dayChipStateDescription_reportsSelectedAndNotSelectedInBothLocales() {
        assertEquals("selected", configureAlarmDayChipStateDescription(true, AppLocale.EN))
        assertEquals("not selected", configureAlarmDayChipStateDescription(false, AppLocale.EN))

        assertEquals("dipilih", configureAlarmDayChipStateDescription(true, AppLocale.ID))
        assertEquals("tidak dipilih", configureAlarmDayChipStateDescription(false, AppLocale.ID))
    }

    @Test
    fun radiusPresetRows_splitSubKilometerAndKilometerPillsAcrossFiveColumnGrid() {
        assertEquals(5, CONFIGURE_ALARM_PILL_GRID_COLUMNS)
        assertEquals(2, CONFIGURE_ALARM_RADIUS_PRESET_ROWS.size)
        assertEquals(
            listOf(50, 100, 200, 300, 500),
            CONFIGURE_ALARM_RADIUS_PRESET_ROWS[0].map { it.meters },
        )
        assertEquals(
            listOf(1_000, 2_000, 3_000),
            CONFIGURE_ALARM_RADIUS_PRESET_ROWS[1].map { it.meters },
        )
    }

    @Test
    fun dayChipRows_placeMondayThroughFridayInRowOneAndWeekendInRowTwo() {
        assertEquals(2, CONFIGURE_ALARM_DAY_CHIP_ROWS.size)
        assertEquals(listOf(0, 1, 2, 3, 4), CONFIGURE_ALARM_DAY_CHIP_ROWS[0])
        assertEquals(listOf(5, 6), CONFIGURE_ALARM_DAY_CHIP_ROWS[1])
    }
}
