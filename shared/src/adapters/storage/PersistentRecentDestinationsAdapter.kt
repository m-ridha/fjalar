package com.muhammadridha.fjalar.adapters.storage

import com.muhammadridha.fjalar.domain.model.RecentDestination
import com.muhammadridha.fjalar.domain.ports.outbound.RecentDestinationsPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Concrete outbound persistence adapter for [RecentDestinationsPort] backed by [KeyValueStorage].
 * Maintains an in-memory reactive cache backed by serialized JSON storage.
 */
class PersistentRecentDestinationsAdapter(
    private val storage: KeyValueStorage,
) : RecentDestinationsPort {

    private val mutex = Mutex()
    private val recentsFlow = MutableStateFlow<List<RecentDestination>>(loadInitialDestinations())

    override fun observeRecentDestinations(): Flow<List<RecentDestination>> =
        recentsFlow.asStateFlow()

    override suspend fun getRecentDestinations(): List<RecentDestination> =
        recentsFlow.value

    override suspend fun recordDestination(destination: RecentDestination): Unit = mutex.withLock {
        val updated = RecentDestination.updateRecents(
            current = recentsFlow.value,
            latest = destination,
        )
        val json = RecentDestinationJsonCodec.toJsonList(updated)
        storage.putString(KEY_RECENT_DESTINATIONS, json)
        recentsFlow.value = updated
    }

    override suspend fun clearRecentDestinations(): Unit = mutex.withLock {
        storage.remove(KEY_RECENT_DESTINATIONS)
        recentsFlow.value = emptyList()
    }

    private fun loadInitialDestinations(): List<RecentDestination> {
        val json = storage.getString(KEY_RECENT_DESTINATIONS)
        if (!json.isNullOrBlank()) {
            return RecentDestinationJsonCodec.fromJsonList(json)
        }
        return emptyList()
    }

    companion object {
        const val KEY_RECENT_DESTINATIONS = "fjalar_recent_destinations"
    }
}
