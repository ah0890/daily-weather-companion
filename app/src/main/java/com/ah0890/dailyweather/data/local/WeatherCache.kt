package com.ah0890.dailyweather.data.local

import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.CurrentWeather
import com.ah0890.dailyweather.domain.model.DailyForecast
import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A forecast saved on the device together with when it was downloaded. */
data class CachedForecast(val forecast: WeatherForecast, val fetchedAt: Instant)

/** Keeps recent forecasts for offline use. */
interface WeatherCache {
    /** @return the cached forecast for [coordinates] (compared at ~1 km precision), if any. */
    suspend fun load(coordinates: Coordinates): CachedForecast?

    suspend fun save(forecast: WeatherForecast, fetchedAt: Instant)
}

/**
 * Stores the latest forecast for each of the last few places as small JSON files in [directory],
 * one per ~1 km cell (current location, home, office…). A database would add weight without
 * benefit. Writes go to a temporary file that is then moved into place, so a crash mid-write never
 * leaves a half-written cache. Unreadable or outdated-format files are treated as missing.
 *
 * @param maxEntries older files beyond this many are deleted after each save.
 * @param legacyFile the single-file cache of earlier versions, deleted on the first save.
 */
class FileWeatherCache(
    private val directory: File,
    private val maxEntries: Int = 6,
    private val legacyFile: File? = null,
) : WeatherCache {

    private val json = Json { ignoreUnknownKeys = true }

    /** The file holding the forecast for [coordinates]'s ~1 km cell. */
    internal fun fileFor(coordinates: Coordinates): File {
        val cell = coordinates.approximate()
        return File(directory, "$PREFIX${cell.latitude}_${cell.longitude}.json")
    }

    override suspend fun load(coordinates: Coordinates): CachedForecast? = withContext(Dispatchers.IO) {
        val file = fileFor(coordinates)
        if (!file.exists()) return@withContext null
        val entry = try {
            json.decodeFromString(CacheEntry.serializer(), file.readText())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IOException) {
            null
        }
        if (entry == null || entry.version != CacheEntry.VERSION) return@withContext null
        val cached = entry.toDomain() ?: return@withContext null
        cached.takeIf { it.forecast.coordinates.approximate() == coordinates.approximate() }
    }

    override suspend fun save(forecast: WeatherForecast, fetchedAt: Instant) = withContext(Dispatchers.IO) {
        val text = json.encodeToString(CacheEntry.serializer(), CacheEntry.from(forecast, fetchedAt))
        directory.mkdirs()
        val file = fileFor(forecast.coordinates)
        val temp = File(directory, file.name + ".tmp")
        temp.writeText(text)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        prune()
        legacyFile?.delete()
        Unit
    }

    /** Keeps only the [maxEntries] most recently saved forecasts. */
    private fun prune() {
        directory.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(maxEntries)
            ?.forEach { it.delete() }
    }

    private companion object {
        const val PREFIX = "forecast_"
    }
}

// --- On-disk format. Kept separate from domain models so they can evolve independently. ---

@Serializable
private data class CacheEntry(
    val version: Int,
    val fetchedAtEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
    val current: CurrentEntry,
    val hourly: List<HourlyEntry>,
    val daily: List<DailyEntry>,
) {
    fun toDomain(): CachedForecast? = try {
        CachedForecast(
            forecast = WeatherForecast(
                coordinates = Coordinates(latitude, longitude),
                timeZone = ZoneId.of(timeZone),
                current = current.toDomain(),
                hourly = hourly.map { it.toDomain() },
                daily = daily.map { it.toDomain() },
            ),
            fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMs),
        )
    } catch (_: DateTimeException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        /**
         * Bump when the format changes incompatibly; older files are then ignored. New optional
         * fields with defaults don't need a bump: older files simply read them as unknown.
         */
        const val VERSION = 1

        fun from(forecast: WeatherForecast, fetchedAt: Instant) = CacheEntry(
            version = VERSION,
            fetchedAtEpochMs = fetchedAt.toEpochMilli(),
            latitude = forecast.coordinates.latitude,
            longitude = forecast.coordinates.longitude,
            timeZone = forecast.timeZone.id,
            current = CurrentEntry.from(forecast.current),
            hourly = forecast.hourly.map { HourlyEntry.from(it) },
            daily = forecast.daily.map { DailyEntry.from(it) },
        )
    }
}

@Serializable
private data class CurrentEntry(
    val time: String,
    val condition: String,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val humidityPercent: Int? = null,
    val windSpeedKmh: Double? = null,
    val windDirectionDegrees: Int? = null,
    val precipitationMm: Double? = null,
    val isDay: Boolean,
    val uvIndex: Double? = null,
    val visibilityMeters: Double? = null,
) {
    fun toDomain() = CurrentWeather(
        time = LocalDateTime.parse(time),
        condition = conditionOf(condition),
        temperatureC = temperatureC,
        feelsLikeC = feelsLikeC,
        humidityPercent = humidityPercent,
        windSpeedKmh = windSpeedKmh,
        windDirectionDegrees = windDirectionDegrees,
        precipitationMm = precipitationMm,
        isDay = isDay,
        uvIndex = uvIndex,
        visibilityMeters = visibilityMeters,
    )

    companion object {
        fun from(c: CurrentWeather) = CurrentEntry(
            c.time.toString(), c.condition.name, c.temperatureC, c.feelsLikeC, c.humidityPercent, c.windSpeedKmh,
            c.windDirectionDegrees, c.precipitationMm, c.isDay, c.uvIndex, c.visibilityMeters,
        )
    }
}

@Serializable
private data class HourlyEntry(
    val time: String,
    val condition: String,
    val temperatureC: Double,
    val precipitationProbability: Int? = null,
    val precipitationMm: Double? = null,
    val windSpeedKmh: Double? = null,
    val isDay: Boolean,
    val feelsLikeC: Double? = null,
    val windGustsKmh: Double? = null,
    val visibilityMeters: Double? = null,
    val uvIndex: Double? = null,
) {
    fun toDomain() = HourlyForecast(
        time = LocalDateTime.parse(time),
        condition = conditionOf(condition),
        temperatureC = temperatureC,
        precipitationProbability = precipitationProbability,
        precipitationMm = precipitationMm,
        windSpeedKmh = windSpeedKmh,
        isDay = isDay,
        feelsLikeC = feelsLikeC,
        windGustsKmh = windGustsKmh,
        visibilityMeters = visibilityMeters,
        uvIndex = uvIndex,
    )

    companion object {
        fun from(h: HourlyForecast) = HourlyEntry(
            h.time.toString(), h.condition.name, h.temperatureC, h.precipitationProbability, h.precipitationMm,
            h.windSpeedKmh, h.isDay, h.feelsLikeC, h.windGustsKmh, h.visibilityMeters, h.uvIndex,
        )
    }
}

@Serializable
private data class DailyEntry(
    val date: String,
    val condition: String,
    val maxTemperatureC: Double,
    val minTemperatureC: Double,
    val precipitationProbabilityMax: Int? = null,
    val precipitationSumMm: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val uvIndexMax: Double? = null,
    val windSpeedMaxKmh: Double? = null,
) {
    fun toDomain() = DailyForecast(
        date = LocalDate.parse(date),
        condition = conditionOf(condition),
        maxTemperatureC = maxTemperatureC,
        minTemperatureC = minTemperatureC,
        precipitationProbabilityMax = precipitationProbabilityMax,
        precipitationSumMm = precipitationSumMm,
        sunrise = sunrise?.let { LocalDateTime.parse(it) },
        sunset = sunset?.let { LocalDateTime.parse(it) },
        uvIndexMax = uvIndexMax,
        windSpeedMaxKmh = windSpeedMaxKmh,
    )

    companion object {
        fun from(d: DailyForecast) = DailyEntry(
            d.date.toString(), d.condition.name, d.maxTemperatureC, d.minTemperatureC, d.precipitationProbabilityMax,
            d.precipitationSumMm, d.sunrise?.toString(), d.sunset?.toString(), d.uvIndexMax, d.windSpeedMaxKmh,
        )
    }
}

/** Unknown names (e.g. from a newer app version) degrade to [WeatherCondition.Unknown]. */
private fun conditionOf(name: String): WeatherCondition =
    WeatherCondition.entries.firstOrNull { it.name == name } ?: WeatherCondition.Unknown
