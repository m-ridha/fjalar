package com.muhammadridha.fjalar.adapters.accessibility

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.muhammadridha.fjalar.domain.ports.outbound.AccessibilityPort
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Contract separating Android system accessibility APIs from the adapter logic for unit testing.
 */
interface AndroidAccessibilityPlatformGateway {
    fun isHighTextContrastEnabled(): Boolean
    fun isReduceTransparencySettingEnabled(): Boolean
    fun registerObserver(onChange: () -> Unit): AutoCloseable
}

/**
 * Production implementation dispatching to Android [AccessibilityManager] and [Settings.Secure].
 */
open class DefaultAndroidAccessibilityPlatformGateway(
    private val context: Context,
) : AndroidAccessibilityPlatformGateway {

    private val accessibilityManager: AccessibilityManager? =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

    override fun isHighTextContrastEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return try {
            accessibilityManager?.isHighContrastTextEnabled == true
        } catch (_: Throwable) {
            false
        }
    }

    override fun isReduceTransparencySettingEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(
                context.contentResolver,
                AndroidAccessibilityAdapter.ACCESSIBILITY_REDUCE_TRANSPARENCY,
                0,
            ) == 1
        } catch (_: Throwable) {
            false
        }
    }

    override fun registerObserver(onChange: () -> Unit): AutoCloseable {
        val mainHandler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onChange()
            }
        }

        val settingUri = Settings.Secure.getUriFor(AndroidAccessibilityAdapter.ACCESSIBILITY_REDUCE_TRANSPARENCY)
        if (settingUri != null) {
            try {
                context.contentResolver.registerContentObserver(settingUri, false, observer)
            } catch (_: Throwable) {
                // Defensive in restricted/test environments
            }
        }

        val highContrastListener = if (Build.VERSION.SDK_INT >= 36) {
            try {
                val listener = AccessibilityManager.HighContrastTextStateChangeListener {
                    onChange()
                }
                accessibilityManager?.addHighContrastTextStateChangeListener(context.mainExecutor, listener)
                listener
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        return AutoCloseable {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (_: Throwable) {
                // Ignore
            }
            if (Build.VERSION.SDK_INT >= 36 && highContrastListener != null) {
                try {
                    accessibilityManager?.removeHighContrastTextStateChangeListener(highContrastListener)
                } catch (_: Throwable) {
                    // Ignore
                }
            }
        }
    }
}

/**
 * Android implementation of [AccessibilityPort] detecting and observing user display settings.
 *
 * Combines Android 14+ (API 34+) "Reduce transparency" system setting with high-contrast text
 * state to dynamically toggle high-contrast liquid glass tokens.
 */
class AndroidAccessibilityAdapter(
    context: Context? = null,
    private val gateway: AndroidAccessibilityPlatformGateway = DefaultAndroidAccessibilityPlatformGateway(
        context = requireNotNull(context) { "Context is required when platform gateway is not provided" },
    ),
) : AccessibilityPort {

    override val isReduceTransparencyActive: Boolean
        get() = gateway.isHighTextContrastEnabled() || gateway.isReduceTransparencySettingEnabled()

    override val reduceTransparencyFlow: Flow<Boolean> = callbackFlow {
        trySend(isReduceTransparencyActive)

        val registration = gateway.registerObserver {
            trySend(isReduceTransparencyActive)
        }

        awaitClose {
            registration.close()
        }
    }.distinctUntilChanged()

    companion object {
        const val ACCESSIBILITY_REDUCE_TRANSPARENCY = "accessibility_reduce_transparency"
    }
}
