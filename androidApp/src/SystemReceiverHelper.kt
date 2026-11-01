package com.muhammadridha.fjalar

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.os.PowerManager
import android.util.Log
import org.koin.android.ext.android.getKoin
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SystemReceiverHelper {

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
                (context.applicationContext as Application).getKoin().get<SyncSchedulesPort>().syncSchedules()
            } catch (error: Exception) {
                Log.e("SystemReceiverHelper", "Schedule synchronization failed", error)
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
