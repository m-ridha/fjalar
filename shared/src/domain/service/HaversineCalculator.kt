package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object HaversineCalculator : DistanceCalculator {
    /** IUGG mean Earth radius. */
    const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8

    override fun calculateDistanceMeters(from: GeoCoordinates, to: GeoCoordinates): Double =
        distanceMeters(from, to)

    fun distanceMeters(from: GeoCoordinates, to: GeoCoordinates): Double {
        val lat1 = from.latitude.toRadians()
        val lat2 = to.latitude.toRadians()
        val halfDLat = sin((lat2 - lat1) / 2)
        val halfDLon = sin((to.longitude - from.longitude).toRadians() / 2)
        val a = halfDLat * halfDLat + cos(lat1) * cos(lat2) * halfDLon * halfDLon
        // min() guards against floating-point drift above 1 for near-antipodal points.
        return 2 * EARTH_MEAN_RADIUS_METERS * asin(sqrt(min(1.0, a)))
    }

    private fun Double.toRadians() = this * PI / 180
}
