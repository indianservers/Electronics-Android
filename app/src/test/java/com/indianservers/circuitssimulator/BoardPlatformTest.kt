package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.simulation.CircuitDiagnostics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class BoardPlatformTest {
    private fun p(kind:Kind,ref:String)=PlacedComponent(kind=kind,reference=ref,x=0f,y=0f)
    private fun w(a:PlacedComponent,ap:Int,b:PlacedComponent,bp:Int)=
        Wire(start=TerminalRef(a.id,ap),end=TerminalRef(b.id,bp))

    @Test fun registryCoversEveryKindAndSearchesBoards() {
        assertTrue(ComponentRegistry.validate().joinToString(),ComponentRegistry.validate().isEmpty())
        assertEquals(Kind.entries.size,ComponentRegistry.definitions.size)
        assertTrue(ComponentRegistry.search("Arduino").any { it.kind==Kind.ARDUINO_UNO })
        assertTrue(ComponentRegistry.search("RP2040").any { it.kind==Kind.RASPBERRY_PICO })
        assertTrue(ComponentRegistry.search("I2C").any { it.kind==Kind.ESP32_DEVKIT })
    }

    @Test fun sevenBoardsHaveRealPinCapabilities() {
        assertEquals(11,BoardRegistry.boards.size)
        for(board in BoardRegistry.boards.values) {
            assertTrue(board.pins.size>=16)
            assertTrue(board.pins.any { PinCapability.GROUND in it.capabilities })
            assertTrue(board.pins.any { PinCapability.ANALOG_INPUT in it.capabilities })
            assertTrue(board.pins.any { PinCapability.PWM in it.capabilities })
            assertTrue(board.pins.any { PinCapability.UART in it.capabilities })
            assertTrue(board.pins.any { PinCapability.I2C in it.capabilities })
            assertTrue(board.pins.any { PinCapability.SPI in it.capabilities })
            assertTrue(board.sourceUrl.startsWith("https://"))
            assertEquals(board.pins.size,p(board.kind,"B1").terminalCount)
        }
        assertFalse(PinCapability.DIGITAL_OUTPUT in BoardRegistry.boards.getValue(Kind.ARDUINO_NANO).pin("A6")!!.capabilities)
        assertFalse(PinCapability.DIGITAL_OUTPUT in BoardRegistry.boards.getValue(Kind.ESP32_DEVKIT).pin("IO34")!!.capabilities)
        assertTrue(PinCapability.PWM in BoardRegistry.boards.getValue(Kind.NODEMCU_ESP8266).pin("D0")!!.capabilities)
        assertFalse(PinCapability.INTERRUPT in BoardRegistry.boards.getValue(Kind.NODEMCU_ESP8266).pin("D0")!!.capabilities)
    }

    @Test fun poweredGpioDrivesLedButUnpoweredOrInputPinDoesNot() {
        for(kind in BoardRegistry.boards.keys) {
            val board=BoardRegistry.boards.getValue(kind)
            val gpio=board.pins.first { PinCapability.DIGITAL_OUTPUT in it.capabilities &&
                it.name !in setOf("TX","RX") }.name
            val supply=board.supplyPins.first()
            val mcu=p(kind,"U1").copy(parameters=p(kind,"U1").parameters+("gpio_$gpio" to 2.0))
            val source=p(Kind.SOURCE,"V1").copy(parameters=mapOf("voltage" to board.logicVoltage))
            val resistor=p(Kind.RESISTOR,"R1").copy(parameters=mapOf("resistance" to 330.0))
            val led=p(Kind.LED,"D1")
            val ground=p(Kind.GROUND,"G1")
            val wires=listOf(w(source,0,mcu,board.index(supply)),w(source,1,ground,0),
                w(mcu,board.index("GND"),ground,0),w(mcu,board.index(gpio),resistor,0),
                w(resistor,1,led,0),w(led,1,ground,0))
            val circuit=Circuit(kind.title,listOf(source,mcu,resistor,led,ground),wires)
            val on=DcSolver().solve(circuit)
            assertNull("$kind: ${on.error}",on.error)
            assertTrue("$kind GPIO high",abs(on.readings.getValue(led.id).current)>.001)
            val input=DcSolver().solve(circuit.copy(components=circuit.components.map {
                if(it.id==mcu.id) it.copy(parameters=it.parameters+("gpio_$gpio" to 0.0)) else it }))
            assertNull("$kind: ${input.error}",input.error)
            assertTrue("$kind GPIO input",abs(input.readings.getValue(led.id).current)<1e-6)
            val unpowered=DcSolver().solve(circuit.copy(wires=wires.drop(1)))
            assertTrue("$kind GPIO unpowered",unpowered.error!=null ||
                abs(unpowered.readings.getValue(led.id).current)<1e-6)
        }
    }

    @Test fun overvoltageOnSensitiveBoardInputProducesWarning() {
        val board=p(Kind.RASPBERRY_PICO,"P1")
        val source=p(Kind.SOURCE,"V1").copy(parameters=mapOf("voltage" to 5.0))
        val ground=p(Kind.GROUND,"G1")
        val pins=BoardRegistry.boards.getValue(board.kind)
        val circuit=Circuit("Unsafe input",listOf(board,source,ground),listOf(
            w(source,0,board,pins.index("GP26")),w(source,1,ground,0),
            w(board,pins.index("GND"),ground,0)))
        val result=DcSolver().solve(circuit)
        assertNull(result.error)
        assertTrue(CircuitDiagnostics.inspect(circuit,result).any {
            it.code.startsWith("BOARD_PIN_VOLTAGE") && it.message.contains("GP26") })
    }
}
