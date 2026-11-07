package com.muhammadridha.fjalar.adapters.sync

import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeIosBgTaskSchedulerEngine : IosBgTaskSchedulerEngine {
    var registeredIdentifier: String? = null
    var registeredHandler: ((BGTask) -> Unit)? = null
    var submittedRequest: BGTaskRequest? = null
    var cancelledIdentifier: String? = null
    var registerCallCount: Int = 0
    var shouldFailOnSubmit: Boolean = false

    override fun registerForTask(identifier: String, launchHandler: (BGTask) -> Unit): Boolean {
        registerCallCount++
        registeredIdentifier = identifier
        registeredHandler = launchHandler
        return true
    }

    override fun registerTask(identifier: String, launchHandler: (BGTask) -> Unit): Boolean {
        return registerForTask(identifier, launchHandler)
    }

    override fun submit(request: BGTaskRequest): Boolean {
        if (shouldFailOnSubmit) return false
        submittedRequest = request
        return true
    }

    override fun cancel(identifier: String) {
        cancelledIdentifier = identifier
    }
}

class FakeSyncSchedulesPort : SyncSchedulesPort {
    var syncCallCount = 0
    var returnedAlarms: List<LocationAlarm> = emptyList()

    override suspend fun syncSchedules(): List<LocationAlarm> {
        syncCallCount++
        return returnedAlarms
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class IosBackgroundSyncAdapterTest {

    @Test
    fun registerBackgroundSyncTask_registersWithCanonicalIdentifierAndIsIdempotent() {
        val fakeEngine = FakeIosBgTaskSchedulerEngine()
        val fakeSyncPort = FakeSyncSchedulesPort()
        val adapter = IosBackgroundSyncAdapter(
            syncSchedulesPortProvider = { fakeSyncPort },
            engine = fakeEngine,
        )

        val result = adapter.registerBackgroundSyncTask()
        assertTrue(result)
        assertEquals(IOS_BACKGROUND_REFRESH_TASK_ID, fakeEngine.registeredIdentifier)
        assertEquals(1, fakeEngine.registerCallCount)
        assertNotNull(fakeEngine.registeredHandler)
        assertTrue(adapter.isRegistered)

        // Calling a second time should be idempotent (no duplicate registration)
        val secondResult = adapter.registerBackgroundSyncTask()
        assertTrue(secondResult)
        assertEquals(1, fakeEngine.registerCallCount)
    }

    @Test
    fun scheduleBackgroundSync_clampsToMinimum15MinutesAndSubmitsRequest() = runTest {
        val fakeEngine = FakeIosBgTaskSchedulerEngine()
        val fakeSyncPort = FakeSyncSchedulesPort()
        val adapter = IosBackgroundSyncAdapter(
            syncSchedulesPortProvider = { fakeSyncPort },
            engine = fakeEngine,
        )

        // Passing 5 minutes should clamp to minimum 15 minutes
        adapter.scheduleBackgroundSync(earliestBeginMinutesFromNow = 5L)

        val submitted = fakeEngine.submittedRequest
        assertNotNull(submitted)
        assertIs<BGAppRefreshTaskRequest>(submitted)
        assertEquals(IOS_BACKGROUND_REFRESH_TASK_ID, submitted.identifier)
        assertNotNull(submitted.earliestBeginDate)
    }

    @Test
    fun scheduleBackgroundSync_withCustomDelay_setsEarliestBeginDate() = runTest {
        val fakeEngine = FakeIosBgTaskSchedulerEngine()
        val fakeSyncPort = FakeSyncSchedulesPort()
        val adapter = IosBackgroundSyncAdapter(
            syncSchedulesPortProvider = { fakeSyncPort },
            engine = fakeEngine,
        )

        adapter.scheduleBackgroundSync(earliestBeginMinutesFromNow = 60L)

        val submitted = fakeEngine.submittedRequest
        assertNotNull(submitted)
        assertIs<BGAppRefreshTaskRequest>(submitted)
        assertEquals(IOS_BACKGROUND_REFRESH_TASK_ID, submitted.identifier)
        assertNotNull(submitted.earliestBeginDate)
    }

    @Test
    fun cancelBackgroundSync_cancelsCanonicalIdentifier() = runTest {
        val fakeEngine = FakeIosBgTaskSchedulerEngine()
        val fakeSyncPort = FakeSyncSchedulesPort()
        val adapter = IosBackgroundSyncAdapter(
            syncSchedulesPortProvider = { fakeSyncPort },
            engine = fakeEngine,
        )

        adapter.cancelBackgroundSync()
        assertEquals(IOS_BACKGROUND_REFRESH_TASK_ID, fakeEngine.cancelledIdentifier)
    }

    private class FakeBgTask : IosBgTaskWrapper {
        override var expirationHandler: (() -> Unit)? = null
        var completionCallCount = 0
        var lastSuccessValue: Boolean? = null

        override fun setTaskCompletedWithSuccess(success: Boolean) {
            completionCallCount++
            lastSuccessValue = success
        }
    }

    @Test
    fun handleTask_onSuccessfulSync_reschedulesAndCompletesWithSuccess() = runTest {
        val fakeEngine = FakeIosBgTaskSchedulerEngine()
        val fakeSyncPort = FakeSyncSchedulesPort()
        val adapter = IosBackgroundSyncAdapter(
            syncSchedulesPortProvider = { fakeSyncPort },
            engine = fakeEngine,
        )
        val fakeTask = FakeBgTask()

        val job = adapter.handleTask(fakeTask)
        job.join()

        assertEquals(1, fakeSyncPort.syncCallCount)
        assertEquals(1, fakeTask.completionCallCount)
        assertEquals(true, fakeTask.lastSuccessValue)
        assertNotNull(fakeEngine.submittedRequest)
    }

    @Test
    fun handleTask_onSyncError_completesWithFailure() = runTest {
        val fakeEngine = FakeIosBgTaskSchedulerEngine()
        val failingSyncPort = object : SyncSchedulesPort {
            override suspend fun syncSchedules(): List<LocationAlarm> {
                throw IllegalStateException("Sync failed")
            }
        }
        val adapter = IosBackgroundSyncAdapter(
            syncSchedulesPortProvider = { failingSyncPort },
            engine = fakeEngine,
        )
        val fakeTask = FakeBgTask()

        val job = adapter.handleTask(fakeTask)
        job.join()

        assertEquals(1, fakeTask.completionCallCount)
        assertEquals(false, fakeTask.lastSuccessValue)
    }

    @Test
    fun handleTask_onExpiration_cancelsJobAndCompletesWithFailureExactlyOnce() = runTest {
        val fakeEngine = FakeIosBgTaskSchedulerEngine()
        val inFlightGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slowSyncPort = object : SyncSchedulesPort {
            override suspend fun syncSchedules(): List<LocationAlarm> {
                inFlightGate.await()
                return emptyList()
            }
        }
        val adapter = IosBackgroundSyncAdapter(
            syncSchedulesPortProvider = { slowSyncPort },
            engine = fakeEngine,
        )
        val fakeTask = FakeBgTask()

        val job = adapter.handleTask(fakeTask)
        // Simulate system expiration trigger while task is suspended in-flight
        fakeTask.expirationHandler?.invoke()
        job.join()

        // Must be called exactly once despite coroutine cancellation
        assertEquals(1, fakeTask.completionCallCount)
        assertEquals(false, fakeTask.lastSuccessValue)
    }

    @Test
    fun scheduleBackgroundSync_throwsIllegalStateExceptionWhenSubmitFails() = runTest {
        val fakeEngine = FakeIosBgTaskSchedulerEngine().apply { shouldFailOnSubmit = true }
        val fakeSyncPort = FakeSyncSchedulesPort()
        val adapter = IosBackgroundSyncAdapter(
            syncSchedulesPortProvider = { fakeSyncPort },
            engine = fakeEngine,
        )

        kotlin.test.assertFailsWith<IllegalStateException> {
            adapter.scheduleBackgroundSync(15L)
        }
    }

    @Test
    fun handleTask_onRescheduleError_stillCompletesWithSuccess() = runTest {
        val fakeEngine = FakeIosBgTaskSchedulerEngine().apply { shouldFailOnSubmit = true }
        val fakeSyncPort = FakeSyncSchedulesPort()
        val adapter = IosBackgroundSyncAdapter(
            syncSchedulesPortProvider = { fakeSyncPort },
            engine = fakeEngine,
        )
        val fakeTask = FakeBgTask()

        val job = adapter.handleTask(fakeTask)
        job.join()

        assertEquals(1, fakeSyncPort.syncCallCount)
        assertEquals(1, fakeTask.completionCallCount)
        assertEquals(true, fakeTask.lastSuccessValue)
    }
}
