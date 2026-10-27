package com.muhammadridha.fjalar.ui.navigation

import androidx.savedstate.savedState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavigatorProvider
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Exercises the real Navigation graph. Android local tests lack the android.net.Uri runtime. */
class NavigationGraphConstructionTest {
    @Test
    fun allRootDestinationsBuildWithDoublePickerCoordinates() {
        val provider = NavigatorProvider()
        provider.addNavigator(NavGraphNavigator(provider))
        provider.addNavigator(ComposeNavigator())

        val graph = NavGraphBuilder(provider, MapRoute::class, null, emptyMap()).apply {
            composable<MapRoute> {}
            composable<AlarmsRoute> {}
            composable<ConfigureRoute> {}
            mapPickerDestination {}
        }.build()

        assertEquals(4, graph.count())
        val picker = graph.first { it.route?.contains("MapPickerRoute") == true }
        assertEquals(setOf("initialLat", "initialLon"), picker.arguments.keys)
    }

    @Test
    fun coordinateArgumentsSurviveSavedStateStorageAtFullPrecision() {
        val arguments = savedState()
        assertNull(CoordinateNavType[arguments, "missing"])
        CoordinateNavType.put(arguments, "initialLat", -6.2088123456)
        CoordinateNavType.put(arguments, "initialLon", 179.999999999)
        assertEquals(-6.2088123456, CoordinateNavType[arguments, "initialLat"])
        assertEquals(179.999999999, CoordinateNavType[arguments, "initialLon"])
    }
}
