package com.muhammadridha.fjalar.adapters.sync

import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.outbound.BackgroundSyncPort
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

const val IOS_BACKGROUND_REFRESH_TASK_ID = "com.muhammadridha.fjalar.refresh"

@OptIn(ExperimentalForeignApi::class)
interface IosBgTaskSchedulerEngine {
    fun registerForTask(identifier: String, launchHandler: (BGTask) -> Unit): Boolean
    fun registerTask(identifier: String, launchHandler: (BGTask) -> Unit): Boolean = registerForTask(identifier, launchHandler)
    fun submit(request: BGTaskRequest): Boolean
    fun cancel(identifier: String)
}

interface IosBgTaskWrapper {
    var expirationHandler: (() -> Unit)?
    fun setTaskCompletedWithSuccess(success: Boolean)
}

@OptIn(ExperimentalForeignApi::class)
class PlatformIosBgTask(private val task: BGTask) : IosBgTaskWrapper {
    override var expirationHandler: (() -> Unit)?
        get() = task.expirationHandler
        set(value) {
            task.expirationHandler = value
        }

    override fun setTaskCompletedWithSuccess(success: Boolean) {
        task.setTaskCompletedWithSuccess(success)
    }
}

@OptIn(ExperimentalForeignApi::class)
class DefaultIosBgTaskSchedulerEngine(
    private val schedulerProvider: () -> BGTaskScheduler = { BGTaskScheduler.sharedScheduler },
) : IosBgTaskSchedulerEngine {
    override fun registerForTask(identifier: String, launchHandler: (BGTask) -> Unit): Boolean {
        return schedulerProvider().registerForTaskWithIdentifier(
            identifier = identifier,
            usingQueue = null,
            launchHandler = { task ->
                if (task != null) {
                    launchHandler(task)
                }
            },
        )
    }

    override fun registerTask(identifier: String, launchHandler: (BGTask) -> Unit): Boolean =
        registerForTask(identifier, launchHandler)

    override fun submit(request: BGTaskRequest): Boolean {
        return schedulerProvider().submitTaskRequest(taskRequest = request, error = null)
    }

    override fun cancel(identifier: String) {
        schedulerProvider().cancelTaskRequestWithIdentifier(identifier)
    }
}

@OptIn(ExperimentalForeignApi::class)
class IosBackgroundSyncAdapter(
    private val syncSchedulesPortProvider: () -> SyncSchedulesPort,
    private val schedulerEngine: IosBgTaskSchedulerEngine = DefaultIosBgTaskSchedulerEngine(),
    private val adapterScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : BackgroundSyncPort {

    constructor(
        syncSchedulesPortProvider: () -> SyncSchedulesPort,
        engine: IosBgTaskSchedulerEngine,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this(
        syncSchedulesPortProvider = syncSchedulesPortProvider,
        schedulerEngine = engine,
        adapterScope = CoroutineScope(SupervisorJob() + dispatcher),
    )

    var isRegistered: Boolean = false
        private set

    fun close() {
        adapterScope.cancel()
    }

    fun registerBackgroundSyncTask(): Boolean {
        if (isRegistered) return true
        val success = schedulerEngine.registerForTask(IOS_BACKGROUND_REFRESH_TASK_ID) { task ->
            handleTask(task)
        }
        isRegistered = success
        return success
    }

    internal fun handleTask(task: BGTask): Job = handleTask(PlatformIosBgTask(task))

    internal fun handleTask(task: IosBgTaskWrapper): Job {
        val completed = AtomicInt(0)
        fun complete(success: Boolean) {
            if (completed.compareAndSet(0, 1)) {
                task.setTaskCompletedWithSuccess(success)
            }
        }

        val job = adapterScope.launch {
            try {
                val syncPort = syncSchedulesPortProvider()
                syncPort.syncSchedules()
                try {
                    scheduleBackgroundSync(DEFAULT_BACKGROUND_SYNC_INTERVAL_MINUTES)
                } catch (_: Throwable) {
                    // Reschedule failure does not fail an already successful sync
                }
                complete(true)
            } catch (e: Throwable) {
                complete(false)
            }
        }
        task.expirationHandler = {
            job.cancel()
            complete(false)
        }
        return job
    }

    override suspend fun scheduleBackgroundSync(earliestBeginMinutesFromNow: Long) {
        val minutes = earliestBeginMinutesFromNow.coerceAtLeast(MINIMUM_BACKGROUND_SYNC_MINUTES)
        val request = BGAppRefreshTaskRequest(IOS_BACKGROUND_REFRESH_TASK_ID).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(minutes * 60.0)
        }
        val submitted = schedulerEngine.submit(request)
        check(submitted) { "Failed to submit BGAppRefreshTaskRequest for $IOS_BACKGROUND_REFRESH_TASK_ID" }
    }

    override suspend fun cancelBackgroundSync() {
        schedulerEngine.cancel(IOS_BACKGROUND_REFRESH_TASK_ID)
    }

    companion object {
        const val MINIMUM_BACKGROUND_SYNC_MINUTES = 15L
        const val DEFAULT_BACKGROUND_SYNC_INTERVAL_MINUTES = 15L
    }
}
