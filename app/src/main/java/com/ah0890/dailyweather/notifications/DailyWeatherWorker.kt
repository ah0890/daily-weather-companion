package com.ah0890.dailyweather.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ah0890.dailyweather.DailyWeatherApplication
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

/**
 * WorkManager entry point for scheduled notifications; the logic lives in [DailySummaryWork].
 * Jobs queued before the evening alert existed carry no kind and are morning summaries.
 */
class DailyWeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as DailyWeatherApplication).container
        val kind = NotificationKind.entries.firstOrNull { it.name == inputData.getString(KEY_KIND) }
            ?: NotificationKind.MorningSummary
        val work = DailySummaryWork(
            preferences = container.preferencesRepository,
            locations = container.locationRepository,
            weather = container.weatherRepository,
            notifier = container.dailySummaryNotifier,
            kind = kind,
            places = container.placesRepository,
        )

        val outcome = work.run(attempt = runAttemptCount)
        if (outcome == DailySummaryWork.Outcome.Retry) return Result.retry()

        // Queue the next run. Never return failure: that would also fail the queued run.
        try {
            val prefs = container.preferencesRepository.preferences.first()
            if (prefs.timing(kind).active) container.notificationScheduler.sync(kind, prefs, NotificationScheduler.Policy.AfterCurrent)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Opening the app re-schedules notifications, so this is recoverable.
        }
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
    }
}
