package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.MapKit.MKCoordinateRegion
import platform.MapKit.MKLocalSearch
import platform.MapKit.MKLocalSearchRequest
import platform.MapKit.MKMapItem
import platform.MapKit.MKPointOfInterestCategoryAirport
import platform.MapKit.MKPointOfInterestCategoryPublicTransport
import kotlin.coroutines.resume

/**
 * iOS implementation of [OnlineGeocodingEngine] backed by MapKit [MKLocalSearch].
 *
 * MapKit returns point-of-interest categories, which keeps the Train/Metro/Bus/Transit filters
 * useful outside OSM-tagged results. Region bias stays approximate because the adapter rounds
 * the requested proximity before calling this engine.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAppleLocalSearchEngine : OnlineGeocodingEngine {

    override suspend fun geocode(
        query: String,
        locale: AppLocale,
        proximity: GeoCoordinates?,
        limit: Int,
    ): List<SearchResultPlace> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || limit <= 0) {
            return emptyList()
        }

        return try {
            suspendCancellableCoroutine { continuation ->
                val request = MKLocalSearchRequest().apply {
                    naturalLanguageQuery = trimmed
                    if (proximity != null) {
                        region = cValue<MKCoordinateRegion> {
                            center.latitude = proximity.latitude
                            center.longitude = proximity.longitude
                            span.latitudeDelta = BIAS_SPAN_DEGREES
                            span.longitudeDelta = BIAS_SPAN_DEGREES
                        }
                    }
                }
                val search = MKLocalSearch(request)
                continuation.invokeOnCancellation { search.cancel() }

                search.startWithCompletionHandler { response, error ->
                    if (error != null || response == null) {
                        if (continuation.isActive) {
                            continuation.resume(emptyList())
                        }
                        return@startWithCompletionHandler
                    }

                    val results = mutableListOf<SearchResultPlace>()
                    val mapItems = response.mapItems
                    for (index in mapItems.indices) {
                        if (results.size >= limit) break
                        val mapItem = mapItems[index] as? MKMapItem ?: continue
                        val placemark = mapItem.placemark
                        val location = placemark.location ?: continue

                        val coordinates = location.coordinate.useContents {
                            GeoCoordinates(
                                latitude = latitude.coerceIn(-90.0, 90.0),
                                longitude = longitude.coerceIn(-180.0, 180.0),
                            )
                        }
                        val name = mapItem.name ?: placemark.name ?: continue

                        val detailParts = listOfNotNull(
                            placemark.locality?.takeIf { it.isNotBlank() },
                            placemark.administrativeArea?.takeIf { it.isNotBlank() },
                            placemark.country?.takeIf { it.isNotBlank() },
                        )
                        val detail = detailParts.takeIf { it.isNotEmpty() }?.joinToString(", ")

                        val providerCategory = when (mapItem.pointOfInterestCategory) {
                            MKPointOfInterestCategoryPublicTransport,
                            MKPointOfInterestCategoryAirport,
                            -> PlaceCategory.TRANSIT_STOP
                            else -> PlaceCategory.GENERAL
                        }

                        results.add(
                            SearchResultPlace(
                                id = "mk_${index}_${coordinates.latitude}_${coordinates.longitude}",
                                name = name,
                                coordinates = coordinates,
                                category = PlaceCategoryHeuristics.refine(name, providerCategory),
                                detail = detail,
                            )
                        )
                    }

                    if (continuation.isActive) {
                        continuation.resume(results)
                    }
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            emptyList()
        }
    }

    companion object {
        private const val BIAS_SPAN_DEGREES: Double = 0.35
    }
}
