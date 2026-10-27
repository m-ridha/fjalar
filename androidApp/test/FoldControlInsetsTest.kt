package com.muhammadridha.fjalar

import kotlin.test.Test
import kotlin.test.assertEquals

class FoldControlInsetsTest {
    @Test
    fun verticalHingeKeepsControlsInTheWiderPaneWithClearance() {
        assertEquals(FoldControlInsets(right = 520), foldControlInsets(1000, 700, 490, 0, 510, 700, true, 10))
    }

    @Test
    fun horizontalFoldKeepsControlsAboveTheCrease() {
        assertEquals(FoldControlInsets(bottom = 370), foldControlInsets(700, 700, 0, 340, 700, 360, false, 10))
    }

    @Test
    fun narrowLeadingPaneSelectsTheTrailingPane() {
        assertEquals(FoldControlInsets(left = 320), foldControlInsets(1000, 700, 290, 0, 310, 700, true, 10))
    }

    @Test
    fun zeroWidthSeparatingFoldStillHasInteractionClearance() {
        assertEquals(FoldControlInsets(right = 510), foldControlInsets(1000, 700, 500, 0, 500, 700, true, 10))
    }
}
