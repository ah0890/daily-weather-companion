package com.ah0890.dailyweather.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Stores user preferences in a Preferences DataStore. Enums are stored by name and times as
 * minutes after midnight and sets of days as bit masks; unknown or out-of-range values fall back to
 * the defaults.
 */
class DataStorePreferencesRepository(private val dataStore: DataStore<Preferences>) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toUserPreferences() }
        .distinctUntilChanged()

    override suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        dataStore.edit { it[Keys.TemperatureUnit] = unit.name }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.ThemeMode] = mode.name }
    }

    override suspend fun setMorningCommute(time: LocalTime) {
        dataStore.edit { it[Keys.MorningCommute] = time.toMinutes() }
    }

    override suspend fun setEveningCommute(time: LocalTime) {
        dataStore.edit { it[Keys.EveningCommute] = time.toMinutes() }
    }

    override suspend fun setTransportMode(mode: TransportMode) {
        dataStore.edit { it[Keys.TransportMode] = mode.name }
    }

    override suspend fun setDailyNotificationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NotificationEnabled] = enabled }
    }

    override suspend fun setDailyNotificationTime(time: LocalTime) {
        dataStore.edit { it[Keys.NotificationTime] = time.toMinutes() }
    }

    override suspend fun setDailyNotificationDays(days: Set<DayOfWeek>) {
        dataStore.edit { it[Keys.NotificationDays] = days.toMask() }
    }

    override suspend fun setWorkdays(days: Set<DayOfWeek>) {
        dataStore.edit { it[Keys.Workdays] = days.toMask() }
    }

    override suspend fun setEveningAlertEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.EveningAlertEnabled] = enabled }
    }

    override suspend fun setEveningAlertTime(time: LocalTime) {
        dataStore.edit { it[Keys.EveningAlertTime] = time.toMinutes() }
    }

    override suspend fun setEveningAlertDays(days: Set<DayOfWeek>) {
        dataStore.edit { it[Keys.EveningAlertDays] = days.toMask() }
    }

    override suspend fun setSevereAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SevereAlertsEnabled] = enabled }
    }

    override suspend fun setNotificationPromptDismissed() {
        dataStore.edit { it[Keys.NotificationPromptDismissed] = true }
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            temperatureUnit = enumOrNull<TemperatureUnit>(this[Keys.TemperatureUnit]) ?: defaults.temperatureUnit,
            themeMode = enumOrNull<ThemeMode>(this[Keys.ThemeMode]) ?: defaults.themeMode,
            commuteTimes = CommuteTimes(
                morning = this[Keys.MorningCommute]?.toTimeOrNull() ?: defaults.commuteTimes.morning,
                evening = this[Keys.EveningCommute]?.toTimeOrNull() ?: defaults.commuteTimes.evening,
            ),
            transportMode = enumOrNull<TransportMode>(this[Keys.TransportMode]) ?: defaults.transportMode,
            workdays = this[Keys.Workdays]?.toDaysOrNull() ?: defaults.workdays,
            dailyNotificationEnabled = this[Keys.NotificationEnabled] ?: defaults.dailyNotificationEnabled,
            dailyNotificationTime = this[Keys.NotificationTime]?.toTimeOrNull() ?: defaults.dailyNotificationTime,
            dailyNotificationDays = this[Keys.NotificationDays]?.toDaysOrNull() ?: defaults.dailyNotificationDays,
            eveningAlertEnabled = this[Keys.EveningAlertEnabled] ?: defaults.eveningAlertEnabled,
            eveningAlertTime = this[Keys.EveningAlertTime]?.toTimeOrNull() ?: defaults.eveningAlertTime,
            eveningAlertDays = this[Keys.EveningAlertDays]?.toDaysOrNull() ?: defaults.eveningAlertDays,
            severeAlertsEnabled = this[Keys.SevereAlertsEnabled] ?: defaults.severeAlertsEnabled,
            notificationPromptDismissed = this[Keys.NotificationPromptDismissed] ?: defaults.notificationPromptDismissed,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private fun LocalTime.toMinutes(): Int = hour * 60 + minute

    private fun Int.toTimeOrNull(): LocalTime? =
        if (this in 0 until 24 * 60) LocalTime.of(this / 60, this % 60) else null

    /** Days as a bit mask: Monday is bit 0, Sunday bit 6. */
    private fun Set<DayOfWeek>.toMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    private fun Int.toDaysOrNull(): Set<DayOfWeek>? =
        if (this in 0..0b111_1111) DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet() else null

    private object Keys {
        val TemperatureUnit = stringPreferencesKey("temperature_unit")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val MorningCommute = intPreferencesKey("commute_morning_minutes")
        val EveningCommute = intPreferencesKey("commute_evening_minutes")
        val TransportMode = stringPreferencesKey("transport_mode")
        val NotificationEnabled = booleanPreferencesKey("daily_notification_enabled")
        val NotificationTime = intPreferencesKey("daily_notification_minutes")
        val NotificationDays = intPreferencesKey("daily_notification_days")
        val Workdays = intPreferencesKey("workdays")
        val EveningAlertEnabled = booleanPreferencesKey("evening_alert_enabled")
        val EveningAlertTime = intPreferencesKey("evening_alert_minutes")
        val EveningAlertDays = intPreferencesKey("evening_alert_days")
        val SevereAlertsEnabled = booleanPreferencesKey("severe_alerts_enabled")
        val NotificationPromptDismissed = booleanPreferencesKey("notification_prompt_dismissed")
    }
}
