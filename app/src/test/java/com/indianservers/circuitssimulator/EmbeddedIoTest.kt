package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.simulation.ExternalDrive
import com.indianservers.circuitssimulator.simulation.embedded.*
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import org.junit.Assert.*
import org.junit.Test

class EmbeddedIoTest {
    @Test fun finiteGpioDriveAnchorsAnOtherwiseFloatingNode() {
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=0f,y=0f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=100f,y=0f)
        val circuit=Circuit("floating load",listOf(resistor,ground),emptyList())
        val pin=TerminalRef(resistor.id,0)
        val solved=DcSolver().solve(circuit,listOf(ExternalDrive(pin,5.0,50.0)))
        assertNull(solved.error)
        assertEquals(5.0,solved.nodeVoltages.getValue(pin),1e-4)
    }

    @Test fun adcSamplesActualCircuitNodeAndGpioDrivesIt() {
        val circuit=SampleCircuits.lightDivider()
        val sensor=circuit.components.first { it.kind==Kind.LDR }
        val terminal=TerminalRef(sensor.id,0)
        val board=EducationalMcu(mapOf("A0" to terminal))
        val input=board.step(circuit,.001)
        assertNull(input.circuitResult.error)
        assertTrue(board.readAdc("A0") in 511..512)
        assertEquals(LogicState.UNKNOWN,board.readDigital("A0"))
        board.configure("A0",PinMode.OUTPUT)
        board.write("A0",PinLevel.HIGH)
        val high=board.step(circuit,.001)
        assertTrue(high.circuitResult.nodeVoltages.getValue(terminal)>4.9)
        assertTrue(high.pins.getValue("A0").currentAmps>0)
        assertEquals(LogicState.HIGH,board.readDigital("A0"))
        board.write("A0",PinLevel.LOW)
        val low=board.step(circuit,.001)
        assertTrue(low.circuitResult.nodeVoltages.getValue(terminal)<0.1)
        assertTrue(low.pins.getValue("A0").currentAmps<0)
        assertEquals(LogicState.LOW,board.readDigital("A0"))
    }

    @Test fun sustainedGpioOvercurrentOpensDamagedDriver() {
        val circuit=SampleCircuits.lightDivider()
        val ground=circuit.components.first { it.kind==Kind.GROUND }
        val board=EducationalMcu(mapOf("D0" to TerminalRef(ground.id,0)))
        board.configure("D0",PinMode.OUTPUT)
        board.write("D0",PinLevel.HIGH)
        val fault=board.step(circuit,1.0)
        assertNull(fault.circuitResult.error)
        assertTrue(fault.pins.getValue("D0").damaged)
        assertEquals(0.0,fault.pins.getValue("D0").currentAmps,0.0)
        board.reset()
        assertEquals(0.0,board.timeSeconds,0.0)
        assertFalse(board.step(circuit,.001).pins.getValue("D0").damaged)
    }
}
