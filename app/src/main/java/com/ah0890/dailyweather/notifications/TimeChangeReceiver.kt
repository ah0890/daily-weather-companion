package com.ah0890.dailyweather.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ah0890.dailyweather.DailyWeatherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-schedules notifications after the time zone or clock changes. Scheduled jobs wait for a
 * fixed delay, so without this a summary planned for 7:00 would arrive at the wrong local time
 * after travelling to another time zone until the next run corrected it.
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED && intent.action != Intent.ACTION_TIME_CHANGED) return
        val container = (context.applicationContext as DailyWeatherApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val prefs = container.preferencesRepository.preferences.first()
                container.notificationScheduler.syncAll(prefs, NotificationScheduler.Policy.Replace)
            } catch (_: Exception) {
                // The next app start or notification run re-schedules anyway.
            } finally {
                pending.finish()
            }
        }
    }
}
