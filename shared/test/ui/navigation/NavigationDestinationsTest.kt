package com.muhammadridha.fjalar.ui.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationDestinationsTest {

    @Test
    fun configureRoute_defaultAlarmIdIsNull() {
        val route = ConfigureRoute()
        assertNull(route.alarmId)
    }

    @Test
    fun configureRoute_customAlarmIdIsPreserved() {
        val route = ConfigureRoute(alarmId = "alarm-123")
        assertEquals("alarm-123", route.alarmId)
    }

    @Test
    fun mapPickerRoute_coordinatesArePreserved() {
        val route = MapPickerRoute(initialLat = -6.2088, initialLon = 106.8456)
        assertEquals(-6.2088, route.initialLat)
        assertEquals(106.8456, route.initialLon)
    }

    @Test
    fun serialization_roundtripsRoutes() {
        val json = Json { ignoreUnknownKeys = true }

        val config = ConfigureRoute("alarm-xyz")
        val configJson = json.encodeToString(config)
        val decodedConfig = json.decodeFromString<ConfigureRoute>(configJson)
        assertEquals(config, decodedConfig)

        val picker = MapPickerRoute(-6.1754, 106.8272)
        val pickerJson = json.encodeToString(picker)
        val decodedPicker = json.decodeFromString<MapPickerRoute>(pickerJson)
        assertEquals(picker, decodedPicker)

        val mapJson = json.encodeToString(MapRoute)
        val decodedMap = json.decodeFromString<MapRoute>(mapJson)
        assertEquals(MapRoute, decodedMap)

        val alarmsJson = json.encodeToString(AlarmsRoute)
        val decodedAlarms = json.decodeFromString<AlarmsRoute>(alarmsJson)
        assertEquals(AlarmsRoute, decodedAlarms)
    }

    @Test
    fun isMapRoute_matchesCorrectly() {
        assertTrue(isMapRoute(MapRoute))
        assertTrue(isMapRoute(MapRoute::class))
        assertTrue(isMapRoute("com.muhammadridha.fjalar.ui.navigation.MapRoute"))
        assertFalse(isMapRoute(AlarmsRoute))
        assertFalse(isMapRoute(ConfigureRoute()))
        assertFalse(isMapRoute(null))
    }

    @Test
    fun isAlarmsRoute_matchesCorrectly() {
        assertTrue(isAlarmsRoute(AlarmsRoute))
        assertTrue(isAlarmsRoute(AlarmsRoute::class))
        assertTrue(isAlarmsRoute("com.muhammadridha.fjalar.ui.navigation.AlarmsRoute"))
        assertFalse(isAlarmsRoute(MapRoute))
        assertFalse(isAlarmsRoute(ConfigureRoute()))
        assertFalse(isAlarmsRoute(null))
    }

    @Test
    fun resolveActiveTab_resolvesExpectedTabs() {
        assertEquals(NavigationTab.MAP, resolveActiveTab(MapRoute))
        assertEquals(NavigationTab.MAP, resolveActiveTab(MapRoute::class))
        assertEquals(NavigationTab.ALARMS, resolveActiveTab(AlarmsRoute))
        assertEquals(NavigationTab.ALARMS, resolveActiveTab(AlarmsRoute::class))
        assertNull(resolveActiveTab(ConfigureRoute()))
        assertNull(resolveActiveTab(MapPickerRoute(-6.2088, 106.8456)))
        assertNull(resolveActiveTab(null))
    }
}
