package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.ui.screens.ConfigureAlarmDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlarmEditorExitTest {
    private val valid = ConfigureAlarmDraft(
        coordinates = GeoCoordinates(-6.2, 106.8),
        label = "Station",
    )

    @Test
    fun destinationPrecedesNameAndNamePrecedesSchedule() {
        assertEquals(AlarmEditorNotice.NO_DESTINATION, ConfigureAlarmDraft().validationNotice())
        assertEquals(AlarmEditorNotice.NAME_REQUIRED, valid.copy(label = " ").validationNotice())
        assertEquals(
            AlarmEditorNotice.DIFFERENT_TIMES_REQUIRED,
            valid.copy(startMinuteOfDay = 420, endMinuteOfDay = 420, daysOfWeekMask = 0).validationNotice(),
        )
    }

    @Test
    fun daysAndRadiusAreValidatedAfterTimes() {
        assertEquals(AlarmEditorNotice.TIME_INVALID, valid.copy(startMinuteOfDay = -1).validationNotice())
        assertEquals(AlarmEditorNotice.TIME_INVALID, valid.copy(endMinuteOfDay = 1440).validationNotice())
        assertEquals(AlarmEditorNotice.DAYS_REQUIRED, valid.copy(daysOfWeekMask = 0).validationNotice())
        assertEquals(AlarmEditorNotice.DAYS_INVALID, valid.copy(daysOfWeekMask = 128).validationNotice())
        assertEquals(AlarmEditorNotice.RADIUS_INVALID, valid.copy(radiusMeters = 49).validationNotice())
        assertEquals(AlarmEditorNotice.RADIUS_INVALID, valid.copy(radiusMeters = 10_001).validationNotice())
    }

    @Test
    fun midnightWindowAndOneTimeInactiveScheduleFieldsAreValid() {
        assertNull(valid.copy(startMinuteOfDay = 23 * 60, endMinuteOfDay = 2 * 60).validationNotice())
        assertNull(
            valid.copy(
                scheduleType = ScheduleType.ONE_TIME,
                startMinuteOfDay = -1,
                endMinuteOfDay = -1,
                daysOfWeekMask = 0,
            ).validationNotice(),
        )
        assertEquals(AlarmSchedule.WEEKDAYS, valid.daysOfWeekMask)
    }
}
