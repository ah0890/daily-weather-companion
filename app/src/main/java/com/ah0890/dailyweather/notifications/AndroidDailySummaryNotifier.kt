package com.ah0890.dailyweather.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
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
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.LegAdvice
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.usecase.RainSummary
import com.ah0890.dailyweather.domain.usecase.WeatherAlert
import com.ah0890.dailyweather.presentation.weather.describe
import com.ah0890.dailyweather.presentation.weather.dot
import com.ah0890.dailyweather.presentation.weather.emoji
import com.ah0890.dailyweather.presentation.weather.formatPrecipitation
import com.ah0890.dailyweather.presentation.weather.formatTemperature
import com.ah0890.dailyweather.presentation.weather.headlineRes
import com.ah0890.dailyweather.presentation.weather.labelRes
import com.ah0890.dailyweather.presentation.weather.textRes
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Posts the morning summary and the evening commute alert as standard, concise notifications. */
class AndroidDailySummaryNotifier(private val context: Context) : DailySummaryNotifier {

    override fun canNotify(): Boolean {
        val permitted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override fun show(summary: DailySummary, unit: TemperatureUnit) {
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

        val commuteLines = summary.commute.map { legLine(it) }
        val tips = summary.commute.flatMap { it.tips }.distinct().map { context.getString(it.textRes) }
        val body = buildList {
            add(headline)
            add(rainText(summary.rain))
            addAll(commuteLines)
            if (tips.isNotEmpty()) add(tips.joinToString(" · ")) else add(umbrellaText(summary.umbrella))
        }.joinToString("\n")

        // Collapsed view: the most important commute concern, or the umbrella advice.
        val concern = summary.commute.firstOrNull { it.rating != TravelRating.Favorable }?.let { legLine(it) }
        post(SUMMARY_CHANNEL, SUMMARY_ID, title, "$headline · ${concern ?: umbrellaText(summary.umbrella)}", body)
    }

    override fun showEveningAlert(alert: EveningAlert, unit: TemperatureUnit) {
        val leg = alert.advice.leg
        val emoji = leg.condition.emoji(leg.isDay)
        val title = alert.locationName
            ?.let { context.getString(R.string.notification_evening_title_location, emoji, it) }
            ?: context.getString(R.string.notification_evening_title, emoji)

        val headline = alert.advice.rating.dot + " " + context.getString(headlineRes(alert.advice.mode, alert.advice.rating))
        val body = buildList {
            alert.rainChance?.let { add(context.getString(R.string.notification_rain_chance, it)) }
            add(rainText(alert.rain))
            add(headline)
            alert.advice.concerns.forEach { add(it.emoji + " " + context.getString(it.labelRes)) }
            alert.advice.tips.forEach { add(context.getString(it.textRes)) }
        }.joinToString("\n")

        val short = listOfNotNull(
            alert.rainChance?.let { context.getString(R.string.notification_rain_chance, it) },
            alert.advice.tips.firstOrNull()?.let { context.getString(it.textRes) } ?: headline,
        ).joinToString(" · ")
        post(COMMUTE_CHANNEL, COMMUTE_ID, title, short, body)
    }

    override fun showSevereWeather(alerts: List<WeatherAlert>, locationName: String?, unit: TemperatureUnit) {
        if (alerts.isEmpty()) return
        val title = locationName?.let { context.getString(R.string.notification_severe_title_location, it) }
            ?: context.getString(R.string.notification_severe_title)
        val lines = alerts.map { it.describe(context.resources, hourFormatter(), unit) }
        val body = (lines + context.getString(R.string.alerts_disclaimer)).joinToString("\n")
        post(SEVERE_CHANNEL, SEVERE_ID, title, lines.first(), body)
    }

    /** e.g. "Return trip: rain 72% · 🔴 Riding conditions may be difficult" */
    private fun legLine(advice: LegAdvice): String {
        val label = context.getString(
            if (advice.leg.type == CommuteLegType.Morning) R.string.notification_leg_morning else R.string.notification_leg_evening,
        )
        val headline = advice.rating.dot + " " + context.getString(headlineRes(advice.mode, advice.rating))
        val rain = advice.leg.rainChance?.let { context.getString(R.string.notification_leg_rain, it) }
        return context.getString(R.string.notification_leg, label, listOfNotNull(rain, headline).joinToString(" · "))
    }

    @SuppressLint("MissingPermission") // Checked by canNotify() before every call.
    private fun post(channel: Channel, id: Int, title: String, text: String, body: String) {
        if (!canNotify()) return
        ensureChannel(channel)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications were blocked after canNotify() was checked; nothing to show.
        }
    }

    private fun hourFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "j"))

    private fun rainText(rain: RainSummary): String {
        val hours = hourFormatter()
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

    private fun ensureChannel(channel: Channel) {
        val notificationChannel = NotificationChannel(
            channel.id,
            context.getString(channel.name),
            channel.importance,
        ).apply { description = context.getString(channel.description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(notificationChannel)
    }

    private class Channel(
        val id: String,
        val name: Int,
        val description: Int,
        val importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
    )

    private companion object {
        val SUMMARY_CHANNEL = Channel("daily_summary", R.string.notification_channel_name, R.string.notification_channel_description)
        val COMMUTE_CHANNEL = Channel(
            "commute_alerts",
            R.string.notification_commute_channel_name,
            R.string.notification_commute_channel_description,
        )
        val SEVERE_CHANNEL = Channel(
            "severe_weather",
            R.string.notification_severe_channel_name,
            R.string.notification_severe_channel_description,
            NotificationManager.IMPORTANCE_HIGH,
        )
        const val SUMMARY_ID = 1
        const val COMMUTE_ID = 2
        const val SEVERE_ID = 3
    }
}
