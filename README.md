# Daily Weather Companion

A calm, location-aware Android weather app built around one question: **what does today's weather mean for my workday?**

> **Version 1.0.1**, being extended into a personal weather and office-commute assistant. Covered by 329 unit tests. Built for personal daily use.

## Overview

Daily Weather Companion focuses on daily decisions rather than raw data:

- Will it rain on the way to the office, or on the way home?
- Should I ride the motorcycle today, take the car, or carry an umbrella?
- Is it unusually hot, windy or foggy for the commute?
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
| "Office day": morning (Home → Office) and evening (Office → Home) commute with advice for your transport | ✅ Done |
| Office Weather Score (0–100) and "Today's travel checklist" | ✅ Done |
| My transportation: motorcycle, car, public transport, walking, other | ✅ Done |
| 7-day forecast with expandable day details and hour-by-hour rain | ✅ Done |
| Offline cache of the last forecast with "last updated" notice | ✅ Done |
| Morning summary and evening commute alert with per-day schedules (WorkManager) | ✅ Done |
| Office days; 7-day commute indicators with best and highest-risk commute day | ✅ Done |
| Multiple saved places (Home, Office, Parents, Other) with commute home/office and a default place | ✅ Done |
| Severe-weather alerts, "rain soon", poor-visibility and heat advisories | ✅ Done |
| Air quality (US AQI category, main pollutant, pollutant details) | ✅ Done |
| Pollen (tree, grass, weed) where the provider has data (Europe only) | ✅ Done, limited by data |
| Settings: location, office days, commute times, transport, notification days, °C/°F, System/Light/Dark theme, privacy, about | ✅ Done |

## Screenshots

| Today | 7 days | Settings |
| --- | --- | --- |
| _add `docs/screenshots/today.png`_ | _add `docs/screenshots/forecast.png`_ | _add `docs/screenshots/settings.png`_ |

Capture them on a device or emulator (`adb exec-out screencap -p > today.png`) and place them in `docs/screenshots/`.

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
domain/         Pure Kotlin models, repository interfaces and use cases (rain analysis, commute)
data/           Open-Meteo API client, weather repository, offline forecast cache and saved-location storage
location/       One-shot location lookup and place-name resolution
notifications/  Morning summary: scheduling rules, WorkManager job and notification
ui/theme/       Colours, typography, shapes and weather backdrops
```

Dependencies are wired with a small manual container rather than a DI framework — the app is small enough that Hilt/Dagger would add more build complexity than it saves.

## Weather API

The app uses **[Open-Meteo](https://open-meteo.com/)**, chosen over keyed services (OpenWeatherMap, WeatherAPI, Tomorrow.io) because it offers everything the app needs without an account:

- Free for non-commercial use, **no API key required**
- Coordinates-based hourly and daily forecasts over HTTPS
- Hourly **precipitation probability** and **precipitation amount**, temperature, apparent temperature, wind and gusts, UV index, visibility, sunrise/sunset and WMO weather codes
- Good documentation and generous rate limits (~10,000 calls/day for non-commercial use)

One request returns current conditions, 7 days of hourly data and 7 daily summaries, and the app requests it only when the location changes, on pull-to-refresh, on return after 30 minutes, and once each morning — far below the free limits. Data is attributed in Settings as required by Open-Meteo's CC BY 4.0 licence.

Place names are resolved with Android's `Geocoder`, so no separate geocoding service is needed.

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

The wording is deliberately non-absolute ("recommended", "consider", "probably not needed"). The logic lives in `domain/usecase/RainAnalysis.kt` and is unit-tested.

## Office Day and Commute Advice

The **Office day** card looks at the departure hour and the hour after it for the morning (Home → Office, default 8:00) and evening (Office → Home, default 17:00) trips. After the evening trip, tomorrow's office day is shown. For each trip the app detects hazards with fixed thresholds (`domain/usecase/HazardAnalysis.kt`):

| Hazard | Threshold |
| --- | --- |
| Rain possible / likely | hourly chance ≥ 30 % / ≥ 60 % |
| Heavy rain | ≥ 4 mm in an hour (Met Office definition) |
| Thunderstorm, snow or ice | WMO weather code |
| Strong wind / gale | sustained ≥ 39 / 62 km/h, or gusts ≥ 60 / 85 km/h |
| Poor visibility / dense fog | < 1 km / < 200 m (or a fog code) |
| Very hot / extreme heat | feels like ≥ 35 °C / ≥ 41 °C |
| High UV | UV index ≥ 6 in daylight |

**My transportation** decides how much each hazard matters (`TravelAdvisor.kt`): a motorcyclist is warned about rain, wind, visibility and storms; a driver mainly about heavy rain, fog and severe weather; someone walking about rain, heat, UV and wind. Each trip gets a cautious verdict ("Bike conditions look favourable", "Ride with extra care", "Riding conditions may be difficult") plus tips such as *Pack rain gear*, *Consider the car or public transport* or *Allow extra travel time*.

The **Office Weather Score** starts at 100 and subtracts 0.3 points per percent of rain chance on each trip and fixed penalties for hazards (full on a trip, half between trips). 75+ is *Good*, 45–74 *Moderate*, below 45 *Poor*. It summarises the forecast and is not a safety rating.

**Office days** (default Monday–Friday) decide which day the card shows: on a day off, or after the evening trip, it shows the next office day ("Office day · Monday"). On the **7 days** screen every office day gets a small indicator (🟢 Good commute, 🟡 Carry an umbrella / Take care, 🔴 High rain risk / Difficult commute), and a *Plan your week* card names the best and highest-risk commute days when they differ, with a reminder that later days are less certain.

**Saved places.** In Settings you can add up to 10 places, by searching for a town (Open-Meteo's free geocoding API) or with a one-shot "use my current location", give each a name and type, edit or delete it, and mark one as **commute home**, one as **commute office** and one as **shown first**. The Today header becomes a location picker (current location or any saved place). With home and office saved, the morning trip uses the forecast at home and the evening trip and working day the forecast at the office; the route shows your own names ("Home → Office"). Only the place on screen, home and office are ever requested; the offline cache keeps a forecast for each of the last 6 places.

**Today's travel checklist** lists only what is relevant: umbrella or rain gear, "consider the car for the return trip" (motorcycle), jacket (feels like ≤ 15 °C, 3 °C colder when riding), water (feels like ≥ 32 °C), sunglasses (bright daytime trip) and sun protection (UV ≥ 6).

## Weather Alerts

The Today screen checks the next 24 hours of the hourly forecast against the hazard thresholds above, hour by hour, and groups consecutive hours into alerts:

- **Severe** (red card, separate notification): thunderstorms, heavy rain, gales, dense fog, extreme heat, snow or ice, e.g. *"⛈️ Thunderstorms possible during your evening commute."*
- **Worth knowing** (calm card): poor visibility (*"🌫️ Poor visibility possible during your morning commute."*) and very hot weather (*"🌡️ Very hot (feels like 37°) between 1 PM and 4 PM."* plus *"Carry water if you'll be outdoors."*).

Alerts are only raised when forecast data meets a threshold, use cautious wording, and say they are not official warnings. The **Severe weather alerts** switch in Settings controls the notification; it is checked during the scheduled morning and evening runs, so no extra background polling is added.

**Rain soon** is a one-line note from the next two hourly values (no minute-level claims): *"Rain may begin within the next hour"* (next hour ≥ 50 %), *"Some chance of rain in the next two hours"* (≥ 30 %), *"Rain may ease within the next hour"*, or *"No significant rain expected in the next two hours."*

## Air Quality

Open-Meteo also offers a free, keyless **air-quality API** (CAMS model data), so no new provider was needed. It is called with the same rounded coordinates, for the place on screen only, and kept in memory for 30 minutes.

The Today screen shows one simple line, e.g. **🟡 Air quality: Moderate** · US AQI 72 · Main pollutant: Fine particles (PM2.5), and *"May reach Unhealthy around 3 PM"* if a worse category is forecast within 24 hours. Tapping it reveals the pollutant concentrations (µg/m³) and sub-indices. Categories follow the US EPA bands (Good 0–50, Moderate 51–100, Unhealthy for sensitive groups 101–150, Unhealthy 151–200, Very unhealthy 201–300, Hazardous 301+). If the service can't be reached, has no data for the place, or a copy older than 3 hours would be needed, the card says **"Air quality unavailable"**; no value is ever guessed, and the forecast is unaffected.

### Pollen

The same air-quality request returns hourly pollen counts (alder, birch, olive → **tree**; **grass**; mugwort, ragweed → **weed**). The app takes the highest count per type over the next 24 hours and rates it with the US National Allergy Bureau scale (e.g. grass: Low < 5, Moderate 5–19, High 20–199, Very high ≥ 200 grains/m³), shown as *"🌿 Pollen: High (Grass pollen)"*.

**Limitation:** Open-Meteo's pollen data comes from the CAMS *European* model and is empty outside Europe (checked for Islamabad and New York, October 2026). There the app shows no pollen level and the details say *"Pollen forecasts aren't available for this region"*. Nothing is estimated. Pollen sits in the air-quality data layer, so a regional provider could be added behind `AirQualityRepository` later.

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

The daily notification uses this saved location, so the app never needs background location access.

## Settings and Privacy

Settings are stored in a private DataStore file (`user_preferences`): temperature unit, theme and commute departure times. Forecasts are always kept in °C, so switching to °F is instant and needs no download. The in-app theme also drives the status-bar icon colours.

The **Privacy** page (Settings → Privacy) explains in plain language that the app uses approximate location only, what is stored on the phone, that rounded coordinates go to Open-Meteo (which, like any website, sees the request's IP address) and to the phone's geocoder, and that there is no account, analytics or advertising. Settings → *Weather data by Open-Meteo.com* provides the attribution required by Open-Meteo's CC BY 4.0 licence.

## Notification System

Two notifications, each with its own switch, time and days (presets *Every day*, *Weekdays*, *Weekends*, or any custom selection):

**Morning summary** (default 7:00, Monday–Friday): the day's condition, high/low, peak rain chance and rain timing, and on office days the commute for your transport, e.g.:

> **🌦️ Today in London**
> Rain · 16° / 9° · Rain chance 78%
> Rain is most likely between 2 PM and 5 PM.
> Morning commute: rain 12% · 🟢 Bike conditions look favourable
> Return trip: rain 74% · 🔴 Riding conditions may be difficult
> 🚗 Consider the car or public transport · 🌧️ Pack rain gear

**Evening commute alert** (default 15:30, Monday–Friday): a heads-up about the trip home, posted **only when there is something to act on** (rain chance ≥ 30 %, a hazard, or a tip), e.g.:

> **🌧️ Office return · London**
> Rain chance 74%
> Rain is most likely between 4 PM and 6 PM.
> 🔴 Riding conditions may be difficult
> 🚗 Consider the car or public transport

How they are scheduled, and why:

- **WorkManager one-time jobs that re-schedule themselves** after each run (`daily_weather_summary`, `evening_commute_alert`). The next run is computed from the calendar on the selected days, so notifications stay at the same wall-clock time across daylight-saving changes (a fixed 24-hour periodic job would drift).
- **Time-zone and clock changes:** a receiver for `TIMEZONE_CHANGED` / `TIME_SET` re-schedules both, so 7:00 means 7:00 local time after travelling.
- **No exact-alarm permission.** Android 12+ restricts exact alarms, and these notifications don't need minute precision; Android may delay delivery slightly (Doze) to save battery. They arrive *around* the chosen time.
- **Survives restarts and reboots:** WorkManager persists the jobs; the app also re-checks the schedule on every start (without shifting it) and re-schedules immediately when a time, day or switch changes. Selecting no days is the same as switching off.
- **Late runs:** a run more than 3 hours after its most recent scheduled time (e.g. the phone was off, or Friday's job was delayed into Saturday) is skipped rather than showing stale advice. The evening alert is also skipped once the trip home is over.
- **No background location:** the jobs use the *saved* location and download a fresh forecast, falling back to an offline copy up to 6 hours old, with a few retries.
- **Android 13+ permission:** `POST_NOTIFICATIONS` is requested only after an explanation — a one-time card on the Today screen ("Turn on" / "Not now") or the Settings switch. If notifications are blocked, Settings says so and links to the system page.

## Security

- No API keys or secrets are required by the weather provider.
- `local.properties`, `keystore.properties`, keystores and `.env` files are git-ignored. If a keyed provider is ever added, its key should be read from `local.properties` into `BuildConfig` at build time and never committed.
- HTTPS only (`usesCleartextTraffic="false"`); no request logging, so coordinates never reach logcat.
- Coordinates are rounded to ~1 km before they are sent anywhere or stored.
- App data is excluded from cloud backup and device-to-device transfer (`data_extraction_rules.xml`).
- API responses are validated: malformed or partial data is rejected or skipped, never trusted blindly.

Permissions in the installed app (from the merged manifest):

| Permission | Why |
| --- | --- |
| `ACCESS_COARSE_LOCATION` | Approximate location for the local forecast (foreground only) |
| `INTERNET` | Downloading forecasts |
| `POST_NOTIFICATIONS` | The morning summary (Android 13+, asked after an explanation) |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE` | Added by WorkManager so the daily job survives reboots and runs reliably |

WorkManager's `FOREGROUND_SERVICE` permission is removed because the app has no foreground work.

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
./gradlew installDebug         # install on a connected device/emulator
./gradlew assembleRelease      # minified release APK → app/build/outputs/apk/release/
```

### Signing a release build

Release builds are unsigned unless a `keystore.properties` file exists in the project root. It is git-ignored; never commit it or the keystore.

```properties
storeFile=release.jks
storePassword=…
keyAlias=dailyweather
keyPassword=…
```

Create a keystore once with `keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias dailyweather` and keep a backup somewhere safe — losing it means you can't update the installed app.

## Testing

```bash
./gradlew testDebugUnitTest    # JVM unit tests (329)
./gradlew lintDebug            # Android Lint
```

The unit tests cover, among other things:

- **Parsing and validation** of Open-Meteo responses, including malformed, partial and out-of-range data, and the real HTTP stack against a local mock server (timeouts, 429, 5xx, invalid JSON, unreachable server).
- **Rain and umbrella rules** at 0 / 20 / 50 / 80 / 100 %, threshold boundaries, period merging and the rest-of-day window.
- **Commute, hazard thresholds, transport-specific advice, office score and checklist**, **offline cache** (corrupt files, format versions, location mismatch) and **stale-forecast handling**.
- **Air quality and pollen**: EPA categories, NAB pollen bands, no pollen data outside Europe, main pollutant, 24-hour peak, missing and malformed values, caching and offline fallback.
- **Alerts**: grouping, levels, commute overlap, 24-hour window, several hazards together, rain soon.
- **Saved places**: add, edit, delete, roles and default, persistence, corrupt data, place search against a mock server, and which forecasts are loaded.
- **Location and settings persistence**, permission and location state handling in the ViewModels.
- **Notification scheduling**: weekdays, weekends and custom days, no days, custom times, daylight-saving and time-zone changes, late runs and retries; morning and evening notification content; workday selection and the 7-day commute ratings.

### Manual test checklist

Things that need a real device or emulator:

1. First launch: explanation → permission dialog; deny once / twice; *Open settings*.
2. Weather loads for the location; pull to refresh; switch tabs (no extra download).
3. Airplane mode: cached forecast with *"Last updated …"*; cold start offline.
4. Settings: °C/°F and theme apply immediately; change commute and notification times.
5. Notification: set the time a few minutes ahead and lock the phone; check it arrives (Android may delay it by several minutes) and survives a reboot.

## Known Limitations

- The morning summary arrives *around* the chosen time; Android's battery optimisation can delay it, and some manufacturers' aggressive battery savers can block background work unless the app is exempted.
- Place names depend on the phone's geocoder; without one (or offline) the app shows "Current location" / "Saved location".
- The forecast covers 7 days; very old cached data can't be moved forward beyond that.

## Future Improvements

The architecture (repository interfaces, provider-independent domain models, pure use cases) is meant to make these straightforward: a home-screen widget, UV alerts, custom notification schedules and Wear OS support.

## License

No license has been chosen yet; all rights reserved by the author until one is added.
