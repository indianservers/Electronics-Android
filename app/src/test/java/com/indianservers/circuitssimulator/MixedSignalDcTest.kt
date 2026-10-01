package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import com.indianservers.circuitssimulator.simulation.digital.*
import org.junit.Assert.*
import org.junit.Test

class MixedSignalDcTest {
    private fun fixture(inputVolts:Double):Triple<Circuit,TerminalRef,TerminalRef> {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=0f,y=0f,
            parameters=mapOf("voltage" to inputVolts))
        val load=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=200f,y=0f,
            parameters=mapOf("resistance" to 1000.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=100f,y=100f)
        val wires=listOf(Wire(start=TerminalRef(source.id,1),end=TerminalRef(ground.id,0)),
            Wire(start=TerminalRef(load.id,1),end=TerminalRef(ground.id,0)))
        return Triple(Circuit("Mixed gate",listOf(source,load,ground),wires),
            TerminalRef(source.id,0),TerminalRef(load.id,0))
    }

    @Test fun analogInputPropagatesThroughGateAtEventBoundaryAndLoadsAnalogOutput() {
        val (circuit,input,output)=fixture(5.0)
        val engine=DigitalEngine(listOf(LogicGate(listOf("sense"),"gate",GateType.NOT,
            PropagationDelay(1e-6,1e-6))))
        val run=MixedSignalDcScheduler().run(circuit,engine,mapOf("sense" to input),
            mapOf("gate" to output),LogicFamily.TTL_5V,2e-6,2e-6)
        assertNull(run.error)
        assertTrue(run.frames.any { kotlin.math.abs(it.timeSeconds-1e-6)<1e-12 })
        val last=run.frames.last()
        assertEquals(LogicState.HIGH,last.digital["sense"])
        assertEquals(LogicState.LOW,last.digital["gate"])
        assertTrue(last.analog.nodeVoltages.getValue(output) in 0.3..0.5)
    }

    @Test fun lowAnalogInputProducesLoadedHighOutput() {
        val (circuit,input,output)=fixture(.5)
        val engine=DigitalEngine(listOf(LogicGate(listOf("sense"),"gate",GateType.NOT,
            PropagationDelay(1e-6,1e-6))))
        val run=MixedSignalDcScheduler().run(circuit,engine,mapOf("sense" to input),
            mapOf("gate" to output),LogicFamily.TTL_5V,2e-6,2e-6)
        assertNull(run.error)
        assertEquals(LogicState.HIGH,run.frames.last().digital["gate"])
        assertTrue(run.frames.last().analog.nodeVoltages.getValue(output) in 4.0..4.5)
    }

    @Test fun opposingDigitalDriversReportContentionAndIntermediateVoltage() {
        val (circuit,_,output)=fixture(.5)
        val engine=DigitalEngine(emptyList())
        engine.drive("a",LogicState.HIGH,0.0)
        engine.drive("b",LogicState.LOW,0.0)
        val run=MixedSignalDcScheduler().run(circuit,engine,emptyMap(),
            mapOf("a" to output,"b" to output),LogicFamily.TTL_5V,1e-6,1e-6)
        assertNull(run.error)
        assertTrue(output in run.contentionTerminals)
        assertTrue(run.frames.last().analog.nodeVoltages.getValue(output) in 2.0..2.5)
        assertTrue(run.frames.last().digitalDriveCurrentsAmps.getValue("a")>0.03)
        assertTrue(run.frames.last().digitalDriveCurrentsAmps.getValue("b")< -0.03)
    }
}
