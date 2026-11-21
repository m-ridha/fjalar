package com.muhammadridha.fjalar.domain.ports.outbound

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface PreferencesPort {
    /**
     * Synchronously readable stored theme choice, or null when the adapter cannot read it without
     * suspending. Lets the first frame use the user's choice instead of waiting for a collector.
     */
    fun peekThemeMode(): ThemeMode? = null

    /**
     * Device-local position last observed by the app, readable without suspending so a cold start
     * can open the map where the user last was instead of the neutral `0,0` origin. Null when
     * nothing valid has been stored yet.
     */
    fun peekLastKnownLocation(): GeoCoordinates? = null

    /**
     * Remembers an observed position on device so the next cold start has a real viewport.
     * Best effort: callers treat a failed write as a missed optimization, never as an error.
     */
    fun rememberLastKnownLocation(coordinates: GeoCoordinates) = Unit

    fun observeThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    fun observeLocale(): Flow<AppLocale>
    suspend fun setLocale(locale: AppLocale)
}
