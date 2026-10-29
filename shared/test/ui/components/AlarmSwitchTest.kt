package com.muhammadridha.fjalar.ui.components

import com.muhammadridha.fjalar.ui.theme.DarkLiquidGlassColors
import com.muhammadridha.fjalar.ui.theme.LightLiquidGlassColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AlarmSwitchTest {
    @Test
    fun bothThemesExposeDifferentOnAndOffThumbsAndTracks() {
        for (colors in listOf(LightLiquidGlassColors, DarkLiquidGlassColors)) {
            val palette = colors.alarmSwitchPalette()
            assertEquals(colors.primary, palette.checkedThumb)
            assertEquals(colors.primary.copy(alpha = 0.4f), palette.checkedTrack)
            assertEquals(colors.textSecondary.copy(alpha = 0.7f), palette.uncheckedThumb)
            assertEquals(colors.textSecondary.copy(alpha = 0.2f), palette.uncheckedTrack)
            assertNotEquals(palette.checkedThumb, palette.uncheckedThumb)
            assertNotEquals(palette.checkedTrack, palette.uncheckedTrack)
            assertTrue(palette.checkedThumb.alpha > palette.checkedTrack.alpha)
            assertTrue(palette.uncheckedThumb.alpha > palette.uncheckedTrack.alpha)
        }
    }

    @Test
    fun reducedTransparencyRetainsTheSameVisibleSwitchStates() {
        for (colors in listOf(LightLiquidGlassColors, DarkLiquidGlassColors)) {
            assertEquals(colors.alarmSwitchPalette(), colors.withReducedTransparency().alarmSwitchPalette())
        }
    }
}
