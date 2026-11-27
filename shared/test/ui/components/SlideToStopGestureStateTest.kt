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

    @Test
    fun thumbHitTargetTracksCurrentThumbBoundsAndRejectsMidTrackTouches() {
        assertTrue(isWithinThumbHitTarget(touchX = 0f, dragFraction = 0f, maxDragPx = 280f, thumbSizePx = 52f))
        assertTrue(isWithinThumbHitTarget(touchX = 26f, dragFraction = 0f, maxDragPx = 280f, thumbSizePx = 52f))
        assertTrue(
            isWithinThumbHitTarget(
                touchX = 58f,
                dragFraction = 0f,
                maxDragPx = 280f,
                thumbSizePx = 52f,
                hitPaddingPx = 8f,
            ),
        )
        assertFalse(
            isWithinThumbHitTarget(
                touchX = 140f,
                dragFraction = 0f,
                maxDragPx = 280f,
                thumbSizePx = 52f,
                hitPaddingPx = 8f,
            ),
        )
        assertTrue(
            isWithinThumbHitTarget(
                touchX = 166f,
                dragFraction = 0.5f,
                maxDragPx = 280f,
                thumbSizePx = 52f,
            ),
        )
        assertFalse(isWithinThumbHitTarget(touchX = 10f, dragFraction = 0f, maxDragPx = 280f, thumbSizePx = 0f))
    }

    @Test
    fun stationaryCoordinateTranslationReachesFullTravelAndCompletes() {
        val state = SlideToStopState()
        val maxDragPx = 281f
        val downX = 25f
        var stops = 0

        // Simulate stationary pointer positions moving across the full track width.
        val midX = downX + (maxDragPx * 0.65f)
        state.updateDrag(0f + (midX - downX), maxDragPx)
        assertEquals(0.65f, state.dragFraction, absoluteTolerance = 0.001f)

        val endX = downX + maxDragPx
        state.updateDrag(0f + (endX - downX), maxDragPx)
        assertEquals(1.0f, state.dragFraction, absoluteTolerance = 0.001f)
        assertTrue(state.checkThresholdAndResolve { stops++ })
        assertEquals(1, stops)
    }

    @Test
    fun updateDragAfterCompletionDoesNotRegressDragFraction() {
        val state = SlideToStopState()
        state.updateDrag(100f, 100f)
        assertTrue(state.checkThresholdAndResolve {})

        state.updateDrag(0f, 100f)
        assertEquals(1.0f, state.dragFraction)
        state.dragBy(-50f, 100f)
        assertEquals(1.0f, state.dragFraction)
    }

    @Test
    fun thumbHitTargetExactEdgesWithPaddingAreInclusive() {
        // With dragFraction = 0.5f, maxDragPx = 200f, thumbStart = 100f, thumbEnd = 152f.
        assertTrue(isWithinThumbHitTarget(touchX = 92f, dragFraction = 0.5f, maxDragPx = 200f, thumbSizePx = 52f, hitPaddingPx = 8f))
        assertTrue(isWithinThumbHitTarget(touchX = 160f, dragFraction = 0.5f, maxDragPx = 200f, thumbSizePx = 52f, hitPaddingPx = 8f))
        assertFalse(isWithinThumbHitTarget(touchX = 91.9f, dragFraction = 0.5f, maxDragPx = 200f, thumbSizePx = 52f, hitPaddingPx = 8f))
        assertFalse(isWithinThumbHitTarget(touchX = 160.1f, dragFraction = 0.5f, maxDragPx = 200f, thumbSizePx = 52f, hitPaddingPx = 8f))
    }
}
