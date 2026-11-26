package com.muhammadridha.fjalar.adapters.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinDragGestureStateTest {

    @Test
    fun normalSwipeDoesNotBeginOrCommitPinDrag() {
        val state = PinDragGestureState()

        assertFalse(state.isDragging)
        assertFalse(state.shouldConsumeMove())
        assertFalse(state.finishDrag(commit = true))
        assertFalse(state.isDragging)
    }

    @Test
    fun longPressEnablesMoveConsumptionAndCommitsOnRelease() {
        val state = PinDragGestureState()

        state.beginLongPressDrag()
        assertTrue(state.isDragging)
        assertTrue(state.shouldConsumeMove())

        assertTrue(state.finishDrag(commit = true))
        assertFalse(state.isDragging)
        assertFalse(state.shouldConsumeMove())
    }

    @Test
    fun cancelAndResetClearDraggingWithoutCommitting() {
        val state = PinDragGestureState()

        state.beginLongPressDrag()
        assertFalse(state.finishDrag(commit = false))
        assertFalse(state.isDragging)

        state.beginLongPressDrag()
        state.reset()
        assertFalse(state.isDragging)
        assertFalse(state.finishDrag(commit = true))
    }
}
