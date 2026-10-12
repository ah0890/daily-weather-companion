package com.ah0890.dailyweather.domain.model

/** What a saved place is, mainly for its icon. Commute roles are assigned separately. */
enum class PlaceType { Home, Office, Parents, Other }

/**
 * A location the user saved by hand, such as "Home" or "Office". Only what weather lookup needs
 * is kept: a label, rounded coordinates and the town name for context.
 *
 * @property name the user's own label, e.g. "Office".
 * @property placeName the town or city at [coordinates], when known, e.g. "Islamabad".
 */
data class Place(
    val id: String,
    val name: String,
    val type: PlaceType,
    val coordinates: Coordinates,
    val placeName: String?,
)

/**
 * All saved places and which of them play a role: [homeId]/[officeId] for the commute (morning
 * trip from home, evening trip from the office) and [defaultId] for the forecast shown first.
 */
data class Places(
    val places: List<Place> = emptyList(),
    val homeId: String? = null,
    val officeId: String? = null,
    val defaultId: String? = null,
) {
    val home: Place? get() = find(homeId)
    val office: Place? get() = find(officeId)
    val default: Place? get() = find(defaultId)

    fun find(id: String?): Place? = id?.let { wanted -> places.firstOrNull { it.id == wanted } }

    companion object {
        const val MAX_PLACES = 10
        const val MAX_NAME_LENGTH = 40
    }
}

/** A search result when adding a place by name. */
data class PlaceSuggestion(
    val name: String,
    /** Region and country for telling places with the same name apart, e.g. "Punjab, Pakistan". */
    val region: String?,
    val coordinates: Coordinates,
)
