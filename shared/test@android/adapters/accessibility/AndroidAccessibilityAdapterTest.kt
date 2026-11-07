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
class AndroidAccessibilityAdapterTest {

    private class FakeAndroidAccessibilityPlatformGateway : AndroidAccessibilityPlatformGateway {
        var highContrast = false
        var reduceTransparencySetting = false
        var observerAction: (() -> Unit)? = null
        var unregisterCount = 0

        override fun isHighTextContrastEnabled(): Boolean = highContrast
        override fun isReduceTransparencySettingEnabled(): Boolean = reduceTransparencySetting

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
    fun defaultState_isFalseWhenBothSettingsDisabled() {
        val gateway = FakeAndroidAccessibilityPlatformGateway()
        val adapter = AndroidAccessibilityAdapter(gateway = gateway)

        assertFalse(adapter.isReduceTransparencyActive)
    }

    @Test
    fun activeState_isTrueWhenHighContrastEnabled() {
        val gateway = FakeAndroidAccessibilityPlatformGateway().apply {
            highContrast = true
        }
        val adapter = AndroidAccessibilityAdapter(gateway = gateway)

        assertTrue(adapter.isReduceTransparencyActive)
    }

    @Test
    fun activeState_isTrueWhenReduceTransparencySettingEnabled() {
        val gateway = FakeAndroidAccessibilityPlatformGateway().apply {
            reduceTransparencySetting = true
        }
        val adapter = AndroidAccessibilityAdapter(gateway = gateway)

        assertTrue(adapter.isReduceTransparencyActive)
    }

    @Test
    fun activeState_isTrueWhenBothEnabled() {
        val gateway = FakeAndroidAccessibilityPlatformGateway().apply {
            highContrast = true
            reduceTransparencySetting = true
        }
        val adapter = AndroidAccessibilityAdapter(gateway = gateway)

        assertTrue(adapter.isReduceTransparencyActive)
    }

    @Test
    fun reduceTransparencyFlow_emitsInitialAndUpdatedValues() = runTest {
        val gateway = FakeAndroidAccessibilityPlatformGateway()
        val adapter = AndroidAccessibilityAdapter(gateway = gateway)

        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.reduceTransparencyFlow.toList(emissions)
        }

        assertEquals(listOf(false), emissions)

        gateway.reduceTransparencySetting = true
        gateway.notifyChanged()
        assertEquals(listOf(false, true), emissions)

        gateway.reduceTransparencySetting = false
        gateway.highContrast = true
        // isReduceTransparencyActive remains true, distinctUntilChanged filters duplicate
        gateway.notifyChanged()
        assertEquals(listOf(false, true), emissions)

        gateway.highContrast = false
        gateway.notifyChanged()
        assertEquals(listOf(false, true, false), emissions)

        job.cancel()
        assertEquals(1, gateway.unregisterCount)
    }
}
