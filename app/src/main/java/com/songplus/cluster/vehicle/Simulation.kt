package com.songplus.cluster.vehicle

import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.data.Gear
import com.songplus.cluster.data.Tyre
import com.songplus.cluster.data.TyreState
import com.songplus.cluster.data.VehicleState
import com.songplus.cluster.data.sim
import kotlin.math.abs

enum class SignalInput { OFF, LEFT, RIGHT, HAZARD }
enum class LightsInput { OFF, LOW, HIGH }

/** Everything the user can set on the simulator panel. */
data class SimInputs(
    val gear: Gear = Gear.D,
    val mode: DriveMode = DriveMode.NORMAL,
    val signal: SignalInput = SignalInput.OFF,
    val lights: LightsInput = LightsInput.LOW,
    val brake: Boolean = false,
    val beltFastened: Boolean = true,
    val doorFL: Boolean = false,
    val doorFR: Boolean = false,
    val doorRL: Boolean = false,
    val doorRR: Boolean = false,
    val trunk: Boolean = false,
    val locked: Boolean = false,
    val charging: Boolean = false,
    val tyreLow: Boolean = false,
    val targetSpeedKmh: Float = 0f
)

/**
 * Small physical model of a Song Plus EV (71.8 kWh battery, 150 kW motor, ~16 kWh/100 km).
 * Pure Kotlin so it can be unit-tested. Every value it produces is tagged as SIM.
 */
class SimEngine(startSoc: Float = 78f, startOdo: Float = 12408f) {
    var speed = 0f
        private set
    var soc = startSoc
        private set
    var odo = startOdo
        private set
    var trip = 0f
        private set
    private var power = 0f   // normalised -1..1
    private var lastSpeed = 0f

    fun step(inp: SimInputs, dt: Float): VehicleState {
        val parked = inp.gear == Gear.P || inp.gear == Gear.N
        var target = if (parked) 0f else inp.targetSpeedKmh
        if (inp.gear == Gear.R) target = target.coerceAtMost(12f)
        if (inp.brake) target = 0f
        val accelLimit = if (inp.mode == DriveMode.SPORT) 18f else if (inp.mode == DriveMode.ECO) 8f else 12f   // km/h per s
        val decelLimit = if (inp.brake) 28f else 10f
        val diff = target - speed
        val change = if (diff > 0f) minOf(diff, accelLimit * dt) else maxOf(diff, -decelLimit * dt)
        speed = (speed + change).coerceAtLeast(0f)
        val accel = if (dt > 0f) (speed - lastSpeed) / dt else 0f
        lastSpeed = speed

        // Normalised power: positive while accelerating or cruising, negative while slowing down.
        val cruise = speed / 260f
        val want = if (accel > 0.2f) cruise + accel / 20f else if (accel < -0.2f) accel / 18f else cruise * 0.8f
        val clamped = want.coerceIn(-1f, 1f)
        power += (clamped - power) * (dt * 6f).coerceAtMost(1f)
        val kw = power * 150f

        val avg = when (inp.mode) { DriveMode.ECO -> 13.7f; DriveMode.NORMAL -> 16.2f; DriveMode.SPORT -> 19.6f }
        // soc change from drive/regen and (time-lapsed x20 so the demo is visible) charging
        if (inp.charging && parked) {
            soc += 7f * dt / 3600f / 71.8f * 100f * 20f
        } else {
            soc -= kw * dt / 3600f / 71.8f * 100f
        }
        soc = soc.coerceIn(0f, 100f)
        val distKm = speed * dt / 3600f
        odo += distKm
        trip += distKm
        val rangeKm = (soc / 100f * 71.8f) / (avg / 100f)

        val lowT = Tyre(1.8f, TyreState.LOW)
        val ok = Tyre(2.5f, TyreState.OK)
        val hazard = inp.signal == SignalInput.HAZARD
        return VehicleState(
            speedKmh = sim(speed), gear = sim(inp.gear), mode = sim(inp.mode), socPct = sim(soc), rangeKm = sim(rangeKm),
            powerNorm = sim(power), powerKw = sim(kw), regenActive = sim(power < -0.03f),
            avgKwhPer100 = sim(avg), odoKm = sim(odo), tripKm = sim(trip),
            leftSignal = sim(inp.signal == SignalInput.LEFT || hazard),
            rightSignal = sim(inp.signal == SignalInput.RIGHT || hazard),
            hazard = sim(hazard),
            lowBeam = sim(inp.lights != LightsInput.OFF), highBeam = sim(inp.lights == LightsInput.HIGH),
            brakePedal = sim(inp.brake), parkBrake = sim(inp.gear == Gear.P),
            beltDriver = sim(inp.beltFastened),
            doorFL = sim(inp.doorFL), doorFR = sim(inp.doorFR), doorRL = sim(inp.doorRL), doorRR = sim(inp.doorRR),
            trunk = sim(inp.trunk), locked = sim(inp.locked),
            charging = sim(inp.charging && parked), chargeKw = sim(if (inp.charging && parked) 7f else 0f),
            tyreFL = sim(ok), tyreFR = sim(ok), tyreRL = sim(if (inp.tyreLow) lowT else ok), tyreRR = sim(ok),
            outsideTempC = sim(23f)
        )
    }

    fun resetTrip() { trip = 0f }
}

/** Scripted drive used by "auto drive": a 60 s loop of acceleration, cruising, regen braking and a stop. */
object AutoDrive {
    const val CYCLE_S = 60f

    fun inputsAt(tSec: Float, base: SimInputs): SimInputs {
        val t = tSec % CYCLE_S
        return when {
            t < 4f -> base.copy(gear = Gear.D, targetSpeedKmh = 0f, brake = false, signal = SignalInput.OFF)
            t < 16f -> base.copy(gear = Gear.D, targetSpeedKmh = 55f, brake = false, signal = SignalInput.OFF)
            t < 20f -> base.copy(gear = Gear.D, targetSpeedKmh = 55f, brake = false, signal = SignalInput.RIGHT)
            t < 34f -> base.copy(gear = Gear.D, targetSpeedKmh = 92f, brake = false, signal = SignalInput.OFF)
            t < 40f -> base.copy(gear = Gear.D, targetSpeedKmh = 40f, brake = false, signal = SignalInput.LEFT)
            t < 46f -> base.copy(gear = Gear.D, targetSpeedKmh = 0f, brake = true, signal = SignalInput.OFF)
            t < 54f -> base.copy(gear = Gear.P, targetSpeedKmh = 0f, brake = false, signal = SignalInput.OFF)
            else -> base.copy(gear = Gear.D, targetSpeedKmh = 30f, brake = false, signal = SignalInput.OFF)
        }
    }

    fun isStationary(speed: Float) = abs(speed) < 0.5f
}
