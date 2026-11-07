package com.muhammadridha.fjalar

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.localization.FjalarStringKey
import com.muhammadridha.fjalar.localization.FjalarStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationQuickActionTest {

    @Test
    fun disarmConstantsMatchExpectedContract() {
        assertEquals(
            "com.muhammadridha.fjalar.ACTION_DISARM_ACTIVE_ALARM",
            FjalarLocationService.ACTION_DISARM_ACTIVE_ALARM,
        )
        assertEquals(
            "extra_alarm_id",
            FjalarLocationService.EXTRA_ALARM_ID,
        )
        assertEquals(
            1003,
            FjalarLocationService.DISARM_REQUEST_CODE,
        )
    }

    @Test
    fun localizedStringsProvideAccurateBilingualCopy() {
        assertEquals("Skip Today", FjalarStrings.get(FjalarStringKey.NOTIF_ACTION_SKIP_TODAY, AppLocale.EN))
        assertEquals("Lewati Hari Ini", FjalarStrings.get(FjalarStringKey.NOTIF_ACTION_SKIP_TODAY, AppLocale.ID))

        assertEquals("Stop Monitoring", FjalarStrings.get(FjalarStringKey.NOTIF_ACTION_STOP_MONITORING, AppLocale.EN))
        assertEquals("Hentikan", FjalarStrings.get(FjalarStringKey.NOTIF_ACTION_STOP_MONITORING, AppLocale.ID))
    }
}

