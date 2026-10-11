package com.ah0890.dailyweather.notifications

import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.domain.repository.LocationRepository
import com.ah0890.dailyweather.domain.repository.PreferencesRepository
import com.ah0890.dailyweather.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException

/** Schedules (or cancels) the morning summary. */
interface NotificationScheduler {
    enum class Policy {
        /** Leave an already-scheduled run alone (app start). */
        KeepExisting,

        /** Replace any scheduled run, e.g. after the time changed in Settings. */
        Replace,

        /** Queue after the run that is executing now (used by the job itself). */
        AfterCurrent,
    }

    fun schedule(time: LocalTime, policy: Policy)

    fun cancel()
}

/** Shows the morning summary to the user. */
interface DailySummaryNotifier {
    /** False if notifications are blocked for the app. */
    fun canNotify(): Boolean

    fun show(summary: DailySummary, unit: TemperatureUnit)
}

/**
 * One run of the morning summary job, independent of WorkManager so it can be unit-tested.
 * Uses the saved location only: the app never looks up location in the background.
 */
class DailySummaryWork(
    private val preferences: PreferencesRepository,
    private val locations: LocationRepository,
    private val weather: WeatherRepository,
    private val notifier: DailySummaryNotifier,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    sealed interface Outcome {
        data class Notified(val summary: DailySummary) : Outcome
        data class Skipped(val reason: SkipReason) : Outcome

        /** Temporary problem (e.g. no network yet); worth trying again shortly. */
        data object Retry : Outcome
    }

    enum class SkipReason { Disabled, Blocked, NoLocation, TooLate, NoData, Error }

    /**
     * @param attempt 0 for the first try; after [MAX_ATTEMPTS] tries the day is skipped.
     * Never throws (except for cancellation): an unexpected problem skips today's summary but
     * must not stop tomorrow's from being scheduled.
     */
    suspend fun run(attempt: Int): Outcome =
        try {
            runOnce(attempt)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Outcome.Skipped(SkipReason.Error)
        }

    private suspend fun runOnce(attempt: Int): Outcome {
        val prefs = preferences.preferences.first()
        if (!prefs.dailyNotificationEnabled) return Outcome.Skipped(SkipReason.Disabled)
        if (!notifier.canNotify()) return Outcome.Skipped(SkipReason.Blocked)

        val now = ZonedDateTime.now(clock)
        if (NotificationSchedule.isTooLate(now, prefs.dailyNotificationTime)) return Outcome.Skipped(SkipReason.TooLate)

        val location = locations.savedLocation.first() ?: return Outcome.Skipped(SkipReason.NoLocation)
        val forecast = when (val result = weather.getForecast(location.location.coordinates)) {
            is WeatherResult.Success -> result.forecast
            // An offline copy from the last few hours is still a useful morning summary.
            is WeatherResult.Stale -> result.forecast.takeIf {
                Duration.between(result.fetchedAt, clock.instant()) <= MAX_STALE_AGE
            }
            is WeatherResult.Failure -> null
        } ?: return if (attempt + 1 < MAX_ATTEMPTS) Outcome.Retry else Outcome.Skipped(SkipReason.NoData)

        val localNow = LocalDateTime.ofInstant(clock.instant(), forecast.timeZone)
        val summary = DailySummary.from(forecast, location.location.name, localNow)
        notifier.show(summary, prefs.temperatureUnit)
        return Outcome.Notified(summary)
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        val MAX_STALE_AGE: Duration = Duration.ofHours(6)
    }
}
