package com.ah0890.dailyweather.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.DayPreset
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * Picks a set of days: quick presets (every day, weekdays, weekends) plus one toggle per day for
 * a custom selection. The week starts on the locale's first day.
 */
@Composable
fun DaysRow(title: String, days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val week = remember(locale) {
        val first = WeekFields.of(locale).firstDayOfWeek
        (0L..6L).map { first.plus(it) }
    }
    val preset = DayPreset.of(days)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                DayPreset.EveryDay to R.string.days_every_day,
                DayPreset.Weekdays to R.string.days_weekdays,
                DayPreset.Weekends to R.string.days_weekends,
            ).forEach { (option, label) ->
                FilterChip(
                    selected = preset == option,
                    onClick = { option.days?.let(onChange) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.forEach { day ->
                DayToggle(
                    letter = day.getDisplayName(TextStyle.NARROW, locale),
                    name = day.getDisplayName(TextStyle.FULL, locale),
                    selected = day in days,
                    onToggle = { onChange(if (it) days + day else days - day) },
                )
            }
        }
        if (days.isEmpty()) {
            Text(
                text = stringResource(R.string.days_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DayToggle(letter: String, name: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(40.dp)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = onToggle)
            .semantics { contentDescription = name },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = letter, style = MaterialTheme.typography.labelLarge)
        }
    }
}
