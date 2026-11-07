package com.muhammadridha.fjalar.adapters.sync

import com.muhammadridha.fjalar.domain.ports.outbound.BackgroundSyncPort

/**
 * Android implementation of [BackgroundSyncPort].
 *
 * On Android, commute window scheduling and background synchronization are driven
 * directly by exact wakeups via [android.app.AlarmManager] and active monitoring
 * via foreground location services. Consequently, auxiliary background refresh
 * tasks (such as WorkManager or JobScheduler periodic sync) are not required for
 * schedule synchronization, making this a safe no-op implementation.
 */
class AndroidBackgroundSyncAdapter : BackgroundSyncPort {
    override suspend fun scheduleBackgroundSync(earliestBeginMinutesFromNow: Long) {
        // No-op on Android: AlarmManager handles exact wakeups and schedule synchronization.
    }

    override suspend fun cancelBackgroundSync() {
        // No-op on Android.
    }
}
