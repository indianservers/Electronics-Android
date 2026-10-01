package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

class CircuitDiagnosticsTest {
    @Test fun bundledSamplesHaveNoFloatingIslandOrContradictorySources() {
        val samples=listOf(SampleCircuits.led(),SampleCircuits.lamp(),SampleCircuits.divider(),
            SampleCircuits.rc(),SampleCircuits.rl(),SampleCircuits.generator(),
            SampleCircuits.rcLowPass(),SampleCircuits.rcHighPass(),SampleCircuits.rlLowPass(),
            SampleCircuits.lightDivider(),SampleCircuits.potentiometerDemo(),
            SampleCircuits.fuseFault(),SampleCircuits.nonInvertingOpAmp(),
            SampleCircuits.npnSwitch(),SampleCircuits.nmosSwitch())
        samples.forEach { circuit ->
            val codes=CircuitDiagnostics.inspect(circuit).map { it.code }
            assertFalse(circuit.name,"FLOATING_ISLAND" in codes)
            assertFalse(circuit.name,"SOURCE_CONFLICT" in codes)
        }
    }

    @Test fun disconnectedGroupWithItsOwnWiresIsReported() {
        val circuit=SampleCircuits.divider()
        val a=PlacedComponent(kind=Kind.RESISTOR,reference="R99",x=900f,y=200f)
        val b=PlacedComponent(kind=Kind.RESISTOR,reference="R100",x=900f,y=400f)
        val isolated=circuit.copy(components=circuit.components+listOf(a,b),
            wires=circuit.wires+Wire(start=TerminalRef(a.id,1),end=TerminalRef(b.id,0)))
        assertTrue(CircuitDiagnostics.inspect(isolated).any { it.code=="FLOATING_ISLAND" &&
            it.message.contains("R99") })
    }

    @Test fun contradictoryParallelIdealSourcesAreReported() {
        val first=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=100f,y=100f,
            parameters=mapOf("voltage" to 9.0))
        val second=PlacedComponent(kind=Kind.BATTERY,reference="B2",x=300f,y=100f,
            parameters=mapOf("voltage" to 5.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=200f,y=300f)
        val circuit=Circuit("Conflicting supplies",listOf(first,second,ground),listOf(
            Wire(start=TerminalRef(first.id,0),end=TerminalRef(second.id,0)),
            Wire(start=TerminalRef(first.id,1),end=TerminalRef(second.id,1)),
            Wire(start=TerminalRef(first.id,1),end=TerminalRef(ground.id,0))))
        assertTrue(CircuitDiagnostics.inspect(circuit).any { it.code=="SOURCE_CONFLICT" &&
            it.level==DiagnosticLevel.ERROR })
    }

    @Test fun directlyShortedSourceIsExplained() {
        val source=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=0f,y=0f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=100f,y=0f)
        val circuit=Circuit("Short",listOf(source,ground),listOf(
            Wire(start=TerminalRef(source.id,0),end=TerminalRef(ground.id,0)),
            Wire(start=TerminalRef(source.id,1),end=TerminalRef(ground.id,0))))
        val issues=CircuitDiagnostics.inspect(circuit,DcSolver().solve(circuit))
        assertTrue(issues.any { it.code=="SOURCE_SHORT" && it.level==DiagnosticLevel.ERROR })
    }

    @Test fun componentWarningsIncludeOrientationAndRatings() {
        val led=PlacedComponent(kind=Kind.LED,reference="D1",x=0f,y=0f)
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=100f,y=0f)
        val circuit=Circuit("Warnings",listOf(led,resistor),emptyList())
        val result=DcResult(readings=mapOf(led.id to Reading(-5.0,-.03,.15),
            resistor.id to Reading(20.0,.1,2.0)))
        val codes=CircuitDiagnostics.inspect(circuit,result).map { it.code }.toSet()
        assertTrue("NO_GROUND" in codes)
        assertTrue("REVERSE_LED" in codes)
        assertTrue("LED_CURRENT" in codes)
        assertTrue("POWER_RATING" in codes)
        assertTrue("UNWIRED_PIN" in codes)
    }
}
