package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.TransientSolver
import com.indianservers.circuitssimulator.simulation.TrapezoidalIntegration
import com.indianservers.circuitssimulator.simulation.Health
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

class TransientSolverTest {
    private val solver=TransientSolver()
    private fun wire(a: PlacedComponent,ai: Int,b: PlacedComponent,bi: Int)=
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))

    @Test fun blinkingLedAlternatesBetweenOnAndOff() {
        // Pure transient fixture; the Featured Project now blinks from executed board firmware.
        val generator=PlacedComponent(kind=Kind.FUNCTION_GENERATOR,reference="FG1",x=100f,y=200f,
            parameters=mapOf("frequency" to 2.0,"amplitude" to 4.5,"offset" to 4.5,
                "duty" to .5,"waveform" to 1.0))
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=300f,y=200f,
            parameters=mapOf("resistance" to 330.0))
        val led=PlacedComponent(kind=Kind.LED,reference="D1",x=500f,y=200f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=300f,y=500f)
        val circuit=Circuit("Square-wave fixture",listOf(generator,resistor,led,ground),listOf(
            wire(generator,0,resistor,0),wire(resistor,1,led,0),
            wire(led,1,generator,1),wire(generator,1,ground,0)))
        val result=solver.simulate(circuit,1.0,.002)
        assertNull(result.error)
        val currents=result.frames.mapNotNull { it.readings[led.id]?.current }
        assertTrue(currents.any { it > .01 })
        assertTrue(currents.any { abs(it) < .0001 })
    }

    @Test fun fiveVoltOneKiloOhmHundredMicrofaradMatchesTau() {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=0f,y=0f,
            parameters=mapOf("voltage" to 5.0))
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=0f,y=0f,
            parameters=mapOf("resistance" to 1000.0,"rating" to .25))
        val cap=PlacedComponent(kind=Kind.CAPACITOR,reference="C1",x=0f,y=0f,
            parameters=mapOf("capacitance" to 100e-6,"maxVoltage" to 16.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=0f,y=0f)
        val circuit=Circuit("RC golden",listOf(source,resistor,cap,ground),listOf(
            wire(source,0,resistor,0),wire(resistor,1,cap,0),
            wire(cap,1,source,1),wire(source,1,ground,0)))
        val tau=0.1
        val result=solver.simulate(circuit,5*tau,tau/50)
        assertNull(result.error)
        val atTau=result.traces.getValue(cap.id).samples.minBy { abs(it.timeSeconds-tau) }
        assertEquals(5.0*(1-exp(-1.0)),atTau.value,.08)
    }

    @Test fun rcChargingMatchesAnalyticCurve() {
        val circuit=SampleCircuits.rc()
        val cap=circuit.components.first { it.kind==Kind.CAPACITOR }
        val result=solver.simulate(circuit,.5,.001)
        assertNull(result.error)
        val sample=result.traces.getValue(cap.id).samples.minBy { abs(it.timeSeconds-.1) }
        assertEquals(9.0*(1-exp(-1.0)),sample.value,.05)
        val final=result.traces.getValue(cap.id).samples.last().value
        assertEquals(9.0*(1-exp(-5.0)),final,.05)
    }

    @Test fun rcDischargingDecays() {
        val cap=PlacedComponent(kind=Kind.CAPACITOR,reference="C1",x=0f,y=0f,
            parameters=mapOf("capacitance" to 100e-6,"initialVoltage" to 9.0,"maxVoltage" to 25.0))
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=0f,y=0f,
            parameters=mapOf("resistance" to 1000.0,"rating" to .25,"tolerance" to 5.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=0f,y=0f)
        val circuit=Circuit("RC discharge",listOf(cap,resistor,ground),listOf(
            wire(cap,0,resistor,0),wire(cap,1,resistor,1),wire(cap,1,ground,0)))
        val result=solver.simulate(circuit,.1,.001)
        assertNull(result.error)
        assertEquals(9.0/exp(1.0),result.traces.getValue(cap.id).samples.last().value,.05)
    }

    @Test fun rlRiseAndDecay() {
        val circuit=SampleCircuits.rl()
        val inductor=circuit.components.first { it.kind==Kind.INDUCTOR }
        val rise=solver.simulate(circuit,.05,.0001)
        assertNull(rise.error)
        val measured=rise.frameAt(.01)!!.readings.getValue(inductor.id).current
        assertEquals(.09*(1-exp(-1.0)),measured,.002)
        val resistor=circuit.components.first { it.kind==Kind.RESISTOR }
        val ground=circuit.components.first { it.kind==Kind.GROUND }
        val charged=inductor.copy(parameters=inductor.parameters+("initialCurrent" to .09))
        val decayCircuit=Circuit("RL decay",listOf(charged,resistor,ground),listOf(
            wire(charged,0,resistor,1),wire(charged,1,resistor,0),wire(charged,1,ground,0)))
        val decay=solver.simulate(decayCircuit,.01,.0001)
        assertNull(decay.error)
        assertEquals(.09/exp(1.0),abs(decay.frames.last().readings.getValue(charged.id).current),.002)
    }

    @Test fun smallerStepApproachesSameAnswer() {
        val circuit=SampleCircuits.rc();val cap=circuit.components.first { it.kind==Kind.CAPACITOR }
        val coarse=solver.simulate(circuit,.1,.002).traces.getValue(cap.id).samples.last().value
        val fine=solver.simulate(circuit,.1,.0005).traces.getValue(cap.id).samples.last().value
        assertEquals(fine,coarse,.05)
    }

    @Test fun trapezoidalRcAndRlMatchAnalyticResponses() {
        val trapezoidal=TransientSolver(TrapezoidalIntegration)
        val rc=SampleCircuits.rc()
        val cap=rc.components.first { it.kind==Kind.CAPACITOR }
        val vc=trapezoidal.simulate(rc,.1,.0005)
        assertNull(vc.error)
        assertEquals(9.0*(1-exp(-1.0)),vc.traces.getValue(cap.id).samples.last().value,.04)
        val rl=SampleCircuits.rl()
        val coil=rl.components.first { it.kind==Kind.INDUCTOR }
        val il=trapezoidal.simulate(rl,.01,.00005)
        assertNull(il.error)
        assertEquals(.09*(1-exp(-1.0)),il.frames.last().readings.getValue(coil.id).current,.002)
    }

    @Test fun rlcResponseCrossesZeroAndDecays() {
        val cap=PlacedComponent(kind=Kind.CAPACITOR,reference="C1",x=0f,y=0f,
            parameters=mapOf("capacitance" to 100e-6,"initialVoltage" to 9.0,"maxVoltage" to 25.0))
        val coil=PlacedComponent(kind=Kind.INDUCTOR,reference="L1",x=0f,y=0f,
            parameters=mapOf("inductance" to 1.0,"initialCurrent" to 0.0))
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=0f,y=0f,
            parameters=mapOf("resistance" to 100.0,"rating" to 1.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=0f,y=0f)
        val circuit=Circuit("Parallel RLC",listOf(cap,coil,resistor,ground),listOf(
            wire(cap,0,coil,0),wire(cap,0,resistor,0),wire(cap,1,coil,1),
            wire(cap,1,resistor,1),wire(cap,1,ground,0)))
        val result=TransientSolver(TrapezoidalIntegration).simulate(circuit,.1,.0001)
        assertNull(result.error)
        val samples=result.traces.getValue(cap.id).samples
        assertTrue(samples.any { it.timeSeconds>.025 && it.value<0.0 })
        assertTrue(abs(samples.last().value)<9.0)
    }

    @Test fun severeResistorStressFailsOpenAndChangesCircuitCurrent() {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=0f,y=0f,
            parameters=mapOf("voltage" to 10.0))
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=0f,y=0f,
            parameters=mapOf("resistance" to 10.0,"rating" to .25))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=0f,y=0f)
        val circuit=Circuit("Stress",listOf(source,resistor,ground),listOf(
            wire(source,0,resistor,0),wire(resistor,1,source,1),wire(source,1,ground,0)))
        val result=solver.simulate(circuit,.01,.001)
        assertNull(result.error)
        assertEquals(Health.OVERLOAD,result.frames.first().readings.getValue(resistor.id).health)
        assertEquals(Health.FAILED_OPEN,result.frames.last().readings.getValue(resistor.id).health)
        assertTrue(abs(result.frames.last().readings.getValue(source.id).current)<1e-8)
    }
}
