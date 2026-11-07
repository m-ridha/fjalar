package com.muhammadridha.fjalar.ui.components

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmCardSemanticsTest {

    private val sampleCoordinates = GeoCoordinates(-6.202444, 106.823194)

    @Test
    fun scheduledArmedAlarmWithAddressGeneratesFullDescriptionEnglish() {
        val alarm = LocationAlarm(
            id = "alarm-1",
            label = "Work",
            addressLabel = "Jl. Jenderal Sudirman No. 1",
            coordinates = sampleCoordinates,
            radius = TriggerRadius(500),
            schedule = AlarmSchedule(
                type = ScheduleType.SCHEDULED,
                startMinuteOfDay = 480, // 08:00
                endMinuteOfDay = 540,   // 09:00
                daysOfWeekMask = AlarmSchedule.WEEKDAYS,
            ),
            soundConfig = AlarmSoundConfig(soundEnabled = true, vibrateEnabled = true),
            state = AlarmState.ARMED_MONITORING,
        )

        val description = alarmCardSemanticDescription(alarm, AppLocale.EN)
        assertEquals(
            "Work, Jl. Jenderal Sudirman No. 1, 08:00 – 09:00 • Mon–Fri, Trigger radius: 500m, Sound + vibration, Commute monitoring active",
            description,
        )
    }

    @Test
    fun scheduledAlarmSkippedTodayAnnouncesDoneForThisWindow() {
        val alarm = LocationAlarm(
            id = "alarm-2",
            label = "Office Commute",
            addressLabel = "Tower 2, Sudirman",
            coordinates = sampleCoordinates,
            radius = TriggerRadius(200),
            schedule = AlarmSchedule(
                type = ScheduleType.SCHEDULED,
                startMinuteOfDay = 420, // 07:00
                endMinuteOfDay = 570,   // 09:30
                daysOfWeekMask = AlarmSchedule.WEEKDAYS,
            ),
            soundConfig = AlarmSoundConfig(soundEnabled = true, vibrateEnabled = true),
            state = AlarmState.COMPLETED_FOR_WINDOW,
        )

        val description = alarmCardSemanticDescription(alarm, AppLocale.EN)
        assertEquals(
            "Office Commute, Tower 2, Sudirman, 07:00 – 09:30 • Mon–Fri, Trigger radius: 200m, Sound + vibration, Done for this window",
            description,
        )
    }

    @Test
    fun disabledAlarmOmitsAddressWhenNullAndAnnouncesInactive() {
        val alarm = LocationAlarm(
            id = "alarm-3",
            label = "Gym",
            addressLabel = null,
            coordinates = sampleCoordinates,
            radius = TriggerRadius(100),
            schedule = AlarmSchedule(
                type = ScheduleType.SCHEDULED,
                startMinuteOfDay = 360, // 06:00
                endMinuteOfDay = 420,   // 07:00
                daysOfWeekMask = AlarmSchedule.MONDAY or AlarmSchedule.WEDNESDAY or AlarmSchedule.FRIDAY,
            ),
            soundConfig = AlarmSoundConfig(soundEnabled = true, vibrateEnabled = false),
            state = AlarmState.DISABLED,
        )

        val description = alarmCardSemanticDescription(alarm, AppLocale.EN)
        assertEquals(
            "Gym, 06:00 – 07:00 • Mon, Wed, Fri, Trigger radius: 100m, Sound, inactive",
            description,
        )
    }

    @Test
    fun oneTimeAlarmAnnouncesOneTimeTripAndVibrateOnly() {
        val alarm = LocationAlarm(
            id = "alarm-4",
            label = "Airport Dropoff",
            addressLabel = "Terminal 3",
            coordinates = sampleCoordinates,
            radius = TriggerRadius(1_000),
            schedule = AlarmSchedule.ONE_TIME,
            soundConfig = AlarmSoundConfig(soundEnabled = false, vibrateEnabled = true),
            state = AlarmState.ARMED_MONITORING,
        )

        val description = alarmCardSemanticDescription(alarm, AppLocale.EN)
        assertEquals(
            "Airport Dropoff, Terminal 3, One-time trip • 1 km radius, Trigger radius: 1 km, Vibration, Commute monitoring active",
            description,
        )
    }

    @Test
    fun indonesianLocaleProducesCorrectPhrasingAcrossAllScenarios() {
        val scheduledAlarm = LocationAlarm(
            id = "alarm-id-1",
            label = "Kantor",
            addressLabel = "Jl. Sudirman No. 1",
            coordinates = sampleCoordinates,
            radius = TriggerRadius(500),
            schedule = AlarmSchedule(
                type = ScheduleType.SCHEDULED,
                startMinuteOfDay = 480, // 08:00
                endMinuteOfDay = 540,   // 09:00
                daysOfWeekMask = AlarmSchedule.WEEKDAYS,
            ),
            soundConfig = AlarmSoundConfig(soundEnabled = true, vibrateEnabled = true),
            state = AlarmState.ARMED_MONITORING,
        )
        assertEquals(
            "Kantor, Jl. Sudirman No. 1, 08:00 – 09:00 • Sn–Jm, Radius pemantauan: 500m, Suara + getaran, Pemantauan komuter aktif",
            alarmCardSemanticDescription(scheduledAlarm, AppLocale.ID),
        )

        val skippedAlarm = scheduledAlarm.copy(state = AlarmState.COMPLETED_FOR_WINDOW)
        assertEquals(
            "Kantor, Jl. Sudirman No. 1, 08:00 – 09:00 • Sn–Jm, Radius pemantauan: 500m, Suara + getaran, Selesai untuk periode ini",
            alarmCardSemanticDescription(skippedAlarm, AppLocale.ID),
        )

        val disabledAlarm = scheduledAlarm.copy(state = AlarmState.DISABLED)
        assertEquals(
            "Kantor, Jl. Sudirman No. 1, 08:00 – 09:00 • Sn–Jm, Radius pemantauan: 500m, Suara + getaran, tidak aktif",
            alarmCardSemanticDescription(disabledAlarm, AppLocale.ID),
        )

        val oneTimeAlarm = LocationAlarm(
            id = "alarm-id-2",
            label = "Stasiun",
            addressLabel = null,
            coordinates = sampleCoordinates,
            radius = TriggerRadius(1_200),
            schedule = AlarmSchedule.ONE_TIME,
            soundConfig = AlarmSoundConfig(soundEnabled = true, vibrateEnabled = false),
            state = AlarmState.ARMED_MONITORING,
        )
        assertEquals(
            "Stasiun, Sekali jalan • radius 1,2 km, Radius pemantauan: 1,2 km, Suara, Pemantauan komuter aktif",
            alarmCardSemanticDescription(oneTimeAlarm, AppLocale.ID),
        )
    }

    @Test
    fun deleteButtonContentDescriptionFormatsCorrectly() {
        val labelEn = FjalarStrings.get(FjalarStringKey.ACCESSIBLE_DELETE_ALARM, AppLocale.EN, "Morning Commute")
        assertEquals("Delete alarm Morning Commute", labelEn)

        val labelId = FjalarStrings.get(FjalarStringKey.ACCESSIBLE_DELETE_ALARM, AppLocale.ID, "Komuter Pagi")
        assertEquals("Hapus alarm Komuter Pagi", labelId)
    }
}
