package com.ah0890.dailyweather.data.remote.openmeteo

import com.ah0890.dailyweather.data.remote.MalformedWeatherDataException
import com.ah0890.dailyweather.data.remote.WeatherRemoteDataSource
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.WeatherForecast
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class OpenMeteoDataSource(private val api: OpenMeteoApi) : WeatherRemoteDataSource {

    override suspend fun fetchForecast(coordinates: Coordinates): WeatherForecast {
        val approximate = coordinates.approximate()
        val response = try {
            api.forecast(latitude = approximate.latitude, longitude = approximate.longitude)
        } catch (e: SerializationException) {
            throw MalformedWeatherDataException(e.message ?: "unreadable response")
        }
        return response.toDomain(approximate)
    }

    companion object {
        /** Builds a data source with sensible timeouts; see [openMeteoRetrofit]. */
        fun create(baseUrl: String = OpenMeteoApi.BASE_URL): OpenMeteoDataSource =
            OpenMeteoDataSource(openMeteoRetrofit(baseUrl).create(OpenMeteoApi::class.java))
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Retrofit for an Open-Meteo API with sensible timeouts. No logging interceptor is installed so
 * that request URLs, which contain coordinates or search terms, never end up in logcat.
 */
internal fun openMeteoRetrofit(baseUrl: String): Retrofit {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
