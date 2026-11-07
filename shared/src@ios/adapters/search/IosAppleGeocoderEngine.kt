package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLPlacemark
import platform.Foundation.NSLocale
import kotlin.coroutines.resume

/**
 * iOS implementation of [OnlineGeocodingEngine] backed by Apple CoreLocation [CLGeocoder].
 * Provides native geocoding capabilities on Apple platforms.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAppleGeocoderEngine(
    private val geocoderProvider: () -> CLGeocoder = { CLGeocoder() },
) : OnlineGeocodingEngine {

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
                val geocoder = geocoderProvider()
                continuation.invokeOnCancellation {
                    geocoder.cancelGeocode()
                }

                val preferredLocale = NSLocale(localeIdentifier = if (locale == AppLocale.ID) "id_ID" else "en_US")
                geocoder.geocodeAddressString(
                    addressString = trimmed,
                    inRegion = null,
                    preferredLocale = preferredLocale,
                ) { placemarks, error ->
                    if (error != null || placemarks == null) {
                        if (continuation.isActive) {
                            continuation.resume(emptyList())
                        }
                        return@geocodeAddressString
                    }

                    val results = mutableListOf<SearchResultPlace>()
                    for (i in 0 until placemarks.size) {
                        if (results.size >= limit) break
                        val placemark = placemarks[i] as? CLPlacemark ?: continue
                        val location = placemark.location ?: continue

                        val coords = location.coordinate.useContents {
                            val lat = latitude.coerceIn(-90.0, 90.0)
                            val lon = longitude.coerceIn(-180.0, 180.0)
                            GeoCoordinates(lat, lon)
                        }

                        val name = placemark.name ?: trimmed
                        val detailParts = listOfNotNull(
                            placemark.locality,
                            placemark.administrativeArea,
                            placemark.country,
                        )
                        val detail = if (detailParts.isNotEmpty()) detailParts.joinToString(", ") else null
                        val id = "apple_${coords.latitude}_${coords.longitude}_$i"

                        results.add(
                            SearchResultPlace(
                                id = id,
                                name = name,
                                coordinates = coords,
                                category = PlaceCategory.GENERAL,
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
}
