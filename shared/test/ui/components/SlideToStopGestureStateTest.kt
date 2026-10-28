package com.muhammadridha.fjalar.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlideToStopGestureStateTest {
    @Test
    fun accessibleStopRequiresTwoActivationsAndResetRequiresFreshConfirmation() {
        val confirmation = AccessibleStopConfirmation()
        var stops = 0
        confirmation.activate { stops++ }
        assertEquals(0, stops)
        assertTrue(confirmation.awaitingConfirmation)
        confirmation.reset()
        confirmation.activate { stops++ }
        assertEquals(0, stops)
        confirmation.activate { stops++ }
        assertEquals(1, stops)
        assertFalse(confirmation.awaitingConfirmation)
    }

    @Test
    fun immediateReleaseReadsLatestProgressAndDuplicateEndsDoNotStopTwice() {
        val state = SlideToStopState()
        var stops = 0
        // Consecutive pointer deltas and up arrive before any recomposition.
        state.dragBy(40f, 100f)
        state.dragBy(45f, 100f)
        assertTrue(state.checkThresholdAndResolve { stops++ })
        assertFalse(state.checkThresholdAndResolve { stops++ })
        assertEquals(1, stops)
    }

    @Test
    fun belowThresholdAndTapNeverStop() {
        val state = SlideToStopState()
        assertFalse(state.checkThresholdAndResolve { error("tap stopped") })
        state.updateDrag(79f, 100f)
        assertFalse(state.checkThresholdAndResolve { error("short drag stopped") })
    }

    @Test
    fun recompositionOrResumeCannotReopenCompletedPresentation() {
        val state = SlideToStopState()
        state.updateDrag(100f, 100f)
        assertTrue(state.checkThresholdAndResolve {})
        state.updateDrag(100f, 100f)
        assertFalse(state.checkThresholdAndResolve { error("duplicate stop") })
    }
    @Test
    fun failureResetsProgressAndPermitsFreshDrag() {
        val state = SlideToStopState()
        state.updateDrag(90f, 100f)
        assertTrue(state.checkThresholdAndResolve {})
        state.resetAfterFailure()
        assertEquals(0f, state.dragFraction)
        assertFalse(state.checkThresholdAndResolve { error("retry requires drag") })
        state.updateDrag(90f, 100f)
        assertTrue(state.checkThresholdAndResolve {})
    }

    @Test
    fun cancellationAndLaterPresentationStartAtZero() {
        val state = SlideToStopState()
        state.updateDrag(90f, 100f)
        state.cancelDrag()
        assertFalse(state.checkThresholdAndResolve { error("cancel stopped") })
        state.updateDrag(90f, 100f)
        assertTrue(state.checkThresholdAndResolve {})
        val nextPresentation = SlideToStopState()
        nextPresentation.updateDrag(90f, 100f)
        assertTrue(nextPresentation.checkThresholdAndResolve {})
    }
}
