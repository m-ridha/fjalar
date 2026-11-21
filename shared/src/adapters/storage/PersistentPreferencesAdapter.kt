package com.muhammadridha.fjalar.adapters.storage

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Concrete outbound persistence adapter for [PreferencesPort] backed by [KeyValueStorage].
 * Exposes reactive state flows for UI theme mode and application locale.
 */
class PersistentPreferencesAdapter(
    private val storage: KeyValueStorage,
) : PreferencesPort {

    private val themeModeFlow = MutableStateFlow(loadThemeMode())
    private val localeFlow = MutableStateFlow(loadLocale())

    override fun peekThemeMode(): ThemeMode = themeModeFlow.value

    override fun observeThemeMode(): Flow<ThemeMode> = themeModeFlow.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        storage.putString(KEY_THEME_MODE, mode.name)
        themeModeFlow.value = mode
    }

    override fun peekLastKnownLocation(): GeoCoordinates? = readLastKnownLocation(storage)

    override fun rememberLastKnownLocation(coordinates: GeoCoordinates) {
        storage.putString(KEY_LAST_KNOWN_LOCATION, "${coordinates.latitude},${coordinates.longitude}")
    }

    override fun observeLocale(): Flow<AppLocale> = localeFlow.asStateFlow()

    override suspend fun setLocale(locale: AppLocale) {
        storage.putString(KEY_LOCALE, locale.name)
        localeFlow.value = locale
    }

    private fun loadThemeMode(): ThemeMode = readThemeMode(storage)

    private fun loadLocale(): AppLocale = readLocale(storage)

    companion object {
        /** Synchronous read used by the adapter and by platform code that runs before DI starts. */
        fun readThemeMode(storage: KeyValueStorage): ThemeMode {
            val stored = storage.getString(KEY_THEME_MODE) ?: return ThemeMode.SYSTEM
            return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
        }

        fun readLocale(storage: KeyValueStorage): AppLocale {
            val stored = storage.getString(KEY_LOCALE) ?: return AppLocale.EN
            return AppLocale.entries.firstOrNull {
                it.name.equals(stored, ignoreCase = true) || it.tag.equals(stored, ignoreCase = true)
            } ?: AppLocale.EN
        }

        /**
         * Parses the stored position defensively: malformed pairs and out-of-range values are
         * treated as "no stored position" rather than failing startup.
         */
        fun readLastKnownLocation(storage: KeyValueStorage): GeoCoordinates? {
            val stored = storage.getString(KEY_LAST_KNOWN_LOCATION) ?: return null
            val parts = stored.split(LOCATION_SEPARATOR)
            if (parts.size != 2) return null
            val latitude = parts[0].trim().toDoubleOrNull() ?: return null
            val longitude = parts[1].trim().toDoubleOrNull() ?: return null
            return runCatching { GeoCoordinates(latitude, longitude) }.getOrNull()
        }

        const val KEY_THEME_MODE = "fjalar_pref_theme_mode"
        const val KEY_LOCALE = "fjalar_pref_locale"
        const val KEY_LAST_KNOWN_LOCATION = "fjalar_pref_last_known_location"

        private const val LOCATION_SEPARATOR = ","
    }
}
