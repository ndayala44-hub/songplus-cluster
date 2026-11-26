package com.songplus.cluster.vehicle

import android.content.Context
import android.content.pm.PackageManager
import com.songplus.cluster.data.DeviceProbe
import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.data.Gear
import com.songplus.cluster.data.ProbeStatus
import com.songplus.cluster.data.Sig
import com.songplus.cluster.data.Tyre
import com.songplus.cluster.data.TyreState
import com.songplus.cluster.data.VehicleState
import com.songplus.cluster.data.na
import com.songplus.cluster.data.real
import java.lang.reflect.InvocationTargetException

/**
 * Reads real vehicle data from BYD's `android.hardware.bydauto.*` device classes.
 *
 * Why reflection: these classes exist only in BYD's firmware (the DashCast project compiles against a stub jar for
 * the same reason). Using reflection means the app installs and runs on any Android device; on a non-BYD device
 * [classesPresent] is false and nothing here is called.
 *
 * Only methods that exist in the public stub signatures are used (see docs/VEHICLE_SUPPORT.md). Nothing is assumed
 * to be granted: each read is guarded, a failure marks just that value UNAVAILABLE, and the reason is recorded for
 * the diagnostics screen. This class never writes to the vehicle.
 */
class BydAutoSource(private val ctx: Context) {

    private class Dev(val name: String, val className: String, val permission: String?)

    private val devs = listOf(
        Dev("Speed", "android.hardware.bydauto.speed.BYDAutoSpeedDevice", "android.permission.BYDAUTO_SPEED_GET"),
        Dev("Energy", "android.hardware.bydauto.energy.BYDAutoEnergyDevice", "android.permission.BYDAUTO_ENERGY_GET"),
        Dev("Gearbox", "android.hardware.bydauto.gearbox.BYDAutoGearboxDevice", "android.permission.BYDAUTO_GEARBOX_GET"),
        Dev("Light", "android.hardware.bydauto.light.BYDAutoLightDevice", "android.permission.BYDAUTO_LIGHT_GET"),
        Dev("Bodywork", "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice", "android.permission.BYDAUTO_BODYWORK_GET"),
        Dev("DoorLock", "android.hardware.bydauto.doorlock.BYDAutoDoorLockDevice", "android.permission.BYDAUTO_DOOR_LOCK_GET"),
        Dev("Tyre", "android.hardware.bydauto.tyre.BYDAutoTyreDevice", "android.permission.BYDAUTO_TYRE_GET"),
        Dev("Instrument", "android.hardware.bydauto.instrument.BYDAutoInstrumentDevice", "android.permission.BYDAUTO_INSTRUMENT_GET"),
        // No permission name for these three is documented by the reference project, so none is claimed here.
        Dev("Statistic", "android.hardware.bydauto.statistic.BYDAutoStatisticDevice", null),
        Dev("SafetyBelt", "android.hardware.bydauto.safetybelt.BYDAutoSafetyBeltDevice", null),
        Dev("Charging", "android.hardware.bydauto.charging.BYDAutoChargingDevice", null)
    )

    private val classes = HashMap<String, Class<*>?>()
    private val instances = HashMap<String, Any?>()
    private var cycle = HashMap<String, Pair<ProbeStatus, String>>()
    private var lastCycle: Map<String, Pair<ProbeStatus, String>> = emptyMap()

    val classesPresent: Boolean = classOf("Speed") != null

    private fun devByName(name: String): Dev? = devs.firstOrNull { it.name == name }

    private fun classOf(name: String): Class<*>? {
        if (classes.containsKey(name)) return classes[name]
        val cn = devByName(name)?.className
        val c = try { if (cn == null) null else Class.forName(cn) } catch (t: Throwable) { null }
        classes[name] = c
        return c
    }

    private fun mark(dev: String, status: ProbeStatus, detail: String) {
        val cur = cycle[dev]
        if (cur == null || cur.first == ProbeStatus.OK) cycle[dev] = Pair(status, detail)
    }

    private fun fail(dev: String, what: String, t: Throwable) {
        val cause = if (t is InvocationTargetException) (t.targetException ?: t) else t
        if (cause is SecurityException) mark(dev, ProbeStatus.PERMISSION_DENIED, "$what: permission denied")
        else mark(dev, ProbeStatus.ERROR, "$what: ${cause.javaClass.simpleName} ${cause.message ?: ""}".trim())
    }

    private fun instance(name: String): Any? {
        if (instances.containsKey(name)) return instances[name]
        val c = classOf(name)
        var inst: Any? = null
        if (c == null) {
            mark(name, ProbeStatus.UNSUPPORTED, "class not present on this system")
        } else {
            try {
                inst = c.getMethod("getInstance", Context::class.java).invoke(null, ctx)
                if (inst == null) mark(name, ProbeStatus.ERROR, "getInstance returned null")
            } catch (t: Throwable) {
                fail(name, "getInstance", t)
            }
        }
        // Do not cache a failed lookup permanently: permissions can be granted later.
        if (inst != null) instances[name] = inst
        return inst
    }

    private fun call(dev: String, method: String, vararg args: Int): Any? {
        val inst = instance(dev) ?: return null
        val m = inst.javaClass.methods.firstOrNull { it.name == method && it.parameterTypes.size == args.size }
        if (m == null) {
            mark(dev, ProbeStatus.UNSUPPORTED, "$method not found")
            return null
        }
        return try {
            val boxed = arrayOfNulls<Any>(args.size)
            for (i in args.indices) boxed[i] = args[i]
            val r = m.invoke(inst, *boxed)
            mark(dev, ProbeStatus.OK, "$method ok")
            r
        } catch (t: Throwable) {
            fail(dev, method, t)
            null
        }
    }

    private fun constInt(dev: String, field: String): Int? = try {
        classOf(dev)?.getField(field)?.getInt(null)
    } catch (t: Throwable) {
        null
    }

    private fun int(dev: String, method: String, vararg args: Int): Int? = (call(dev, method, *args) as? Number)?.toInt()
    private fun dbl(dev: String, method: String, vararg args: Int): Double? = (call(dev, method, *args) as? Number)?.toDouble()

    private fun <T> sig(v: T?, fallback: T): Sig<T> = if (v == null) na(fallback) else real(v)

    private fun door(area: Int): Sig<Boolean> {
        val open = constInt("Bodywork", "BODYWORK_STATE_OPEN") ?: 1
        val v = int("Bodywork", "getDoorState", area) ?: return na(false)
        return if (v == 255 || (v != open && v != 0)) na(false) else real(v == open)
    }

    private fun light(constName: String): Sig<Boolean> {
        val id = constInt("Light", constName) ?: return na(false)
        val on = constInt("Light", "LIGHT_ON") ?: return na(false)
        val v = int("Light", "getLightStatus", id) ?: return na(false)
        return real(v == on)
    }

    private fun tyre(areaConst: String): Sig<Tyre> {
        val area = constInt("Tyre", areaConst) ?: return na(Tyre(null, TyreState.UNKNOWN))
        val st = int("Tyre", "getTyrePressureState", area) ?: return na(Tyre(null, TyreState.UNKNOWN))
        val state = when (st) {
            constInt("Tyre", "TYRE_PRESSURE_STATE_NORMAL") -> TyreState.OK
            constInt("Tyre", "TYRE_PRESSURE_STATE_UNDERPRESSURE") -> TyreState.LOW
            constInt("Tyre", "TYRE_PRESSURE_STATE_OVERPRESSURE") -> TyreState.HIGH
            else -> TyreState.UNKNOWN
        }
        // The stubs do not document the unit of getTyrePressureValue, so no bar value is derived from it.
        return real(Tyre(null, state))
    }

    /** One full read of everything this source can supply. Call from a background thread. */
    fun read(): VehicleState {
        cycle = HashMap()
        val u = VehicleState.unavailable()

        val speedRaw = dbl("Speed", "getCurrentSpeed")
        val speedMax = try { classOf("Speed")?.getField("SPEED_MAX")?.getDouble(null) } catch (t: Throwable) { null } ?: 300.0
        val speed: Sig<Float> =
            if (speedRaw != null && !speedRaw.isNaN() && speedRaw >= 0.0 && speedRaw <= speedMax) real(speedRaw.toFloat()) else na(0f)

        val gearRaw = int("Gearbox", "getGearboxAutoModeType")
        val gear: Sig<Gear> = when (gearRaw) {
            null -> na(Gear.P)
            constInt("Gearbox", "GEARBOX_AUTO_MODE_P") -> real(Gear.P)
            constInt("Gearbox", "GEARBOX_AUTO_MODE_R") -> real(Gear.R)
            constInt("Gearbox", "GEARBOX_AUTO_MODE_N") -> real(Gear.N)
            constInt("Gearbox", "GEARBOX_AUTO_MODE_D"), constInt("Gearbox", "GEARBOX_AUTO_MODE_S"),
            constInt("Gearbox", "GEARBOX_AUTO_MODE_M") -> real(Gear.D)
            else -> na(Gear.P)
        }

        val opMode = int("Energy", "getOperationMode")
        val mode: Sig<DriveMode> = when (opMode) {
            null -> na(DriveMode.NORMAL)
            constInt("Energy", "ENERGY_OPERATION_ECONOMY") -> real(DriveMode.ECO)
            constInt("Energy", "ENERGY_OPERATION_SPORT") -> real(DriveMode.SPORT)
            else -> real(DriveMode.NORMAL)
        }

        // Sentinel/invalid readings (negative, NaN, out of physical range) are treated as unavailable, never shown.
        val soc = dbl("Statistic", "getElecPercentageValue")?.takeIf { !it.isNaN() && it in 0.0..100.0 }
        val range = int("Statistic", "getElecDrivingRangeValue")?.takeIf { it in 0..2000 }
        val avg = dbl("Statistic", "getTotalElecConPHMValue")?.takeIf { !it.isNaN() && it > 0.0 && it < 100.0 }
        val odo = int("Statistic", "getTotalMileageValue")?.takeIf { it >= 0 }

        val accel = int("Speed", "getAccelerateDeepness")
        val brakeDeep = int("Speed", "getBrakeDeepness")
        val pedal: Sig<Float> =
            if (accel == null) na(0f)
            else real(((accel - (brakeDeep ?: 0)) / 100f).coerceIn(-1f, 1f))
        val regenConst = constInt("Energy", "ENERGY_POWER_GENERATING")
        val regenRaw = int("Energy", "getPowerGenerationState")
        val regen: Sig<Boolean> = if (regenRaw == null || regenConst == null) na(false) else real(regenRaw == regenConst)

        val left = light("LIGHT_LEFT_TURN_SIGNAL")
        val right = light("LIGHT_RIGHT_TURN_SIGNAL")
        val hazard: Sig<Boolean> = if (left.available && right.available) real(left.value && right.value) else na(false)

        val pressConst = constInt("Gearbox", "GEARBOX_BREAK_PADAL_PRESS")
        val brakeRaw = int("Gearbox", "getBrakePedalState")
        val brake: Sig<Boolean> = if (brakeRaw == null || pressConst == null) na(false) else real(brakeRaw == pressConst)
        val parkConst = constInt("Gearbox", "GEARBOX_PARK_BREAK_SWITCH_VALID")
        val parkRaw = int("Gearbox", "getParkBrakeSwitch")
        val park: Sig<Boolean> = if (parkRaw == null || parkConst == null) na(false) else real(parkRaw == parkConst)

        val beltArea = constInt("SafetyBelt", "SAFETY_BELT_AREA_MAIN")
        val beltLock = constInt("SafetyBelt", "SAFETY_BELT_STATE_LOCK")
        val beltRaw = if (beltArea == null) null else int("SafetyBelt", "getSafetyBeltStatus", beltArea)
        val belt: Sig<Boolean> = if (beltRaw == null || beltLock == null) na(true) else real(beltRaw == beltLock)

        val lockArea = constInt("DoorLock", "DOOR_LOCK_AREA_LEFT_FRONT")
        val lockedConst = constInt("DoorLock", "DOOR_LOCK_STATE_LOCK")
        val lockRaw = if (lockArea == null) null else int("DoorLock", "getDoorLockStatus", lockArea)
        val locked: Sig<Boolean> = if (lockRaw == null || lockedConst == null) na(false) else real(lockRaw == lockedConst)

        val chargeKw = dbl("Charging", "getChargingPower")?.takeIf { !it.isNaN() && it >= 0.0 && it < 500.0 }
        val charging: Sig<Boolean> = if (chargeKw == null) na(false) else real(chargeKw > 0.3)

        lastCycle = cycle
        return u.copy(
            speedKmh = speed, gear = gear, mode = mode,
            socPct = sig(soc?.toFloat(), 0f), rangeKm = sig(range?.toFloat(), 0f),
            powerNorm = pedal, regenActive = regen,
            avgKwhPer100 = sig(avg?.toFloat(), 0f), odoKm = sig(odo?.toFloat(), 0f),
            leftSignal = left, rightSignal = right, hazard = hazard,
            lowBeam = light("LIGHT_LOW_BEAM"), highBeam = light("LIGHT_HIGH_BEAM"),
            brakePedal = brake, parkBrake = park, beltDriver = belt,
            doorFL = door(constInt("Bodywork", "BODYWORK_CMD_DOOR_LEFT_FRONT") ?: 1),
            doorFR = door(constInt("Bodywork", "BODYWORK_CMD_DOOR_RIGHT_FRONT") ?: 2),
            doorRL = door(constInt("Bodywork", "BODYWORK_CMD_DOOR_LEFT_REAR") ?: 3),
            doorRR = door(constInt("Bodywork", "BODYWORK_CMD_DOOR_RIGHT_REAR") ?: 4),
            trunk = door(constInt("Bodywork", "BODYWORK_CMD_DOOR_LUGGAGE_DOOR") ?: 6),
            locked = locked, charging = charging, chargeKw = sig(chargeKw?.toFloat(), 0f),
            tyreFL = tyre("TYRE_COMMAND_AREA_LEFT_FRONT"), tyreFR = tyre("TYRE_COMMAND_AREA_RIGHT_FRONT"),
            tyreRL = tyre("TYRE_COMMAND_AREA_LEFT_REAR"), tyreRR = tyre("TYRE_COMMAND_AREA_RIGHT_REAR")
        )
    }

    /** True when the core signal (speed) could be read: used by AUTO mode to decide between real and simulated data. */
    fun hasCoreData(state: VehicleState): Boolean = state.speedKmh.available

    /** Per-device status for the diagnostics screen. */
    fun probes(): List<DeviceProbe> = devs.map { d ->
        val st = lastCycle[d.name]
        val granted = d.permission?.let { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        DeviceProbe(
            device = d.name,
            status = when {
                !classesPresent -> ProbeStatus.UNSUPPORTED
                st == null -> ProbeStatus.NOT_PROBED
                else -> st.first
            },
            detail = when {
                !classesPresent -> "BYD device classes not present on this system"
                st == null -> "not read yet"
                else -> st.second
            },
            permission = d.permission,
            permissionGranted = granted
        )
    }
}
