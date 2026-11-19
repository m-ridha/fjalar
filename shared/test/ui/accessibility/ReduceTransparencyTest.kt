package com.muhammadridha.fjalar.ui.accessibility

import com.muhammadridha.fjalar.domain.ports.outbound.AccessibilityPort
import com.muhammadridha.fjalar.fakes.FakeAccessibilityAdapter
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class ReduceTransparencyTest {

    @BeforeTest
    fun ensureCleanKoinBefore() {
        stopKoin()
    }

    @AfterTest
    fun ensureCleanKoinAfter() {
        stopKoin()
    }

    @Test
    fun resolveAccessibilityPort_returnsKoinSingletonWithoutInvokingFallbackWhenStarted() {
        val singleton = FakeAccessibilityAdapter(initialActive = true)
        startKoin {
            modules(module {
                single<AccessibilityPort> { singleton }
            })
        }

        var fallbackCalls = 0
        val first = resolveAccessibilityPort {
            fallbackCalls++
            FakeAccessibilityAdapter()
        }
        val second = resolveAccessibilityPort {
            fallbackCalls++
            FakeAccessibilityAdapter()
        }

        assertSame(singleton, first)
        assertSame(singleton, second)
        assertSame(first, second)
        assertFalse(fallbackCalls > 0)
    }

    @Test
    fun resolveAccessibilityPort_usesFallbackWhenKoinIsNotInitialized() {
        assertNull(currentKoinOrNull())

        var fallbackCalls = 0
        val fallbackAdapter = FakeAccessibilityAdapter(initialActive = true)
        val resolved = resolveAccessibilityPort {
            fallbackCalls++
            fallbackAdapter
        }

        assertSame(fallbackAdapter, resolved)
        kotlin.test.assertEquals(1, fallbackCalls)
    }

    @Test
    fun resolveAccessibilityPort_usesFallbackWhenKoinHasNoAccessibilityBinding() {
        startKoin {
            modules(module {})
        }

        var fallbackCalls = 0
        val fallbackAdapter = FakeAccessibilityAdapter(initialActive = false)
        val resolved = resolveAccessibilityPort {
            fallbackCalls++
            fallbackAdapter
        }

        assertSame(fallbackAdapter, resolved)
        kotlin.test.assertEquals(1, fallbackCalls)
    }
}
