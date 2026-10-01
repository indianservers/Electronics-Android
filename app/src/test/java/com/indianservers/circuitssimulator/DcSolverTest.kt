package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcSolver
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class DcSolverTest {
    @Test fun emptyNewCircuitIsReadyForEditing() {
        val result=DcSolver().solve(Circuit("New",emptyList(),emptyList()))
        assertNull(result.error)
        assertTrue(result.readings.isEmpty())
    }
    private val solver = DcSolver()
    private fun w(a: PlacedComponent, ai: Int, b: PlacedComponent, bi: Int) =
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))
    private fun source(voltage: Double) = PlacedComponent(kind=Kind.SOURCE,reference="V1",x=0f,y=0f,
        parameters=mapOf("voltage" to voltage))
    private fun resistor(ref: String, ohms: Double) = PlacedComponent(kind=Kind.RESISTOR,reference=ref,x=0f,y=0f,
        parameters=mapOf("resistance" to ohms,"rating" to 1.0,"tolerance" to 5.0))
    private fun ground() = PlacedComponent(kind=Kind.GROUND,reference="GND1",x=0f,y=0f)

    @Test fun resistorTenVoltsOneKilohm() {
        val v=source(10.0);val r=resistor("R1",1000.0);val g=ground()
        val result=solver.solve(Circuit("test",listOf(v,r,g),listOf(w(v,0,r,0),w(r,1,v,1),w(v,1,g,0))))
        assertNull(result.error)
        assertEquals(.01,abs(result.readings.getValue(r.id).current),1e-5)
    }
    @Test fun seriesAndDivider() {
        val v=source(10.0);val a=resistor("R1",1000.0);val b=resistor("R2",1000.0);val g=ground()
        val result=solver.solve(Circuit("test",listOf(v,a,b,g),listOf(w(v,0,a,0),w(a,1,b,0),w(b,1,v,1),w(v,1,g,0))))
        assertNull(result.error)
        assertEquals(.005,abs(result.readings.getValue(a.id).current),1e-5)
        assertEquals(5.0,result.nodeVoltages.getValue(TerminalRef(a.id,1)),1e-3)
    }
    @Test fun switchControlsLedAndResistanceChangesCurrent() {
        val demo=SampleCircuits.led()
        val closed=solver.solve(demo)
        assertNull(closed.error)
        val led=demo.components.first { it.kind==Kind.LED }
        val switch=demo.components.first { it.kind==Kind.SWITCH }
        val resistor=demo.components.first { it.kind==Kind.RESISTOR }
        val open=solver.solve(demo.copy(components=demo.components.map { if(it.id==switch.id) it.copy(closed=false) else it }))
        assertNull(open.error)
        assertTrue(abs(open.readings.getValue(led.id).current)<1e-7)
        val higher=solver.solve(demo.copy(components=demo.components.map { if(it.id==resistor.id) it.copy(parameters=it.parameters+("resistance" to 680.0)) else it }))
        assertTrue(abs(higher.readings.getValue(led.id).current)<abs(closed.readings.getValue(led.id).current))
    }
    @Test fun reversedLedBlocksAndShortFails() {
        val demo=SampleCircuits.led();val led=demo.components.first { it.kind==Kind.LED }
        val reversed=demo.copy(wires=demo.wires.map { wire -> wire.copy(
            start=if(wire.start.componentId==led.id) wire.start.copy(index=1-wire.start.index) else wire.start,
            end=if(wire.end.componentId==led.id) wire.end.copy(index=1-wire.end.index) else wire.end) })
        val result=solver.solve(reversed)
        assertNull(result.error)
        assertTrue(abs(result.readings.getValue(led.id).current)<1e-6)
        val battery=demo.components.first { it.kind==Kind.BATTERY }
        val short=solver.solve(demo.copy(wires=demo.wires+w(battery,0,battery,1)))
        assertTrue(short.error?.contains("short",true)==true)
    }
    @Test fun missingGroundReportsError() {
        val demo=SampleCircuits.led()
        assertTrue(solver.solve(demo.copy(components=demo.components.filterNot { it.kind==Kind.GROUND })).error?.contains("ground",true)==true)
    }
    @Test fun isolatedPartReportsFloatingNode() {
        val demo=SampleCircuits.led()
        val extra=resistor("R2",1000.0)
        val result=solver.solve(demo.copy(components=demo.components+extra))
        assertTrue(result.error?.contains("floating",true)==true)
    }
    @Test fun builtInLampAndDividerSolve() {
        val lamp=SampleCircuits.lamp()
        val lampResult=solver.solve(lamp)
        assertNull(lampResult.error)
        val bulb=lamp.components.first { it.kind==Kind.LAMP }
        assertEquals(.09,abs(lampResult.readings.getValue(bulb.id).current),1e-4)
        val divider=SampleCircuits.divider()
        val dividerResult=solver.solve(divider)
        assertNull(dividerResult.error)
        val r1=divider.components.first { it.reference=="R1" }
        assertEquals(5.0,dividerResult.nodeVoltages.getValue(TerminalRef(r1.id,1)),1e-3)
    }
    @Test fun explicitJunctionPreservesTopology() {
        val demo=SampleCircuits.led()
        val original=solver.solve(demo)
        val junction=PlacedComponent(kind=Kind.JUNCTION,reference="J1",x=200f,y=300f)
        val first=demo.wires.first()
        val split=demo.copy(components=demo.components+junction,wires=demo.wires.drop(1)+
            Wire(start=first.start,end=TerminalRef(junction.id,0))+
            Wire(start=TerminalRef(junction.id,0),end=first.end))
        val result=solver.solve(split)
        assertNull(result.error)
        val led=demo.components.first { it.kind==Kind.LED }
        assertEquals(original.readings.getValue(led.id).current,result.readings.getValue(led.id).current,1e-7)
    }
}
