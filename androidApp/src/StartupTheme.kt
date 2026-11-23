package com.muhammadridha.fjalar

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.adapters.storage.SharedPreferencesKeyValueStorage
import com.muhammadridha.fjalar.domain.model.ThemeMode

/**
 * Resolves the user's saved light/dark choice before Koin or Compose exist, so the native splash
 * resources (`values/` vs `values-night/`) follow the in-app choice rather than only the OS.
 *
 * - API 31+: the system draws the splash before app code runs, so the choice is pushed to the
 *   system as the per-app night mode ([UiModeManager.setApplicationNightMode]). It applies from
 *   the next cold start. It is synced on start and when the app stops.
 * - API 30 and below: the compat splash theme resolves resources in the activity, so the
 *   activity base context gets the matching night `uiMode`.
 * - SYSTEM, or no stored value, follows the OS.
 */
internal object StartupTheme {

    fun readStoredThemeMode(context: Context): ThemeMode =
        PersistentPreferencesAdapter.readThemeMode(SharedPreferencesKeyValueStorage(context))

    /** `uiMode` with its night bits replaced by the user's choice; unchanged for SYSTEM. */
    fun resolveUiMode(uiMode: Int, themeMode: ThemeMode): Int {
        val nightBits = when (themeMode) {
            ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
            ThemeMode.SYSTEM -> return uiMode
        }
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
    }

    /** [UiModeManager] night-mode constant for a theme choice (AUTO follows the OS). */
    fun applicationNightMode(themeMode: ThemeMode): Int = when (themeMode) {
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    }

    /** API 30 and below: make the activity resolve night resources from the stored choice. */
    fun wrapBaseContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return base
        val themeMode = readStoredThemeMode(base)
        if (themeMode == ThemeMode.SYSTEM) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.uiMode = resolveUiMode(configuration.uiMode, themeMode)
        return base.createConfigurationContext(configuration)
    }

    /** API 31+: store the user's choice as the per-app night mode for the next launch. */
    fun syncApplicationNightMode(
        context: Context,
        themeMode: ThemeMode = readStoredThemeMode(context),
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiModeManager = context.getSystemService(UiModeManager::class.java) ?: return
        val desired = applicationNightMode(themeMode)
        if (uiModeManager.nightMode != desired) {
            uiModeManager.setApplicationNightMode(desired)
        }
    }
}
