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
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        /** Builds a data source with sensible timeouts. No logging interceptor is installed so
         *  that request URLs, which contain coordinates, never end up in logcat. */
        fun create(baseUrl: String = OpenMeteoApi.BASE_URL): OpenMeteoDataSource {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()

            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OpenMeteoApi::class.java)

            return OpenMeteoDataSource(api)
        }
    }
}
