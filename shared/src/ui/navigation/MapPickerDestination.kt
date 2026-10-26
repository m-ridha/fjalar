package com.muhammadridha.fjalar.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.core.bundle.Bundle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import kotlin.reflect.typeOf

/** Navigation 2.8 does not infer Double arguments; keep coordinates at full precision. */
internal object CoordinateNavType : NavType<Double>(isNullableAllowed = false) {
    override fun put(bundle: Bundle, key: String, value: Double) {
        bundle.putDouble(key, value)
    }

    override fun get(bundle: Bundle, key: String): Double? =
        if (bundle.containsKey(key)) bundle.getDouble(key) else null

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
