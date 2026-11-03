package com.muhammadridha.fjalar.adapters.scheduler

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.domain.model.AppLocale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosBackgroundSchedulerAdapterTest {

    @Test
    fun notificationCopyUsesSavedAppLanguageForTitleWindowAndSnooze() = runTest {
        val storage = InMemoryKeyValueStorage()
        val preferences = PersistentPreferencesAdapter(storage)
        val adapter = IosBackgroundSchedulerAdapter(
            localeProvider = { PersistentPreferencesAdapter.readLocale(storage) },
        )
        preferences.setLocale(AppLocale.ID)
        assertEquals("Bangunkan Saya di", adapter.resolveNotificationTitle())
        assertEquals(
            "Waktu alarm terjadwal sudah dimulai. Buka aplikasi untuk memeriksa alarm.",
            adapter.resolveNotificationBody(),
        )
        assertEquals("Waktu tunda selesai. Buka aplikasi untuk menghentikan alarm.", adapter.resolveSnoozeBody())
        preferences.setLocale(AppLocale.EN)
        assertEquals("Wake Me at", adapter.resolveNotificationTitle())
        assertEquals("Snooze ended. Open the app to stop the alarm.", adapter.resolveSnoozeBody())
        assertEquals("Your scheduled time has started. Open the app to check your alarm.", adapter.resolveNotificationBody())
    }

    @Test
    fun snoozeRequiresAuthorizedAlertsAndConfiguredSound() {
        kotlin.test.assertFalse(snoozeDeliveryAvailable(false, true, true, true))
        kotlin.test.assertFalse(snoozeDeliveryAvailable(true, false, true, true))
        kotlin.test.assertFalse(snoozeDeliveryAvailable(true, true, false, true))
        kotlin.test.assertTrue(snoozeDeliveryAvailable(true, true, false, false))
        kotlin.test.assertTrue(snoozeDeliveryAvailable(true, true, true, true))
    }

    @Test
    fun resolveNotificationBody_indonesianPrefix_returnsIndonesianString() {
        val adapter = IosBackgroundSchedulerAdapter()
        assertEquals("Waktu alarm terjadwal sudah dimulai. Buka aplikasi untuk memeriksa alarm.", adapter.resolveNotificationBody("id-ID"))
        assertEquals("Waktu alarm terjadwal sudah dimulai. Buka aplikasi untuk memeriksa alarm.", adapter.resolveNotificationBody("id"))
        assertEquals("Waktu alarm terjadwal sudah dimulai. Buka aplikasi untuk memeriksa alarm.", adapter.resolveNotificationBody("in-ID"))
    }

    @Test
    fun resolveNotificationBody_englishOrOther_returnsDefaultString() {
        val adapter = IosBackgroundSchedulerAdapter()
        assertEquals("Your scheduled time has started. Open the app to check your alarm.", adapter.resolveNotificationBody("en-US"))
        assertEquals("Your scheduled time has started. Open the app to check your alarm.", adapter.resolveNotificationBody("en"))
        assertEquals("Your scheduled time has started. Open the app to check your alarm.", adapter.resolveNotificationBody("fr-FR"))
        assertEquals("Your scheduled time has started. Open the app to check your alarm.", adapter.resolveNotificationBody("inh-RU"))
        assertEquals("Your scheduled time has started. Open the app to check your alarm.", adapter.resolveNotificationBody(""))
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

    @Test
    fun resolveLocalizedBundleTitle_blankOrNullLanguage_returnsNull() {
        assertEquals(null, DefaultIosNotificationTitleResolver.resolveLocalizedBundleTitle(null))
        assertEquals(null, DefaultIosNotificationTitleResolver.resolveLocalizedBundleTitle(""))
        assertEquals(null, DefaultIosNotificationTitleResolver.resolveLocalizedBundleTitle("   "))
    }

    @Test
    fun resolveLocalizedBundleTitle_prefersDisplayNameWhenValid() {
        val title = DefaultIosNotificationTitleResolver.resolveLocalizedBundleTitle(
            languageCode = "id",
            bundleLookup = { lang, key ->
                when (key) {
                    "CFBundleDisplayName" -> "Bangunkan Saya di"
                    "CFBundleName" -> "Fjalar"
                    else -> null
                }
            }
        )
        assertEquals("Bangunkan Saya di", title)
    }

    @Test
    fun resolveLocalizedBundleTitle_fallsBackToBundleNameWhenDisplayNameMissingOrPlaceholder() {
        // When CFBundleDisplayName is missing, localizedStringForKey returns the key placeholder
        val title = DefaultIosNotificationTitleResolver.resolveLocalizedBundleTitle(
            languageCode = "id",
            bundleLookup = { lang, key ->
                when (key) {
                    "CFBundleDisplayName" -> null // simulated rejected placeholder/null
                    "CFBundleName" -> "Fjalar"
                    else -> null
                }
            }
        )
        assertEquals("Fjalar", title)
    }

    @Test
    fun resolveLocalizedBundleTitle_returnsNullWhenBothKeysMissingOrBlank() {
        val title = DefaultIosNotificationTitleResolver.resolveLocalizedBundleTitle(
            languageCode = "id",
            bundleLookup = { _, _ -> null }
        )
        assertEquals(null, title)
    }
}
