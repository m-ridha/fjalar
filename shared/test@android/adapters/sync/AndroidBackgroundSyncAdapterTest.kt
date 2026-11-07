package com.muhammadridha.fjalar.adapters.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AndroidBackgroundSyncAdapterTest {

    @Test
    fun androidBackgroundSyncAdapter_executesSafelyAsNoOp() = runTest {
        val adapter = AndroidBackgroundSyncAdapter()
        // Should execute cleanly without throwing any exception
        adapter.scheduleBackgroundSync(15L)
        adapter.cancelBackgroundSync()
    }
}
