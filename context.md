# Project Context — Daily Weather Companion

Quick-start context for continuing development. Read this first; open source files only when needed.
Last updated: 2026-10-11 (after the current-weather milestone).

## 1. What this is

Personal-use Android weather app answering: *"What should I expect today?"* and *"Do I need an umbrella / should I ride the motorcycle?"*
Priorities: today's weather → rain probability → rain timing → temperature → morning/evening conditions → 7-day → recommendation.
Design: calm, premium, readable in sunlight, light + dark. Not a data dump.

- Repo: <https://github.com/ah0890/daily-weather-companion> (branch `main`)
- Package / applicationId: `com.ah0890.dailyweather`
- Weather API: **Open-Meteo** (free, no API key). Place names: Android `Geocoder`.

## 2. Working rules (must follow)

- **Never run** `git add/commit/push/pull/merge/rebase/stash/reset/checkout/clean`. Never change git user config. Read-only git (`status`, `log`, `diff`, `branch`, `remote -v`) is fine.
- **No AI attribution** anywhere (no `Co-Authored-By: Claude` in suggested commit messages).
- One milestone at a time. After each: build → tests → lint → security/secret scan → `git status` → report → **stop and wait for the user to reply `DONE`**.
- Report format: `MILESTONE COMPLETED` (Milestone, What I implemented, Files created/modified, Testing, Security check, Git status) → `YOUR MANUAL GIT STEPS` (explicit per-file `git add` grouped by purpose, exact conventional commit message, `git push origin main`) → `WAIT FOR ME`.
- Before starting a milestone, check `git status`; if uncommitted work exists, stop and report.
- Never commit secrets; `local.properties` is git-ignored. Don't put real personal locations in tests (use London/Paris/Sydney).

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

Manual DI: `DailyWeatherApplication.container` (`AppContainer`, lazy singletons). ViewModels use `viewModelFactory { initializer { … APPLICATION_KEY … } }`.

| Path (`app/src/main/java/com/ah0890/dailyweather/`) | Purpose |
| --- | --- |
| `MainActivity.kt` | Edge-to-edge, `DailyWeatherTheme { DailyWeatherApp() }` |
| `AppContainer.kt` | Wires `LocationProvider`, `PlaceNameResolver`, `WeatherRepository` (Open-Meteo), `LocationRepository` (DataStore `saved_location`) |
| `domain/model/` | `WeatherForecast`, `CurrentWeather`, `HourlyForecast`, `DailyForecast` (metric units, location-local `LocalDateTime`), `WeatherCondition` (+`isPrecipitation`), `Coordinates` (validated; `approximate()` = 2 decimals), `LocationData`, `SavedLocation`, `WeatherResult` (`Success(forecast, fetchedAt)` / `Failure(WeatherError)`) |
| `domain/repository/` | `WeatherRepository.getForecast(coords): WeatherResult` (never throws), `LocationRepository` (`savedLocation: Flow`, `save()`) |
| `data/remote/openmeteo/` | `OpenMeteoApi` (single `v1/forecast` call: current + 7-day hourly + daily, `timezone=auto`), nullable DTOs, `OpenMeteoMapper` (validates, skips bad rows, WMO→condition), `OpenMeteoDataSource.create()` (timeouts, no logging) |
| `data/repository/DefaultWeatherRepository.kt` | Maps exceptions → `WeatherError` (NoConnection, Timeout, RateLimited, ServiceUnavailable, InvalidData, Unexpected); rethrows cancellation |
| `data/local/DataStoreLocationRepository.kt` | Stores rounded lat/lon, name (≤100 chars), saved time; corrupt → null |
| `location/` | `AndroidLocationProvider` (one-shot fix: fused/network 10 s → GPS 20 s on API 31+ → last-known ≤3 h), `GeocoderPlaceNameResolver` (rounded coords, 10 s timeout) |
| `presentation/home/` | `HomeRoute` (permission launcher, resume check, settings intents) + stateless `HomeScreen(state, weather, HomeActions)` in a `PullToRefreshBox`; `HomeViewModel` (location + weather); `LocationUiState(location, source Saved/Current, status)`; `WeatherUiState` (Idle/Loading/Loaded(+isRefreshing, refreshError)/Error); `CurrentWeatherSection` (hero card, details grid, loading/error/refresh-failed) |
| `presentation/components/` | `PlaceholderScreen`, `RelativeAge` ("updated 3 hours ago") |
| `presentation/weather/` | `ConditionUi` (condition → `WeatherMood`, emoji, label) and `WeatherFormatting` (°, km/h, compass, visibility, UV band) |
| `presentation/forecast/`, `presentation/settings/` | Placeholders for now |
| `ui/theme/` | Custom light/dark colours, typography, shapes; `WeatherBackdrop.forMood(WeatherMood, dark)` pastel/deep gradients with high-contrast content colour; `LocalDarkTheme` |

Tests (`app/src/test/...`): `testing/SampleForecasts` (domain fixture), `FakeWeatherRepository`, `MainDispatcherRule`, `InMemoryPreferencesDataStore` (file DataStore can't overwrite on Windows JVM — use in-memory for multi-write tests). JSON fixture: `test/resources/openmeteo/forecast_sample.json`. Currently **94 tests**, all passing; lint clean.

## 6. Key behaviour & decisions

- Permission: **ACCESS_COARSE_LOCATION only** + INTERNET. No fine/background location. `usesCleartextTraffic=false`, backups fully disabled (`data_extraction_rules.xml`).
- Location flow: explain card → system dialog only on tap. Denied once → "Try again"; permanent → "Open settings". Location services off → "Turn on location" (auto-retries on resume).
- Weather loads when the location's (rounded) coordinates change, on pull-to-refresh/retry, and on resume if older than 30 min — never polled. A failed refresh keeps the last forecast with a notice. Not yet persisted (offline cache is a later milestone).
- Saved location shown instantly on launch. Auto lookup only if none saved or saved > 6 h old (quiet; failure keeps saved). Manual refresh icon in header. Failures with a saved location show a small non-blocking notice.
- Coordinates rounded to ~1 km before sending to Open-Meteo, Geocoder, or storage. Coordinates are never logged.
- All user text in `res/values/strings.xml`; status text uses `liveRegion`; headings use `semantics { heading() }`.

## 7. Milestones

Done (commit):

1. Project foundation, theme, navigation (`1e590c7`) + README (`878384a`)
2. Domain models + Open-Meteo client (`cab5bb3`)
3. Weather repository + error categorization (`11db3c0`)
4. Location permission flow + one-shot lookup (`ca2215a`)
5. Saved location + manual update (`5807709`)
6. Current weather on Today screen (pending commit)

Next (in order, adapt if needed):

7. Hourly forecast timeline (rain % per hour)
8. 7-day forecast screen + day details
9. Rain timing + umbrella recommendation (wording: "recommended / consider / probably not needed")
10. Daily commute summary (morning/evening)
11. Offline cache (Room or DataStore) + "Last updated" / stale banner
12. Daily ~7:00 notification (WorkManager, no exact alarms, POST_NOTIFICATIONS on 13+)
13. Settings (notification on/off + time, °C/°F, theme, location, about, privacy)
14. Accessibility pass, error-handling polish, UI polish, docs, final QA

## 8. Open items / notes

- GitHub "Contributors" sidebar may still show a cached "claude" entry from early history (trailers were removed via `filter-branch`; API already shows only `ah0890`). It refreshes on its own.
- README documents API, permissions, saved location, security, setup — keep it updated per milestone.
