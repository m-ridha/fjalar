package com.muhammadridha.fjalar.adapters.storage

import com.muhammadridha.fjalar.domain.model.AppLocale
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

    override fun observeThemeMode(): Flow<ThemeMode> = themeModeFlow.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        storage.putString(KEY_THEME_MODE, mode.name)
        themeModeFlow.value = mode
    }

    override fun observeLocale(): Flow<AppLocale> = localeFlow.asStateFlow()

    override suspend fun setLocale(locale: AppLocale) {
        storage.putString(KEY_LOCALE, locale.name)
        localeFlow.value = locale
    }

    private fun loadThemeMode(): ThemeMode {
        val stored = storage.getString(KEY_THEME_MODE) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private fun loadLocale(): AppLocale = readLocale(storage)

    companion object {
        fun readLocale(storage: KeyValueStorage): AppLocale {
            val stored = storage.getString(KEY_LOCALE) ?: return AppLocale.EN
            return AppLocale.entries.firstOrNull {
                it.name.equals(stored, ignoreCase = true) || it.tag.equals(stored, ignoreCase = true)
            } ?: AppLocale.EN
        }

        const val KEY_THEME_MODE = "fjalar_pref_theme_mode"
        const val KEY_LOCALE = "fjalar_pref_locale"
    }
}
