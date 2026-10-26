package com.muhammadridha.fjalar.adapters.scheduler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        assertEquals("Commute alarm window is active", adapter.resolveNotificationBody("inh-RU"))
        assertEquals("Commute alarm window is active", adapter.resolveNotificationBody(""))
    }

    @Test
    fun resolveNotificationTitle_bilingualLocalization() = runTest {
        val adapter = IosBackgroundSchedulerAdapter()
        assertEquals("Bangunkan Saya di", adapter.resolveNotificationTitle("id-ID"))
        assertEquals("Bangunkan Saya di", adapter.resolveNotificationTitle("in"))
        assertEquals("Wake Me at", adapter.resolveNotificationTitle("en-US"))
        assertEquals("Wake Me at", adapter.resolveNotificationTitle("inh-RU"))
        assertEquals("Wake Me at", adapter.resolveNotificationTitle(""))
    }

    @Test
    fun resolveNotificationTitle_customInjectedTitleResolver() = runTest {
        val customResolver = object : IosNotificationTitleResolver {
            override suspend fun resolveTitle(preferredLanguage: String?): String = "Custom Title"
        }
        val adapter = IosBackgroundSchedulerAdapter(titleResolver = customResolver)
        assertEquals("Custom Title", adapter.resolveNotificationTitle())
    }

    @Test
    fun resolveNotificationTitle_staticLocalizedTitleConstructor() = runTest {
        val adapter = IosBackgroundSchedulerAdapter(localizedTitle = "Fixed Title")
        assertEquals("Fixed Title", adapter.resolveNotificationTitle())
    }

    @Test
    fun resolveNotificationTitle_propagatesCancellationException() = runTest {
        val cancellingResolver = DefaultIosNotificationTitleResolver(
            appNameResourceProvider = { throw CancellationException("Scope cancelled") }
        )
        val adapter = IosBackgroundSchedulerAdapter(titleResolver = cancellingResolver)
        assertFailsWith<CancellationException> {
            adapter.resolveNotificationTitle()
        }
    }

    @Test
    fun resolveNotificationTitle_localizedBundleResolver_usesDynamicBundleLookup() = runTest {
        val dynamicResolver = DefaultIosNotificationTitleResolver(
            localizedBundleResolver = { lang ->
                if (lang == "id") "Bangunkan Saya (Bundle)" else null
            }
        )
        val adapter = IosBackgroundSchedulerAdapter(titleResolver = dynamicResolver)
        assertEquals("Bangunkan Saya (Bundle)", adapter.resolveNotificationTitle("id-ID"))
        assertEquals("Wake Me at", adapter.resolveNotificationTitle("en-US"))
    }
}
