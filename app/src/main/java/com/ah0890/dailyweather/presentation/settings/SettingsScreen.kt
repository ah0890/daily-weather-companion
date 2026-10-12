package com.ah0890.dailyweather.presentation.settings

import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ah0890.dailyweather.BuildConfig
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.SavedLocation
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.presentation.components.LOCATION_PERMISSION
import com.ah0890.dailyweather.presentation.components.RelativeAge
import com.ah0890.dailyweather.presentation.components.canPostNotifications
import com.ah0890.dailyweather.presentation.components.hasLocationPermission
import com.ah0890.dailyweather.presentation.components.needsNotificationPermission
import com.ah0890.dailyweather.presentation.components.openNotificationSettings
import com.ah0890.dailyweather.presentation.components.openUrl
import com.ah0890.dailyweather.presentation.components.requestNotificationPermission
import com.ah0890.dailyweather.presentation.components.text
import com.ah0890.dailyweather.notifications.NotificationKind
import com.ah0890.dailyweather.presentation.home.HomeViewModel
import com.ah0890.dailyweather.presentation.weather.emoji
import com.ah0890.dailyweather.presentation.weather.labelRes
import com.ah0890.dailyweather.ui.theme.cardContainer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val OPEN_METEO_URL = "https://open-meteo.com/"

/**
 * @param weatherViewModel shared with the Today screen, so "Update location" uses the same flow.
 * @param onLocationUpdateRequested called after an update was started, e.g. to show the Today tab.
 */
@Composable
fun SettingsRoute(
    weatherViewModel: HomeViewModel,
    onOpenPrivacy: () -> Unit,
    onLocationUpdateRequested: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
    val location by weatherViewModel.locationState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, LOCATION_PERMISSION)
        weatherViewModel.onPermissionResult(granted, canAskAgain)
        onLocationUpdateRequested()
    }

    // Re-checked on resume: notification access can be changed in system settings.
    var notificationsAllowed by remember { mutableStateOf(context.canPostNotifications()) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = context.canPostNotifications()
        onPauseOrDispose { }
    }
    // Which notification the permission dialog was shown for.
    var pendingKind by rememberSaveable { mutableStateOf<NotificationKind?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = context.canPostNotifications()
        // Only switch on if notifications were actually allowed.
        if (granted) {
            when (pendingKind) {
                NotificationKind.MorningSummary -> settingsViewModel.setDailyNotificationEnabled(true)
                NotificationKind.EveningCommute -> settingsViewModel.setEveningAlertEnabled(true)
                null -> Unit
            }
        }
        pendingKind = null
    }
    fun enableWithPermission(kind: NotificationKind, enabled: Boolean, set: (Boolean) -> Unit) {
        if (enabled && needsNotificationPermission && !context.canPostNotifications()) {
            pendingKind = kind
            notificationLauncher.requestNotificationPermission()
        } else {
            set(enabled)
        }
    }

    SettingsScreen(
        preferences = preferences,
        savedLocation = location.location,
        notificationsAllowed = notificationsAllowed,
        actions = SettingsActions(
            updateLocation = {
                if (context.hasLocationPermission()) {
                    weatherViewModel.updateLocation()
                    onLocationUpdateRequested()
                } else {
                    permissionLauncher.launch(LOCATION_PERMISSION)
                }
            },
            setNotificationsEnabled = { enabled ->
                enableWithPermission(NotificationKind.MorningSummary, enabled, settingsViewModel::setDailyNotificationEnabled)
            },
            setNotificationTime = settingsViewModel::setDailyNotificationTime,
            setNotificationDays = settingsViewModel::setDailyNotificationDays,
            setEveningAlertEnabled = { enabled ->
                enableWithPermission(NotificationKind.EveningCommute, enabled, settingsViewModel::setEveningAlertEnabled)
            },
            setEveningAlertTime = settingsViewModel::setEveningAlertTime,
            setEveningAlertDays = settingsViewModel::setEveningAlertDays,
            setWorkdays = settingsViewModel::setWorkdays,
            openNotificationSettings = { context.openNotificationSettings() },
            setMorningCommute = settingsViewModel::setMorningCommute,
            setEveningCommute = settingsViewModel::setEveningCommute,
            setTransportMode = settingsViewModel::setTransportMode,
            setTemperatureUnit = settingsViewModel::setTemperatureUnit,
            setThemeMode = settingsViewModel::setThemeMode,
            openPrivacy = onOpenPrivacy,
            openDataSource = { context.openUrl(OPEN_METEO_URL) },
        ),
    )
}

class SettingsActions(
    val updateLocation: () -> Unit = {},
    val setNotificationsEnabled: (Boolean) -> Unit = {},
    val setNotificationTime: (LocalTime) -> Unit = {},
    val setNotificationDays: (Set<DayOfWeek>) -> Unit = {},
    val setEveningAlertEnabled: (Boolean) -> Unit = {},
    val setEveningAlertTime: (LocalTime) -> Unit = {},
    val setEveningAlertDays: (Set<DayOfWeek>) -> Unit = {},
    val setWorkdays: (Set<DayOfWeek>) -> Unit = {},
    val openNotificationSettings: () -> Unit = {},
    val setMorningCommute: (LocalTime) -> Unit = {},
    val setEveningCommute: (LocalTime) -> Unit = {},
    val setTransportMode: (TransportMode) -> Unit = {},
    val setTemperatureUnit: (TemperatureUnit) -> Unit = {},
    val setThemeMode: (ThemeMode) -> Unit = {},
    val openPrivacy: () -> Unit = {},
    val openDataSource: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    savedLocation: SavedLocation?,
    notificationsAllowed: Boolean,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    // Which commute time is being edited, if any (survives rotation).
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        Section(R.string.settings_section_location) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (savedLocation != null) {
                    Text(
                        text = savedLocation.location.name ?: stringResource(R.string.location_saved),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.weather_updated,
                            RelativeAge.between(savedLocation.savedAt, Instant.now()).text(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(text = stringResource(R.string.settings_location_none), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = stringResource(R.string.settings_location_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = actions.updateLocation) {
                    Text(stringResource(R.string.settings_location_update))
                }
            }
        }

        Section(R.string.settings_section_notifications) {
            SwitchRow(
                title = stringResource(R.string.settings_notifications_switch),
                summary = stringResource(R.string.settings_notifications_switch_summary),
                checked = preferences.dailyNotificationEnabled,
                onCheckedChange = actions.setNotificationsEnabled,
            )
            if (preferences.dailyNotificationEnabled) {
                ClickableRow(
                    title = stringResource(R.string.settings_notifications_time),
                    value = preferences.dailyNotificationTime.format(timeFormatter),
                    onClick = { editing = "notification" },
                )
                DaysRow(stringResource(R.string.settings_notifications_days), preferences.dailyNotificationDays, actions.setNotificationDays)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                title = stringResource(R.string.settings_evening_switch),
                summary = stringResource(R.string.settings_evening_switch_summary),
                checked = preferences.eveningAlertEnabled,
                onCheckedChange = actions.setEveningAlertEnabled,
            )
            if (preferences.eveningAlertEnabled) {
                ClickableRow(
                    title = stringResource(R.string.settings_notifications_time),
                    value = preferences.eveningAlertTime.format(timeFormatter),
                    onClick = { editing = "evening_alert" },
                )
                DaysRow(stringResource(R.string.settings_notifications_days), preferences.eveningAlertDays, actions.setEveningAlertDays)
            }
            if ((preferences.dailyNotificationEnabled || preferences.eveningAlertEnabled) && !notificationsAllowed) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_notifications_blocked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = actions.openNotificationSettings) {
                        Text(stringResource(R.string.settings_notifications_open_settings))
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_notifications_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }

        Section(R.string.settings_section_commute) {
            DaysRow(stringResource(R.string.settings_workdays), preferences.workdays, actions.setWorkdays)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ClickableRow(
                title = stringResource(R.string.settings_commute_morning),
                value = preferences.commuteTimes.morning.format(timeFormatter),
                onClick = { editing = "morning" },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ClickableRow(
                title = stringResource(R.string.settings_commute_evening),
                value = preferences.commuteTimes.evening.format(timeFormatter),
                onClick = { editing = "evening" },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ClickableRow(
                title = stringResource(R.string.settings_transport),
                value = preferences.transportMode.emoji + " " + stringResource(preferences.transportMode.labelRes),
                onClick = { editing = "transport" },
            )
            Text(
                text = stringResource(R.string.settings_commute_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }

        Section(R.string.settings_section_display) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ChoiceRow(
                    title = stringResource(R.string.settings_temperature_unit),
                    options = listOf(
                        Choice(TemperatureUnit.Celsius, R.string.settings_unit_celsius, R.string.settings_unit_celsius_description),
                        Choice(TemperatureUnit.Fahrenheit, R.string.settings_unit_fahrenheit, R.string.settings_unit_fahrenheit_description),
                    ),
                    selected = preferences.temperatureUnit,
                    onSelect = actions.setTemperatureUnit,
                )
                ChoiceRow(
                    title = stringResource(R.string.settings_theme),
                    options = listOf(
                        Choice(ThemeMode.System, R.string.settings_theme_system),
                        Choice(ThemeMode.Light, R.string.settings_theme_light),
                        Choice(ThemeMode.Dark, R.string.settings_theme_dark),
                    ),
                    selected = preferences.themeMode,
                    onSelect = actions.setThemeMode,
                )
            }
        }

        Section(R.string.settings_section_about) {
            ClickableRow(
                title = stringResource(R.string.settings_privacy),
                value = stringResource(R.string.settings_privacy_summary),
                onClick = actions.openPrivacy,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ClickableRow(
                title = stringResource(R.string.settings_data_source),
                value = stringResource(R.string.settings_data_source_summary),
                onClick = actions.openDataSource,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    when (editing) {
        "morning" -> TimeDialog(
            initial = preferences.commuteTimes.morning,
            onConfirm = { actions.setMorningCommute(it); editing = null },
            onDismiss = { editing = null },
        )
        "notification" -> TimeDialog(
            initial = preferences.dailyNotificationTime,
            onConfirm = { actions.setNotificationTime(it); editing = null },
            onDismiss = { editing = null },
        )
        "evening" -> TimeDialog(
            initial = preferences.commuteTimes.evening,
            onConfirm = { actions.setEveningCommute(it); editing = null },
            onDismiss = { editing = null },
        )
        "evening_alert" -> TimeDialog(
            initial = preferences.eveningAlertTime,
            onConfirm = { actions.setEveningAlertTime(it); editing = null },
            onDismiss = { editing = null },
        )
        "transport" -> TransportDialog(
            selected = preferences.transportMode,
            onSelect = { actions.setTransportMode(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.cardContainer),
            modifier = Modifier.fillMaxWidth(),
        ) { content() }
    }
}

@Composable
private fun ClickableRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The whole row is the toggle; the switch only shows its state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

private data class Choice<T>(val value: T, val label: Int, val description: Int = label)

@Composable
private fun <T> ChoiceRow(title: String, options: List<Choice<T>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val description = stringResource(option.description)
                SegmentedButton(
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.semantics { contentDescription = description },
                ) {
                    Text(stringResource(option.label))
                }
            }
        }
    }
}

@Composable
private fun TransportDialog(selected: TransportMode, onSelect: (TransportMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_transport)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                TransportMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = mode == selected, role = Role.RadioButton, onClick = { onSelect(mode) }),
                    ) {
                        RadioButton(selected = mode == selected, onClick = null)
                        Text(
                            text = mode.emoji + "  " + stringResource(mode.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_transport_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_time_picker_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.settings_time_picker_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_time_picker_cancel)) }
        },
        text = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TimePicker(state = state)
            }
        },
    )
}
