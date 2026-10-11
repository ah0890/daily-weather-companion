package com.ah0890.dailyweather.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.TemperatureUnit
import com.ah0890.dailyweather.domain.model.ThemeMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime

/**
 * Stores user preferences in a Preferences DataStore. Enums are stored by name and times as
 * minutes after midnight; unknown or out-of-range values fall back to the defaults.
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

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            temperatureUnit = enumOrNull<TemperatureUnit>(this[Keys.TemperatureUnit]) ?: defaults.temperatureUnit,
            themeMode = enumOrNull<ThemeMode>(this[Keys.ThemeMode]) ?: defaults.themeMode,
            commuteTimes = CommuteTimes(
                morning = this[Keys.MorningCommute]?.toTimeOrNull() ?: defaults.commuteTimes.morning,
                evening = this[Keys.EveningCommute]?.toTimeOrNull() ?: defaults.commuteTimes.evening,
            ),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private fun LocalTime.toMinutes(): Int = hour * 60 + minute

    private fun Int.toTimeOrNull(): LocalTime? =
        if (this in 0 until 24 * 60) LocalTime.of(this / 60, this % 60) else null

    private object Keys {
        val TemperatureUnit = stringPreferencesKey("temperature_unit")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val MorningCommute = intPreferencesKey("commute_morning_minutes")
        val EveningCommute = intPreferencesKey("commute_evening_minutes")
    }
}
