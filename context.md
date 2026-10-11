# Project Context — Daily Weather Companion

Quick-start context for continuing development. Read this first; open source files only when needed.
Last updated: 2026-10-11 (after the accessibility pass).

## 1. What this is

Personal-use Android weather app answering: *"What should I expect today?"* and *"Do I need an umbrella / should I ride the motorcycle?"*
Priorities: today's weather → rain probability → rain timing → temperature → morning/evening conditions → 7-day → recommendation.
Design: calm, premium, readable in sunlight, light + dark. Not a data dump.

- Repo: <https://github.com/ah0890/daily-weather-companion> (branch `main`)
- Package / applicationId: `com.ah0890.dailyweather`
- Weather API: **Open-Meteo** (free, no API key). Place names: Android `Geocoder`.

## 2. Working rules (must follow)

- Work one milestone at a time. After each: build → unit tests → lint → release build → secret scan → stage files **explicitly** → commit (conventional message) → `git push origin main` → continue with the next milestone. (Automatic commit/push requested by the owner on 2026-10-11; before that the owner ran Git manually.)
- **No AI attribution** anywhere — no `Co-Authored-By` trailers. Never change git user config, never force-push or rewrite history, never stash/reset/clean.
- Before starting a milestone, check `git status`; if unexpected uncommitted work exists, stop and report.
- Keep the emulator **off** until the project is complete (owner's request) — verify with tests, lint and builds.
- Never commit secrets; `local.properties` is git-ignored. Don't put real personal locations in tests (use London/Paris/Sydney).
- Keep `README.md` and this file up to date in each milestone commit.

## 3. Environment (Windows 11, 8 GB RAM)

- JDK 21 (`JAVA_HOME` set). Build from terminal with `./gradlew` — installed Android Studio (2020.3) is too old to open the project.
- Android SDK: `D:\Android\Sdk` (`ANDROID_HOME`), `local.properties` → `sdk.dir=D\:/Android/Sdk`.
- Emulator AVD **Pixel** (API 36, google_apis x86_64, 2 GB RAM), data in `D:\Android\avd`; pointer file at `~/.android/avd/Pixel.ini`.
- C: drive has little free space — keep SDK/emulator data on D:.

Commands (Git Bash):

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug   # standard check (~2–5 min)
./gradlew assembleRelease                              # verify R8/minified build
./gradlew installDebug                                 # install on running emulator
D:/Android/Sdk/emulator/emulator.exe -avd Pixel        # start emulator (boot ~2 min); run in background
```

Gotchas:

- After SDK path changes or odd `android.jar` errors: `./gradlew --stop` and delete `.gradle/configuration-cache`.
- adb from Git Bash: prefix with `export MSYS_NO_PATHCONV=1` (otherwise `/sdcard/...` paths get mangled).
- Emulator GPS only reports while a fix is sent: loop `adb emu geo fix -0.1278 51.5074` every ~1.5 s while the app is locating.
- Find dialog buttons via `adb shell uiautomator dump /sdcard/ui.xml` + read bounds — positions vary (e.g. "Don't allow" vs "Only this time").
- `pm clear com.ah0890.dailyweather` resets app data and permissions; `pm grant/revoke ... ACCESS_COARSE_LOCATION`.
- Lint result can be stale for `local.properties`; use `--rerun-tasks` if needed.
- The test console prints a harmless `android.util.Log isLoggable not mocked` warning from OkHttp.

## 4. Tech stack (versions in `gradle/libs.versions.toml`)

AGP 9.4.1 (built-in Kotlin, no `kotlin-android` plugin) · Gradle 9.8.1 · Kotlin 2.4.21 · Compose BOM 2026.09.00 (Material 3 1.4) · compileSdk/targetSdk 37 · minSdk 26 · Navigation Compose 2.10.2 (type-safe `@Serializable` routes) · Lifecycle 2.11 · Retrofit 3.0 + OkHttp 5.5 + kotlinx.serialization 1.11 · DataStore Preferences 1.2.1 · Tests: JUnit4, coroutines-test 1.11, mockwebserver3.
Not used on purpose: Hilt (manual DI), Play Services location (framework `LocationManager`), material-icons-extended (only `material-icons-core`).

## 5. Architecture

```text
UI (Compose) → ViewModel (StateFlow) → Repository interface (domain) → data sources (remote / local)
```

`HomeViewModel` is created once in `DailyWeatherApp` (activity scope) and shared by Today and 7 days.
Manual DI: `DailyWeatherApplication.container` (`AppContainer`, lazy singletons). ViewModels use `viewModelFactory { initializer { … APPLICATION_KEY … } }`.

| Path (`app/src/main/java/com/ah0890/dailyweather/`) | Purpose |
| --- | --- |
| `MainActivity.kt` | Collects preferences → `LocalUserPreferences`, resolves theme (System/Light/Dark) and re-applies edge-to-edge bar styles |
| `AppContainer.kt` | Wires `LocationProvider`, `PlaceNameResolver`, `WeatherRepository` (Open-Meteo), `LocationRepository` (DataStore `saved_location`) |
| `domain/model/` | `WeatherForecast`, `CurrentWeather`, `HourlyForecast`, `DailyForecast` (metric units, location-local `LocalDateTime`), `WeatherCondition` (+`isPrecipitation`), `Coordinates` (validated; `approximate()` = 2 decimals), `LocationData`, `SavedLocation`, `WeatherResult` (`Success(forecast, fetchedAt)` / `Failure(WeatherError)`) |
| `domain/model/` (rain) | `RainInformation` (max %, peak time, total mm, `RainPeriod`s), `WeatherRecommendation` (`UmbrellaAdvice` Recommended/Consider/NotNeeded), `TodayOutlook` |
| `domain/usecase/RainAnalysis.kt` | Pure rules: ≥40% = rain likely, periods merge across 1 dry hour; umbrella ≥60% or raining now → Recommended, ≥30% or ≥1 mm → Consider; window = rest of today (≥6 h); `summary()` → `RainSummary` (NowUntil/Between/ChanceAround/LightRain/Low) — reuse for the notification |
| `domain/model/Commute.kt` + `domain/usecase/CommuteAnalysis.kt` | `CommuteTimes` (default 08:00/17:00 — not yet user-configurable; Settings milestone must persist them and pass into `CurrentWeatherSection(commuteTimes=…)`), legs use departure hour + next hour, rain levels reuse umbrella thresholds, windy ≥ 39 km/h; after evening trip → tomorrow |
| `domain/model/UserPreferences.kt` + `data/local/DataStorePreferencesRepository.kt` | `TemperatureUnit`, `ThemeMode`, `CommuteTimes` in DataStore `user_preferences` (enums by name, times as minutes; invalid → defaults) |
| `domain/repository/` | `WeatherRepository.getForecast(coords): WeatherResult` (never throws), `LocationRepository` (`savedLocation: Flow`, `save()`) |
| `data/remote/openmeteo/` | `OpenMeteoApi` (single `v1/forecast` call: current + 7-day hourly + daily, `timezone=auto`), nullable DTOs, `OpenMeteoMapper` (validates, skips bad rows, WMO→condition), `OpenMeteoDataSource.create()` (timeouts, no logging) |
| `data/repository/DefaultWeatherRepository.kt` | Saves each success to the cache; on failure returns `WeatherResult.Stale(cached, error)` if cached; `cachedForecast()` for instant start. Maps exceptions → `WeatherError` (NoConnection, Timeout, RateLimited, ServiceUnavailable, InvalidData, Unexpected); rethrows cancellation |
| `data/local/WeatherCache.kt` | `FileWeatherCache` — one versioned JSON record (`files/weather_cache.json`), atomic temp-file move, matches by ~1 km coords, corrupt/old → null |
| `data/local/DataStoreLocationRepository.kt` | Stores rounded lat/lon, name (≤100 chars), saved time; corrupt → null |
| `location/` | `AndroidLocationProvider` (one-shot fix: fused/network 10 s → GPS 20 s on API 31+ → last-known ≤3 h), `GeocoderPlaceNameResolver` (rounded coords, 10 s timeout) |
| `notifications/` | `NotificationSchedule` (pure: `nextRun` by calendar/DST-safe, `isTooLate` > 3 h), `DailySummary` (pure content from forecast), `DailySummaryWork` (testable run: disabled/blocked/no location/too late/retry ×3/stale ≤ 6 h → `Outcome`), `DailyWeatherWorker` (CoroutineWorker; re-schedules next run with APPEND_OR_REPLACE, never returns failure), `WorkManagerNotificationScheduler` (unique work `daily_weather_summary`; KEEP on app start, REPLACE on settings change), `AndroidDailySummaryNotifier` (channel `daily_summary`, BigText) |
| `presentation/home/` | `HomeRoute` (permission launcher, resume check, settings intents) + stateless `HomeScreen(state, weather, HomeActions)` in a `PullToRefreshBox`; `HomeViewModel` (location + weather); `LocationUiState(location, source Saved/Current, status)`; `WeatherUiState` (Idle/Loading/Loaded(+isRefreshing, refreshError)/Error); `CurrentWeatherSection` (hero card → `TodayOutlookCard` → `CommuteCard` → hourly → details; loading/error/refresh-failed); now in `presentation/weather/` |
| `presentation/components/` | `RelativeAge` ("updated 3 hours ago") |
| `presentation/weather/` | Shared weather UI: `ConditionUi` (condition → `WeatherMood`, emoji, label), `WeatherFormatting` (°, km/h, mm, compass, visibility, UV band), `HourlyForecastRow` (LazyRow, rain bar, ≥50% highlighted, bleeds to edge), `WeatherErrorCard` |
| `presentation/forecast/` | `ForecastRoute`/`ForecastScreen`: LazyColumn of `DayCard`s (Today/Tomorrow/weekday, rain %, low–high `RangeBar` vs week span), tap to expand `DayDetails` + that day's `HourlyForecastRow`; `DayLabel`, `TemperatureSpan` |
| `presentation/settings/` | `SettingsRoute`/`SettingsScreen` (sections: Location w/ Update, Commute times via M3 `TimePicker` dialog, Display: °C/°F + theme `SegmentedButton`s, About: Privacy, Open-Meteo attribution link, version), `SettingsViewModel`, `PrivacyScreen` (route `PrivacyRoute`, keeps Settings tab selected) |
| `presentation/components/` (more) | `LocalUserPreferences` (provided in `MainActivity`; use `temperatureText()` not `formatTemperature()` in UI), `SystemIntents` (`LOCATION_PERMISSION`, `hasLocationPermission`, `openSettings`, `openUrl`) |
| `ui/theme/` | Custom light/dark colours, typography, shapes; `WeatherBackdrop.forMood(WeatherMood, dark)` pastel/deep gradients with high-contrast content colour; `LocalDarkTheme` |

Tests (`app/src/test/...`): `testing/SampleForecasts` (domain fixture), `FakeWeatherRepository`, `MainDispatcherRule`, `InMemoryPreferencesDataStore` (file DataStore can't overwrite on Windows JVM — use in-memory for multi-write tests). JSON fixture: `test/resources/openmeteo/forecast_sample.json`. Currently **189 tests**, all passing; lint clean.

## 6. Key behaviour & decisions

- Permission: **ACCESS_COARSE_LOCATION only** + INTERNET + POST_NOTIFICATIONS (asked after explanation: one-time `NotificationPromptCard` on Today or Settings switch). No fine/background location. `usesCleartextTraffic=false`, backups fully disabled (`data_extraction_rules.xml`).
- Location flow: explain card → system dialog only on tap. Denied once → "Try again"; permanent → "Open settings". Location services off → "Turn on location" (auto-retries on resume).
- Weather loads when the location's (rounded) coordinates change, on pull-to-refresh/retry, and on resume if older than 30 min — never polled. On start the cache is shown first; a cache < 30 min old means no network call. Failed refresh / offline → last forecast + `StaleForecastNotice` ("Last updated: Today, 6:45 AM"; >1 h old → "may be out of date"). VM wraps every forecast with `advancedTo(now at location)` so stale data never shows past hours as now.
- Saved location shown instantly on launch. Auto lookup only if none saved or saved > 6 h old (quiet; failure keeps saved). Manual refresh icon in header. Failures with a saved location show a small non-blocking notice.
- Coordinates rounded to ~1 km before sending to Open-Meteo, Geocoder, or storage. Coordinates are never logged.
- Cards use `MaterialTheme.colorScheme.cardContainer` (ui/theme; lighter than background in dark mode). Text-bearing fixed widths use `widthIn(min=…)` so large font scales don't clip.
- All user text in `res/values/strings*.xml`; status text uses `liveRegion`; headings use `semantics { heading() }`.

## 7. Milestones

Done (commit):

1. Project foundation, theme, navigation (`1e590c7`) + README (`878384a`)
2. Domain models + Open-Meteo client (`cab5bb3`)
3. Weather repository + error categorization (`11db3c0`)
4. Location permission flow + one-shot lookup (`ca2215a`)
5. Saved location + manual update (`5807709`)
6. Current weather on Today screen (`a7eeeb5`)
7. Hourly forecast timeline (`1fa4209`)
8. 7-day forecast + day details (`ab4f55d`)
9. Rain timing + umbrella recommendation (`40fff9c`)
10. Commute summary ("Plan your day") (`c61de46`)
11. Offline cache + "last updated" (`600339d`)
12. Settings (units, theme, commute times, privacy, about) (`cca313c`)
13. Daily morning notification (`34839dd`)
14. Accessibility pass (large-text min widths, dark-mode `cardContainer`)

Next (in order, adapt if needed):

15. Error-handling/UI polish, docs, final QA + 1.0.0

## 8. Open items / notes

- GitHub "Contributors" sidebar may still show a cached "claude" entry from early history (trailers were removed via `filter-branch`; API already shows only `ah0890`). It refreshes on its own.
- README documents API, permissions, saved location, security, setup — keep it updated per milestone.
