package com.muhammadridha.fjalar.ui.components

import com.muhammadridha.fjalar.ui.theme.DarkLiquidGlassColors
import com.muhammadridha.fjalar.ui.theme.LightLiquidGlassColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingingAlertOverlayTest {

    @Test
    fun backdropScrimUsesThemeCanvasWithHighOpacityAcrossThemes() {
        val lightBackdrop = ringingOverlayBackdropColor(LightLiquidGlassColors)
        val darkBackdrop = ringingOverlayBackdropColor(DarkLiquidGlassColors)

        assertEquals(LightLiquidGlassColors.canvas.copy(alpha = RINGING_BACKDROP_SCRIM_ALPHA), lightBackdrop)
        assertEquals(DarkLiquidGlassColors.canvas.copy(alpha = RINGING_BACKDROP_SCRIM_ALPHA), darkBackdrop)
        assertTrue(lightBackdrop.alpha >= 0.90f)
        assertTrue(darkBackdrop.alpha >= 0.90f)
    }
}
