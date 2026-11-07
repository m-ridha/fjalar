package com.muhammadridha.fjalar.domain.ports

import com.muhammadridha.fjalar.fakes.FakeBackgroundSyncAdapter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundSyncPortTest {

    @Test
    fun fakeBackgroundSyncAdapter_tracksScheduledDelayAndCancellation() = runTest {
        val adapter = FakeBackgroundSyncAdapter()
        assertNull(adapter.scheduledDelayMinutes)
        assertFalse(adapter.isCancelled)

        adapter.scheduleBackgroundSync(30L)
        assertEquals(30L, adapter.scheduledDelayMinutes)
        assertFalse(adapter.isCancelled)

        adapter.cancelBackgroundSync()
        assertNull(adapter.scheduledDelayMinutes)
        assertTrue(adapter.isCancelled)

        adapter.scheduleBackgroundSync(15L)
        assertEquals(15L, adapter.scheduledDelayMinutes)
        assertFalse(adapter.isCancelled)
    }
}
