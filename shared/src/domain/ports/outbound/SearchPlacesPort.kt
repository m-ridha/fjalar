package com.muhammadridha.fjalar.domain.ports.outbound

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.SearchResultPlace

interface SearchPlacesPort {
    suspend fun searchPlaces(
        query: String,
        locale: AppLocale,
        proximity: GeoCoordinates? = null,
    ): List<SearchResultPlace>
}
