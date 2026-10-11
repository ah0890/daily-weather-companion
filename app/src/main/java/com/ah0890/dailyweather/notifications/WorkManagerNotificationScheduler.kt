package com.ah0890.dailyweather.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the morning summary as a one-time WorkManager job that re-schedules itself after each
 * run. Re-computing the delay every day keeps it at the same wall-clock time across time-zone and
 * daylight-saving changes, unlike a fixed 24-hour periodic job. No exact-alarm permission is used:
 * the summary arrives around the chosen time, and Android may delay it slightly to save battery.
 * WorkManager keeps the job across app restarts and device reboots.
 */
class WorkManagerNotificationScheduler(private val context: Context) : NotificationScheduler {

    override fun schedule(time: LocalTime, policy: NotificationScheduler.Policy) {
        val delay = NotificationSchedule.delayUntilNextRun(ZonedDateTime.now(), time)
        val request = OneTimeWorkRequestBuilder<DailyWeatherWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        val existing = when (policy) {
            NotificationScheduler.Policy.KeepExisting -> ExistingWorkPolicy.KEEP
            NotificationScheduler.Policy.Replace -> ExistingWorkPolicy.REPLACE
            // REPLACE would cancel the job that is calling this; append after it instead.
            NotificationScheduler.Policy.AfterCurrent -> ExistingWorkPolicy.APPEND_OR_REPLACE
        }
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, existing, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "daily_weather_summary"
    }
}
