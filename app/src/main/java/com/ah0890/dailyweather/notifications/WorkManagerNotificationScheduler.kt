package com.ah0890.dailyweather.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules each notification as a one-time WorkManager job that re-schedules itself after each
 * run. Re-computing the delay every time keeps it at the same wall-clock time across
 * daylight-saving changes, unlike a fixed 24-hour periodic job; time-zone and clock changes are
 * handled by [TimeChangeReceiver]. No exact-alarm permission is used: notifications arrive
 * around the chosen time, and Android may delay them slightly to save battery. WorkManager keeps
 * the jobs across app restarts and device reboots.
 */
class WorkManagerNotificationScheduler(private val context: Context) : NotificationScheduler {

    override fun schedule(kind: NotificationKind, time: LocalTime, days: Set<DayOfWeek>, policy: NotificationScheduler.Policy) {
        val delay = NotificationSchedule.delayUntilNextRun(ZonedDateTime.now(), time, days) ?: return cancel(kind)
        val request = OneTimeWorkRequestBuilder<DailyWeatherWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(DailyWeatherWorker.KEY_KIND to kind.name))
            .addTag(kind.workName)
            .build()
        val existing = when (policy) {
            NotificationScheduler.Policy.KeepExisting -> ExistingWorkPolicy.KEEP
            NotificationScheduler.Policy.Replace -> ExistingWorkPolicy.REPLACE
            // REPLACE would cancel the job that is calling this; append after it instead.
            NotificationScheduler.Policy.AfterCurrent -> ExistingWorkPolicy.APPEND_OR_REPLACE
        }
        WorkManager.getInstance(context).enqueueUniqueWork(kind.workName, existing, request)
    }

    override fun cancel(kind: NotificationKind) {
        WorkManager.getInstance(context).cancelUniqueWork(kind.workName)
    }
}
