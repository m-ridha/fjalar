package com.muhammadridha.fjalar.adapters.storage

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.RecentDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class PersistentRecentDestinationsAdapterTest {

    private fun sampleDestination(
        id: String = "dest-1",
        name: String = "Stasiun Manggarai",
        latitude: Double = -6.2098,
        longitude: Double = 106.8501,
        epochMillis: Long = 1731000000000L,
        category: PlaceCategory = PlaceCategory.TRAIN_STATION,
    ): RecentDestination = RecentDestination(
        id = id,
        name = name,
        coordinates = GeoCoordinates(latitude, longitude),
        timestamp = Instant.fromEpochMilliseconds(epochMillis),
        category = category,
    )

    // =========================================================================
    // RecentDestinationJsonCodec Tests
    // =========================================================================

    @Test
    fun codec_singleDestination_roundTrip() {
        val original = sampleDestination()
        val json = RecentDestinationJsonCodec.toJson(original)

        assertTrue(json.contains("\"id\":\"dest-1\""))
        assertTrue(json.contains("\"name\":\"Stasiun Manggarai\""))
        assertTrue(json.contains("\"latitude\":-6.2098"))
        assertTrue(json.contains("\"longitude\":106.8501"))
        assertTrue(json.contains("\"timestamp\":1731000000000"))
        assertTrue(json.contains("\"category\":\"TRAIN_STATION\""))

        val decoded = RecentDestinationJsonCodec.fromJson(json)
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun codec_listDestinations_roundTrip() {
        val list = listOf(
            sampleDestination(id = "dest-1", name = "Stasiun Manggarai", latitude = -6.2098, longitude = 106.8501),
            sampleDestination(id = "dest-2", name = "Stasiun Sudirman", latitude = -6.2024, longitude = 106.8236),
            sampleDestination(id = "dest-3", name = "Stasiun Tanah Abang", latitude = -6.1856, longitude = 106.8110),
        )

        val json = RecentDestinationJsonCodec.toJsonList(list)
        val decoded = RecentDestinationJsonCodec.fromJsonList(json)

        assertEquals(3, decoded.size)
        assertEquals(list, decoded)
    }

    @Test
    fun codec_emptyList_blankString_andCorruptJson_handledGracefully() {
        val emptyJson = RecentDestinationJsonCodec.toJsonList(emptyList())
        assertEquals("[]", emptyJson)
        assertEquals(emptyList(), RecentDestinationJsonCodec.fromJsonList(emptyJson))

        // Blank strings
        assertEquals(emptyList(), RecentDestinationJsonCodec.fromJsonList(""))
        assertEquals(emptyList(), RecentDestinationJsonCodec.fromJsonList("   "))
        assertNull(RecentDestinationJsonCodec.fromJson(""))
        assertNull(RecentDestinationJsonCodec.fromJson("   "))

        // Corrupt syntax
        assertEquals(emptyList(), RecentDestinationJsonCodec.fromJsonList("{ not an array }"))
        assertEquals(emptyList(), RecentDestinationJsonCodec.fromJsonList("[{ broken"))
        assertNull(RecentDestinationJsonCodec.fromJson("{ broken json"))
        assertNull(RecentDestinationJsonCodec.fromJson("[ not an object ]"))
    }

    @Test
    fun codec_missingOrBadFields_skipsBadElements_returnsValidOnes() {
        val json = """
            [
                {
                    "id": "valid-1",
                    "name": "Valid Destination 1",
                    "coordinates": { "latitude": -6.2, "longitude": 106.8 },
                    "timestamp": 1731000000000,
                    "category": "METRO_STATION"
                },
                {
                    "id": "",
                    "name": "Blank ID",
                    "coordinates": { "latitude": -6.2, "longitude": 106.8 },
                    "timestamp": 1731000000000
                },
                {
                    "id": "bad-2",
                    "name": "   ",
                    "coordinates": { "latitude": -6.2, "longitude": 106.8 },
                    "timestamp": 1731000000000
                },
                {
                    "id": "bad-3",
                    "name": "Missing Coords",
                    "timestamp": 1731000000000
                },
                {
                    "id": "bad-4",
                    "name": "Invalid Lat Bounds",
                    "coordinates": { "latitude": 95.0, "longitude": 106.8 },
                    "timestamp": 1731000000000
                },
                {
                    "id": "bad-5",
                    "name": "Missing Timestamp",
                    "coordinates": { "latitude": -6.2, "longitude": 106.8 }
                },
                {
                    "id": "valid-2",
                    "name": "Valid Destination 2 (Fallback Category)",
                    "coordinates": { "latitude": -6.3, "longitude": 106.9 },
                    "timestamp": 1731000001000,
                    "category": "NON_EXISTENT_CATEGORY"
                }
            ]
        """.trimIndent()

        val decoded = RecentDestinationJsonCodec.fromJsonList(json)
        assertEquals(2, decoded.size)

        assertEquals("valid-1", decoded[0].id)
        assertEquals(PlaceCategory.METRO_STATION, decoded[0].category)

        assertEquals("valid-2", decoded[1].id)
        assertEquals(PlaceCategory.GENERAL, decoded[1].category) // Fallback to GENERAL
    }

    @Test
    fun codec_timestampParsing_fromEpochMilliseconds_andIsoString() {
        val jsonEpoch = """
            {
                "id": "dest-epoch",
                "name": "Epoch Station",
                "coordinates": { "latitude": -6.2, "longitude": 106.8 },
                "timestamp": 1731000000000,
                "category": "TRANSIT_STOP"
            }
        """.trimIndent()

        val decodedEpoch = RecentDestinationJsonCodec.fromJson(jsonEpoch)
        assertNotNull(decodedEpoch)
        assertEquals(Instant.fromEpochMilliseconds(1731000000000L), decodedEpoch.timestamp)

        val jsonIso = """
            {
                "id": "dest-iso",
                "name": "ISO Station",
                "coordinates": { "latitude": -6.2, "longitude": 106.8 },
                "timestamp": "2026-11-08T03:00:00Z",
                "category": "LANDMARK"
            }
        """.trimIndent()

        val decodedIso = RecentDestinationJsonCodec.fromJson(jsonIso)
        assertNotNull(decodedIso)
        assertEquals(Instant.parse("2026-11-08T03:00:00Z"), decodedIso.timestamp)
    }

    @Test
    fun codec_flatCoordinates_parsedCorrectly() {
        val jsonFlat = """
            {
                "id": "dest-flat",
                "name": "Flat Coordinates Station",
                "latitude": -6.2098,
                "longitude": 106.8501,
                "timestamp": 1731000000000
            }
        """.trimIndent()

        val decoded = RecentDestinationJsonCodec.fromJson(jsonFlat)
        assertNotNull(decoded)
        assertEquals(-6.2098, decoded.coordinates.latitude)
        assertEquals(106.8501, decoded.coordinates.longitude)
        assertEquals(PlaceCategory.GENERAL, decoded.category)
    }

    // =========================================================================
    // PersistentRecentDestinationsAdapter Tests
    // =========================================================================

    @Test
    fun adapter_initialState_isEmpty() = runTest {
        val storage = InMemoryKeyValueStorage()
        val adapter = PersistentRecentDestinationsAdapter(storage)

        assertTrue(adapter.getRecentDestinations().isEmpty())
        assertTrue(adapter.observeRecentDestinations().first().isEmpty())
    }

    @Test
    fun adapter_recordDestination_emitsInFlow_andReturnsInGetRecentDestinations() = runTest {
        val storage = InMemoryKeyValueStorage()
        val adapter = PersistentRecentDestinationsAdapter(storage)
        val destination = sampleDestination()

        adapter.recordDestination(destination)

        val direct = adapter.getRecentDestinations()
        assertEquals(1, direct.size)
        assertEquals(destination, direct.first())

        val observed = adapter.observeRecentDestinations().first()
        assertEquals(1, observed.size)
        assertEquals(destination, observed.first())
    }

    @Test
    fun adapter_preservedAcrossNewAdapterInstance_sharingSameStorage() = runTest {
        val storage = InMemoryKeyValueStorage()
        val adapter1 = PersistentRecentDestinationsAdapter(storage)

        val dest1 = sampleDestination(id = "dest-1", name = "Station 1", latitude = -6.1, longitude = 106.1)
        val dest2 = sampleDestination(id = "dest-2", name = "Station 2", latitude = -6.5, longitude = 106.5)

        adapter1.recordDestination(dest1)
        adapter1.recordDestination(dest2)

        // Instantiate second adapter instance pointing to the same storage
        val adapter2 = PersistentRecentDestinationsAdapter(storage)

        val loaded = adapter2.getRecentDestinations()
        assertEquals(2, loaded.size)
        assertEquals("dest-2", loaded[0].id)
        assertEquals("dest-1", loaded[1].id)

        val observed = adapter2.observeRecentDestinations().first()
        assertEquals(2, observed.size)
        assertEquals("dest-2", observed[0].id)
        assertEquals("dest-1", observed[1].id)
    }

    @Test
    fun adapter_lruDeduplication_reRecordingMovesToFront_andCapsAtMaxCapacity() = runTest {
        val storage = InMemoryKeyValueStorage()
        val adapter = PersistentRecentDestinationsAdapter(storage)

        // Record 5 distinct items with coordinates far apart
        for (i in 1..5) {
            adapter.recordDestination(
                sampleDestination(
                    id = "dest-$i",
                    name = "Station $i",
                    latitude = -6.0 - (i * 0.1),
                    longitude = 106.0 + (i * 0.1),
                    epochMillis = 1000L * i,
                ),
            )
        }

        var recents = adapter.getRecentDestinations()
        assertEquals(5, recents.size)
        assertEquals("dest-5", recents[0].id)
        assertEquals("dest-1", recents[4].id)

        // Record a 6th item -> dest-1 should be evicted (max 5 capacity)
        val dest6 = sampleDestination(
            id = "dest-6",
            name = "Station 6",
            latitude = -7.0,
            longitude = 107.0,
            epochMillis = 6000L,
        )
        adapter.recordDestination(dest6)

        recents = adapter.getRecentDestinations()
        assertEquals(5, recents.size)
        assertEquals("dest-6", recents[0].id)
        assertEquals("dest-2", recents[4].id)
        assertTrue(recents.none { it.id == "dest-1" })

        // Re-record dest-3 with newer timestamp -> moves to front
        val dest3Updated = sampleDestination(
            id = "dest-3",
            name = "Station 3",
            latitude = -6.3,
            longitude = 106.3,
            epochMillis = 7000L,
        )
        adapter.recordDestination(dest3Updated)

        recents = adapter.getRecentDestinations()
        assertEquals(5, recents.size)
        assertEquals("dest-3", recents[0].id)
        assertEquals(7000L, recents[0].timestamp.toEpochMilliseconds())
        assertEquals("dest-6", recents[1].id)
    }

    @Test
    fun adapter_clearRecentDestinations_clearsBothMemoryFlowAndStorage() = runTest {
        val storage = InMemoryKeyValueStorage()
        val adapter = PersistentRecentDestinationsAdapter(storage)

        adapter.recordDestination(sampleDestination())
        assertEquals(1, adapter.getRecentDestinations().size)
        assertNotNull(storage.getString(PersistentRecentDestinationsAdapter.KEY_RECENT_DESTINATIONS))

        adapter.clearRecentDestinations()

        assertTrue(adapter.getRecentDestinations().isEmpty())
        assertTrue(adapter.observeRecentDestinations().first().isEmpty())
        assertNull(storage.getString(PersistentRecentDestinationsAdapter.KEY_RECENT_DESTINATIONS))

        // A new instance should also be empty
        val adapter2 = PersistentRecentDestinationsAdapter(storage)
        assertTrue(adapter2.getRecentDestinations().isEmpty())
    }
}
