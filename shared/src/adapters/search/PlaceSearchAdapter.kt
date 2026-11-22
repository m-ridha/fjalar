package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import com.muhammadridha.fjalar.domain.service.DistanceCalculator
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round

/**
 * Online-only implementation of [SearchPlacesPort].
 *
 * Queries a single geocoding engine with a coarse (2-decimal) proximity so the provider can
 * bias results without receiving household-level coordinates. Ranking and deduplication use
 * the exact on-device distance, and no provider result is persisted.
 */
class PlaceSearchAdapter(
    private val onlineEngine: OnlineGeocodingEngine,
    private val distanceCalculator: DistanceCalculator = HaversineCalculator,
    private val resultLimit: Int = RESULT_LIMIT,
    private val minQueryLength: Int = MIN_QUERY_LENGTH,
) : SearchPlacesPort {

    override suspend fun searchPlaces(
        query: String,
        locale: AppLocale,
        proximity: GeoCoordinates?,
    ): List<SearchResultPlace> {
        val trimmed = query.trim()
        if (trimmed.length < minQueryLength) return emptyList()

        val engineProximity = proximity?.let(::coarseCoordinates)
        val providerResults = try {
            onlineEngine.geocode(
                query = trimmed,
                locale = locale,
                proximity = engineProximity,
                limit = resultLimit,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            emptyList()
        }

        val ranked = if (proximity != null) {
            providerResults.sortedBy { place ->
                distanceCalculator.calculateDistanceMeters(proximity, place.coordinates)
            }
        } else {
            providerResults
        }

        return deduplicate(ranked).take(resultLimit)
    }

    private fun deduplicate(places: List<SearchResultPlace>): List<SearchResultPlace> {
        val kept = mutableListOf<SearchResultPlace>()
        for (place in places) {
            val isDuplicate = kept.any { existing ->
                existing.id.equals(place.id, ignoreCase = true) ||
                    distanceCalculator.calculateDistanceMeters(
                        existing.coordinates,
                        place.coordinates,
                    ) < DEDUPLICATION_METERS
            }
            if (!isDuplicate) kept.add(place)
        }
        return kept
    }

    companion object {
        const val RESULT_LIMIT: Int = 8
        const val MIN_QUERY_LENGTH: Int = 3
        const val DEDUPLICATION_METERS: Double = 50.0
        const val PROVIDER_COORDINATE_DECIMALS: Int = 2
    }
}

private const val COORDINATE_SCALE: Double = 100.0

/**
 * Rounds coordinates to [PlaceSearchAdapter.PROVIDER_COORDINATE_DECIMALS] so geocoders only
 * receive an approximate area (about 1.1 km at the equator).
 */
internal fun coarseCoordinates(coordinates: GeoCoordinates): GeoCoordinates =
    GeoCoordinates(
        latitude = round(coordinates.latitude * COORDINATE_SCALE) / COORDINATE_SCALE,
        longitude = round(coordinates.longitude * COORDINATE_SCALE) / COORDINATE_SCALE,
    )
