package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.adapters.location.isFreshLocationFix
import kotlin.test.*

class LocationFixFreshnessTest {
    @Test fun rejectsStaleMissingAndFutureFixes() {
        assertFalse(isFreshLocationFix(0L, 100_000L))
        assertFalse(isFreshLocationFix(1L, 100_000L))
        assertFalse(isFreshLocationFix(100_001L, 100_000L))
        assertTrue(isFreshLocationFix(90_000L, 100_000L))
        assertTrue(isFreshLocationFix(70_000L, 100_000L))
        assertFalse(isFreshLocationFix(69_999L, 100_000L))
    }
}
