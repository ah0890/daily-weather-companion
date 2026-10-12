package com.ah0890.dailyweather.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.Place
import com.ah0890.dailyweather.domain.model.PlaceType
import com.ah0890.dailyweather.domain.model.Places
import com.ah0890.dailyweather.domain.repository.PlacesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

/**
 * Keeps saved places as a small JSON list in a Preferences DataStore, plus the ids of the places
 * with a role. Unreadable entries are dropped rather than failing, and roles pointing at a place
 * that no longer exists are ignored.
 */
class DataStorePlacesRepository(
    private val dataStore: DataStore<Preferences>,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : PlacesRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override val places: Flow<Places> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toPlaces() }
        .distinctUntilChanged()

    override suspend fun add(name: String, type: PlaceType, coordinates: Coordinates, placeName: String?): Place? {
        var added: Place? = null
        dataStore.edit { prefs ->
            val current = prefs.toPlaces()
            if (current.places.size >= Places.MAX_PLACES) return@edit
            val place = Place(
                id = newId(),
                name = cleanName(name, type, placeName),
                type = type,
                coordinates = coordinates.approximate(),
                placeName = placeName?.trim()?.take(MAX_PLACE_NAME_LENGTH)?.ifEmpty { null },
            )
            prefs.writePlaces(current.places + place)
            if (type == PlaceType.Home && current.home == null) prefs[Keys.Home] = place.id
            if (type == PlaceType.Office && current.office == null) prefs[Keys.Office] = place.id
            added = place
        }
        return added
    }

    override suspend fun update(id: String, name: String, type: PlaceType) {
        dataStore.edit { prefs ->
            val updated = prefs.toPlaces().places.map {
                if (it.id == id) it.copy(name = cleanName(name, type, it.placeName), type = type) else it
            }
            prefs.writePlaces(updated)
        }
    }

    override suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            prefs.writePlaces(prefs.toPlaces().places.filterNot { it.id == id })
            listOf(Keys.Home, Keys.Office, Keys.Default).forEach { key -> if (prefs[key] == id) prefs.remove(key) }
        }
    }

    override suspend fun setHome(id: String?) = setRole(Keys.Home, id)

    override suspend fun setOffice(id: String?) = setRole(Keys.Office, id)

    override suspend fun setDefault(id: String?) = setRole(Keys.Default, id)

    private suspend fun setRole(key: Preferences.Key<String>, id: String?) {
        dataStore.edit { prefs ->
            if (id == null || prefs.toPlaces().find(id) == null) prefs.remove(key) else prefs[key] = id
        }
    }

    private fun cleanName(name: String, type: PlaceType, placeName: String?): String =
        name.trim().take(Places.MAX_NAME_LENGTH).ifEmpty { placeName?.take(Places.MAX_NAME_LENGTH) ?: type.name }

    private fun Preferences.toPlaces(): Places {
        val places = this[Keys.Places]?.let { text ->
            try {
                json.decodeFromString(ListSerializer(PlaceEntry.serializer()), text).mapNotNull { it.toDomain() }
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }.orEmpty()
        val result = Places(places)
        return result.copy(
            homeId = this[Keys.Home]?.takeIf { result.find(it) != null },
            officeId = this[Keys.Office]?.takeIf { result.find(it) != null },
            defaultId = this[Keys.Default]?.takeIf { result.find(it) != null },
        )
    }

    private fun MutablePreferences.writePlaces(places: List<Place>) {
        this[Keys.Places] = json.encodeToString(ListSerializer(PlaceEntry.serializer()), places.map { PlaceEntry.from(it) })
    }

    @Serializable
    private data class PlaceEntry(
        val id: String,
        val name: String,
        val type: String,
        val latitude: Double,
        val longitude: Double,
        val placeName: String? = null,
    ) {
        fun toDomain(): Place? {
            val coordinates = runCatching { Coordinates(latitude, longitude) }.getOrNull() ?: return null
            if (id.isBlank()) return null
            return Place(
                id = id,
                name = name,
                type = PlaceType.entries.firstOrNull { it.name == type } ?: PlaceType.Other,
                coordinates = coordinates,
                placeName = placeName,
            )
        }

        companion object {
            fun from(place: Place) = PlaceEntry(
                place.id, place.name, place.type.name, place.coordinates.latitude, place.coordinates.longitude, place.placeName,
            )
        }
    }

    private object Keys {
        val Places = stringPreferencesKey("places")
        val Home = stringPreferencesKey("home_id")
        val Office = stringPreferencesKey("office_id")
        val Default = stringPreferencesKey("default_id")
    }

    private companion object {
        const val MAX_PLACE_NAME_LENGTH = 100
    }
}
