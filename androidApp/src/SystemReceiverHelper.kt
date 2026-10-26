package com.muhammadridha.fjalar

import android.content.BroadcastReceiver
import android.content.Context
import android.os.PowerManager
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SystemReceiverHelper {

    var syncPortOverride: ((Context) -> SyncSchedulesPort?)? = null

    fun dispatchSync(
        receiver: BroadcastReceiver,
        context: Context,
        tag: String,
        coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
        onBeforeSync: ((Context) -> Unit)? = null,
    ) {
        val pendingResult = receiver.goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "fjalar:$tag",
        )?.apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MILLIS)
        }

        CoroutineScope(coroutineDispatcher).launch {
            try {
                onBeforeSync?.invoke(context)
                val port = syncPortOverride?.invoke(context)
                    ?: (context.applicationContext as? FjalarApplication)?.container?.syncSchedulesPort
                port?.syncSchedules()
            } catch (_: Exception) {
                // Defensive: prevent unhandled coroutine exceptions from crashing receiver
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Throwable) {
                }
                pendingResult?.finish()
            }
        }
    }

    private const val WAKELOCK_TIMEOUT_MILLIS = 10_000L
}
