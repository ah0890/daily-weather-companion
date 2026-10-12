package com.ah0890.dailyweather.widget

import android.content.Context
import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ah0890.dailyweather.DailyWeatherApplication
import com.ah0890.dailyweather.MainActivity
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.presentation.weather.describe
import com.ah0890.dailyweather.presentation.weather.dot
import com.ah0890.dailyweather.presentation.weather.emoji
import com.ah0890.dailyweather.presentation.weather.formatTemperature
import com.ah0890.dailyweather.presentation.weather.headlineRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-screen widget: current weather, today's rain chance, the next commute verdict and any
 * severe alert. It only reads forecasts the app already cached and never downloads anything;
 * it is redrawn when a new forecast is saved and re-rendered hourly from the cache.
 */
class DailyWeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (snapshot, preferences) = load(context)
        provideContent {
            GlanceTheme { Content(snapshot, preferences.temperatureUnit) }
        }
    }

    private suspend fun load(context: Context): Pair<WidgetSnapshot?, UserPreferences> {
        val container = (context.applicationContext as DailyWeatherApplication).container
        val preferences = runCatching { container.preferencesRepository.preferences.first() }.getOrDefault(UserPreferences())
        val snapshot = runCatching {
            val places = container.placesRepository.places.first()
            val device = container.locationRepository.savedLocation.first()?.location
            // The widget follows the place the app opens with, else home, else the last device location.
            val place = places.default ?: places.home
            val coordinates = place?.coordinates ?: device?.coordinates ?: return@runCatching null
            val name = place?.name ?: device?.name
            val weather = container.weatherRepository
            val cached = weather.cachedForecast(coordinates) ?: return@runCatching null
            WidgetSnapshot.from(
                forecast = cached.forecast,
                fetchedAt = cached.fetchedAt,
                locationName = name,
                preferences = preferences,
                now = Instant.now(),
                homeForecast = places.home?.let { weather.cachedForecast(it.coordinates)?.forecast },
                officeForecast = places.office?.let { weather.cachedForecast(it.coordinates)?.forecast },
            )
        }.getOrNull()
        return snapshot to preferences
    }
}

@Composable
private fun Content(snapshot: WidgetSnapshot?, unit: TemperatureUnit) {
    val context = LocalContext.current
    val primary = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp)
    val secondary = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (snapshot == null) {
            Text(context.getString(R.string.app_name), style = primary.copy(fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            Text(context.getString(R.string.widget_no_data), style = secondary)
            return@Column
        }

        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "📍 " + (snapshot.locationName ?: context.getString(R.string.location_saved)),
                style = secondary,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (snapshot.outdated) Text(context.getString(R.string.widget_outdated), style = secondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = snapshot.condition.emoji(snapshot.isDay) + " " + formatTemperature(snapshot.temperatureC, unit),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
            snapshot.rainChance?.let {
                Text(
                    text = "   " + context.getString(R.string.widget_rain, it),
                    style = primary,
                )
            }
        }
        snapshot.commute?.let { advice ->
            val trip = context.getString(
                if (advice.leg.type == CommuteLegType.Morning) R.string.notification_leg_morning else R.string.notification_leg_evening,
            )
            val day = if (advice.leg.departure.toLocalDate() != LocalDate.now()) context.getString(R.string.forecast_tomorrow) + " · " else ""
            Text(
                text = "$day$trip: ${advice.rating.dot} " + context.getString(headlineRes(advice.mode, advice.rating)),
                style = primary,
                maxLines = 2,
            )
        }
        val alert = snapshot.alert
        when {
            alert != null -> Text(
                text = alert.describe(context.resources, hourFormatter(), unit),
                style = primary.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            snapshot.umbrella != UmbrellaAdvice.NotNeeded -> Text(
                text = context.getString(
                    if (snapshot.umbrella == UmbrellaAdvice.Recommended) R.string.notification_umbrella_recommended else R.string.notification_umbrella_consider,
                ),
                style = primary,
                maxLines = 1,
            )
        }
    }
}

private fun hourFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern(AndroidDateFormat.getBestDateTimePattern(Locale.getDefault(), "j")).withZone(ZoneId.systemDefault())

class DailyWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DailyWeatherWidget()
}

/** Redraws placed widgets from the cache; does nothing when none are on the home screen. */
class WidgetUpdater(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun requestUpdate() {
        scope.launch {
            runCatching {
                val manager = GlanceAppWidgetManager(context)
                if (manager.getGlanceIds(DailyWeatherWidget::class.java).isNotEmpty()) DailyWeatherWidget().updateAll(context)
            }
        }
    }
}
