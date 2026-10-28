package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.adapters.location.isFreshLocationFix
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.LocationAvailability
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import kotlin.math.roundToInt

/** Derives display data only. Alarm trigger accuracy and radius remain domain decisions. */
internal fun FjalarUiState.withLocationDistances(): FjalarUiState {
    val fix = locationFix?.takeIf {
        val age = it.monotonicObservation.elapsedNow()
        locationAvailability == LocationAvailability.AVAILABLE &&
            isFreshLocationFix(age)
    }
    val dot = fix?.takeIf { it.horizontalAccuracyMeters <= 1_000f }
    val preciseFix = fix?.takeIf { it.horizontalAccuracyMeters <= 100f }
    val actualDistances = alarms.associate { alarm ->
        alarm.id to preciseFix?.let {
            HaversineCalculator.distanceMeters(it.coordinates, alarm.coordinates)
        }
    }
    val nearest = alarms.filter { it.state == AlarmState.ARMED_MONITORING }
        .minWithOrNull(compareBy({ actualDistances[it.id] ?: Double.POSITIVE_INFINITY }, { it.id }))
    return copy(
        userLocation = dot?.coordinates,
        distanceMetersByAlarmId = actualDistances.mapValues { (_, distance) -> distance?.roundToInt() },
        nearestArmedAlarm = nearest,
    )
}
