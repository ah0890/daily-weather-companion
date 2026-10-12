package com.ah0890.dailyweather.data.remote.openmeteo

import com.ah0890.dailyweather.domain.model.AirQuality
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.Pollutant
import com.ah0890.dailyweather.domain.model.PollutantReading
import com.ah0890.dailyweather.domain.repository.AirQualityRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException

/** Open-Meteo's air-quality API (CAMS data, https://open-meteo.com/en/docs/air-quality-api). */
interface OpenMeteoAirQualityApi {

    @GET("v1/air-quality")
    suspend fun airQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("hourly") hourly: String = HOURLY_FIELDS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 2,
    ): AirQualityResponseDto

    companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/"
        const val CURRENT_FIELDS = "us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide," +
            "us_aqi_pm2_5,us_aqi_pm10,us_aqi_ozone,us_aqi_nitrogen_dioxide,us_aqi_sulphur_dioxide,us_aqi_carbon_monoxide"
        const val HOURLY_FIELDS = "us_aqi"
    }
}

@Serializable
data class AirQualityResponseDto(
    val current: AirQualityCurrentDto? = null,
    val hourly: AirQualityHourlyDto? = null,
)

@Serializable
data class AirQualityCurrentDto(
    val time: String? = null,
    @SerialName("us_aqi") val usAqi: Int? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    val pm10: Double? = null,
    val ozone: Double? = null,
    @SerialName("nitrogen_dioxide") val nitrogenDioxide: Double? = null,
    @SerialName("sulphur_dioxide") val sulphurDioxide: Double? = null,
    @SerialName("carbon_monoxide") val carbonMonoxide: Double? = null,
    @SerialName("us_aqi_pm2_5") val aqiPm25: Int? = null,
    @SerialName("us_aqi_pm10") val aqiPm10: Int? = null,
    @SerialName("us_aqi_ozone") val aqiOzone: Int? = null,
    @SerialName("us_aqi_nitrogen_dioxide") val aqiNitrogenDioxide: Int? = null,
    @SerialName("us_aqi_sulphur_dioxide") val aqiSulphurDioxide: Int? = null,
    @SerialName("us_aqi_carbon_monoxide") val aqiCarbonMonoxide: Int? = null,
)

@Serializable
data class AirQualityHourlyDto(
    val time: List<String>? = null,
    @SerialName("us_aqi") val usAqi: List<Int?>? = null,
)

/**
 * Null when the response has no usable current AQI, e.g. a place the model doesn't cover.
 * Negative values are treated as missing.
 */
internal fun AirQualityResponseDto.toDomain(): AirQuality? {
    val current = current ?: return null
    val time = current.time?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return null
    val aqi = current.usAqi?.takeIf { it >= 0 } ?: return null

    fun reading(pollutant: Pollutant, concentration: Double?, index: Int?) =
        PollutantReading(pollutant, concentration?.takeIf { it >= 0 }, index?.takeIf { it >= 0 })

    val readings = listOf(
        reading(Pollutant.Pm25, current.pm25, current.aqiPm25),
        reading(Pollutant.Pm10, current.pm10, current.aqiPm10),
        reading(Pollutant.Ozone, current.ozone, current.aqiOzone),
        reading(Pollutant.NitrogenDioxide, current.nitrogenDioxide, current.aqiNitrogenDioxide),
        reading(Pollutant.SulphurDioxide, current.sulphurDioxide, current.aqiSulphurDioxide),
        reading(Pollutant.CarbonMonoxide, current.carbonMonoxide, current.aqiCarbonMonoxide),
    ).filter { it.concentration != null || it.index != null }

    val thisHour = time.truncatedTo(ChronoUnit.HOURS)
    val upcoming = hourly?.time.orEmpty().mapIndexedNotNull { i, raw ->
        val hour = runCatching { LocalDateTime.parse(raw) }.getOrNull() ?: return@mapIndexedNotNull null
        val value = hourly?.usAqi?.getOrNull(i)?.takeIf { it >= 0 } ?: return@mapIndexedNotNull null
        (hour to value).takeIf { !hour.isBefore(thisHour) && hour.isBefore(thisHour.plusHours(24)) }
    }
    val peak = upcoming.maxByOrNull { it.second }

    return AirQuality(time, aqi, readings, peakUsAqi = peak?.second, peakTime = peak?.first)
}

/**
 * Downloads air quality with the same rounded coordinates as the forecast. Keeps the last result
 * per place in memory: a copy younger than [FRESH_FOR] is reused, and when a download fails a copy
 * up to [USABLE_FOR] old is still shown. Older data is worse than none, so it isn't persisted.
 */
class OpenMeteoAirQualityRepository(
    private val api: OpenMeteoAirQualityApi,
    private val clock: Clock = Clock.systemUTC(),
) : AirQualityRepository {

    private data class Entry(val airQuality: AirQuality, val fetchedAt: Instant)

    private val cache = mutableMapOf<Coordinates, Entry>()
    private val mutex = Mutex()

    override suspend fun airQuality(coordinates: Coordinates, refresh: Boolean): AirQuality? {
        val cell = coordinates.approximate()
        val cached = mutex.withLock { cache[cell] }
        if (!refresh && cached != null && age(cached) < FRESH_FOR) return cached.airQuality

        val downloaded = try {
            api.airQuality(cell.latitude, cell.longitude).toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (downloaded != null) {
            mutex.withLock { cache[cell] = Entry(downloaded, clock.instant()) }
            return downloaded
        }
        return cached?.takeIf { age(it) < USABLE_FOR }?.airQuality
    }

    private fun age(entry: Entry) = Duration.between(entry.fetchedAt, clock.instant())

    companion object {
        val FRESH_FOR: Duration = Duration.ofMinutes(30)
        val USABLE_FOR: Duration = Duration.ofHours(3)

        fun create(baseUrl: String = OpenMeteoAirQualityApi.BASE_URL, clock: Clock = Clock.systemUTC()) =
            OpenMeteoAirQualityRepository(openMeteoRetrofit(baseUrl).create(OpenMeteoAirQualityApi::class.java), clock)
    }
}
