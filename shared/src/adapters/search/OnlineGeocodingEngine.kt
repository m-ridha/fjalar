package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.SearchResultPlace

/**
 * Interface contract for platform or network-based geocoding engines used as a fallback
 * when local offline station catalog matching returns fewer results than desired.
 */
interface OnlineGeocodingEngine {
    suspend fun geocode(
        query: String,
        locale: AppLocale,
        proximity: GeoCoordinates? = null,
        limit: Int = 5,
    ): List<SearchResultPlace>
}
