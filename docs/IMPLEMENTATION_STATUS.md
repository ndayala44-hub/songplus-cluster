# Implementation status

What is implemented, what is covered by automated tests, and what specifically needs the BYD Song Plus EV to validate.

## 1. Cluster integration
- **Implemented:** `resizeableActivity="true"` + `configChanges` (orientation/size/density/uiMode) so the activity keeps
  rendering without a restart when moved to the cluster display or when the display reconfigures; a
  `DisplayManager.DisplayListener` tracks display add/remove; the fractional 1920×720 layout scales to any surface; the
  app runs standalone on the main display with no dependency on DashCast.
- **Tested (JVM):** layout scaling is deterministic from the fractional model.
- **Needs the car / instrumented device:** actual projection onto the cluster via DashCast, and physical
  add/remove/reconfigure of the second display.

## 2. Platform signing
- **Implemented & build-ready:** opt-in signing config (`app/build.gradle.kts`) activated by `-PPLATFORM_KEYSTORE`
  (local) or the `PLATFORM_KEYSTORE_B64` CI secret; no keys committed; package identity (`applicationId`) unchanged.
- **Exact local command:**
  ```bash
  ./gradlew assembleDebug \
    -PPLATFORM_KEYSTORE=/abs/path/platform.keystore \
    -PPLATFORM_KS_PASSWORD=android -PPLATFORM_KEY_ALIAS=platform -PPLATFORM_KEY_PASSWORD=android
  ```
  Full keystore creation and CI setup: `docs/PLATFORM_SIGNING.md`.
- **Diagnostic:** the Vehicle-integration screen shows the installed APK's signing certificate (fingerprint), so you can
  confirm a platform-signed install versus the debug key, alongside the per-permission grant/protection status.
- **Needs the car:** whether this ROM grants `BYDAUTO_*_GET` to a platform-signed app (run the probe after install).

## 3. Evidence-gatherer
- **Implemented:** read-only scan of BYD packages listing exported providers/services/receivers with each one's required
  permission and protection level; anything exported without a signature guard is flagged POSSIBLE CLEAN ROUTE. For
  candidate **providers** it goes further and performs a read-only `ContentResolver.query`, reporting the returned
  columns/row count or the exact error (SecurityException, UnsupportedOperationException, …). Services and receivers are
  listed but never invoked, since binding/broadcasting could cause side effects.
- **Integrated (not wire-ready):** a candidate provider can be adopted as a live read-only data source from the scan
  screen ("Use as data source"). `ProviderVehicleSource` auto-maps the provider's own column names to speed / SoC / range
  / gear, queries it read-only each cycle, parses types safely, and feeds the dashboard through the normal selector.
- **Tested (JVM):** `ProviderMapTest` covers column auto-mapping, type/gear parsing, junk/missing values, and that only
  present fields are marked real.
- **Needs the car:** whether any such clean provider actually exists on the unit, and that its columns carry real data —
  run the scan, tap a candidate, and confirm the dashboard shows its values.

## 4. Vehicle-data integration
- **Implemented:** all four modes. Source selection is a pure, tested unit (`SourceSelector`): Automatic = BYD → real
  OBD2 → labelled simulation; BYD-only and OBD2 never simulate (show `--` when unavailable); simulation is always
  labelled. Freshness guard rejects stale readings; OBD2 reconnects after a dropped link with a backoff; the status line
  reports the effective source (including "· via OBD2" when Automatic falls back).
- **Provider source:** when a clean provider is configured it joins the BYD family in selection (BYD API first, then the
  provider), so Automatic shows it before falling back to OBD2/simulation; the status line reports "· via provider".
- **OBD2 parsing** is now a pure unit (`Obd2Parse`) with `Obd2ParseTest`: standard PID maths, header/length tolerance,
  and null on every error/empty/short reply (invalid responses never surface as live).
- **Tested (JVM):** `SourceSelectorTest` covers the full mode × availability matrix, the data-integrity invariants
  (real is never labelled sim; sim is never real), the freshness window, and the reconnect backoff.
- **Needs the car:** real BYD getter values (post-signing) and real ELM327 readings.

## 5. Validation summary
- **Automated, passing in CI:** source selection and fallback, data-integrity invariants, freshness, reconnect backoff,
  OBD2 PID parsing, provider column mapping/parsing, palette/mode (red reserved), settings round-trip.
- **Requires the physical vehicle:** platform-signed permission grant, component-scan results, real OBD2 telemetry,
  cluster projection, and the reverse-camera feed. Each is reduced to a single on-car step (install → open the relevant
  screen → read the result), not open-ended work.
