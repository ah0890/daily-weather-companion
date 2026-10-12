package com.ah0890.dailyweather.data.remote.openmeteo

import com.ah0890.dailyweather.data.remote.toWeatherError
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.PlaceSuggestion
import com.ah0890.dailyweather.domain.model.WeatherError
import com.ah0890.dailyweather.domain.repository.PlaceSearchRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Open-Meteo's place-name search (https://open-meteo.com/en/docs/geocoding-api), free and keyless. */
interface OpenMeteoGeocodingApi {

    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String,
        @Query("format") format: String = "json",
    ): GeocodingResponseDto

    companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/"
    }
}

@Serializable
data class GeocodingResponseDto(val results: List<GeocodingResultDto>? = null)

@Serializable
data class GeocodingResultDto(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val country: String? = null,
    @SerialName("admin1") val region: String? = null,
)

/** Results without a name or with invalid coordinates are skipped. */
internal fun GeocodingResponseDto.toSuggestions(): List<PlaceSuggestion> =
    results.orEmpty().mapNotNull { result ->
        val name = result.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val coordinates = runCatching { Coordinates(result.latitude!!, result.longitude!!) }.getOrNull() ?: return@mapNotNull null
        val region = listOfNotNull(result.region, result.country).filter { it.isNotBlank() && it != name }.distinct()
        PlaceSuggestion(name, region.joinToString(", ").ifEmpty { null }, coordinates)
    }

class OpenMeteoPlaceSearch(private val api: OpenMeteoGeocodingApi) : PlaceSearchRepository {

    override suspend fun search(query: String): PlaceSearchRepository.Result {
        val trimmed = query.trim()
        // The API needs at least two characters; shorter queries simply have no results.
        if (trimmed.length < 2) return PlaceSearchRepository.Result.Success(emptyList())
        return try {
            val response = api.search(name = trimmed.take(MAX_QUERY_LENGTH), language = Locale.getDefault().language)
            PlaceSearchRepository.Result.Success(response.toSuggestions())
        } catch (e: CancellationException) {
            throw e
        } catch (_: SerializationException) {
            PlaceSearchRepository.Result.Failure(WeatherError.InvalidData)
        } catch (e: Exception) {
            PlaceSearchRepository.Result.Failure(e.toWeatherError())
        }
    }

    companion object {
        private const val MAX_QUERY_LENGTH = 100

        fun create(baseUrl: String = OpenMeteoGeocodingApi.BASE_URL): OpenMeteoPlaceSearch =
            OpenMeteoPlaceSearch(openMeteoRetrofit(baseUrl).create(OpenMeteoGeocodingApi::class.java))
    }
}
