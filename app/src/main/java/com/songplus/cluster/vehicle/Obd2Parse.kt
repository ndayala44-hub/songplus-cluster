package com.songplus.cluster.vehicle

/**
 * Pure parsing of ELM327 replies for the standard PIDs we read. Kept free of Android/IO types so the hex handling,
 * error detection and value maths are unit-tested on the JVM without an adapter.
 */
object Obd2Parse {

    /** Adapter/vehicle "no answer" responses that must never be read as a value. */
    fun isErrorReply(reply: String): Boolean {
        val up = reply.uppercase()
        return up.contains("NO DATA") || up.contains("UNABLE") || up.contains("STOPPED") ||
            up.contains("ERROR") || up.contains("SEARCHING") || up.contains("?")
    }

    /** Data bytes after the "mode pid" echo (e.g. 41 0D xx), or null if the echo is absent / there are no data bytes. */
    fun dataBytes(reply: String, mode: String, pid: String): List<Int>? {
        val tokens = ArrayList<String>()
        for (raw in reply.trim().split(Regex("\\s+"))) {
            if (!raw.matches(Regex("[0-9A-Fa-f]+"))) continue
            if (raw.length % 2 != 0) continue                 // odd-length (e.g. a 3-nibble header) is not byte-aligned
            var i = 0; while (i < raw.length) { tokens.add(raw.substring(i, i + 2)); i += 2 }  // split contiguous hex into bytes
        }
        var idx = -1
        for (i in 0 until tokens.size - 1) {
            if (tokens[i].equals(mode, true) && tokens[i + 1].equals(pid, true)) { idx = i; break }
        }
        if (idx < 0) return null
        val data = tokens.drop(idx + 2)
        if (data.isEmpty()) return null
        return data.mapNotNull { it.toIntOrNull(16) }
    }

    /** 010D: speed in km/h = first data byte. */
    fun speedKmh(reply: String): Int? {
        if (isErrorReply(reply)) return null
        return dataBytes(reply, "41", "0D")?.getOrNull(0)
    }

    /** 010C: rpm = (256*A + B) / 4. */
    fun rpm(reply: String): Int? {
        if (isErrorReply(reply)) return null
        val b = dataBytes(reply, "41", "0C") ?: return null
        if (b.size < 2) return null
        return (b[0] * 256 + b[1]) / 4
    }

    /** 0105: coolant temperature in Celsius = A - 40. */
    fun coolantC(reply: String): Int? {
        if (isErrorReply(reply)) return null
        return dataBytes(reply, "41", "05")?.getOrNull(0)?.minus(40)
    }
}
