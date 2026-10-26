package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.model.GeoCoordinates

/**
 * Functional interface for calculating distance in meters between two geographical coordinates.
 */
fun interface DistanceCalculator {
    fun calculateDistanceMeters(from: GeoCoordinates, to: GeoCoordinates): Double
}
