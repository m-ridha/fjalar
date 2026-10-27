package com.muhammadridha.fjalar.ui.screens

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.ui.components.formatDeleteMessage
import com.muhammadridha.fjalar.ui.screens.calculateActiveCount
import com.muhammadridha.fjalar.ui.screens.formatActiveCountBadge
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmsScreenTest {

    private fun createTestAlarm(
        id: String = "alarm-1",
        label: String = "Sudirman Station",
        isEnabled: Boolean = true,
    ): LocationAlarm {
        return LocationAlarm(
            id = id,
            label = label,
            coordinates = GeoCoordinates(-6.2024, 106.8236),
            radius = TriggerRadius(500),
            schedule = AlarmSchedule.ONE_TIME,
            soundConfig = AlarmSoundConfig(),
            state = if (isEnabled) AlarmState.ARMED_MONITORING else AlarmState.DISABLED,
        )
    }

    @Test
    fun calculateActiveCount_countsOnlyEnabledAlarms() {
        val alarms = listOf(
            createTestAlarm(id = "1", isEnabled = true),
            createTestAlarm(id = "2", isEnabled = false),
            createTestAlarm(id = "3", isEnabled = true),
        )
        assertEquals(2, calculateActiveCount(alarms))
        assertEquals(0, calculateActiveCount(emptyList()))
        assertEquals(0, calculateActiveCount(listOf(createTestAlarm(id = "4", isEnabled = false))))
    }

    @Test
    fun formatActiveCountBadge_formatsBilingually() {
        assertEquals("2 Active", formatActiveCountBadge(2, AppLocale.EN))
        assertEquals("2 Aktif", formatActiveCountBadge(2, AppLocale.ID))
        assertEquals("0 Active", formatActiveCountBadge(0, AppLocale.EN))
        assertEquals("0 Aktif", formatActiveCountBadge(0, AppLocale.ID))
    }

    @Test
    fun formatDeleteConfirmationMessage_formatsBilinguallyWithLabel() {
        val alarm = createTestAlarm(label = "Manggarai Morning")
        val enMessage = formatDeleteMessage(alarm.label, AppLocale.EN)
        val idMessage = formatDeleteMessage(alarm.label, AppLocale.ID)

        assertEquals("Are you sure you want to delete 'Manggarai Morning'?", enMessage)
        assertEquals("Apakah Anda yakin ingin menghapus 'Manggarai Morning'?", idMessage)
    }

    @Test
    fun emptyStateStrings_resolveCorrectlyBilingually() {
        val enTitle = FjalarStrings.get(FjalarStringKey.ALARMS_EMPTY_TITLE, AppLocale.EN)
        val enBody = FjalarStrings.get(FjalarStringKey.ALARMS_EMPTY_BODY, AppLocale.EN)
        val idTitle = FjalarStrings.get(FjalarStringKey.ALARMS_EMPTY_TITLE, AppLocale.ID)
        val idBody = FjalarStrings.get(FjalarStringKey.ALARMS_EMPTY_BODY, AppLocale.ID)

        assertEquals("No Alarms Yet", enTitle)
        assertEquals("Tap + Add to set your first wake-up alarm.", enBody)
        assertEquals("Belum Ada Alarm", idTitle)
        assertEquals("Ketuk + Tambah untuk menyetel alarm bangun pertama Anda.", idBody)
    }

    @Test
    fun deleteDialogLabels_resolveCorrectlyBilingually() {
        val enConfirm = FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, AppLocale.EN)
        val enCancel = FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CANCEL, AppLocale.EN)
        val idConfirm = FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, AppLocale.ID)
        val idCancel = FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CANCEL, AppLocale.ID)

        assertEquals("Delete", enConfirm)
        assertEquals("Cancel", enCancel)
        assertEquals("Hapus", idConfirm)
        assertEquals("Batal", idCancel)
    }

}
