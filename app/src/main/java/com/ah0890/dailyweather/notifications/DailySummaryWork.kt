package com.ah0890.dailyweather.notifications

import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.model.WeatherResult
import com.ah0890.dailyweather.domain.repository.LocationRepository
import com.ah0890.dailyweather.domain.repository.PreferencesRepository
import com.ah0890.dailyweather.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException

/** The scheduled notifications. [workName] identifies each one's WorkManager job. */
enum class NotificationKind(val workName: String) {
    /** Morning weather summary with today's commute. */
    MorningSummary("daily_weather_summary"),

    /** Afternoon alert about the trip home, posted only when there is something to act on. */
    EveningCommute("evening_commute_alert"),
}

/** When a notification should run, as configured by the user. */
data class NotificationTiming(val enabled: Boolean, val time: LocalTime, val days: Set<DayOfWeek>) {
    /** Enabled but with no days selected is the same as switched off. */
    val active: Boolean get() = enabled && days.isNotEmpty()
}

fun UserPreferences.timing(kind: NotificationKind): NotificationTiming = when (kind) {
    NotificationKind.MorningSummary -> NotificationTiming(dailyNotificationEnabled, dailyNotificationTime, dailyNotificationDays)
    NotificationKind.EveningCommute -> NotificationTiming(eveningAlertEnabled, eveningAlertTime, eveningAlertDays)
}

/** Schedules (or cancels) the scheduled notifications. */
interface NotificationScheduler {
    enum class Policy {
        /** Leave an already-scheduled run alone (app start). */
        KeepExisting,

        /** Replace any scheduled run, e.g. after the time changed in Settings. */
        Replace,

        /** Queue after the run that is executing now (used by the job itself). */
        AfterCurrent,
    }

    /** Schedules the next run at [time] on one of [days]. */
    fun schedule(kind: NotificationKind, time: LocalTime, days: Set<DayOfWeek>, policy: Policy)

    fun cancel(kind: NotificationKind)
}

/** Schedules or cancels [kind] to match [prefs]. */
fun NotificationScheduler.sync(kind: NotificationKind, prefs: UserPreferences, policy: NotificationScheduler.Policy) {
    val timing = prefs.timing(kind)
    if (timing.active) schedule(kind, timing.time, timing.days, policy) else cancel(kind)
}

/** Schedules or cancels every notification to match [prefs]. */
fun NotificationScheduler.syncAll(prefs: UserPreferences, policy: NotificationScheduler.Policy) {
    NotificationKind.entries.forEach { sync(it, prefs, policy) }
}

/** Shows notifications to the user. */
interface DailySummaryNotifier {
    /** False if notifications are blocked for the app. */
    fun canNotify(): Boolean

    fun show(summary: DailySummary, unit: TemperatureUnit)

    fun showEveningAlert(alert: EveningAlert, unit: TemperatureUnit)
}

/**
 * One run of a scheduled notification job, independent of WorkManager so it can be unit-tested.
 * Uses the saved location only: the app never looks up location in the background.
 */
class DailySummaryWork(
    private val preferences: PreferencesRepository,
    private val locations: LocationRepository,
    private val weather: WeatherRepository,
    private val notifier: DailySummaryNotifier,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val kind: NotificationKind = NotificationKind.MorningSummary,
) {
    sealed interface Outcome {
        data class Notified(val summary: DailySummary) : Outcome
        data class Alerted(val alert: EveningAlert) : Outcome
        data class Skipped(val reason: SkipReason) : Outcome

        /** Temporary problem (e.g. no network yet); worth trying again shortly. */
        data object Retry : Outcome
    }

    enum class SkipReason {
        Disabled,
        Blocked,
        NoLocation,
        TooLate,
        NoData,
        Error,

        /** Evening alert: today has no trip home left (not a workday, or it's already over). */
        NoCommute,

        /** Evening alert: the trip home looks fine, so there is nothing worth interrupting for. */
        NothingToReport,
    }

    /**
     * @param attempt 0 for the first try; after [MAX_ATTEMPTS] tries the day is skipped.
     * Never throws (except for cancellation): an unexpected problem skips today's notification but
     * must not stop the next one from being scheduled.
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
        val timing = prefs.timing(kind)
        if (!timing.active) return Outcome.Skipped(SkipReason.Disabled)
        if (!notifier.canNotify()) return Outcome.Skipped(SkipReason.Blocked)

        val now = ZonedDateTime.now(clock)
        if (NotificationSchedule.isTooLate(now, timing.time, timing.days)) return Outcome.Skipped(SkipReason.TooLate)

        val location = locations.savedLocation.first() ?: return Outcome.Skipped(SkipReason.NoLocation)
        val forecast = loadForecast(location.location.coordinates)
            ?: return if (attempt + 1 < MAX_ATTEMPTS) Outcome.Retry else Outcome.Skipped(SkipReason.NoData)

        val localNow = LocalDateTime.ofInstant(clock.instant(), forecast.timeZone)
        val name = location.location.name
        return when (kind) {
            NotificationKind.MorningSummary -> {
                val summary = DailySummary.from(forecast, name, localNow, prefs.commuteTimes, prefs.transportMode, prefs.workdays)
                notifier.show(summary, prefs.temperatureUnit)
                Outcome.Notified(summary)
            }
            NotificationKind.EveningCommute -> {
                val alert = EveningAlert.from(forecast, name, localNow, prefs.commuteTimes, prefs.transportMode, prefs.workdays)
                when {
                    alert == null -> Outcome.Skipped(SkipReason.NoCommute)
                    !alert.worthNotifying -> Outcome.Skipped(SkipReason.NothingToReport)
                    else -> {
                        notifier.showEveningAlert(alert, prefs.temperatureUnit)
                        Outcome.Alerted(alert)
                    }
                }
            }
        }
    }

    private suspend fun loadForecast(coordinates: Coordinates): WeatherForecast? =
        when (val result = weather.getForecast(coordinates)) {
            is WeatherResult.Success -> result.forecast
            // An offline copy from the last few hours is still useful.
            is WeatherResult.Stale -> result.forecast.takeIf {
                Duration.between(result.fetchedAt, clock.instant()) <= MAX_STALE_AGE
            }
            is WeatherResult.Failure -> null
        }

    companion object {
        const val MAX_ATTEMPTS = 3
        val MAX_STALE_AGE: Duration = Duration.ofHours(6)
    }
}
