package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import com.muhammadridha.fjalar.domain.service.DistanceCalculator
import com.muhammadridha.fjalar.domain.service.HaversineCalculator

/**
 * Hybrid implementation of [SearchPlacesPort].
 * Combines zero-latency, privacy-first offline station catalog lookup with an optional
 * online fallback when offline matches fall below [offlineMatchThreshold].
 */
class HybridPlaceSearchAdapter(
    private val offlineStations: List<SearchResultPlace> = TransitStationCatalog.STATIONS,
    private val onlineEngine: OnlineGeocodingEngine? = null,
    private val distanceCalculator: DistanceCalculator = HaversineCalculator,
    private val offlineMatchThreshold: Int = 5,
) : SearchPlacesPort {

    override suspend fun searchPlaces(
        query: String,
        locale: AppLocale,
        proximity: GeoCoordinates?,
    ): List<SearchResultPlace> {
        if (query.isBlank()) {
            return emptyList()
        }

        val matchingOffline = offlineStations.filter { place ->
            place.matchesQuery(query)
        }

        val sortedOffline = if (proximity != null) {
            matchingOffline.sortedBy { place ->
                distanceCalculator.calculateDistanceMeters(proximity, place.coordinates)
            }
        } else {
            matchingOffline
        }

        if (sortedOffline.size >= offlineMatchThreshold || onlineEngine == null) {
            return sortedOffline
        }

        val remainingLimit = offlineMatchThreshold - sortedOffline.size
        val onlineResults = try {
            onlineEngine.geocode(
                query = query,
                locale = locale,
                proximity = proximity,
                limit = remainingLimit,
            )
        } catch (_: Exception) {
            emptyList()
        }

        if (onlineResults.isEmpty()) {
            return sortedOffline
        }

        val combined = sortedOffline.toMutableList()
        for (onlinePlace in onlineResults) {
            val isDuplicate = combined.any { existing ->
                existing.id.equals(onlinePlace.id, ignoreCase = true) ||
                    distanceCalculator.calculateDistanceMeters(existing.coordinates, onlinePlace.coordinates) < 50.0
            }
            if (!isDuplicate) {
                combined.add(onlinePlace)
            }
        }

        return combined
    }
}
