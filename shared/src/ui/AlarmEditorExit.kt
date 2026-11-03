package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.ui.screens.ConfigureAlarmDraft

enum class AlarmEditorNotice {
    NO_DESTINATION,
    NAME_REQUIRED,
    TIME_INVALID,
    DIFFERENT_TIMES_REQUIRED,
    DAYS_REQUIRED,
    DAYS_INVALID,
    RADIUS_INVALID,
    ALARM_DELETED,
    UPDATE_FAILED,
}

fun ConfigureAlarmDraft.validationNotice(): AlarmEditorNotice? {
    if (coordinates == null) return AlarmEditorNotice.NO_DESTINATION
    if (label.isBlank()) return AlarmEditorNotice.NAME_REQUIRED
    if (scheduleType == ScheduleType.SCHEDULED) {
        if (startMinuteOfDay !in AlarmSchedule.MINUTES_OF_DAY ||
            endMinuteOfDay !in AlarmSchedule.MINUTES_OF_DAY
        ) {
            return AlarmEditorNotice.TIME_INVALID
        }
        if (startMinuteOfDay == endMinuteOfDay) return AlarmEditorNotice.DIFFERENT_TIMES_REQUIRED
        if (daysOfWeekMask == 0) return AlarmEditorNotice.DAYS_REQUIRED
        if (daysOfWeekMask !in 1..AlarmSchedule.EVERY_DAY) return AlarmEditorNotice.DAYS_INVALID
    }
    if (radiusMeters !in TriggerRadius.MIN_METERS..TriggerRadius.MAX_METERS) {
        return AlarmEditorNotice.RADIUS_INVALID
    }
    return null
}
