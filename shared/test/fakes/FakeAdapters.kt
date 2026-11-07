package com.muhammadridha.fjalar.fakes

import com.muhammadridha.fjalar.domain.model.GeofenceRegion
import com.muhammadridha.fjalar.domain.model.GeofenceTransitionEvent
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.RecentDestination
import com.muhammadridha.fjalar.domain.ports.outbound.AccessibilityPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRepositoryPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.BackgroundSyncPort
import com.muhammadridha.fjalar.domain.ports.outbound.GeofencePort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.RecentDestinationsPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant

class InMemoryAlarmRepository : AlarmRepositoryPort {
    private val alarms = MutableStateFlow<Map<String, LocationAlarm>>(emptyMap())

    override fun observeAlarms(): Flow<List<LocationAlarm>> = alarms.map { it.values.toList() }
    override suspend fun getAlarms(): List<LocationAlarm> = alarms.value.values.toList()
    override suspend fun getAlarm(id: String): LocationAlarm? = alarms.value[id]
    override suspend fun upsertAlarm(alarm: LocationAlarm) = alarms.update { it + (alarm.id to alarm) }
    override suspend fun deleteAlarm(id: String) = alarms.update { it - id }
}

class FakeLocationTracker : LocationTrackerPort {
    var isTracking = false
        private set
    var updateIntervalMillis: Long? = null
        private set
    var currentTier: com.muhammadridha.fjalar.domain.model.TrackingTier? = null
        private set

    override suspend fun startTracking(updateIntervalMillis: Long) {
        startTracking(updateIntervalMillis, com.muhammadridha.fjalar.domain.model.TrackingTier.fromInterval(updateIntervalMillis))
    }

    override suspend fun startTracking(updateIntervalMillis: Long, tier: com.muhammadridha.fjalar.domain.model.TrackingTier) {
        isTracking = true
        this.updateIntervalMillis = updateIntervalMillis
        this.currentTier = tier
    }

    override suspend fun stopTracking() {
        isTracking = false
        updateIntervalMillis = null
        currentTier = null
    }
}

class FakeAlarmRinger : AlarmRingerPort {
    val ringingIds = mutableSetOf<String>()
    var startCount = 0
        private set
    var shouldFailOnStop = false

    override suspend fun startRinging(alarm: LocationAlarm) {
        startCount++
        ringingIds += alarm.id
    }

    override suspend fun stopRinging(alarmId: String) {
        if (shouldFailOnStop) {
            throw IllegalStateException("Simulated ringer stop failure")
        }
        ringingIds -= alarmId
    }
}

class FakeAlarmScheduler : AlarmSchedulerPort {
    var shouldFailOnSchedule = false
    val wakeups = mutableMapOf<String, Instant>()
    val cancelledWakeups = mutableSetOf<String>()

    override suspend fun scheduleWakeup(alarmId: String, at: Instant) {
        check(!shouldFailOnSchedule) { "Simulated scheduling failure" }
        wakeups[alarmId] = at
        cancelledWakeups -= alarmId
    }

    override suspend fun cancelWakeup(alarmId: String) {
        wakeups -= alarmId
        cancelledWakeups += alarmId
    }
}

class FakeClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

class FakeAccessibilityAdapter(
    initialActive: Boolean = false,
) : AccessibilityPort {
    private val _flow = MutableStateFlow(initialActive)

    override var isReduceTransparencyActive: Boolean
        get() = _flow.value
        set(value) {
            _flow.value = value
        }

    override val reduceTransparencyFlow: Flow<Boolean> = _flow

    fun setActive(active: Boolean) {
        isReduceTransparencyActive = active
    }
}

class FakeRecentDestinationsAdapter(
    initialDestinations: List<RecentDestination> = emptyList(),
) : RecentDestinationsPort {
    val flow = MutableStateFlow(initialDestinations)
    override fun observeRecentDestinations(): Flow<List<RecentDestination>> = flow
    override suspend fun getRecentDestinations(): List<RecentDestination> = flow.value
    override suspend fun recordDestination(destination: RecentDestination) {
        flow.value = RecentDestination.updateRecents(flow.value, destination)
    }
    override suspend fun clearRecentDestinations() {
        flow.value = emptyList()
    }
}

class FakeBackgroundSyncAdapter : BackgroundSyncPort {
    var scheduledDelayMinutes: Long? = null
    var isCancelled: Boolean = false

    override suspend fun scheduleBackgroundSync(earliestBeginMinutesFromNow: Long) {
        scheduledDelayMinutes = earliestBeginMinutesFromNow
        isCancelled = false
    }

    override suspend fun cancelBackgroundSync() {
        isCancelled = true
        scheduledDelayMinutes = null
    }
}

class FakeGeofenceAdapter : GeofencePort {
    val registeredRegions = mutableMapOf<String, GeofenceRegion>()
    val eventsFlow = MutableSharedFlow<GeofenceTransitionEvent>(extraBufferCapacity = 64)

    override suspend fun registerGeofences(regions: List<GeofenceRegion>) {
        for (region in regions) {
            registeredRegions[region.id] = region
        }
    }

    override suspend fun unregisterGeofences(regionIds: List<String>) {
        for (id in regionIds) {
            registeredRegions.remove(id)
        }
    }

    override fun observeGeofenceTransitions(): Flow<GeofenceTransitionEvent> = eventsFlow

    suspend fun emitTransition(event: GeofenceTransitionEvent) {
        eventsFlow.emit(event)
    }
}

