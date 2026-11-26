# Architecture

MVVM with unidirectional data flow and a hand-written dependency container.

```
UI (Compose)  ──reads StateFlow──▶  ClusterViewModel  ──▶  Repositories / stores
 ClusterCanvas                       settings, draft layout       SettingsStore   (JSON, SharedPreferences)
 DesignerScreen                      vehicle state + status       LayoutRepository(JSON, SharedPreferences)
 SettingsScreen                      simulator inputs             VehicleRepository
                                                                    ├─ BydAutoSource  (reflection, read-only)
                                                                    └─ SimEngine      (pure Kotlin)
```

* **Data model**: `Sig<T>(value, src)` with `Source { REAL, SIM, UNAVAILABLE }`. `VehicleState` is one immutable
  snapshot. `VehicleState.unavailable()` is the starting point for real reads, so any field a source cannot fill
  stays UNAVAILABLE.
* **Layout model**: `ClusterConfig` holds 13 `WidgetSpec`s with x, y, w, h as fractions (0..1) of the canvas, plus
  style (gauge, font, background, glow, digit scale, palettes, accent). `clamped()` keeps widgets on the canvas
  and above a minimum size. Built-in layouts are read-only; saving one creates a user copy.
* **Persistence**: `AppSettings` is one JSON object, layouts are a JSON array. Parsing tolerates unknown enum
  values, missing keys and out-of-range numbers. Export bundles both under `format: songplus-cluster-config`.
* **Vehicle polling**: `VehicleRepository.start()` runs one coroutine loop at the configured rate (1–30 Hz) while
  the Activity is started; `stop()` is called from `onStop`. Reads happen off the main thread, and every call
  is wrapped so a missing class, permission error or exception only marks that device.
* **Rendering**: `ClusterCanvas` lays widgets out with fractions of its own size and scales fonts and strokes by
  `min(height / 720, width / 1920)`. The designer reuses it with an overlay for drag and resize handles.
* **Lifecycle**: settings changes apply live (orientation, keep-screen-on, brightness, locale through
  `AppCompatDelegate.setApplicationLocales`, system bars). The ViewModel survives configuration changes.
* **Error handling**: JSON import returns `ImportResult.Failed` with a message; storage failures fall back to
  defaults; reflection failures become diagnostics.

## Extending

* New data source: implement a class that returns `VehicleState` with REAL or UNAVAILABLE fields and add it to
  `VehicleRepository`.
* New widget: add a `WidgetId`, a default `WidgetSpec` in every built-in layout, a composable in `Widgets.kt`, and a
  name string.
