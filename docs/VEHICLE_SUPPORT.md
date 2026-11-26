# Vehicle support and limits

## Relationship to DashCast

[Kiroha/byd-dashcast](https://github.com/Kiroha/byd-dashcast) projects Android apps onto the BYD instrument
cluster. It does this with a three-layer design: an app, a foreground service, and a privileged proxy daemon started
through ADB over localhost. The daemon uses hidden SurfaceControl and ActivityTaskManager APIs and the cluster's
AutoContainer binder. DashCast reports testing on a Seal EU 2024 (DiLink 3.0, Android 10) and claims DiLink 5;
some units deny the BYD permissions.

This app **does not include that tooling**. It draws the cluster UI in a normal Activity. Showing it on the
physical cluster display needs DashCast or equivalent privileged setup, and I have not integrated or tested
that. I did not invent any API for it.

## Where real data comes from

BYD's firmware ships classes in `android.hardware.bydauto.*`. They are not part of AOSP, so the app finds them by
reflection and calls only methods that appear in the DashCast stub jar (checked with `javap`). Behaviour on a real
car is **not verified by me**.

| Cluster value | BYD class and call | Notes |
|---|---|---|
| Speed | Speed `getCurrentSpeed` | Rejected if negative, NaN or above `SPEED_MAX` |
| Pedal / power bar | Speed `getAccelerateDeepness`, `getBrakeDeepness` | Normalised pedal position, **not** kW |
| Drive mode | Energy `getOperationMode` | Economy → Eco, Sport → Sport, other → Normal |
| Regen active | Energy `getPowerGenerationState` | |
| Gear | Gearbox `getGearboxAutoModeType` | P, R, N, D (D / S / M all show D) |
| Brake pedal, parking brake | Gearbox `getBrakePedalState`, `getParkBrakeSwitch` | |
| Battery % | Statistic `getElecPercentageValue` | Must be 0–100 |
| Range | Statistic `getElecDrivingRangeValue` | Must be 0–2000 |
| Average consumption | Statistic `getTotalElecConPHMValue` | Unit assumed kWh/100 km |
| Odometer | Statistic `getTotalMileageValue` | |
| Low / high beam, turn signals | Light `getLightStatus` | Hazard = both signals on |
| Doors, trunk | Bodywork `getDoorState` | Unknown return values are shown as unavailable |
| Door lock | DoorLock `getDoorLockStatus` (front-left) | |
| Seat belt (driver) | SafetyBelt `getSafetyBeltStatus` | |
| Tyre pressure state | Tyre `getTyrePressureState` | Normal / low / high only |
| Charging power | Charging `getChargingPower` | |

Permissions declared: `BYDAUTO_{SPEED,ENERGY,GEARBOX,LIGHT,BODYWORK,DOOR_LOCK,TYRE,INSTRUMENT}_{COMMON,GET}` and
`BYDAUTO_SENSOR_GET`, as listed in DashCast's manifest. The `_COMMON` ones are dangerous (runtime) permissions,
the `_GET` ones are signature level, so a normal app may never get them. For Statistic, SafetyBelt and Charging
no permission name is documented by the reference project, so the app claims none and shows a diagnostic instead.

## Trip and range data (v1.1)

* Trip distance is the integral of the speed signal; there is no trip counter read from the car.
* Trip energy is **estimated** from state-of-charge change times the capacity set in Settings (default 71.8 kWh is
  an assumption: check your car). State of charge is coarse, so short trips give rough efficiency.
* Range projection = remaining energy (SoC × capacity) ÷ consumption over the last 10 / 100 / 500 km of recorded
  trips. Needs about 3 km of history; shows `--` otherwise. It is an estimate, not a BMS value.
* Trips are recorded only while the app is open and reading data (no background service was added, because
  background persistence on BYD firmware is unverified).
* Speed guard: real speed that reads 0 for 60 s while the odometer rises by 1 km is shown as unavailable.

## Not supported (shown as `--` or hidden, never invented)

* **Car's own trip counters**: not read (the app computes its own trips).
* **Motor power in kW** on real data (no verified call). The bar uses pedal position.
* **Tyre pressure in bar**: the stub exposes a value call but its unit is undocumented, so only the state is shown.
* **Outside temperature**, **ADAS / ACC / lane-keep state**, **V2L**, **battery heating**, **DC / AC charge type**,
  **regen strength setting**: not read.
* **Other doors' lock state**, passenger and rear belts: not read.
* **Writing anything** to the car, including HUD or cluster CAN writes that DashCast performs.
* **Projection onto the physical cluster** (see above).
* Non-BYD cars, and BYD models or firmware where the classes or permissions are absent.

## REAL, SIMULATED, UNAVAILABLE

* Every value is a `Sig<T>` with a source. The renderer shows `--` and dims the icon for UNAVAILABLE.
* **Automatic** mode uses BYD data only if speed is readable; otherwise the **whole** state is simulated and the
  badge says SIMULATION. Real and simulated values are never mixed in one frame.
* **BYD only** never simulates. **Simulation** never reads the car.
* The badge (LIVE VEHICLE DATA, SIMULATION, NO DATA, and PARTIAL when some core signals are unreadable) is always
  visible on the cluster.
* **Settings > Vehicle integration > Diagnostics** lists each BYD device with OK, PERMISSION DENIED, UNSUPPORTED or
  ERROR, the reason, and whether its permission is granted.

## Safety

This is an unofficial hobby app. Do not rely on it for safety-critical information and keep the factory cluster
usable. Check the laws and your warranty before running it while driving.
