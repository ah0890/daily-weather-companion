package com.ah0890.dailyweather.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.LocationData
import com.ah0890.dailyweather.domain.model.SavedLocation
import com.ah0890.dailyweather.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Clock
import java.time.Instant

/**
 * Keeps the saved location in a small Preferences DataStore. Only what the app needs is stored:
 * rounded coordinates, the place name and when it was saved.
 */
class DataStoreLocationRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock = Clock.systemUTC(),
) : LocationRepository {

    override val savedLocation: Flow<SavedLocation?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSavedLocation() }
        .distinctUntilChanged()

    override suspend fun save(location: LocationData) {
        val coordinates = location.coordinates.approximate()
        dataStore.edit { prefs ->
            prefs[Keys.Latitude] = coordinates.latitude
            prefs[Keys.Longitude] = coordinates.longitude
            prefs[Keys.SavedAt] = clock.millis()
            val name = location.name?.trim()?.take(MAX_NAME_LENGTH)
            if (name.isNullOrEmpty()) prefs.remove(Keys.Name) else prefs[Keys.Name] = name
        }
    }

    /** Returns null for missing or corrupted entries rather than failing. */
    private fun Preferences.toSavedLocation(): SavedLocation? {
        val latitude = this[Keys.Latitude] ?: return null
        val longitude = this[Keys.Longitude] ?: return null
        val savedAt = this[Keys.SavedAt] ?: return null
        val coordinates = runCatching { Coordinates(latitude, longitude) }.getOrNull() ?: return null
        return SavedLocation(
            location = LocationData(coordinates, this[Keys.Name]),
            savedAt = Instant.ofEpochMilli(savedAt),
        )
    }

    private object Keys {
        val Latitude = doublePreferencesKey("latitude")
        val Longitude = doublePreferencesKey("longitude")
        val Name = stringPreferencesKey("name")
        val SavedAt = longPreferencesKey("saved_at_epoch_ms")
    }

    private companion object {
        const val MAX_NAME_LENGTH = 100
    }
}
