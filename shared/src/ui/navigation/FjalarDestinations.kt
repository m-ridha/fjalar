package com.muhammadridha.fjalar.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destination routes for Fjalar.
 */
@Serializable
data object MapRoute

@Serializable
data object AlarmsRoute

@Serializable
data class ConfigureRoute(val alarmId: String? = null)

@Serializable
data class MapPickerRoute(val initialLat: Double, val initialLon: Double)

/**
 * Checks whether the given route matches [MapRoute].
 * Handles typed route objects, class references, or string qualified class names from NavBackStackEntry.
 */
fun isMapRoute(route: Any?): Boolean = when (route) {
    is MapRoute, MapRoute::class -> true
    is String -> route == "MapRoute" || route == "com.muhammadridha.fjalar.ui.navigation.MapRoute"
    else -> false
}

/**
 * Checks whether the given route matches [AlarmsRoute].
 * Handles typed route objects, class references, or string qualified class names from NavBackStackEntry.
 */
fun isAlarmsRoute(route: Any?): Boolean = when (route) {
    is AlarmsRoute, AlarmsRoute::class -> true
    is String -> route == "AlarmsRoute" || route == "com.muhammadridha.fjalar.ui.navigation.AlarmsRoute"
    else -> false
}

/**
 * Determines whether the bottom navigation bar should be visible for the given route.
 * Bottom navigation is only visible on top-level tabs (MapRoute and AlarmsRoute).
 */
fun isBottomBarVisible(route: Any?): Boolean =
    isMapRoute(route ?: "MapRoute") || isAlarmsRoute(route)
