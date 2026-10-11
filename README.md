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
| Hourly forecast with rain probability timeline | Planned |
| Rain timing and umbrella recommendation | Planned |
| Morning/evening commute summary | Planned |
| 7-day forecast with day details | Planned |
| Offline cache of the last successful forecast | Planned |
| Daily ~7:00 AM weather notification | Planned |
| Settings: notification time, units, theme, location | Planned |

## Screenshots

_Screenshots will be added once the main screens are implemented._

## Technology Stack

- **Kotlin** with **Jetpack Compose** and **Material 3**
- **Navigation Compose** with type-safe (serializable) routes
- **Kotlin Coroutines / Flow** for asynchronous work and observable state
- **Retrofit/OkHttp + kotlinx.serialization** for networking
- **DataStore** for the saved location; **Room** for the forecast cache _(upcoming)_
- **WorkManager** for the daily notification _(upcoming)_
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
data/           Open-Meteo API client, weather repository and saved-location storage
location/       One-shot location lookup and place-name resolution
notifications/  Daily notification scheduling and content                    (upcoming)
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

## Notification System

_(Planned.)_ A daily morning summary (default ~7:00 AM) will be scheduled with **WorkManager**. Android does not guarantee exact timing for battery-friendly background work, so the notification is delivered _around_ the chosen time without requesting the exact-alarm permission.

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
