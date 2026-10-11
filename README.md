# Daily Weather Companion

A calm, location-aware Android weather app built around one question: **what should I expect today, and do I need an umbrella?**

> **Status:** early development. The project foundation (build, theme, navigation) is in place; weather features are being added milestone by milestone.

## Overview

Daily Weather Companion focuses on daily decisions rather than raw data:

- Is rain expected during the day, and when?
- Should I take an umbrella, or travel differently to work?
- What does the rest of the week look like?

Secondary information (humidity, wind, UV, visibility) stays available but visually subordinate.

## Features

| Feature | Status |
| --- | --- |
| Compose + Material 3 foundation, custom light/dark theme | ✅ Done |
| Bottom navigation: Today · 7 days · Settings | ✅ Done |
| Location permission flow and one-shot current location | ✅ Done |
| Saved location with manual update | ✅ Done |
| Current weather with details, pull-to-refresh and error states | ✅ Done |
| Hourly forecast timeline (next 24 h, rain chance, amount, wind) | ✅ Done |
| "Today's outlook": rain timing and umbrella recommendation | ✅ Done |
| "Plan your day": morning/evening commute summary | ✅ Done |
| 7-day forecast with expandable day details and hour-by-hour rain | ✅ Done |
| Offline cache of the last forecast with "last updated" notice | ✅ Done |
| Daily ~7:00 AM weather notification (WorkManager) | ✅ Done |
| Settings: location, commute times, °C/°F, System/Light/Dark theme, privacy, about | ✅ Done |

## Screenshots

_Screenshots will be added once the main screens are implemented._

## Technology Stack

- **Kotlin** with **Jetpack Compose** and **Material 3**
- **Navigation Compose** with type-safe (serializable) routes
- **Kotlin Coroutines / Flow** for asynchronous work and observable state
- **Retrofit/OkHttp + kotlinx.serialization** for networking
- **DataStore** for the saved location; a JSON file (kotlinx.serialization) for the forecast cache
- **WorkManager** for the daily notification
- **Gradle Kotlin DSL** with a version catalog (`gradle/libs.versions.toml`)

| Tool | Version |
| --- | --- |
| Android Gradle Plugin | 9.4.1 |
| Gradle | 9.8.1 |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 (Android 8.0) |
| JDK | 17+ (built with 21) |

## Architecture

The app follows MVVM with a layered, clean-architecture-inspired structure. The UI never talks to the weather API directly:

```text
UI (Compose screens)
   ↓
ViewModel (StateFlow UI state)
   ↓
Repository (domain interface)
   ↓
Remote data source (weather API)  +  Local data source (cache, saved location)
```

Package layout (`com.ah0890.dailyweather`):

```text
presentation/   Compose screens and ViewModels (home, forecast, settings, navigation, components)
domain/         Pure Kotlin models and repository interfaces (use cases upcoming)
data/           Open-Meteo API client, weather repository, offline forecast cache and saved-location storage
location/       One-shot location lookup and place-name resolution
notifications/  Morning summary: scheduling rules, WorkManager job and notification
ui/theme/       Colours, typography, shapes and weather backdrops
```

Dependencies are wired with a small manual container rather than a DI framework — the app is small enough that Hilt/Dagger would add more build complexity than it saves.

## Weather API

The planned provider is **[Open-Meteo](https://open-meteo.com/)**:

- Free for non-commercial use, **no API key required**
- Coordinates-based hourly and daily forecasts over HTTPS
- Hourly **precipitation probability** and **precipitation amount**, temperature, apparent temperature, wind, UV index, visibility, sunrise/sunset and WMO weather codes
- Good documentation and generous rate limits (~10,000 calls/day for non-commercial use)

Place names are resolved with Android's on-device `Geocoder`, so no separate geocoding service is needed.

The data layer sits behind a repository interface, so the provider can be swapped later without touching the UI.

## Rain Timing and Umbrella Advice

Rain advice is computed from Open-Meteo's **hourly precipitation probability** (not guessed from daily totals), over the rest of the current day (at least the next 6 hours):

| Rule | Value |
| --- | --- |
| An hour counts as "rain likely" | ≥ 40 % |
| Rainy hours separated by a single drier hour | merged into one period |
| Umbrella **recommended** | peak chance ≥ 60 %, or rain falling now |
| **Consider** an umbrella | peak chance ≥ 30 %, or ≥ 1 mm expected |
| **Probably not needed** | otherwise |

**Commute ("Plan your day")** looks at the departure hour and the hour after it for the morning (default 8:00) and evening (default 17:00) trips, using the same rain thresholds, plus a warning for sustained wind ≥ 39 km/h — relevant when riding a motorcycle. After the evening trip, tomorrow's commute is shown.

The wording is deliberately non-absolute ("recommended", "consider", "probably not needed"). The logic lives in `domain/usecase/RainAnalysis.kt` and `CommuteAnalysis.kt` and is unit-tested.

## Offline Behaviour

The most recent successful forecast is saved in a small JSON file in app-private storage (`files/weather_cache.json`). A single record doesn't justify a database, so Room is not used; writes go to a temporary file that is atomically moved into place.

- On launch the cached forecast is shown instantly. If it is less than 30 minutes old, **no network request is made**.
- If the network or the API fails, the cached forecast is shown with *"Weather couldn't be updated. Showing the last available forecast."* and *"Last updated: Today, 6:45 AM"*.
- A forecast older than an hour is marked *"This forecast may be out of date."*
- When the app is opened hours or days later, "now", the hourly timeline, today's outlook and the commute are moved forward to the real current time using the cached hourly data, so past hours never appear as upcoming.
- The cache belongs to one location (~1 km cell); a different location never shows another place's weather. Like all app data it is excluded from backups.

## Location Permissions

The app requests **approximate (coarse) foreground location only** — city-level accuracy is all a forecast needs. Precise and background location are never requested.

How it works today:

- The Today screen first explains why location is needed; the system dialog appears only after the user taps **Allow location access**.
- If the user declines once, a gentle **Try again** is offered. If Android will no longer show the dialog, the app explains how to enable it and offers **Open settings** — it never re-prompts on its own.
- Returning from system settings is detected automatically.
- With permission, the app asks the platform `LocationManager` for **one** fix (fused/network first, GPS as a fallback on Android 12+) and then stops listening. A recent last-known location is used if no fresh fix arrives in time.
- If location services are switched off, the app says so and links to the device's location settings.
- The place name is looked up with Android's `Geocoder` using coordinates rounded to ~1 km. On most devices the geocoder is provided by Google Play services and may contact Google's servers.

### Saved location

The last successfully found location is saved on the device in a private
[DataStore](https://developer.android.com/topic/libraries/architecture/datastore) file containing only:

| Field | Example |
| --- | --- |
| Latitude / longitude, rounded to 2 decimals (~1 km) | `51.51`, `-0.13` |
| Place name | `Greater London` |
| When it was saved | `2026-10-10T06:45Z` |

- On launch the saved location is shown immediately — the user doesn't have to enable location every time.
- A new one-shot lookup happens only if nothing is saved, if the saved location is more than 6 hours old (silently, keeping the saved one if it fails), or when the user taps **Update location**.
- The header always says which location is in use: **Saved location** or **Current location**, with how long ago it was updated.
- If permission is later revoked or GPS is unavailable, the saved location keeps working and a small note explains why it wasn't refreshed.
- The saved location is excluded from cloud backup and device transfer.

Planned: use the saved location for the daily notification instead of tracking location in the background.

## Settings and Privacy

Settings are stored in a private DataStore file (`user_preferences`): temperature unit, theme and commute departure times. Forecasts are always kept in °C, so switching to °F is instant and needs no download. The in-app theme also drives the status-bar icon colours.

The **Privacy** page (Settings → Privacy) explains in plain language that the app uses approximate location only, what is stored on the phone, that rounded coordinates go to Open-Meteo (which, like any website, sees the request's IP address) and to the phone's geocoder, and that there is no account, analytics or advertising. Settings → *Weather data by Open-Meteo.com* provides the attribution required by Open-Meteo's CC BY 4.0 licence.

## Notification System

A short **morning summary** (default **7:00**, configurable in Settings) shows the day's condition, high/low, peak rain chance, when rain is likely and the umbrella advice, e.g.:

> **🌦️ Today in London**
> Rain · 16° / 9° · Rain chance 78%
> Rain is most likely between 2 PM and 5 PM.
> ☂️ Umbrella recommended

How it is scheduled, and why:

- **WorkManager one-time job that re-schedules itself** after each run. The delay is recomputed from the calendar every day, so the summary stays at the same wall-clock time across time-zone and daylight-saving changes (a fixed 24-hour periodic job would drift).
- **No exact-alarm permission.** Android 12+ restricts exact alarms, and a daily summary doesn't need minute precision; Android may delay delivery slightly (Doze) to save battery. The summary arrives *around* the chosen time.
- **Survives restarts and reboots:** WorkManager persists the job; the app also re-checks the schedule on every start (without shifting it) and re-schedules immediately when the time or switch changes.
- **No background location:** the job uses the *saved* location and downloads a fresh forecast, falling back to an offline copy up to 6 hours old. If no usable data is available after a few retries, or the job runs more than 3 hours late (e.g. the phone was off), that day's summary is skipped rather than showing stale advice.
- **Android 13+ permission:** `POST_NOTIFICATIONS` is requested only after an explanation — a one-time card on the Today screen ("Turn on" / "Not now") or the Settings switch. If notifications are blocked, Settings says so and links to the system page.

## Security

- No API keys or secrets are required by the current weather provider.
- `local.properties`, keystores and `.env` files are git-ignored. If a keyed provider is ever added, its key will be read from `local.properties` into `BuildConfig` at build time and never committed.
- Network traffic uses HTTPS only.
- App data is excluded from cloud backup and device-to-device transfer (`data_extraction_rules.xml`).
- Only the permissions a feature actually needs are declared.

## Setup Instructions

1. Install a recent **Android Studio** (or the Android SDK command-line tools) and **JDK 17+**.
2. Clone the repository:

   ```bash
   git clone https://github.com/ah0890/daily-weather-companion.git
   cd daily-weather-companion
   ```

3. Open the project in Android Studio. It creates `local.properties` with your SDK path automatically. From the command line, create it yourself:

   ```properties
   sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk
   ```

### API Key Configuration

None needed — Open-Meteo is keyless. `local.properties` is reserved for any future secrets and is never committed.

## Building the Application

```bash
./gradlew assembleDebug        # debug APK  → app/build/outputs/apk/debug/
./gradlew assembleRelease      # minified release APK (unsigned)
./gradlew installDebug         # install on a connected device/emulator
```

## Testing

```bash
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew lintDebug            # Android Lint
```

## Future Improvements

Multiple saved locations, home-screen widget, severe-weather alerts, air quality, pollen, UV alerts, custom notification schedules and Wear OS support.

## License

No license has been chosen yet; all rights reserved by the author until one is added.
