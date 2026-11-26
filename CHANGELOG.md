# Changelog

## 1.3.0
* **Aurora redesign to the supplied reference**: coral-to-accent gradient battery ring, cyan-to-violet speed arc,
  neon magenta/violet road boundaries with a ground ring, bracketed AVG / TRIP / ODO, translucent date-time-temperature bar.
* **Drive-mode colours**: Eco green/turquoise, Normal cyan/electric blue, Sport magenta/violet. Red (coral) is no longer a
  mode accent; it appears only for low battery, warnings and critical alerts. Saved old accent defaults migrate.
* **Music widget** (lower left): reads the active Android MediaSession of any player (Spotify, YouTube Music, local
  players): artwork, title, artist, progress, elapsed/total, source badge, previous / play-pause / next, and shuffle when
  the player exposes it. Needs notification access; shows a clear message instead of fake data when it is missing.
* **Reverse camera**: in R the central car visualisation is replaced by a square rounded camera tile (Camera2 or an
  MJPEG stream, with a clear fallback message). The factory BYD reverse / 360 camera is not reachable by ordinary apps.

## 1.1.0
New, inspired by ideas in two other open projects (no code was copied; both use restrictive licences):
* **Trip recorder** (idea from byd-trip-stats): trips start when the car moves and end after a configurable
  stand-still time; distance from speed, energy estimated from state-of-charge change; minimum-distance filter;
  simulated trips are excluded by default and always labelled.
* **Trips screen**: history, six sort keys with ascending/descending, minimum-distance and simulated filters,
  per-trip detail with speed chart, delete, CSV and JSON export.
* **Range projection** widget and layout: remaining range from your own consumption over the last 10, 100 or
  500 km of recorded trips, compared with the car's estimate. Shows `--` until enough history exists.
* **Stuck-speed guard** (idea from H9-cluster's speed-source notes): a speed that reads 0 for 60 s while the
  odometer advances is treated as unavailable instead of being shown.
* **Speedometer calibration** (idea from H9-cluster): real speed, +percent or +km/h for the display only.
* **More skins** (idea from H9-cluster): analogue needle-dial gauge, "Sport - analogue dial" and
  "Trip - range projection" presets, a third dark and light palette set already included.
* Hide the gear indicator, "factory panel only" mode (draws nothing), exit-app button.
* GitHub Actions workflow that runs the tests and builds a debug APK.

## 1.0.0
Initial release: cluster renderer, designer, presets, settings, REAL/SIM/UNAVAILABLE data labelling, simulator.
