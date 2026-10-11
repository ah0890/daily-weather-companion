package com.ah0890.dailyweather.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ah0890.dailyweather.DailyWeatherApplication
import kotlinx.coroutines.flow.first

/** WorkManager entry point for the morning summary; the logic lives in [DailySummaryWork]. */
class DailyWeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as DailyWeatherApplication).container
        val work = DailySummaryWork(
            preferences = container.preferencesRepository,
            locations = container.locationRepository,
            weather = container.weatherRepository,
            notifier = container.dailySummaryNotifier,
        )

        val outcome = work.run(attempt = runAttemptCount)
        if (outcome == DailySummaryWork.Outcome.Retry) return Result.retry()

        // Queue tomorrow's run. Never return failure: that would also fail the queued run.
        val prefs = container.preferencesRepository.preferences.first()
        if (prefs.dailyNotificationEnabled) {
            container.notificationScheduler.schedule(prefs.dailyNotificationTime, NotificationScheduler.Policy.AfterCurrent)
        }
        return Result.success()
    }
}
