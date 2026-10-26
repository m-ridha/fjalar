package com.muhammadridha.fjalar.adapters.scheduler

import kotlin.test.Test
import kotlin.test.assertEquals

class IosBackgroundSchedulerAdapterTest {

    @Test
    fun resolveNotificationBody_indonesianPrefix_returnsIndonesianString() {
        val adapter = IosBackgroundSchedulerAdapter()
        assertEquals("Jendela alarm perjalanan aktif", adapter.resolveNotificationBody("id-ID"))
        assertEquals("Jendela alarm perjalanan aktif", adapter.resolveNotificationBody("id"))
        assertEquals("Jendela alarm perjalanan aktif", adapter.resolveNotificationBody("in-ID"))
    }

    @Test
    fun resolveNotificationBody_englishOrOther_returnsDefaultString() {
        val adapter = IosBackgroundSchedulerAdapter()
        assertEquals("Commute alarm window is active", adapter.resolveNotificationBody("en-US"))
        assertEquals("Commute alarm window is active", adapter.resolveNotificationBody("en"))
        assertEquals("Commute alarm window is active", adapter.resolveNotificationBody("fr-FR"))
        assertEquals("Commute alarm window is active", adapter.resolveNotificationBody(""))
    }

    @Test
    fun resolveNotificationTitle_bilingualLocalization() {
        val adapter = IosBackgroundSchedulerAdapter()
        assertEquals("Bangunkan saya di sana", adapter.resolveNotificationTitle("id-ID"))
        assertEquals("Bangunkan saya di sana", adapter.resolveNotificationTitle("in"))
        assertEquals("Wake me up there", adapter.resolveNotificationTitle("en-US"))
        assertEquals("Wake me up there", adapter.resolveNotificationTitle(""))
    }
}
