package com.muhammadridha.fjalar.domain.model

import com.muhammadridha.fjalar.domain.service.DistanceCalculator
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import kotlin.time.Instant

/**
 * Represents a recently selected destination in the search sheet.
 *
 * Invariant: [id] and [name] must not be blank.
 */
data class RecentDestination(
    val id: String,
    val name: String,
    val coordinates: GeoCoordinates,
    val timestamp: Instant,
    val category: PlaceCategory = PlaceCategory.GENERAL,
) {
    init {
        require(id.isNotBlank()) { "Destination id must not be blank" }
        require(name.isNotBlank()) { "Destination name must not be blank" }
    }

    companion object {
        const val MAX_CAPACITY = 5
        const val PROXIMITY_DEDUPLICATION_METERS = 50.0

        /**
         * Updates a list of recent destinations with a [latest] entry:
         * 1. Deduplicates against existing items matching by id, case-insensitive trimmed name,
         *    or within [proximityMeters] distance.
         * 2. Prepends [latest] to the front.
         * 3. Truncates the result to [maxEntries].
         */
        fun updateRecents(
            current: List<RecentDestination>,
            latest: RecentDestination,
            maxEntries: Int = MAX_CAPACITY,
            proximityMeters: Double = PROXIMITY_DEDUPLICATION_METERS,
            distanceCalculator: DistanceCalculator = HaversineCalculator,
        ): List<RecentDestination> {
            val filtered = current.filterNot { existing ->
                val matchesId = existing.id == latest.id
                val matchesName = existing.name.trim().equals(latest.name.trim(), ignoreCase = true)
                val isNearby = distanceCalculator.calculateDistanceMeters(
                    from = existing.coordinates,
                    to = latest.coordinates,
                ) <= proximityMeters

                matchesId || matchesName || isNearby
            }

            return (listOf(latest) + filtered).take(maxEntries)
        }
    }
}

/**
 * Extension function to create a [RecentDestination] from a [SearchResultPlace] and timestamp.
 */
fun SearchResultPlace.toRecentDestination(timestamp: Instant): RecentDestination =
    RecentDestination(
        id = id,
        name = name,
        coordinates = coordinates,
        timestamp = timestamp,
        category = category,
    )
