package com.muhammadridha.fjalar.adapters

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.LocationAlarmJsonCodec
import com.muhammadridha.fjalar.adapters.storage.PersistentAlarmRepository
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class PersistentStorageTest {

    @Test
    fun jsonCodec_roundTrip_withAllFields() {
        val alarm = LocationAlarm(
            id = "alarm-full-1",
            label = "Stasiun Manggarai",
            coordinates = GeoCoordinates(-6.2088, 106.8456),
            radius = TriggerRadius(500),
            schedule = AlarmSchedule(
                type = ScheduleType.SCHEDULED,
                startMinuteOfDay = 420,
                endMinuteOfDay = 540,
                daysOfWeekMask = AlarmSchedule.WEEKDAYS,
            ),
            soundConfig = AlarmSoundConfig(
                soundEnabled = true,
                vibrateEnabled = true,
                volume = 0.85f,
            ),
            state = AlarmState.ARMED_MONITORING,
            addressLabel = "Jl. Manggarai Utara 1, Jakarta",
            suppressedUntil = Instant.parse("2026-10-25T12:00:00Z"),
        )

        val json = LocationAlarmJsonCodec.toJson(alarm)
        val decoded = LocationAlarmJsonCodec.fromJson(json)

        assertNotNull(decoded)
        assertEquals(alarm.id, decoded.id)
        assertEquals(alarm.label, decoded.label)
        assertEquals(alarm.coordinates.latitude, decoded.coordinates.latitude)
        assertEquals(alarm.coordinates.longitude, decoded.coordinates.longitude)
        assertEquals(alarm.radius.meters, decoded.radius.meters)
        assertEquals(alarm.schedule.type, decoded.schedule.type)
        assertEquals(alarm.schedule.startMinuteOfDay, decoded.schedule.startMinuteOfDay)
        assertEquals(alarm.schedule.endMinuteOfDay, decoded.schedule.endMinuteOfDay)
        assertEquals(alarm.schedule.daysOfWeekMask, decoded.schedule.daysOfWeekMask)
        assertEquals(alarm.soundConfig.soundEnabled, decoded.soundConfig.soundEnabled)
        assertEquals(alarm.soundConfig.vibrateEnabled, decoded.soundConfig.vibrateEnabled)
        assertEquals(alarm.soundConfig.volume, decoded.soundConfig.volume)
        assertEquals(alarm.state, decoded.state)
        assertEquals(alarm.addressLabel, decoded.addressLabel)
        assertEquals(alarm.suppressedUntil, decoded.suppressedUntil)
        assertEquals(alarm, decoded)
    }

    @Test
    fun jsonCodec_roundTrip_withNullableAndDefaultFields() {
        val alarm = LocationAlarm(
            id = "alarm-minimal-1",
            label = "Office",
            coordinates = GeoCoordinates(37.7749, -122.4194),
            radius = TriggerRadius(1000),
            schedule = AlarmSchedule.ONE_TIME,
            soundConfig = AlarmSoundConfig(),
            state = AlarmState.DISABLED,
            addressLabel = null,
            suppressedUntil = null,
        )

        val json = LocationAlarmJsonCodec.toJson(alarm)
        val decoded = LocationAlarmJsonCodec.fromJson(json)

        assertNotNull(decoded)
        assertEquals(alarm, decoded)
        assertNull(decoded.addressLabel)
        assertNull(decoded.suppressedUntil)
    }

    @Test
    fun jsonCodec_defensiveParsing_handlesMalformedJsonGracefully() {
        assertNull(LocationAlarmJsonCodec.fromJson(""))
        assertNull(LocationAlarmJsonCodec.fromJson("{ invalid json"))
        assertNull(LocationAlarmJsonCodec.fromJson("""{"id": "test"}""")) // missing coordinates, radius, schedule
        assertNull(LocationAlarmJsonCodec.fromJson("""{
            "id": "bad-lat",
            "label": "Bad",
            "coordinates": {"latitude": 999.0, "longitude": 0.0},
            "radius": {"meters": 500},
            "schedule": {"type": "ONE_TIME"}
        }"""))
        assertNull(LocationAlarmJsonCodec.fromJson("""{
            "id": "bad-radius",
            "label": "Bad",
            "coordinates": {"latitude": 0.0, "longitude": 0.0},
            "radius": {"meters": 5},
            "schedule": {"type": "ONE_TIME"}
        }"""))
    }

    @Test
    fun persistentAlarmRepository_crudAndPersistence() = runTest {
        val storage = InMemoryKeyValueStorage()
        val repo1 = PersistentAlarmRepository(storage)

        // Initially empty
        assertTrue(repo1.getAlarms().isEmpty())
        assertEquals(emptyList<LocationAlarm>(), repo1.observeAlarms().first())
        assertNull(repo1.getAlarm("alarm-1"))

        val alarm1 = LocationAlarm(
            id = "alarm-1",
            label = "Work Commute",
            coordinates = GeoCoordinates(-6.2000, 106.8166),
            radius = TriggerRadius(750),
            schedule = AlarmSchedule.ONE_TIME,
        )
        val alarm2 = LocationAlarm(
            id = "alarm-2",
            label = "Gym",
            coordinates = GeoCoordinates(-6.2200, 106.8000),
            radius = TriggerRadius(300),
            schedule = AlarmSchedule(
                type = ScheduleType.SCHEDULED,
                startMinuteOfDay = 360,
                endMinuteOfDay = 480,
                daysOfWeekMask = AlarmSchedule.MONDAY or AlarmSchedule.WEDNESDAY,
            ),
        )

        // Upsert first alarm
        repo1.upsertAlarm(alarm1)
        assertEquals(1, repo1.getAlarms().size)
        assertEquals(alarm1, repo1.getAlarm("alarm-1"))
        assertEquals(listOf(alarm1), repo1.observeAlarms().first())

        // Verify restored in a new instance with same storage
        val repo2 = PersistentAlarmRepository(storage)
        assertEquals(1, repo2.getAlarms().size)
        assertEquals(alarm1, repo2.getAlarm("alarm-1"))

        // Upsert second alarm
        repo2.upsertAlarm(alarm2)
        assertEquals(2, repo2.getAlarms().size)
        assertEquals(alarm2, repo2.getAlarm("alarm-2"))

        // Update existing alarm
        val updatedAlarm1 = alarm1.copy(state = AlarmState.ARMED_MONITORING, addressLabel = "Updated Address")
        repo2.upsertAlarm(updatedAlarm1)
        val repo3 = PersistentAlarmRepository(storage)
        assertEquals(2, repo3.getAlarms().size)
        assertEquals(updatedAlarm1, repo3.getAlarm("alarm-1"))

        // Delete an alarm
        repo3.deleteAlarm("alarm-1")
        assertNull(repo3.getAlarm("alarm-1"))
        assertEquals(1, repo3.getAlarms().size)
        assertEquals(alarm2, repo3.getAlarm("alarm-2"))

        // Verify deletion persists
        val repo4 = PersistentAlarmRepository(storage)
        assertNull(repo4.getAlarm("alarm-1"))
        assertEquals(1, repo4.getAlarms().size)
        assertEquals(alarm2, repo4.getAlarm("alarm-2"))
    }

    @Test
    fun persistentPreferencesAdapter_mutationsAndFlowEmissions() = runTest {
        val storage = InMemoryKeyValueStorage()
        val prefs1 = PersistentPreferencesAdapter(storage)

        // Initial defaults
        assertEquals(ThemeMode.SYSTEM, prefs1.observeThemeMode().first())
        assertEquals(AppLocale.EN, prefs1.observeLocale().first())

        // Mutate theme mode
        prefs1.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, prefs1.observeThemeMode().first())

        // Mutate locale
        prefs1.setLocale(AppLocale.ID)
        assertEquals(AppLocale.ID, prefs1.observeLocale().first())

        // Verify new instance restores persisted preferences
        val prefs2 = PersistentPreferencesAdapter(storage)
        assertEquals(ThemeMode.DARK, prefs2.observeThemeMode().first())
        assertEquals(AppLocale.ID, prefs2.observeLocale().first())

        // Change again
        prefs2.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, prefs2.observeThemeMode().first())
        val prefs3 = PersistentPreferencesAdapter(storage)
        assertEquals(ThemeMode.LIGHT, prefs3.observeThemeMode().first())
    }

    @Test
    fun persistentAlarmRepository_concurrentUpserts_preventsLostUpdates() = runTest {
        val storage = InMemoryKeyValueStorage()
        val repo = PersistentAlarmRepository(storage)

        val totalAlarms = 50
        val alarmsToInsert = (1..totalAlarms).map { index ->
            LocationAlarm(
                id = "concurrent-alarm-$index",
                label = "Location $index",
                coordinates = GeoCoordinates(-6.0 + (index * 0.001), 106.0 + (index * 0.001)),
                radius = TriggerRadius(200 + index),
                schedule = AlarmSchedule.ONE_TIME,
            )
        }

        coroutineScope {
            alarmsToInsert.map { alarm ->
                async {
                    repo.upsertAlarm(alarm)
                }
            }.awaitAll()
        }

        val inMemoryAlarms = repo.getAlarms()
        assertEquals(totalAlarms, inMemoryAlarms.size)
        assertEquals(totalAlarms, repo.observeAlarms().first().size)

        val reloadedRepo = PersistentAlarmRepository(storage)
        val persistedAlarms = reloadedRepo.getAlarms()
        assertEquals(totalAlarms, persistedAlarms.size)
        alarmsToInsert.forEach { expected ->
            assertEquals(expected, reloadedRepo.getAlarm(expected.id))
        }
    }
}
