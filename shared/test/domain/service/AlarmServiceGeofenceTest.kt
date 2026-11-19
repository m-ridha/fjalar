package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.JAKARTA
import com.muhammadridha.fjalar.domain.MANGGARAI
import com.muhammadridha.fjalar.domain.SUDIRMAN
import com.muhammadridha.fjalar.domain.WEEKDAY_COMMUTE
import com.muhammadridha.fjalar.domain.jakarta
import com.muhammadridha.fjalar.domain.manggaraiAlarm
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.GeofenceRegion
import com.muhammadridha.fjalar.domain.model.GeofenceTransitionEvent
import com.muhammadridha.fjalar.domain.model.GeofenceTransitionType
import com.muhammadridha.fjalar.domain.model.TrackingTier
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.outbound.GeofencePort
import com.muhammadridha.fjalar.fakes.FakeAlarmRinger
import com.muhammadridha.fjalar.fakes.FakeAlarmScheduler
import com.muhammadridha.fjalar.fakes.FakeClock
import com.muhammadridha.fjalar.fakes.FakeGeofenceAdapter
import com.muhammadridha.fjalar.fakes.FakeLocationTracker
import com.muhammadridha.fjalar.fakes.InMemoryAlarmRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlarmServiceGeofenceTest {

    private val repository = InMemoryAlarmRepository()
    private val tracker = FakeLocationTracker()
    private val scheduler = FakeAlarmScheduler()
    private val ringer = FakeAlarmRinger()
    private val geofenceAdapter = FakeGeofenceAdapter()
    private val clock = FakeClock(jakarta(2026, 10, 26, 7)) // Monday 07:00

    private val service = AlarmService(
        repository = repository,
        tracker = tracker,
        scheduler = scheduler,
        ringer = ringer,
        scheduleEvaluator = ScheduleEvaluator(JAKARTA),
        clock = clock,
        geofencePort = geofenceAdapter,
    )

    @Test
    fun armingAndDisarmingAlarmsReconcilesGeofenceRegionsAndTransitionTypes() = runTest {
        val oneTime = manggaraiAlarm(
            radius = TriggerRadius(300),
            schedule = AlarmSchedule.ONE_TIME,
        ).copy(id = "one-time")
        service.saveAlarm(oneTime, enabled = true).getOrThrow()

        assertEquals(1, geofenceAdapter.registeredRegions.size)
        val region = geofenceAdapter.registeredRegions.getValue("one-time")
        assertEquals(MANGGARAI, region.center)
        assertEquals(300.0, region.radiusMeters)
        assertEquals(setOf(GeofenceTransitionType.ENTER), region.transitionTypes)

        // Updating radius re-registers the changed region
        service.saveAlarm(oneTime.copy(radius = TriggerRadius(500)), enabled = true).getOrThrow()
        assertEquals(500.0, geofenceAdapter.registeredRegions.getValue("one-time").radiusMeters)

        // Disabling unregisters all regions (battery invariant)
        service.toggleAlarm("one-time", enabled = false).getOrThrow()
        assertTrue(geofenceAdapter.registeredRegions.isEmpty())
    }

    @Test
    fun scheduledAlarmWithMustExitBeforeTriggerRegistersEnterAndExit() = runTest {
        val scheduled = manggaraiAlarm(
            radius = TriggerRadius(400),
            schedule = WEEKDAY_COMMUTE,
        ).copy(id = "commute")
        service.saveAlarm(scheduled, enabled = true).getOrThrow()

        val saved = repository.getAlarm("commute")!!
        assertEquals(AlarmState.ARMED_MONITORING, saved.state)
        assertTrue(saved.mustExitBeforeTrigger)

        val region = geofenceAdapter.registeredRegions.getValue("commute")
        assertEquals(
            setOf(GeofenceTransitionType.ENTER, GeofenceTransitionType.EXIT),
            region.transitionTypes,
        )

        // After departing the destination area, mustExitBeforeTrigger clears -> region updates to ENTER only
        service.handleLocationUpdate(SUDIRMAN, 15f)
        val updatedRegion = geofenceAdapter.registeredRegions.getValue("commute")
        assertEquals(setOf(GeofenceTransitionType.ENTER), updatedRegion.transitionTypes)
    }

    @Test
    fun geofenceRegistrationCapsAtTwentyRegionsSortedById() = runTest {
        for (i in 25 downTo 1) {
            val id = "alarm-${i.toString().padStart(2, '0')}"
            val alarm = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME).copy(id = id)
            service.saveAlarm(alarm, enabled = true).getOrThrow()
        }

        assertEquals(AlarmService.MAX_GEOFENCE_REGIONS, geofenceAdapter.registeredRegions.size)
        val expectedIds = (1..20).map { "alarm-${it.toString().padStart(2, '0')}" }.toSet()
        assertEquals(expectedIds, geofenceAdapter.registeredRegions.keys)
    }

    @Test
    fun handleGeofenceTransitionEscalatesToApproachWithoutTriggeringOrClearingExitGuard() = runTest {
        val scheduled = manggaraiAlarm(
            radius = TriggerRadius(500),
            schedule = WEEKDAY_COMMUTE,
        ).copy(id = "commute")
        service.saveAlarm(scheduled, enabled = true).getOrThrow()

        // Verify ENTER transition escalates to APPROACH, does NOT ring, and preserves mustExitBeforeTrigger.
        service.handleGeofenceTransition(
            GeofenceTransitionEvent(
                regionId = "commute",
                transitionType = GeofenceTransitionType.ENTER,
                timestamp = clock.now,
            ),
        )

        assertTrue(tracker.isTracking)
        assertEquals(TrackingTier.APPROACH, tracker.currentTier)
        assertEquals(TrackingTier.APPROACH.intervalMillis, tracker.updateIntervalMillis)
        assertTrue(ringer.ringingIds.isEmpty())
        val afterEnter = repository.getAlarm("commute")!!
        assertEquals(AlarmState.ARMED_MONITORING, afterEnter.state)
        assertTrue(afterEnter.mustExitBeforeTrigger)

        // An inside-radius GPS fix is still suppressed because mustExitBeforeTrigger was NOT cleared
        val triggered = service.handleLocationUpdate(MANGGARAI, 10f)
        assertTrue(triggered.isEmpty())
        assertTrue(ringer.ringingIds.isEmpty())
    }

    @Test
    fun handleGeofenceTransitionEscalatesFromFarTierToApproachForArmedOneTimeAlarm() = runTest {
        val alarm = manggaraiAlarm(radius = TriggerRadius(500), schedule = AlarmSchedule.ONE_TIME).copy(id = "one-time")
        service.saveAlarm(alarm, enabled = true).getOrThrow()

        // Move to FAR tier (Bogor ~40 km away)
        service.handleLocationUpdate(GeoCoordinates(-6.5971, 106.7909), 20f)
        assertEquals(TrackingTier.FAR, tracker.currentTier)

        // Geofence ENTER escalates to APPROACH without ringing
        service.handleGeofenceTransition(
            GeofenceTransitionEvent(
                regionId = "one-time",
                transitionType = GeofenceTransitionType.ENTER,
                timestamp = clock.now,
            ),
        )
        assertEquals(TrackingTier.APPROACH, tracker.currentTier)
        assertTrue(ringer.ringingIds.isEmpty())
        assertEquals(AlarmState.ARMED_MONITORING, repository.getAlarm("one-time")!!.state)
    }

    @Test
    fun handleGeofenceTransitionIgnoresDisabledOrUnknownAlarm() = runTest {
        val alarm = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME).copy(id = "disabled-alarm")
        service.saveAlarm(alarm, enabled = false).getOrThrow()
        assertFalse(tracker.isTracking)

        service.handleGeofenceTransition(
            GeofenceTransitionEvent(
                regionId = "disabled-alarm",
                transitionType = GeofenceTransitionType.ENTER,
                timestamp = clock.now,
            ),
        )
        assertFalse(tracker.isTracking)

        service.handleGeofenceTransition(
            GeofenceTransitionEvent(
                regionId = "non-existent",
                transitionType = GeofenceTransitionType.ENTER,
                timestamp = clock.now,
            ),
        )
        assertFalse(tracker.isTracking)
    }

    @Test
    fun geofenceAdapterFailureDoesNotBreakTrackingOrPersistence() = runTest {
        val failingGeofencePort = object : GeofencePort {
            override suspend fun registerGeofences(regions: List<GeofenceRegion>) {
                throw IllegalStateException("Geofence service unavailable")
            }
            override suspend fun unregisterGeofences(regionIds: List<String>) {
                throw IllegalStateException("Geofence service unavailable")
            }
        }
        val resilientService = AlarmService(
            repository = repository,
            tracker = tracker,
            scheduler = scheduler,
            ringer = ringer,
            scheduleEvaluator = ScheduleEvaluator(JAKARTA),
            clock = clock,
            geofencePort = failingGeofencePort,
        )

        val alarm = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME).copy(id = "resilient")
        resilientService.saveAlarm(alarm, enabled = true).getOrThrow()

        assertEquals(AlarmState.ARMED_MONITORING, repository.getAlarm("resilient")!!.state)
        assertTrue(tracker.isTracking)
    }
}
