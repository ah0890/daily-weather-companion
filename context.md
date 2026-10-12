# Project Context — Daily Weather Companion

Hand-over notes for continuing development in a new session. **Read this first**; open source files only when needed.
Last updated: 2026-10-12 — v1.0.1 released; **"office commute assistant" upgrade in progress** (see section 8).

> **Starting a new chat:** say *"Read context.md, then …"* followed by what you want (a new feature, a bug fix, a new APK).
> The assistant should check `git status` / `git log -3` first and follow the working rules in section 2.

## 1. What this is

Personal-use Android weather app answering *"What should I expect today?"* and *"Do I need an umbrella / should I ride the motorcycle?"*
Priorities: today's weather → rain probability → rain timing → temperature → morning/evening conditions → 7-day → recommendation.
Design: calm, premium, readable in sunlight, light + dark. Not a data dump.

- Repo: <https://github.com/ah0890/daily-weather-companion> (branch `main`, public)
- Package / applicationId: `com.ah0890.dailyweather` · current version **1.0.1 (versionCode 2)**
- Weather API: **Open-Meteo** (free, no API key, CC BY 4.0 attribution shown in Settings). Place names: Android `Geocoder`.

## 2. Working rules (must follow)

- One milestone (or fix) at a time: build → unit tests → lint → release build → secret scan → stage files **explicitly** (never `git add .`) → conventional commit (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`) → `git push origin main`.
  The owner authorised automatic commit/push on 2026-10-11; if they ask to go back to manual Git, give them the exact commands instead and wait for "DONE".
- **No AI attribution** anywhere: no `Co-Authored-By` trailers, no mentions in commits/README. Never change git user config, never force-push or rewrite history, never stash/reset/clean the owner's work.
- Before starting work, check `git status`; if unexpected uncommitted changes exist, stop and report.
- Never commit secrets. Git-ignored: `local.properties`, `keystore.properties`, `*.jks`, `.env*`. Don't put real personal locations in tests (use London/Paris/Sydney).
- Keep `README.md` and this file current in the same commit as the change they describe.
- Emulator: the owner tends to close the window; ask before relying on it for long sessions.

## 3. Environment (Windows 11, 8 GB RAM, little space on C:)

- JDK 21 (`JAVA_HOME` set). Build from the terminal with `./gradlew` (installed Android Studio 2020.3 is too old to open the project).
- Android SDK `D:\Android\Sdk` (`ANDROID_HOME`); `local.properties` → `sdk.dir=D\:/Android/Sdk`.
- Emulator AVD **Pixel** (API 36, google_apis x86_64, 2 GB RAM), data in `D:\Android\avd`, pointer `~/.android/avd/Pixel.ini`.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug   # standard check (~2–5 min)
./gradlew assembleRelease                              # signed (if keystore.properties exists), minified release APK
./gradlew installDebug                                 # install on running emulator/phone
D:/Android/Sdk/emulator/emulator.exe -avd Pixel        # start emulator (boot ~2 min); run in background
```

Gotchas:

- Odd `android.jar`/config-cache errors: `./gradlew --stop` and delete `.gradle/configuration-cache`.
- adb from Git Bash: `export MSYS_NO_PATHCONV=1` first (else `/sdcard/...` paths get mangled).
- Shell heredocs strip backslashes: edit `strings.xml` with the Edit tool (or write Python scripts to a file) so `\'` survives.
- Emulator GPS only reports while fixes are sent: loop `adb emu geo fix -0.1278 51.5074` every ~1.5 s while the app locates.
- Dialog buttons move: find them with `adb shell uiautomator dump /sdcard/ui.xml` and read `bounds`. Compose items using `clearAndSetSemantics` don't show their text there.
- Test the morning job now: `adb root`, `adb shell settings put global auto_time 0`, `adb shell date MMDDhhmmYYYY.ss` (just after the notification time), then `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler com.ah0890.dailyweather 0`; restore with `auto_time 1` and `adb unroot`.
- `pm clear com.ah0890.dailyweather` resets data/permissions. A release-signed and a debug-signed build can't replace each other: `adb uninstall` first.
- Harmless test-console warning: `android.util.Log isLoggable not mocked` (OkHttp).

## 4. Releasing an APK

- Keystore: `D:\Android\keys\dailyweather-release.jks` (alias `dailyweather`), **outside the repo**. Passwords are only in the git-ignored `keystore.properties` in the project root. Losing either means future updates can't install over the current app (uninstall/reinstall needed). The owner should keep a backup.
- For every new APK: bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`, run the standard checks, `./gradlew assembleRelease`, verify with `D:/Android/Sdk/build-tools/37.0.0/apksigner.bat verify app/build/outputs/apk/release/app-release.apk`, copy to `D:\DailyWeather\DailyWeather-<version>.apk`.
- Latest: `D:\DailyWeather\DailyWeather-1.0.1.apk` (1.9 MB, APK Signature Scheme v2).

## 5. Tech stack (versions in `gradle/libs.versions.toml`)

AGP 9.4.1 (built-in Kotlin) · Gradle 9.8.1 · Kotlin 2.4.21 · Compose BOM 2026.09.00 (Material 3 1.4) · compileSdk/targetSdk 37 · minSdk 26 · Navigation Compose 2.10.2 (type-safe `@Serializable` routes) · Lifecycle 2.11 · Retrofit 3.0 + OkHttp 5.5 + kotlinx.serialization 1.11 · DataStore Preferences 1.2.1 · WorkManager 2.12 · Tests: JUnit4, coroutines-test 1.11, mockwebserver3.
Deliberately not used: Hilt (manual DI), Room (single-record JSON cache), Play Services location (framework `LocationManager`), material-icons-extended.

## 6. Architecture

```text
UI (Compose) → ViewModel (StateFlow) → Repository interface (domain) → data sources (remote / local)
```

Manual DI: `DailyWeatherApplication.container` (`AppContainer`, lazy singletons); the Application also ensures the morning job is scheduled on every start. ViewModels use `viewModelFactory { initializer { … APPLICATION_KEY … } }`. `HomeViewModel` is created once in `DailyWeatherApp` (activity scope) and shared by Today, 7 days and Settings.

| Path (`app/src/main/java/com/ah0890/dailyweather/`) | Purpose |
| --- | --- |
| `MainActivity.kt` | Collects preferences → `LocalUserPreferences`; resolves theme (System/Light/Dark) and status-bar style |
| `AppContainer.kt` | Location provider/resolver, `WeatherRepository` (Open-Meteo + file cache), `LocationRepository` (DataStore `saved_location`), `PreferencesRepository` (DataStore `user_preferences`), `NotificationScheduler`, `DailySummaryNotifier` |
| `domain/model/` | `WeatherForecast` (+`today`, `upcomingHours()`, `hoursOn()`, `advancedTo(now)`), `CurrentWeather`, `HourlyForecast`, `DailyForecast` (metric, location-local times), `WeatherCondition`, `Coordinates` (`approximate()` = 2 decimals), `LocationData`, `SavedLocation`, `WeatherResult` (Success/Stale/Failure + `WeatherError`), `RainInformation`, `WeatherRecommendation`/`UmbrellaAdvice`/`TodayOutlook`, `Commute*`, `UserPreferences` |
| `domain/model/Travel.kt` | `TransportMode`, `WeatherHazard` (most serious first), `Impact`, `TravelRating`, `TravelTip`, `LegAdvice`, `OfficeWeatherScore`/`ScoreBand`, `ChecklistEntry`, `OfficeDay` |
| `domain/usecase/` (office) | `HazardAnalysis` (thresholds: rain, heavy rain, thunder, wintry, wind + gusts, visibility, heat, UV), `TravelAdvisor` (hazard × transport impact table → rating + tips), `OfficeScore` (100 − rain×0.3 − hazard penalties; ≥75 Good, ≥45 Moderate), `TravelChecklist`, `OfficeDayPlanner` (builds `OfficeDay`), `CommuteAnalysis.workdayHours` |
| `domain/usecase/` | `RainAnalysis` (≥40% likely; periods merge across 1 dry hour; ≥60% or raining → umbrella Recommended, ≥30% or ≥1 mm → Consider; rest of today, ≥6 h; `summary()` → `RainSummary`), `CommuteAnalysis` (departure hour + next; windy ≥39 km/h; after evening trip → tomorrow) |
| `data/remote/openmeteo/` | `OpenMeteoApi` (one `v1/forecast` call incl. hourly `apparent_temperature`), nullable DTOs, `OpenMeteoMapper` (validates, skips bad rows, WMO → condition), `OpenMeteoDataSource.create()` (timeouts, no logging) |
| `data/repository/DefaultWeatherRepository.kt` | Caches each success; failure → `Stale(cached, error)` or `Failure`; `cachedForecast()`; exception → `WeatherError` |
| `data/local/` | `FileWeatherCache` (versioned JSON, atomic move, ~1 km match), `DataStoreLocationRepository`, `DataStorePreferencesRepository` (units, theme, commute times, transport, workdays, morning + evening notification on/time/days as bit masks, prompt dismissed) |
| `location/` | `AndroidLocationProvider` (one fix: fused/network 10 s → GPS 20 s on 12+ → last-known ≤3 h; invalid coords → unavailable), `GeocoderPlaceNameResolver` |
| `notifications/` | `NotificationKind` (MorningSummary → work `daily_weather_summary`, EveningCommute → `evening_commute_alert`), `NotificationTiming` + `sync`/`syncAll` (prefs → schedule/cancel), `NotificationSchedule` (pure, DST-safe `nextRun`/`previousRun` on selected days, `isTooLate` > 3 h), `DailySummary` (+ commute legs) / `EveningAlert` (posted only if `worthNotifying`), `DailySummaryWork(kind)` (testable; never throws; retry ×3, stale ≤6 h), `DailyWeatherWorker` (kind in input data, missing = morning; re-queues with APPEND_OR_REPLACE), `WorkManagerNotificationScheduler` (KEEP on app start, REPLACE on settings change), `TimeChangeReceiver` (TIMEZONE_CHANGED/TIME_SET → REPLACE), `AndroidDailySummaryNotifier` (channels `daily_summary` id 1, `commute_alerts` id 2) |
| `presentation/home/` | `HomeRoute`/`HomeScreen` (pull-to-refresh), `HomeViewModel` (location + weather), `LocationUiState`, `WeatherUiState`, `CurrentWeatherSection` (crossfade: hero → stale notice → `OfficeDayCard` → `TravelChecklistCard` → `TodayOutlookCard` → hourly → details), `NotificationPromptCard` |
| `presentation/forecast/` | 7-day `DayCard`s with range bars, commute indicator on office days (`WorkWeekAnalysis`), `WeekPlanCard` (best/highest-risk day), expandable details + that day's hourly row; `DayLabel`, `TemperatureSpan` |
| `presentation/settings/` | Settings (location; notifications: morning + evening switch/time/days; office schedule: office days, departures, transport; °C/°F, theme, privacy, Open-Meteo link, version), `DaysRow` (presets + 7 day toggles), `SettingsViewModel`, `PrivacyScreen` |
| `presentation/weather/` | Shared: `TravelUi` (emoji/labels for transport, hazards, tips, score, checklist; plain res ids so notifications/widget can reuse), `ConditionUi` (mood/emoji/label; night icons never show a sun), `WeatherFormatting` (use `temperatureText()` in UI for the user's unit), `HourlyForecastRow`, `WeatherErrorCard`, `StaleForecastNotice` |
| `presentation/components/` | `RelativeAge`, `LocalUserPreferences`, `SystemIntents` (permissions, settings/URL intents, notification helpers) |
| `ui/theme/` | Colours, typography, shapes, `WeatherBackdrop`, `LocalDarkTheme`, `ColorScheme.cardContainer` |

Tests (`app/src/test/...`): **272 tests**, all passing; lint clean. Helpers in `testing/`: `SampleForecasts` (+`hour()` builder; its 10 Oct 2026 is a **Saturday**, so set days/workdays explicitly), `FakeNotificationScheduler`, `FakeWeatherRepository`, `MainDispatcherRule`, `InMemoryPreferencesDataStore` (file DataStore can't overwrite on Windows JVMs). Create ViewModels *after* `MainDispatcherRule` is active (use `by lazy` for fields).

## 7. Key behaviour & decisions

- Permissions: `ACCESS_COARSE_LOCATION` (foreground), `INTERNET`, `POST_NOTIFICATIONS` (asked after an explanation), plus WorkManager's boot/wake-lock/network-state; `FOREGROUND_SERVICE` removed. No precise/background location. HTTPS only; backups disabled.
- Location: explain → dialog on tap; denied → Try again / Open settings; saved location shown instantly; auto-refresh only if none saved or > 6 h old; Update button in header and Settings.
- Weather: fetched on location change, pull-to-refresh, or return after 30 min; cache shown first (fresh cache → no network). Offline/failure → last forecast + "Last updated…" notice. Forecasts are always moved forward to "now" at the location.
- Morning summary: default ON at 07:00 Mon–Fri (device time), arrives *around* that time (no exact alarms), uses the saved location, skipped if no data or > 3 h late. Evening commute alert: default ON at 15:30 Mon–Fri, posted only when the trip home needs an umbrella/care/a change of plan.
- Office days default Mon–Fri; the Office day card shows today until the evening trip is over, then the next office day.
- Coordinates rounded to ~1 km before sending or storing; never logged.

## 8. History and status

Milestones (commit): foundation `1e590c7` · README `878384a` · domain + API `cab5bb3` · repository `11db3c0` · location permission `ca2215a` · saved location `5807709` · context notes `c2611b1` · current weather `a7eeeb5` · hourly `1fa4209` · 7-day `ab4f55d` · rain/umbrella `40fff9c` · commute `c61de46` · offline cache `600339d` · settings `cca313c` · notification `34839dd` · accessibility `a3ed22c`, `0e3a14e` · hardening `861b02f` · release prep `81249ec` · final README `2216528` · night icons `3bc276d` · 1.0.1 (header fix, version bump, this file).

Verified on the emulator (2026-10-11): Today, 7-day + expanded day, notification prompt → permission, scheduled job (+9 h to 07:00), forced morning run posted the notification and queued the next day, signed release build runs (location, download, navigation).
**Upgrade to "Personal Weather & Office Commute Assistant"** (started 2026-10-12; one commit per milestone, each pushed):
1. Office commute experience: Office day card, transport mode, hazards, score, checklist; hourly gusts/visibility/UV added to the API call and cache.
2. Office days, notification days for the morning summary and the new evening commute alert, time-zone receiver, 7-day commute indicators + best/highest-risk day.
3. (next) Multiple saved locations with Home/Office roles.
4. Severe-weather alerts, rain soon, fog/heat · 5. Air quality · 6. Pollen · 7. UV alerts · 8. Widget · 9. Wear OS · 10. Polish.

Not yet verified on a device: offline/airplane mode, °F and Dark theme switching, reboot persistence, Privacy page in the release build.

## 9. Ideas / open items

- Owner to-dos: install the APK on the phone, back up the keystore + `keystore.properties`, add screenshots to `docs/screenshots/`, choose a licence (README says "all rights reserved").
- Possible next features: multiple saved locations, home-screen widget, severe-weather alerts, air quality/pollen, UV alerts, custom notification days, Wear OS.
- GitHub "Contributors" may briefly show a cached "claude" entry from rewritten early history; the API already lists only `ah0890`.
