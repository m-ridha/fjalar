package com.muhammadridha.fjalar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reschedules all active alarms and restores monitoring state following device reboot or package update.
 *
 * Catches [Intent.ACTION_BOOT_COMPLETED], [Intent.ACTION_MY_PACKAGE_REPLACED],
 * and OEM quick-boot broadcasts.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_QUICKBOOT_POWERON,
            ACTION_HTC_QUICKBOOT_POWERON -> {
                SystemReceiverHelper.dispatchSync(
                    receiver = this,
                    context = context,
                    tag = "BootReceiver",
                )
            }
        }
    }

    companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
