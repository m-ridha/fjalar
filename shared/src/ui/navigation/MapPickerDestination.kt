package com.muhammadridha.fjalar.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import kotlin.reflect.typeOf

/** Preserve coordinate precision and reject non-finite route arguments. */
internal object CoordinateNavType : NavType<Double>(isNullableAllowed = false) {
    override fun put(bundle: SavedState, key: String, value: Double) {
        bundle.write { putDouble(key, value) }
    }

    override fun get(bundle: SavedState, key: String): Double? =
        bundle.read { if (contains(key)) getDouble(key) else null }

    override fun parseValue(value: String): Double {
        val coordinate = value.toDouble()
        require(coordinate.isFinite()) { "Coordinate must be finite" }
        return coordinate
    }

    override fun serializeAsValue(value: Double): String = value.toString()
}

/** Shared by the actual NavHost and graph-construction regression test. */
fun NavGraphBuilder.mapPickerDestination(
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<MapPickerRoute>(
        typeMap = mapOf(typeOf<Double>() to CoordinateNavType),
        content = content,
    )
}
