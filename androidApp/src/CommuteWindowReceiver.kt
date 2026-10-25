package com.muhammadridha.fjalar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles OS alarm wakeups when an alarm's scheduled commute window begins or ends.
 *
 * Triggered by [com.muhammadridha.fjalar.adapters.scheduler.AndroidAlarmManagerSchedulerAdapter.ACTION_COMMUTE_WINDOW].
 * Wakes up device with a 10s WakeLock, dispatches [com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort.syncSchedules],
 * and transitions alarms into or out of ARMED_MONITORING.
 */
class CommuteWindowReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action == ACTION_COMMUTE_WINDOW) {
            SystemReceiverHelper.dispatchSync(
                receiver = this,
                context = context,
                tag = "CommuteWindowReceiver",
            )
        }
    }

    companion object {
        const val ACTION_COMMUTE_WINDOW = "com.muhammadridha.fjalar.ACTION_COMMUTE_WINDOW"
        const val EXTRA_ALARM_ID = "com.muhammadridha.fjalar.extra.ALARM_ID"
    }
}
