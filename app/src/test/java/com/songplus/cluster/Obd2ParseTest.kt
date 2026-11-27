package com.songplus.cluster

import com.songplus.cluster.vehicle.Obd2Parse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Obd2ParseTest {
    @Test fun speedFromStandardReply() {
        assertEquals(58, Obd2Parse.speedKmh("41 0D 3A"))
        assertEquals(0, Obd2Parse.speedKmh("41 0D 00"))
        assertEquals(58, Obd2Parse.speedKmh("410D3A"))          // no spaces
        assertEquals(58, Obd2Parse.speedKmh("7E8 03 41 0D 3A")) // with header/length bytes
    }

    @Test fun rpmMaths() {
        // (256*0x1A + 0xF8)/4 = (6656+248)/4 = 1726
        assertEquals(1726, Obd2Parse.rpm("41 0C 1A F8"))
        assertEquals(0, Obd2Parse.rpm("41 0C 00 00"))
    }

    @Test fun coolantOffset() {
        assertEquals(50, Obd2Parse.coolantC("41 05 5A")) // 0x5A=90, -40 = 50
        assertEquals(-40, Obd2Parse.coolantC("41 05 00"))
    }

    @Test fun errorAndEmptyRepliesAreNull() {
        for (r in listOf("NO DATA", "UNABLE TO CONNECT", "STOPPED", "SEARCHING...", "?", "7F 01 12")) {
            assertNull("speed should be null for '$r'", Obd2Parse.speedKmh(r))
        }
        assertNull(Obd2Parse.speedKmh("41 0D"))       // echo but no data byte
        assertNull(Obd2Parse.rpm("41 0C 1A"))         // only one of two rpm bytes
        assertNull(Obd2Parse.speedKmh("41 0C 3A"))    // wrong pid echo
    }
}
