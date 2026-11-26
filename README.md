# Song Plus Cluster

A configurable digital instrument-cluster app for the **BYD Song Plus EV** infotainment (Android), written in
Kotlin, Jetpack Compose and Material 3 with an MVVM structure. It is the Android version of the cluster design
prototyped earlier (twin rings, car view with live lights, Eco / Normal / Sport colours, light and dark themes).

> **Read this first: build and test status**
> The project was written and checked in an environment with no Android SDK and no access to Google Maven, so it
> has **not been built with Gradle, installed, or run on a device or on a BYD head unit**. What *was* verified:
> the data, settings, layout and simulation code compiles with `kotlinc` and passes 35 unit tests; every BYD
> class, method and constant used was checked against the stub jar from the DashCast project; every
> `R.string` / `R.drawable` reference resolves; all XML is well-formed. The Compose UI code was syntax-checked
> only. Expect to fix a few compile errors on the first Gradle sync and then test on hardware.

## What it does

* **Full-screen cluster**: speed (km/h or mph), battery % and range, power / regen bar, gear, drive mode,
  turn signals and hazards, low / high beam, parking brake, seat belt, door and trunk warnings, lock state,
  charging, tyre states, odometer and trip, central car view with tail, brake and indicator light layers.
* **Designer**: move and resize every widget by dragging, show / hide widgets, live preview in light or dark,
  gauge style (ring / bar / digital), typography, background, glow, digit size, palettes, accent source.
* **Presets**: three built-in layouts (read-only) plus your own: save, save as, rename, duplicate, delete,
  restore default, choose which one the cluster shows.
* **Themes**: light, dark, system, or automatic by time of day, with independent palettes for each; Eco, Normal
  and Sport accent colours are configurable.
* **Settings** (all persisted): General, Appearance, Instrument cluster, Vehicle integration, Advanced
  (import / export JSON, resets, debug overlay, version).
* **Honest data labelling**: every value carries a source (REAL, SIMULATED or UNAVAILABLE) and a badge is
  always visible on the cluster. Simulated data is never shown as real. See `docs/VEHICLE_SUPPORT.md`.
* **Trips and range** (v1.1): automatic trip recording, trip history with sorting, filtering and CSV / JSON
  export, and a range projection from your own recent consumption (10 / 100 / 500 km window).
* **Speedometer calibration**: show real speed, +percent or +km/h (display only, recordings stay real).
* **Analogue dial skin**, a stuck-speed guard, optional "factory panel only" mode, hide-gear option, exit button.
* English and French.

### Inspiration and licences
v1.1 features were designed after reading [angoikon/byd-trip-stats](https://github.com/angoikon/byd-trip-stats)
(trip detection, consumption-window range projection, trip sorting and export) and
[Arkasha18/H9-cluster](https://github.com/Arkasha18/H9-cluster) (skins, speed calibration modes, stuck-speed
detection, factory-panel mode). Those projects use BUSL-1.1 and PolyForm Noncommercial licences, so **no code,
assets or artwork were copied**; everything here is an independent implementation of the general ideas.
Features that depend on their private integrations (ADB authorisation, Telegram, MQTT, web server, cloud backup,
GWM/TBOX data) were not added.

## What it does not do

* It does **not** put the cluster on the car's physical instrument-cluster display. Android apps cannot draw there
  without privileged tooling. The DashCast project does that; this app only renders the UI on the screen it is
  running on. See `docs/VEHICLE_SUPPORT.md`.
* It never writes to the vehicle. It only reads.

## Requirements

* Android Studio Ladybug (2024.2) or newer, JDK 17, Android SDK 34.
* Device or emulator with Android 9+ (API 28). BYD DiLink 3.0 units run Android 10.

## Get an APK

I could not compile the project where it was written (no Android SDK or Maven access), so no APK is included.
Two ways to get one:

1. **GitHub Actions** (no local setup): push this folder to a GitHub repository. The workflow
   `.github/workflows/build-apk.yml` runs the unit tests, builds `app-debug.apk` and attaches it to the run as
   an artifact (Actions tab > latest run > Artifacts). The debug APK is signed with a debug key and installs fine.
2. **Android Studio / command line**: see Build below.

## Build

```bash
# Android Studio: File > Open > this folder, let Gradle sync, press Run.
# Command line (needs ANDROID_HOME / local.properties pointing at the SDK):
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
```

## Install on a BYD head unit

Only do this on your own car and at your own risk; options depend on your firmware.

1. Enable USB debugging or ADB over the network on the head unit (the method differs between DiLink versions).
2. `adb connect <head-unit-ip>:5555` (or use USB), then `adb install -r app-debug.apk`.
3. Launch **Song Plus Cluster**. In **Settings > Vehicle integration**:
   * *Connection status* tells you whether BYD classes exist on this system and whether data is REAL.
   * Tap **Request BYD permissions** to ask for the `BYDAUTO_*_COMMON` permissions. If the firmware denies
     them (some units do) the devices show PERMISSION DENIED and the app stays in labelled simulation or
     shows `--`, depending on the data mode.
4. Set **Data mode**: *Automatic* (real data if speed is readable, otherwise labelled simulation), *Simulation*,
   or *BYD only* (never simulates).

On a phone or emulator there are no BYD classes, so the app runs in labelled SIMULATION mode, which is the
easiest way to try the designer.

## Simulation

Settings > Vehicle integration > Simulator. A 60-second scripted drive loops by default; switch it off to set
target speed, gear, signals, brake, lights, doors, belt, lock, charging and tyre state by hand. Everything the
simulator produces is tagged SIMULATION on screen.

## Project layout

```
app/src/main/java/com/songplus/cluster/
  data/      models, units, settings (JSON in SharedPreferences), layouts + built-in presets
  vehicle/   BydAutoSource (reflection), SimEngine, VehicleRepository (REAL / SIM / UNAVAILABLE)
  ui/        theme, cluster renderer + widgets, designer, settings, shared controls
  ClusterViewModel.kt, ClusterApp.kt, MainActivity.kt
app/src/test/   unit tests (units, simulation, JSON persistence)
docs/          ARCHITECTURE.md, VEHICLE_SUPPORT.md
```

Dependencies: AndroidX core / appcompat / activity-compose / lifecycle, Compose BOM + Material 3, coroutines.
No DI framework, no navigation library, no image loader.
