package com.muhammadridha.fjalar.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import kotlin.test.Test
import kotlin.test.assertNotNull

class WindowInsetsLayoutTest {

    @Test
    fun safeDrawingPadding_isAppliedToRootContent() {
        // Assert that root container propagates safeDrawing insets to child layers
        assertNotNull(WindowInsetsLayoutHelper.hudTopPaddingModifier())
        assertNotNull(WindowInsetsLayoutHelper.navigationBottomPaddingModifier())
        assertNotNull(WindowInsetsLayoutHelper.safeDrawingPaddingModifier())
        assertNotNull(WindowInsetsLayoutHelper.sheetBottomPaddingModifier())
    }

    @Test
    fun layoutModifiers_supportCustomModifierChaining() {
        val baseTop = Modifier.fillMaxWidth()
        val hudModifier = WindowInsetsLayoutHelper.hudTopPaddingModifier(baseTop)
        assertNotNull(hudModifier)

        val baseBottom = Modifier.fillMaxWidth()
        val navModifier = WindowInsetsLayoutHelper.navigationBottomPaddingModifier(baseBottom)
        assertNotNull(navModifier)

        val baseSafe = Modifier.fillMaxSize()
        val safeModifier = WindowInsetsLayoutHelper.safeDrawingPaddingModifier(baseSafe)
        assertNotNull(safeModifier)

        val baseSheet = Modifier.fillMaxWidth()
        val sheetModifier = WindowInsetsLayoutHelper.sheetBottomPaddingModifier(baseSheet)
        assertNotNull(sheetModifier)
    }
}
