package com.ah0890.dailyweather.presentation.weather

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.WeatherError
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A forecast older than this is flagged as possibly out of date. */
val OUTDATED_AFTER: Duration = Duration.ofHours(1)

enum class UpdatedDay { Today, Yesterday, Earlier }

/** Which day [fetchedAt] falls on relative to [now], in the device's time zone. */
fun updatedDay(fetchedAt: Instant, now: Instant, zone: ZoneId): UpdatedDay {
    val fetchedDate = fetchedAt.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when (fetchedDate) {
        today -> UpdatedDay.Today
        today.minusDays(1) -> UpdatedDay.Yesterday
        else -> UpdatedDay.Earlier
    }
}

fun isOutdated(fetchedAt: Instant, now: Instant): Boolean = Duration.between(fetchedAt, now) > OUTDATED_AFTER

/**
 * Explains that the forecast on screen isn't fresh: either the latest refresh failed, or the
 * forecast is simply old (e.g. shown from the offline copy). Shows nothing for fresh data.
 */
@Composable
fun StaleForecastNotice(fetchedAt: Instant, refreshError: WeatherError?) {
    val now = Instant.now()
    if (refreshError == null && !isOutdated(fetchedAt, now)) return

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(if (refreshError != null) R.string.weather_refresh_failed else R.string.weather_may_be_outdated),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.last_updated, lastUpdatedText(fetchedAt, now)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Today, 6:45 AM", "Yesterday, 6:45 PM" or "9 Oct, 6:45 AM". */
@Composable
fun lastUpdatedText(fetchedAt: Instant, now: Instant): String {
    val locale = LocalLocale.current.platformLocale
    val zone = ZoneId.systemDefault()
    val time = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    val date = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale) }
    val local = fetchedAt.atZone(zone)
    val clock = local.format(time)
    return when (updatedDay(fetchedAt, now, zone)) {
        UpdatedDay.Today -> stringResource(R.string.last_updated_today, clock)
        UpdatedDay.Yesterday -> stringResource(R.string.last_updated_yesterday, clock)
        UpdatedDay.Earlier -> stringResource(R.string.last_updated_date, local.format(date), clock)
    }
}
