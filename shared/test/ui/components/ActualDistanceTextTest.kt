package com.muhammadridha.fjalar.ui.components

import com.muhammadridha.fjalar.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ActualDistanceTextTest {
    private val alarm = LocationAlarm("a", "Station", GeoCoordinates(0.0, 0.0), TriggerRadius(500), AlarmSchedule.ONE_TIME)

    @Test fun unknownDistanceNeverFallsBackToTriggerRadius() {
        assertEquals("Armed: Station • Location unavailable", armedStatusText(alarm, null, AppLocale.EN))
        assertEquals("Aktif: Station • Lokasi tidak tersedia", armedStatusText(alarm, null, AppLocale.ID))
        assertEquals("Distance unavailable", ringingDistanceText(null, AppLocale.EN))
        assertEquals("Jarak tidak tersedia", ringingDistanceText(null, AppLocale.ID))
        assertFalse(armedStatusText(alarm, null, AppLocale.EN).contains("500"))
    }

    @Test fun actualDistancesFormatInEnglishAndIndonesian() {
        assertEquals("Armed: Station • 0m away", armedStatusText(alarm, 0, AppLocale.EN))
        assertEquals("Aktif: Station • 1,2 km lagi", armedStatusText(alarm, 1200, AppLocale.ID))
        assertEquals("1.2 km from destination", ringingDistanceText(1200, AppLocale.EN))
        assertEquals("420m dari tujuan", ringingDistanceText(420, AppLocale.ID))
    }
}
