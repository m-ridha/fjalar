package com.muhammadridha.fjalar.domain.ports.outbound

import com.muhammadridha.fjalar.domain.model.RecentDestination
import kotlinx.coroutines.flow.Flow

/**
 * Outbound port for persisting and observing recently selected destinations.
 */
interface RecentDestinationsPort {
    fun observeRecentDestinations(): Flow<List<RecentDestination>>
    suspend fun getRecentDestinations(): List<RecentDestination>
    suspend fun recordDestination(destination: RecentDestination)
    suspend fun clearRecentDestinations()
}
