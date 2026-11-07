package com.muhammadridha.fjalar.adapters.accessibility

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IosAccessibilityAdapterTest {

    private class FakeIosAccessibilityPlatformGateway : IosAccessibilityPlatformGateway {
        var reduceTransparency = false
        var observerAction: (() -> Unit)? = null
        var unregisterCount = 0

        override fun isReduceTransparencyEnabled(): Boolean = reduceTransparency

        override fun registerObserver(onChange: () -> Unit): AutoCloseable {
            observerAction = onChange
            return AutoCloseable {
                unregisterCount++
                observerAction = null
            }
        }

        fun notifyChanged() {
            observerAction?.invoke()
        }
    }

    @Test
    fun defaultState_isFalseWhenDisabled() {
        val gateway = FakeIosAccessibilityPlatformGateway()
        val adapter = IosAccessibilityAdapter(gateway = gateway)

        assertFalse(adapter.isReduceTransparencyActive)
    }

    @Test
    fun activeState_isTrueWhenEnabled() {
        val gateway = FakeIosAccessibilityPlatformGateway().apply {
            reduceTransparency = true
        }
        val adapter = IosAccessibilityAdapter(gateway = gateway)

        assertTrue(adapter.isReduceTransparencyActive)
    }

    @Test
    fun reduceTransparencyFlow_emitsInitialAndUpdatedValues() = runTest {
        val gateway = FakeIosAccessibilityPlatformGateway()
        val adapter = IosAccessibilityAdapter(gateway = gateway)

        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.reduceTransparencyFlow.toList(emissions)
        }

        assertEquals(listOf(false), emissions)

        gateway.reduceTransparency = true
        gateway.notifyChanged()
        assertEquals(listOf(false, true), emissions)

        // Duplicate update is ignored by distinctUntilChanged
        gateway.notifyChanged()
        assertEquals(listOf(false, true), emissions)

        gateway.reduceTransparency = false
        gateway.notifyChanged()
        assertEquals(listOf(false, true, false), emissions)

        job.cancel()
        assertEquals(1, gateway.unregisterCount)
    }
}
