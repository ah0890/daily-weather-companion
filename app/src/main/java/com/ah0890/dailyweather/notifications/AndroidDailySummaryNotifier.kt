package com.ah0890.dailyweather.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ah0890.dailyweather.MainActivity
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.usecase.RainSummary
import com.ah0890.dailyweather.presentation.weather.emoji
import com.ah0890.dailyweather.presentation.weather.formatPrecipitation
import com.ah0890.dailyweather.presentation.weather.formatTemperature
import com.ah0890.dailyweather.presentation.weather.labelRes
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Posts the morning summary as a standard, concise notification. */
class AndroidDailySummaryNotifier(private val context: Context) : DailySummaryNotifier {

    override fun canNotify(): Boolean {
        val permitted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // Checked by canNotify() before every call.
    override fun show(summary: DailySummary, unit: TemperatureUnit) {
        if (!canNotify()) return
        ensureChannel()

        val title = summary.locationName
            ?.let { context.getString(R.string.notification_title_location, summary.condition.emoji(isDay = true), it) }
            ?: context.getString(R.string.notification_title, summary.condition.emoji(isDay = true))

        val headline = buildList {
            add(context.getString(summary.condition.labelRes))
            if (summary.highC != null && summary.lowC != null) {
                add(context.getString(R.string.notification_high_low, formatTemperature(summary.highC, unit), formatTemperature(summary.lowC, unit)))
            }
            summary.rainChance?.let { add(context.getString(R.string.notification_rain_chance, it)) }
        }.joinToString(" · ")

        val body = listOf(headline, rainText(summary.rain), umbrellaText(summary.umbrella)).joinToString("\n")

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("$headline · ${umbrellaText(summary.umbrella)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notifications were blocked after canNotify() was checked; nothing to show.
        }
    }

    private fun rainText(rain: RainSummary): String {
        val hours = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "j"))
        return when (rain) {
            is RainSummary.NowUntil -> context.getString(R.string.rain_now_until, rain.until.format(hours))
            is RainSummary.Between -> context.getString(R.string.rain_between, rain.start.format(hours), rain.end.format(hours))
            is RainSummary.ChanceAround -> context.getString(R.string.rain_chance_around, rain.percent, rain.time.format(hours))
            is RainSummary.LightRain -> context.getString(R.string.rain_light, formatPrecipitation(rain.totalMm))
            RainSummary.Low -> context.getString(R.string.rain_low)
        }
    }

    private fun umbrellaText(advice: UmbrellaAdvice): String = context.getString(
        when (advice) {
            UmbrellaAdvice.Recommended -> R.string.notification_umbrella_recommended
            UmbrellaAdvice.Consider -> R.string.notification_umbrella_consider
            UmbrellaAdvice.NotNeeded -> R.string.notification_umbrella_not_needed
        },
    )

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "daily_summary"
        const val NOTIFICATION_ID = 1
    }
}
