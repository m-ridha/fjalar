package com.muhammadridha.fjalar.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlideToStopThresholdTest {

    @Test
    fun thresholdConstantIsEightyPercent() {
        assertEquals(0.80f, SLIDE_TO_STOP_THRESHOLD)
    }

    @Test
    fun isSlideToStopSatisfiedReturnsFalseBelowThreshold() {
        assertFalse(isSlideToStopSatisfied(0.0f))
        assertFalse(isSlideToStopSatisfied(0.50f))
        assertFalse(isSlideToStopSatisfied(0.79f))
        assertFalse(isSlideToStopSatisfied(0.7999f))
        assertFalse(isSlideToStopSatisfied(-0.10f))
    }

    @Test
    fun isSlideToStopSatisfiedReturnsTrueAtOrAboveThreshold() {
        assertTrue(isSlideToStopSatisfied(0.80f))
        assertTrue(isSlideToStopSatisfied(0.8001f))
        assertTrue(isSlideToStopSatisfied(0.95f))
        assertTrue(isSlideToStopSatisfied(1.0f))
        assertTrue(isSlideToStopSatisfied(1.20f))
    }

    @Test
    fun stateCalculatesFractionAndSatisfiedProperly() {
        val state = SlideToStopState()

        state.updateDrag(rawPx = 79f, maxPx = 100f)
        assertEquals(0.79f, state.dragFraction)
        assertFalse(state.isSatisfied)

        state.updateDrag(rawPx = 80f, maxPx = 100f)
        assertEquals(0.80f, state.dragFraction)
        assertTrue(state.isSatisfied)

        state.updateDrag(rawPx = 150f, maxPx = 100f)
        assertEquals(1.0f, state.dragFraction)
        assertTrue(state.isSatisfied)

        state.updateDrag(rawPx = -20f, maxPx = 100f)
        assertEquals(0.0f, state.dragFraction)
        assertFalse(state.isSatisfied)
    }

    @Test
    fun checkThresholdAndResolveTriggersCallbackOnlyWhenSatisfied() {
        val state = SlideToStopState()
        var callbackTriggered = false

        state.updateDrag(rawPx = 75f, maxPx = 100f)
        val resultBelow = state.checkThresholdAndResolve { callbackTriggered = true }
        assertFalse(resultBelow)
        assertFalse(callbackTriggered)

        state.updateDrag(rawPx = 85f, maxPx = 100f)
        val resultAbove = state.checkThresholdAndResolve { callbackTriggered = true }
        assertTrue(resultAbove)
        assertTrue(callbackTriggered)
    }

    @Test
    fun customThresholdEvaluatesAgainstInstanceThreshold() {
        val customState = SlideToStopState(threshold = 0.95f)

        customState.updateDrag(rawPx = 90f, maxPx = 100f)
        assertEquals(0.90f, customState.dragFraction)
        assertFalse(customState.isSatisfied)

        customState.updateDrag(rawPx = 95f, maxPx = 100f)
        assertEquals(0.95f, customState.dragFraction)
        assertTrue(customState.isSatisfied)
    }
}
