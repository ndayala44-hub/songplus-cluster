package com.songplus.cluster.data

/** Where a value came from. The UI must never show SIM or UNAVAILABLE values as if they were REAL. */
enum class Source { REAL, SIM, UNAVAILABLE }

/** A vehicle value together with its provenance. */
data class Sig<T>(val value: T, val src: Source) {
    val available: Boolean get() = src != Source.UNAVAILABLE
}

fun <T> real(v: T): Sig<T> = Sig(v, Source.REAL)
fun <T> sim(v: T): Sig<T> = Sig(v, Source.SIM)
fun <T> na(default: T): Sig<T> = Sig(default, Source.UNAVAILABLE)

enum class Gear(val label: String) { P("P"), R("R"), N("N"), D("D") }

/** The three drive modes of the BYD Song Plus EV. */
enum class DriveMode { ECO, NORMAL, SPORT }

enum class TyreState { OK, LOW, HIGH, UNKNOWN }

data class Tyre(val bar: Float?, val state: TyreState)

/**
 * Complete snapshot of everything the cluster can show.
 * Fields the connected data source cannot supply are marked [Source.UNAVAILABLE].
 */
data class VehicleState(
    val speedKmh: Sig<Float>,
    val gear: Sig<Gear>,
    val mode: Sig<DriveMode>,
    val socPct: Sig<Float>,
    val rangeKm: Sig<Float>,
    /** Normalised drive power: +1 = full drive, -1 = full regeneration. */
    val powerNorm: Sig<Float>,
    /** Real motor power in kW when the source can supply it (positive drive, negative regen). */
    val powerKw: Sig<Float>,
    val regenActive: Sig<Boolean>,
    val avgKwhPer100: Sig<Float>,
    val odoKm: Sig<Float>,
    val tripKm: Sig<Float>,
    val leftSignal: Sig<Boolean>,
    val rightSignal: Sig<Boolean>,
    val hazard: Sig<Boolean>,
    val lowBeam: Sig<Boolean>,
    val highBeam: Sig<Boolean>,
    val brakePedal: Sig<Boolean>,
    val parkBrake: Sig<Boolean>,
    val beltDriver: Sig<Boolean>,
    val doorFL: Sig<Boolean>,
    val doorFR: Sig<Boolean>,
    val doorRL: Sig<Boolean>,
    val doorRR: Sig<Boolean>,
    val trunk: Sig<Boolean>,
    val locked: Sig<Boolean>,
    val charging: Sig<Boolean>,
    val chargeKw: Sig<Float>,
    val tyreFL: Sig<Tyre>,
    val tyreFR: Sig<Tyre>,
    val tyreRL: Sig<Tyre>,
    val tyreRR: Sig<Tyre>,
    val outsideTempC: Sig<Float>
) {
    val anyDoorOpen: Boolean
        get() = doorFL.value || doorFR.value || doorRL.value || doorRR.value || trunk.value

    val moving: Boolean get() = speedKmh.value > 1f

    /** True when at least one value is real and at least one is simulated. */
    val anySim: Boolean get() = all().any { it == Source.SIM }
    val anyReal: Boolean get() = all().any { it == Source.REAL }

    private fun all(): List<Source> = listOf(
        speedKmh.src, gear.src, mode.src, socPct.src, rangeKm.src, powerNorm.src, odoKm.src, leftSignal.src,
        lowBeam.src, beltDriver.src, doorFL.src, locked.src, tyreFL.src
    )

    companion object {
        /** All values unavailable. Used before the first reading and for BYD-only mode without access. */
        fun unavailable(): VehicleState {
            val t = Tyre(null, TyreState.UNKNOWN)
            return VehicleState(
                speedKmh = na(0f), gear = na(Gear.P), mode = na(DriveMode.NORMAL), socPct = na(0f), rangeKm = na(0f),
                powerNorm = na(0f), powerKw = na(0f), regenActive = na(false), avgKwhPer100 = na(0f),
                odoKm = na(0f), tripKm = na(0f), leftSignal = na(false), rightSignal = na(false), hazard = na(false),
                lowBeam = na(false), highBeam = na(false), brakePedal = na(false), parkBrake = na(false),
                beltDriver = na(true), doorFL = na(false), doorFR = na(false), doorRL = na(false), doorRR = na(false),
                trunk = na(false), locked = na(false), charging = na(false), chargeKw = na(0f),
                tyreFL = na(t), tyreFR = na(t), tyreRL = na(t), tyreRR = na(t), outsideTempC = na(0f)
            )
        }
    }
}

/** Which kind of data the app is actually showing right now. */
enum class EffectiveSource { REAL, SIMULATION, NONE }

enum class ProbeStatus { OK, PERMISSION_DENIED, UNSUPPORTED, ERROR, NOT_PROBED }

data class DeviceProbe(
    val device: String,
    val status: ProbeStatus,
    val detail: String,
    /** Android permission name documented for this device, or null if none is verified. */
    val permission: String?,
    val permissionGranted: Boolean?
)

data class ConnectionStatus(
    val effective: EffectiveSource,
    val bydClassesPresent: Boolean,
    val message: String,
    val devices: List<DeviceProbe>,
    val lastUpdateMs: Long,
    val updatesPerSecond: Float
) {
    companion object {
        val initial = ConnectionStatus(EffectiveSource.NONE, false, "Starting…", emptyList(), 0L, 0f)
    }
}
