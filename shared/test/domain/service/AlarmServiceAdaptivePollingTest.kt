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
import com.muhammadridha.fjalar.domain.model.TrackingTier
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.fakes.InMemoryAlarmRepository
import com.muhammadridha.fjalar.fakes.FakeAlarmRinger
import com.muhammadridha.fjalar.fakes.FakeAlarmScheduler
import com.muhammadridha.fjalar.fakes.FakeClock
import com.muhammadridha.fjalar.fakes.FakeLocationTracker
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlarmServiceAdaptivePollingTest {

    private val repository = InMemoryAlarmRepository()
    private val tracker = FakeLocationTracker()
    private val scheduler = FakeAlarmScheduler()
    private val ringer = FakeAlarmRinger()
    private val clock = FakeClock(jakarta(2026, 10, 26, 7)) // Monday 07:00

    private val service = AlarmService(
        repository = repository,
        tracker = tracker,
        scheduler = scheduler,
        ringer = ringer,
        scheduleEvaluator = ScheduleEvaluator(JAKARTA),
        clock = clock,
    )

    @Test
    fun initialTracking_beforeAnyFix_usesMediumTier() = runTest {
        val alarm = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME)
        service.saveAlarm(alarm, enabled = true).getOrThrow()

        assertTrue(tracker.isTracking)
        assertEquals(TrackingTier.MEDIUM, tracker.currentTier)
        assertEquals(20_000L, tracker.updateIntervalMillis)
    }

    @Test
    fun adaptivePolling_rampsUpAsCommuterApproachesDestination() = runTest {
        val alarm = manggaraiAlarm(
            radius = TriggerRadius(500),
            schedule = AlarmSchedule.ONE_TIME,
        )
        service.saveAlarm(alarm, enabled = true).getOrThrow()

        // 1. Far away: Bogor station (~40 km south of Manggarai)
        val bogor = GeoCoordinates(-6.5971, 106.7909)
        service.handleLocationUpdate(bogor, 20f)
        assertTrue(tracker.isTracking)
        assertEquals(TrackingTier.FAR, tracker.currentTier)
        assertEquals(60_000L, tracker.updateIntervalMillis)

        // 2. Approaching medium tier: Cawang (~4 km from Manggarai)
        val cawang = GeoCoordinates(-6.2444, 106.8622)
        val distCawang = HaversineCalculator.distanceMeters(cawang, MANGGARAI)
        assertTrue(distCawang in 2_000.0..5_000.0, "Expected Cawang in 2-5km, was $distCawang")

        service.handleLocationUpdate(cawang, 15f)
        assertTrue(tracker.isTracking)
        assertEquals(TrackingTier.MEDIUM, tracker.currentTier)
        assertEquals(20_000L, tracker.updateIntervalMillis)

        // 3. Approaching near tier: Tebet/Sahardjo (~1.16 km from Manggarai)
        val nearPoint = GeoCoordinates(-6.2200, 106.8530)
        val distNear = HaversineCalculator.distanceMeters(nearPoint, MANGGARAI)
        assertTrue(distNear in 500.0..2_000.0, "Expected nearPoint in (500m, 2km), was $distNear")

        service.handleLocationUpdate(nearPoint, 10f)
        assertTrue(tracker.isTracking)
        assertEquals(TrackingTier.APPROACH, tracker.currentTier)
        assertEquals(10_000L, tracker.updateIntervalMillis)
    }

    @Test
    fun hysteresis_preventsSteppingDownDueToBoundaryJitter() = runTest {
        val alarm = manggaraiAlarm(
            radius = TriggerRadius(500),
            schedule = AlarmSchedule.ONE_TIME,
        )
        service.saveAlarm(alarm, enabled = true).getOrThrow()

        // Establish APPROACH tier at ~1.3 km to trigger perimeter (< 2.0 km)
        // Center distance ~1.8 km - 500m radius = ~1.3 km
        val point1800m = GeoCoordinates(MANGGARAI.latitude + 0.0162, MANGGARAI.longitude)
        service.handleLocationUpdate(point1800m, 10f)
        assertEquals(TrackingTier.APPROACH, tracker.currentTier)

        // GPS jitter shifts position so distance to perimeter is 2,057.5m
        // (exceeds 2,000m, but within 2,000m + 100m hysteresis margin)
        val point2550m = GeoCoordinates(MANGGARAI.latitude + 0.023, MANGGARAI.longitude)
        val edgeDist2550 = HaversineCalculator.distanceMeters(point2550m, MANGGARAI) - 500.0
        assertTrue(edgeDist2550 > 2_000.0 && edgeDist2550 < 2_100.0, "Expected edge dist in (2000, 2100), was $edgeDist2550")

        service.handleLocationUpdate(point2550m, 10f)
        // Must stay in APPROACH tier due to hysteresis guard
        assertEquals(TrackingTier.APPROACH, tracker.currentTier)

        // When commuter departs further to edge distance 2,113.1m (> 2,100m), step down to MEDIUM
        val point2610m = GeoCoordinates(MANGGARAI.latitude + 0.0235, MANGGARAI.longitude)
        val edgeDist2610 = HaversineCalculator.distanceMeters(point2610m, MANGGARAI) - 500.0
        assertTrue(edgeDist2610 > 2_100.0, "Expected edge dist > 2100, was $edgeDist2610")

        service.handleLocationUpdate(point2610m, 10f)
        assertEquals(TrackingTier.MEDIUM, tracker.currentTier)
    }

    @Test
    fun multiAlarm_nearestAlarmGovernsTrackingTier() = runTest {
        // Alarm 1 at Manggarai, Alarm 2 at Sudirman (~3.1 km apart)
        val alarm1 = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME).copy(id = "manggarai", coordinates = MANGGARAI)
        val alarm2 = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME).copy(id = "sudirman", coordinates = SUDIRMAN)

        service.saveAlarm(alarm1, enabled = true).getOrThrow()
        service.saveAlarm(alarm2, enabled = true).getOrThrow()

        // Commuter is ~1.19 km west of Sudirman and ~4.27 km from Manggarai.
        // Distance to alarm2 is 1.19 km (< 2 km -> APPROACH), while distance to alarm1 is 4.27 km (MEDIUM).
        // Nearest alarm (Sudirman) must govern the tier -> APPROACH (10s)
        val nearSudirman = GeoCoordinates(SUDIRMAN.latitude, SUDIRMAN.longitude - 0.0108)
        service.handleLocationUpdate(nearSudirman, 10f)
        assertEquals(TrackingTier.APPROACH, tracker.currentTier)
        assertEquals(10_000L, tracker.updateIntervalMillis)
    }

    @Test
    fun trackingStopsImmediately_whenAllAlarmsDismissedOrDisabled() = runTest {
        val alarm = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME)
        service.saveAlarm(alarm, enabled = true).getOrThrow()
        assertTrue(tracker.isTracking)

        // Commuter arrives inside destination -> triggers ringing
        service.handleLocationUpdate(MANGGARAI, 10f)
        // While ringing, tracking stops
        assertFalse(tracker.isTracking)

        // Dismiss alarm
        service.dismissAlarm(alarm.id).getOrThrow()
        assertFalse(tracker.isTracking)
    }
}
