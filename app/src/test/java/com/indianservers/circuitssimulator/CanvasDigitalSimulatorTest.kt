package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.CanvasDigitalSimulator
import com.indianservers.circuitssimulator.simulation.AcSolver
import com.indianservers.circuitssimulator.simulation.SweepScale
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import org.junit.Assert.*
import org.junit.Test

class CanvasDigitalSimulatorTest {
    private fun wire(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))

    @Test fun acSweepRejectsDigitalCircuitInsteadOfReturningAnAnalogOnlyResult() {
        val result=AcSolver().sweep(SampleCircuits.digitalInverter(),10.0,100.0,10,SweepScale.LINEAR)
        assertTrue(result.points.isEmpty())
        assertTrue(result.error?.contains("digital") == true)
    }

    @Test fun sampleClockedInverterActuallyBlinksLed() {
        val circuit=SampleCircuits.digitalInverter()
        val led=circuit.components.first { it.kind==Kind.LED }
        val result=CanvasDigitalSimulator().simulate(circuit,.25,.0005)
        assertNull(result.error)
        val currents=result.frames.map { it.readings.getValue(led.id).current }
        assertTrue(currents.any { it>.001 })
        assertTrue(currents.any { it<.00001 })
    }

    @Test fun unwiredInputRemainsUnknownInsteadOfReadingGroundLeakage() {
        val gate=PlacedComponent(kind=Kind.NOT_GATE,reference="U1",x=0f,y=0f)
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=150f,y=0f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=150f,y=150f)
        val circuit=Circuit("Open input",listOf(gate,resistor,ground),listOf(
            wire(gate,1,resistor,0),wire(resistor,1,ground,0)))
        val result=CanvasDigitalSimulator().simulate(circuit,.001,.0002)
        assertNull(result.error)
        assertEquals(LogicState.UNKNOWN,result.frames.last().digitalStates[TerminalRef(gate.id,0)])
        assertEquals(LogicState.UNKNOWN,result.frames.last().digitalStates[TerminalRef(gate.id,1)])
    }

    @Test fun lightSensorVoltageCrossesLogicThresholdAndChangesLedOutput() {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=0f,y=0f,
            parameters=mapOf("voltage" to 5.0))
        val fixed=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=100f,y=0f,
            parameters=mapOf("resistance" to 10000.0))
        val sensor=PlacedComponent(kind=Kind.LDR,reference="LDR1",x=200f,y=0f)
        val gate=PlacedComponent(kind=Kind.NOT_GATE,reference="U1",x=300f,y=0f)
        val limit=PlacedComponent(kind=Kind.RESISTOR,reference="R2",x=400f,y=0f,
            parameters=mapOf("resistance" to 330.0))
        val led=PlacedComponent(kind=Kind.LED,reference="D1",x=500f,y=0f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=300f,y=150f)
        fun circuit(illuminance:Double):Circuit {
            val adjusted=sensor.copy(parameters=mapOf("illuminanceLux" to illuminance))
            return Circuit("Light logic",listOf(source,fixed,adjusted,gate,limit,led,ground),listOf(
                wire(source,0,fixed,0),wire(fixed,1,adjusted,0),wire(adjusted,0,gate,0),
                wire(adjusted,1,ground,0),wire(source,1,ground,0),
                wire(gate,1,limit,0),wire(limit,1,led,0),wire(led,1,ground,0)))
        }
        val dark=CanvasDigitalSimulator().simulate(circuit(100.0),.002,.0002)
        val light=CanvasDigitalSimulator().simulate(circuit(1000.0),.002,.0002)
        assertNull(dark.error)
        assertNull(light.error)
        assertEquals(LogicState.LOW,dark.frames.last().digitalStates[TerminalRef(gate.id,1)])
        assertEquals(LogicState.HIGH,light.frames.last().digitalStates[TerminalRef(gate.id,1)])
        assertTrue(light.frames.last().readings.getValue(led.id).current>
            dark.frames.last().readings.getValue(led.id).current+.001)
    }

    @Test fun wiredInverterSensesAnalogSourceAndDrivesLoadedOutput() {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=0f,y=0f,
            parameters=mapOf("voltage" to 3.3))
        val gate=PlacedComponent(kind=Kind.NOT_GATE,reference="U1",x=150f,y=0f)
        val load=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=300f,y=0f,
            parameters=mapOf("resistance" to 1000.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=150f,y=150f)
        val circuit=Circuit("Wired inverter",listOf(source,gate,load,ground),listOf(
            wire(source,0,gate,0),wire(source,1,ground,0),
            wire(gate,1,load,0),wire(load,1,ground,0)))
        val result=CanvasDigitalSimulator().simulate(circuit,.002,.0002)
        assertNull(result.error)
        val last=result.frames.last()
        assertEquals(LogicState.HIGH,last.digitalStates[TerminalRef(gate.id,0)])
        assertEquals(LogicState.LOW,last.digitalStates[TerminalRef(gate.id,1)])
        assertTrue(last.nodeVoltages.getValue(TerminalRef(gate.id,1)) in .18..0.21)
        assertTrue(last.readings.getValue(load.id).current>0.0)
    }

    @Test fun clockEdgesChargeAndDischargeCapacitorAtDigitalEventBoundaries() {
        val clock=PlacedComponent(kind=Kind.CLOCK,reference="CLK1",x=0f,y=0f,
            parameters=mapOf("frequency" to 100.0,"duty" to .5))
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=150f,y=0f,
            parameters=mapOf("resistance" to 1000.0))
        val cap=PlacedComponent(kind=Kind.CAPACITOR,reference="C1",x=300f,y=0f,
            parameters=mapOf("capacitance" to 1e-6,"initialVoltage" to 0.0,"maxVoltage" to 25.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=300f,y=150f)
        val circuit=Circuit("Clock RC",listOf(clock,resistor,cap,ground),listOf(
            wire(clock,0,resistor,0),wire(resistor,1,cap,0),wire(cap,1,ground,0)))
        val result=CanvasDigitalSimulator().simulate(circuit,.02,.0005)
        assertNull(result.error)
        val output=TerminalRef(clock.id,0)
        assertTrue(result.frames.any { it.digitalStates[output]==LogicState.HIGH })
        assertTrue(result.frames.any { it.digitalStates[output]==LogicState.LOW })
        assertTrue(result.frames.any { kotlin.math.abs(it.timeSeconds-.005)<1e-10 })
        val high=result.frameAt(.0045)!!.nodeVoltages.getValue(TerminalRef(cap.id,0))
        val low=result.frameAt(.0095)!!.nodeVoltages.getValue(TerminalRef(cap.id,0))
        assertTrue(high>2.0)
        assertTrue(low<1.0)
    }

    @Test fun gateOutputDrivesAnotherGateThroughCanvasWire() {
        val clock=PlacedComponent(kind=Kind.CLOCK,reference="CLK1",x=0f,y=0f,
            parameters=mapOf("frequency" to 100.0,"duty" to .5))
        val first=PlacedComponent(kind=Kind.NOT_GATE,reference="U1",x=150f,y=0f)
        val second=PlacedComponent(kind=Kind.NOT_GATE,reference="U2",x=300f,y=0f)
        val load=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=450f,y=0f,
            parameters=mapOf("resistance" to 1000.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=450f,y=150f)
        val circuit=Circuit("Gate chain",listOf(clock,first,second,load,ground),listOf(
            wire(clock,0,first,0),wire(first,1,second,0),wire(second,1,load,0),
            wire(load,1,ground,0)))
        val run=CanvasDigitalSimulator().simulate(circuit,.012,.0002)
        assertNull(run.error)
        val early=run.frameAt(.004)!!
        val late=run.frameAt(.009)!!
        assertEquals(LogicState.HIGH,early.digitalStates[TerminalRef(second.id,1)])
        assertEquals(LogicState.LOW,late.digitalStates[TerminalRef(second.id,1)])
    }
}
