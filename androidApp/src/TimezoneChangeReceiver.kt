package com.muhammadridha.fjalar

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.muhammadridha.fjalar.di.SystemTimeZone
import org.koin.android.ext.android.getKoin

/**
 * Re-evaluates alarm schedule windows when the user crosses time zones or manual system time changes.
 *
 * Catches [Intent.ACTION_TIMEZONE_CHANGED], [Intent.ACTION_TIME_CHANGED], and [ACTION_TIME_SET].
 * Updates [ScheduleEvaluator] with the new system default time zone before dispatching schedule sync.
 */
class TimezoneChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            ACTION_TIME_SET -> {
                SystemReceiverHelper.dispatchSync(
                    receiver = this,
                    context = context,
                    tag = "TimezoneChangeReceiver",
                    onBeforeSync = { ctx ->
                        (ctx.applicationContext as Application).getKoin().get<SystemTimeZone>().refresh()
                    },
                )
            }
        }
    }

    companion object {
        const val ACTION_TIME_SET = "android.intent.action.TIME_SET"
    }
}
