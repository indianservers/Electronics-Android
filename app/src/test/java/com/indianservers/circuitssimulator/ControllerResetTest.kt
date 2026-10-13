package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Assert.*
import org.junit.Test

class ControllerResetTest {
    @Test fun resetClearsControllerTimingAndDrivesWhilePreservingPeer() {
        val fabric = ProtocolFabric()
        val a = TerminalRef("a", 1)
        val b = TerminalRef("b", 1)
        fabric.completionTimes.putAll(mapOf("a" to 120000L, "b" to 130000L))
        fabric.i2cRoutes.putAll(mapOf("a" to ("SDA" to "SCL"), "b" to ("SDA" to "SCL")))
        fabric.spiConfigurations.putAll(mapOf("a" to SpiConfiguration(), "b" to SpiConfiguration()))
        fabric.attachUart("a", a, a)
        fabric.attachUart("b", b, b)
        fabric.waveforms.add(100000, "I2C:a:1", a, 0.0)
        fabric.waveforms.add(100000, "SPI:a:SCK", a, 0.0)
        fabric.waveforms.add(100000, "UART:a", a, 5.0)
        fabric.waveforms.add(100000, "I2C:b:1", b, 0.0)
        fabric.resetController("a")
        assertFalse(fabric.completionTimes.containsKey("a"))
        assertFalse(fabric.i2cRoutes.containsKey("a"))
        assertFalse(fabric.spiConfigurations.containsKey("a"))
        assertFalse(fabric.uart.containsKey("a"))
        assertEquals(130000L, fabric.completionTimes["b"])
        assertTrue(fabric.i2cRoutes.containsKey("b"))
        assertTrue(fabric.spiConfigurations.containsKey("b"))
        assertTrue(fabric.uart.containsKey("b"))
        assertEquals(listOf(b), fabric.waveforms.drives(110000).map { it.terminal })
        // Cleared future edges must not reappear when the restarted clock reaches them.
        assertEquals(listOf(b), fabric.waveforms.drives(200000).map { it.terminal })
        fabric.resetController("b")
        assertEquals(0L, fabric.waveforms.endTime)
        assertTrue(fabric.waveforms.drives(200000).isEmpty())
    }
}
