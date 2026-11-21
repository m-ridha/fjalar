package com.muhammadridha.fjalar

import android.app.UiModeManager
import android.content.res.Configuration
import com.muhammadridha.fjalar.domain.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class StartupThemeTest {
    private val osLight = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO
    private val osDark = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES

    @Test
    fun lightChoiceForcesNightOffAndKeepsOtherUiModeBits() {
        assertEquals(osLight, StartupTheme.resolveUiMode(osDark, ThemeMode.LIGHT))
        assertEquals(osLight, StartupTheme.resolveUiMode(osLight, ThemeMode.LIGHT))
    }

    @Test
    fun darkChoiceForcesNightOn() {
        assertEquals(osDark, StartupTheme.resolveUiMode(osLight, ThemeMode.DARK))
        assertEquals(osDark, StartupTheme.resolveUiMode(osDark, ThemeMode.DARK))
    }

    @Test
    fun systemChoiceFollowsTheOs() {
        assertEquals(osLight, StartupTheme.resolveUiMode(osLight, ThemeMode.SYSTEM))
        assertEquals(osDark, StartupTheme.resolveUiMode(osDark, ThemeMode.SYSTEM))
    }

    @Test
    fun applicationNightModeMapsEveryChoice() {
        assertEquals(UiModeManager.MODE_NIGHT_NO, StartupTheme.applicationNightMode(ThemeMode.LIGHT))
        assertEquals(UiModeManager.MODE_NIGHT_YES, StartupTheme.applicationNightMode(ThemeMode.DARK))
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, StartupTheme.applicationNightMode(ThemeMode.SYSTEM))
    }
}
