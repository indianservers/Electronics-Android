package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

class FaultModelTest {
    @Test fun sourceResistanceCausesBatteryDroop() {
        val source=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=0f,y=0f,
            parameters=mapOf("voltage" to 9.0,"internalResistance" to 1.0))
        val load=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=0f,y=0f,
            parameters=mapOf("resistance" to 8.0,"rating" to 20.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=0f,y=0f)
        val circuit=Circuit("Droop",listOf(source,load,ground),listOf(
            Wire(start=TerminalRef(source.id,0),end=TerminalRef(load.id,0)),
            Wire(start=TerminalRef(source.id,1),end=TerminalRef(load.id,1)),
            Wire(start=TerminalRef(source.id,1),end=TerminalRef(ground.id,0))))
        val result=DcSolver().solve(circuit)
        assertNull(result.error)
        assertEquals(8.0,result.readings.getValue(source.id).voltage,1e-6)
        assertEquals(1.0,result.readings.getValue(load.id).current,1e-6)
    }
    @Test fun fuseOpeningChangesCurrent() {
        val circuit=SampleCircuits.fuseFault()
        val fuse=circuit.components.first { it.kind==Kind.FUSE }
        val source=circuit.components.first { it.kind==Kind.BATTERY }
        val trace=TransientSolver().simulate(circuit,.05,.001)
        assertNull(trace.error)
        assertTrue(trace.frames.first().readings.getValue(fuse.id).current>3.0)
        assertEquals(Health.FAILED_OPEN,trace.frames.last().readings.getValue(fuse.id).health)
        assertEquals(0.0,trace.frames.last().readings.getValue(source.id).current,1e-7)
    }
    @Test fun resistorTemperatureUsesThermalTimeConstant() {
        val circuit=SampleCircuits.divider()
        val resistor=circuit.components.first { it.reference=="R1" }
        val trace=TransientSolver().simulate(circuit,.5,.001)
        assertNull(trace.error)
        val first=trace.frames.first().readings.getValue(resistor.id).estimatedTemperatureC!!
        val last=trace.frames.last().readings.getValue(resistor.id).estimatedTemperatureC!!
        assertTrue(last>first)
        assertTrue(last<25.1)
        assertEquals("Temperature",trace.traces.getValue(resistor.id).quantity)
    }
}
