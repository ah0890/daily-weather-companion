package com.ah0890.dailyweather

import android.app.Application
import com.ah0890.dailyweather.notifications.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DailyWeatherApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    /** For short app-wide work that must not be tied to a screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ensureDailySummaryScheduled()
    }

    /**
     * Makes sure the morning summary is scheduled (or cancelled) to match the user's setting.
     * KEEP leaves an existing schedule untouched, so opening the app never shifts or cancels it.
     */
    private fun ensureDailySummaryScheduled() {
        appScope.launch {
            val prefs = container.preferencesRepository.preferences.first()
            if (prefs.dailyNotificationEnabled) {
                container.notificationScheduler.schedule(prefs.dailyNotificationTime, NotificationScheduler.Policy.KeepExisting)
            } else {
                container.notificationScheduler.cancel()
            }
        }
    }
}
