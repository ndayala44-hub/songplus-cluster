package com.songplus.cluster.vehicle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Real vehicle data over a standard ELM327 OBD2 Bluetooth adapter.
 *
 * This needs no vehicle permission: it talks to a Bluetooth serial adapter plugged into the OBD2 port, exactly as any
 * diagnostic app does. It reads only standard SAE PIDs (speed 010D, RPM 010C, coolant 0105). It never writes to the
 * vehicle and never guesses: a PID that the car does not answer stays null.
 *
 * On a pure EV some engine PIDs may not be present; speed is usually available. Manufacturer EV PIDs (state of charge,
 * range) are not read here because they are unverified for the Song Plus — that keeps the "no invented data" rule.
 */
class Obd2Source(private val ctx: Context) {

    enum class State { IDLE, CONNECTING, HANDSHAKE, CONNECTED, NO_ADAPTER, NO_PERMISSION, ERROR }

    data class Readings(
        val speedKmh: Int? = null,
        val rpm: Int? = null,
        val coolantC: Int? = null,
        val lastOkMs: Long = 0L
    )

    data class Dev(val name: String, val mac: String)

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _detail = MutableStateFlow("")
    val detail: StateFlow<String> = _detail.asStateFlow()
    private val _readings = MutableStateFlow(Readings())
    val readings: StateFlow<Readings> = _readings.asStateFlow()

    @Volatile private var worker: Thread? = null
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var running = false

    private val spp: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** True on API<31 (legacy BLUETOOTH is normal-level), or when BLUETOOTH_CONNECT is granted on API>=31. */
    fun hasBtPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ctx.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED
        else true

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<Dev> {
        if (!hasBtPermission()) return emptyList()
        val adapter = adapter() ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty().map { Dev(it.name ?: it.address, it.address) }.sortedBy { it.name }
        } catch (t: SecurityException) {
            emptyList()
        }
    }

    private fun adapter(): BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    /** Connect to the bonded adapter with this MAC and start polling. Safe to call repeatedly. */
    @SuppressLint("MissingPermission")
    fun connect(mac: String) {
        if (mac.isBlank()) { _state.value = State.ERROR; _detail.value = "No adapter selected"; return }
        if (!hasBtPermission()) { _state.value = State.NO_PERMISSION; _detail.value = "Bluetooth permission not granted"; return }
        val a = adapter()
        if (a == null || !a.isEnabled) { _state.value = State.NO_ADAPTER; _detail.value = "Bluetooth is off or unavailable"; return }
        stop()
        running = true
        worker = Thread { loop(a, mac) }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Throwable) {}
        socket = null
        worker = null
        if (_state.value != State.IDLE) _state.value = State.IDLE
    }

    @SuppressLint("MissingPermission")
    private fun loop(adapter: BluetoothAdapter, mac: String) {
        try {
            _state.value = State.CONNECTING
            _detail.value = "Opening Bluetooth link…"
            val dev: BluetoothDevice = adapter.getRemoteDevice(mac)
            try { adapter.cancelDiscovery() } catch (_: Throwable) {}
            val sock = dev.createRfcommSocketToServiceRecord(spp)
            socket = sock
            sock.connect()
            val ins = sock.inputStream
            val outs = sock.outputStream

            _state.value = State.HANDSHAKE
            _detail.value = "ELM327 handshake…"
            for (cmd in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATSP0")) {
                if (!running) return
                send(outs, cmd); readUntilPrompt(ins, 2500)
                Thread.sleep(120)
            }

            _state.value = State.CONNECTED
            _detail.value = "Connected"
            while (running) {
                val speed = query(outs, ins, "010D")?.let { Obd2Parse.speedKmh(it) }
                val rpm = query(outs, ins, "010C")?.let { Obd2Parse.rpm(it) }
                val cool = query(outs, ins, "0105")?.let { Obd2Parse.coolantC(it) }

                val cur = _readings.value
                _readings.value = Readings(
                    speedKmh = speed,
                    rpm = rpm,
                    coolantC = cool,
                    lastOkMs = if (speed != null || rpm != null) System.currentTimeMillis() else cur.lastOkMs
                )
                if (speed == null && rpm == null && cool == null) _detail.value = "Connected, but the car returned no data for these PIDs"
                Thread.sleep(200)
            }
        } catch (e: SecurityException) {
            _state.value = State.NO_PERMISSION; _detail.value = "Bluetooth permission denied"
        } catch (e: Throwable) {
            if (running) { _state.value = State.ERROR; _detail.value = e.message ?: e.javaClass.simpleName }
        } finally {
            try { socket?.close() } catch (_: Throwable) {}
            socket = null
            _readings.value = Readings()
        }
    }

    private fun send(out: OutputStream, cmd: String) {
        out.write((cmd + "\r").toByteArray())
        out.flush()
    }

    /** Send a PID and return the raw reply text, or null on timeout / adapter error reply. */
    private fun query(out: OutputStream, ins: InputStream, pid: String): String? {
        if (!running) return null
        send(out, pid)
        val r = readUntilPrompt(ins, 1200) ?: return null
        val up = r.uppercase()
        if (up.contains("NO DATA") || up.contains("UNABLE") || up.contains("STOPPED") || up.contains("ERROR") || up.contains("?")) return null
        return r
    }

    /** Read until the ELM327 '>' prompt or timeout. Returns accumulated text (without the prompt), or null on timeout. */
    private fun readUntilPrompt(ins: InputStream, timeoutMs: Long): String? {
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(64)
        while (System.currentTimeMillis() < deadline) {
            if (ins.available() > 0) {
                val n = ins.read(buf)
                if (n > 0) {
                    for (i in 0 until n) {
                        val ch = buf[i].toInt().toChar()
                        if (ch == '>') return sb.toString()
                        if (ch != '\r' && ch != '\n') sb.append(ch) else sb.append(' ')
                    }
                }
            } else {
                Thread.sleep(10)
            }
        }
        return if (sb.isNotEmpty()) sb.toString() else null
    }

}
