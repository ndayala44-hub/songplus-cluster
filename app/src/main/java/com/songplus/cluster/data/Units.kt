package com.songplus.cluster.data

import kotlin.math.roundToInt

object Units {
    private const val KM_PER_MI = 1.609344f

    fun speed(kmh: Float, unit: SpeedUnit): Int =
        (if (unit == SpeedUnit.MPH) kmh / KM_PER_MI else kmh).roundToInt().coerceAtLeast(0)

    fun speedLabel(unit: SpeedUnit): String = if (unit == SpeedUnit.MPH) "mph" else "km/h"

    /** Upper end of the speed scale in the chosen unit. */
    fun speedMax(unit: SpeedUnit): Float = if (unit == SpeedUnit.MPH) 125f else 200f

    fun distance(km: Float, unit: DistUnit): Float = if (unit == DistUnit.MI) km / KM_PER_MI else km

    fun distanceLabel(unit: DistUnit): String = if (unit == DistUnit.MI) "mi" else "km"

    fun temp(c: Float, unit: TempUnit): Int = (if (unit == TempUnit.F) c * 9f / 5f + 32f else c).roundToInt()

    fun tempLabel(unit: TempUnit): String = if (unit == TempUnit.F) "°F" else "°C"

    /** kWh/100 km -> kWh/100 mi when miles are selected. */
    fun consumption(kwhPer100km: Float, unit: DistUnit): Float =
        if (unit == DistUnit.MI) kwhPer100km * KM_PER_MI else kwhPer100km

    fun consumptionLabel(unit: DistUnit): String = if (unit == DistUnit.MI) "kWh/100 mi" else "kWh/100 km"

    fun bar(bar: Float): String = String.format(java.util.Locale.US, "%.1f", bar)

    /**
     * Speed to display, applying the user's display calibration. Many cars show a few percent above true speed;
     * this lets the cluster match that if the owner wants. It changes the display only, never the recorded data.
     */
    fun displaySpeedKmh(kmh: Float, mode: SpeedDisplayMode, percent: Float, plusKmh: Float): Float {
        if (kmh < 0.5f) return kmh.coerceAtLeast(0f)
        return when (mode) {
            SpeedDisplayMode.REAL -> kmh
            SpeedDisplayMode.PERCENT -> kmh * (1f + percent / 100f)
            SpeedDisplayMode.PLUS_KMH -> kmh + plusKmh
        }
    }
}
