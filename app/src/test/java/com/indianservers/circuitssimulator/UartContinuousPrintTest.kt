package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Test
import org.junit.Assert.*
class UartContinuousPrintTest {
    @Test fun consecutivePrintsDeliverEveryByteWhilePreviousFrameIsLow(){
        val c=PhaseTwoProjects.circuit(Kind.ARDUINO_UNO,16)
        val b=FirmwareBoard(c,"B1");val s=FirmwareSession(ArduinoSubset.compile(c.firmware.single().source,b),b)
        while((s.variables()["cycles"]?.toDouble() ?: 0.0)<1)assertNull(s.advance(10000).error)
        assertNull(s.advance(5000).error)
        assertEquals(s.consoleLines().joinToString("\n")+"\n",s.fabric.displayText()["TELEMETRY"])
        assertEquals("0:0\n",s.fabric.displayText()["TELEMETRY"])
    }
    @Test fun groundedUartStillRejectsDelivery(){
        val base=PhaseTwoProjects.circuit(Kind.ARDUINO_UNO,16)
        val tx=BoardRegistry.boards.getValue(Kind.ARDUINO_UNO).hardware.uartPins.first().first
        val c=base.copy(wires=base.wires+Wire(start=TerminalRef("B1",BoardRegistry.boards.getValue(Kind.ARDUINO_UNO).index(tx)),end=TerminalRef("G1",0)))
        val b=FirmwareBoard(c,"B1");val s=FirmwareSession(ArduinoSubset.compile(c.firmware.single().source,b),b)
        while((s.variables()["cycles"]?.toDouble() ?: 0.0)<1)assertNull(s.advance(10000).error)
        assertNull(s.advance(5000).error)
        assertEquals("",s.fabric.displayText()["TELEMETRY"])
        assertTrue(s.fabric.log.any {it.kind==BusKind.UART && it.ack==false})
    }
}
