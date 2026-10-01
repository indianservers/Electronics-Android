package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.simulation.TransientSolver
import com.indianservers.circuitssimulator.simulation.AcSolver
import com.indianservers.circuitssimulator.simulation.SweepScale
import org.junit.Assert.*
import org.junit.Test

class PerformanceSmokeTest {
    private fun wire(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))

    private fun chain(count:Int):Circuit {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=0f,y=0f,
            parameters=mapOf("voltage" to 10.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=0f,y=0f)
        val resistors=(1..count).map { n -> PlacedComponent(kind=Kind.RESISTOR,
            reference="R$n",x=0f,y=0f,parameters=mapOf("resistance" to 100.0,"rating" to .25)) }
        val wires=mutableListOf(wire(source,0,resistors.first(),0))
        resistors.zipWithNext { a,b -> wires+=wire(a,1,b,0) }
        wires+=wire(resistors.last(),1,source,1)
        wires+=wire(source,1,ground,0)
        return Circuit("$count resistor chain",listOf(source,ground)+resistors,wires)
    }

    @Test fun smallMediumLargeDcAndTransientTiming() {
        // Timings are observations in test output, not hard pass/fail budgets on a shared host.
        for(count in listOf(10,50,100)) {
            val circuit=chain(count)
            val start=System.nanoTime()
            val dc=DcSolver().solve(circuit)
            val dcMs=(System.nanoTime()-start)/1e6
            assertNull(dc.error)
            val expected=10.0/(100.0*count)
            val first=circuit.components.first { it.kind==Kind.RESISTOR }
            assertEquals(expected,dc.readings.getValue(first.id).current,1e-7)
            val transientStart=System.nanoTime()
            val transient=TransientSolver().simulate(circuit,.001,.001)
            val transientMs=(System.nanoTime()-transientStart)/1e6
            assertNull(transient.error)
            println("PERF devices=${count+2} dcMs=$dcMs transientOneStepMs=$transientMs")
        }
    }
    @Test fun acHundredPointsAndTransientThousandStepsTiming() {
        val circuit=SampleCircuits.rcLowPass()
        val acStart=System.nanoTime()
        val ac=AcSolver().sweep(circuit,10.0,100000.0,100,SweepScale.LOGARITHMIC)
        val acMs=(System.nanoTime()-acStart)/1e6
        assertNull(ac.error)
        assertEquals(100,ac.points.size)
        val transientStart=System.nanoTime()
        val transient=TransientSolver().simulate(circuit,.1,.0001)
        val transientMs=(System.nanoTime()-transientStart)/1e6
        assertNull(transient.error)
        assertTrue(transient.frames.size>=999)
        println("PERF ac100Ms=$acMs transient1000Ms=$transientMs frames=${transient.frames.size}")
    }
}
