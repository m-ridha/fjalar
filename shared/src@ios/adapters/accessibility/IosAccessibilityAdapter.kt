package com.muhammadridha.fjalar.adapters.accessibility

import com.muhammadridha.fjalar.domain.ports.outbound.AccessibilityPort
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled
import platform.UIKit.UIAccessibilityReduceTransparencyStatusDidChangeNotification

/**
 * Platform gateway decoupling iOS UIKit accessibility functions and notifications for unit testing.
 */
interface IosAccessibilityPlatformGateway {
    fun isReduceTransparencyEnabled(): Boolean
    fun registerObserver(onChange: () -> Unit): AutoCloseable
}

/**
 * Production implementation querying [UIAccessibilityIsReduceTransparencyEnabled] and observing
 * [UIAccessibilityReduceTransparencyStatusDidChangeNotification] on [NSNotificationCenter].
 */
open class DefaultIosAccessibilityPlatformGateway(
    private val notificationCenter: NSNotificationCenter = NSNotificationCenter.defaultCenter,
) : IosAccessibilityPlatformGateway {

    override fun isReduceTransparencyEnabled(): Boolean {
        return try {
            UIAccessibilityIsReduceTransparencyEnabled()
        } catch (_: Throwable) {
            false
        }
    }

    override fun registerObserver(onChange: () -> Unit): AutoCloseable {
        val observer = notificationCenter.addObserverForName(
            name = UIAccessibilityReduceTransparencyStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            onChange()
        }

        return AutoCloseable {
            try {
                notificationCenter.removeObserver(observer)
            } catch (_: Throwable) {
                // Defensive in headless test environments
            }
        }
    }
}

/**
 * iOS implementation of [AccessibilityPort] detecting and observing user display settings.
 *
 * Observes [UIAccessibilityReduceTransparencyStatusDidChangeNotification] to dynamically
 * toggle high-contrast liquid glass tokens when the user changes iOS accessibility settings.
 */
class IosAccessibilityAdapter(
    private val gateway: IosAccessibilityPlatformGateway = DefaultIosAccessibilityPlatformGateway(),
) : AccessibilityPort {

    override val isReduceTransparencyActive: Boolean
        get() = gateway.isReduceTransparencyEnabled()

    override val reduceTransparencyFlow: Flow<Boolean> = callbackFlow {
        trySend(isReduceTransparencyActive)

        val registration = gateway.registerObserver {
            trySend(isReduceTransparencyActive)
        }

        awaitClose {
            registration.close()
        }
    }.distinctUntilChanged()
}
