# DashCast technical analysis & what this app reuses

A response to the brief: study `github.com/Kiroha/byd-dashcast`, map its mechanisms, reuse what's appropriate, and
document what isn't. This is written against DashCast's documented architecture and its SECURITY.md, cross-checked with
`dicar2`, `byd-trip-stats` and `byd-voice-commands`, which use the same head-unit internals.

## 1. How DashCast actually works

DashCast is a **cluster launcher and mirror**, not a vehicle-data reader. Its two core mechanisms:

1. **Cluster projection** — it pushes any installed app onto the instrument-cluster display and mirrors touch. The
   mechanism is a **uid=2000 (shell) ADB trampoline** plus Android FREEFORM/display-move calls. The app opens an ADB
   connection to the head unit's own `127.0.0.1` and runs the privileged display operations under the **shell identity**,
   because its own app uid is not allowed to move windows to another display.
2. **Platform signing** — to hold signature permissions such as `INJECT_EVENTS` (for the touch mirror), the APK is signed
   with the **public AOSP platform test key**. On a test-keys ROM the OS then grants those permissions at install.

Crucially, DashCast's own SECURITY.md states that **on some units `BYDAUTO_*` are denied at the platform level
regardless of signing**, so speed/energy/instrument data can be unavailable even with platform signing. DashCast working
on a car therefore proves the ROM trusts the test key and that projection works — it does **not** prove the vehicle-data
permissions are obtainable.

## 2. The permission landscape (matches this unit's probe)

`BYDAUTO_*` splits in two:

- `*_COMMON` — **dangerous** (runtime). Grantable by a normal request; on this unit they are granted.
- `*_GET` — **signature**. Granted only to an app signed with the certificate the ROM trusts as platform. No dialog and
  no `pm grant` can substitute. This unit's probe shows every `_GET` as signature + not granted, and the data getters
  (`getInstance`, `getSpeed`, …) fail with `permission denied` because they are gated on the `_GET` permissions.

So reading real BYD data needs one of: platform-key signing (if the ROM grants `BYDAUTO` to test-key apps), or running
under a more-privileged identity (the shell/ADB trampoline). The `_COMMON` grants alone are not enough.

## 3. What this app reuses — and what it does not

**Reused (legitimate):**

- **Platform-key signing** — wired as an opt-in build path (`docs/PLATFORM_SIGNING.md`). This is the genuine signature
  mechanism: the OS verifies the certificate and grants the permission for real. Given DashCast runs on this car, this is
  the most likely route to real data, and it reads the same APIs this app already calls in `BydAutoSource`.
- **Honest diagnostics** — the diagnostics screen now distinguishes `SIGNATURE RESTRICTED` (needs platform signing /
  firmware auth) from a plain runtime `PERMISSION DENIED`, plus `UNSUPPORTED`, `NOT INITIALIZED`, `NO DATA` and
  `SIMULATION`, with the real error per device and a summary of how many data devices are genuinely readable.
- **Data-source integrity** — Automatic / BYD-only / OBD2 / Simulation already follow the brief: BYD-only never
  simulates and shows `--`; simulation is always labelled; OBD2 never claims BYD-specific values it can't read.

**Not reused (the bypass line):**

- **The uid=2000 / ADB `app_process` shell-identity trampoline** — used by DashCast for projection and by
  dicar2/byd-trip-stats for data. It obtains privileges by running code as the shell user to get past the permission the
  app's own uid is denied. This app does not implement it, for either data or projection. It is a circumvention of the
  head unit's access control, not a use of the permission system.
- **Client-side permission shims** (`BydPermissionContext`-style overrides of `enforceCallingOrSelfPermission`). Not
  implemented.

This matches the brief's own clause: *"If a capability depends on BYD platform authorization, document the limitation
rather than inventing a workaround."*

## 4. Getting this cluster onto the instrument display

Projection is the one DashCast capability this app deliberately does not build in (it needs the shell trampoline). You
can still get there without it: **use DashCast itself to project this app onto the cluster.** This app already renders
full-screen at the 1920×720 cluster size and keeps running when moved to another display, so it cooperates with a
DashCast projection as-is. That keeps the privileged display operation inside the tool built for it, rather than baking a
shell-identity channel into a cluster app.

## 5. Compatibility report — BYD Song Plus EV (this unit)

| Capability | Status on this unit | Route |
| --- | --- | --- |
| `*_COMMON` (dangerous) permissions | Grantable / granted | runtime request (built in) |
| `*_GET` (signature) permissions | Denied; signature-level | platform signing (try) or firmware auth |
| Real speed/energy/gear via BYD API | Not available as a normal app | platform signing, **if** ROM grants `BYDAUTO` |
| Real speed via OBD2 | Available with an ELM327 | built in, no permission needed |
| BYD battery %/range via OBD2 | Not available (proprietary PIDs) | shown as `--`, never faked |
| Cluster projection (screen behind wheel) | Works via DashCast | run DashCast; project this app |
| Reverse camera (factory) | Not reachable by a normal app | Camera2/MJPEG fallback built in |

## 6. Remaining limitations (firmware / authorization level)

- If this ROM does not grant `BYDAUTO_*_GET` to test-key-signed apps, real BYD data is unreachable without manufacturer
  signing — no code in this app changes that.
- Steering angle and rear radar: no verified API, so guidelines are static and proximity is not shown.
- None of the BYD-API paths have been verified on this vehicle; only platform signing + re-running the probe will
  confirm whether the data unlocks.
