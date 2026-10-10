package com.ah0890.dailyweather.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.domain.model.SavedLocation
import com.ah0890.dailyweather.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DataStoreLocationRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val now = Instant.parse("2026-10-10T06:45:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun repository() = DataStoreLocationRepository(InMemoryPreferencesDataStore(), clock)

    // --- Real file-backed storage (single write per test; see InMemoryPreferencesDataStore) ---

    @Test
    fun `nothing saved emits null from a real file store`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(tempFolder.root, "empty.preferences_pb")
        }

        assertNull(DataStoreLocationRepository(store, clock).savedLocation.first())
    }

    @Test
    fun `saved location is written to and read back from disk`() = runTest {
        val file = File(tempFolder.root, "location.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }

        DataStoreLocationRepository(store, clock).save(LocationData(Coordinates(51.51, -0.13), "London"))

        assertEquals(
            SavedLocation(LocationData(Coordinates(51.51, -0.13), "London"), savedAt = now),
            DataStoreLocationRepository(store, clock).savedLocation.first(),
        )
        assert(file.length() > 0) { "expected data on disk" }
    }

    // --- Repository logic ---

    @Test
    fun `coordinates are stored at reduced precision`() = runTest {
        val repository = repository()

        repository.save(LocationData(Coordinates(48.856613, 2.352222), "Paris"))

        assertEquals(Coordinates(48.86, 2.35), repository.savedLocation.first()!!.location.coordinates)
    }

    @Test
    fun `saving again replaces the previous location`() = runTest {
        val repository = repository()

        repository.save(LocationData(Coordinates(51.51, -0.13), "London"))
        repository.save(LocationData(Coordinates(48.86, 2.35), null))

        val saved = repository.savedLocation.first()!!
        assertEquals(Coordinates(48.86, 2.35), saved.location.coordinates)
        assertNull("old name must not linger", saved.location.name)
    }

    @Test
    fun `blank names are not stored and long names are trimmed`() = runTest {
        val repository = repository()

        repository.save(LocationData(Coordinates(1.0, 1.0), "   "))
        assertNull(repository.savedLocation.first()!!.location.name)

        repository.save(LocationData(Coordinates(1.0, 1.0), "x".repeat(500)))
        assertEquals(100, repository.savedLocation.first()!!.location.name!!.length)
    }

    @Test
    fun `out of range stored coordinates are treated as no location`() = runTest {
        val store = InMemoryPreferencesDataStore()
        store.edit {
            it[doublePreferencesKey("latitude")] = 123.0
            it[doublePreferencesKey("longitude")] = 0.0
            it[longPreferencesKey("saved_at_epoch_ms")] = now.toEpochMilli()
        }

        assertNull(DataStoreLocationRepository(store, clock).savedLocation.first())
    }

    @Test
    fun `partially stored location is treated as no location`() = runTest {
        val store = InMemoryPreferencesDataStore()
        store.edit { it[doublePreferencesKey("latitude")] = 51.5 }

        assertNull(DataStoreLocationRepository(store, clock).savedLocation.first())
    }
}
