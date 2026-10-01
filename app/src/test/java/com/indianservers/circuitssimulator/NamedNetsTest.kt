package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.simulation.Multimeter
import org.junit.Assert.*
import org.junit.Test

class NamedNetsTest {
    @Test fun matchingLabelsJoinSeparateWireSegmentsElectrically() {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=100f,y=200f,
            parameters=mapOf("voltage" to 10.0))
        val first=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=300f,y=100f,
            parameters=mapOf("resistance" to 1000.0))
        val second=PlacedComponent(kind=Kind.RESISTOR,reference="R2",x=500f,y=100f,
            parameters=mapOf("resistance" to 1000.0))
        val a=PlacedComponent(kind=Kind.JUNCTION,reference="J1",x=300f,y=250f)
        val b=PlacedComponent(kind=Kind.JUNCTION,reference="J2",x=500f,y=250f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=400f,y=400f)
        fun wire(p:PlacedComponent,pin:Int,q:PlacedComponent,other:Int,label:String?=null)=
            Wire(start=TerminalRef(p.id,pin),end=TerminalRef(q.id,other),label=label)
        val circuit=Circuit("Labeled connection",listOf(source,first,second,a,b,ground),listOf(
            wire(source,0,first,0),wire(first,1,a,0,"LINK"),wire(second,0,b,0,"link"),
            wire(second,1,source,1),wire(source,1,ground,0)))
        val result=DcSolver().solve(circuit)
        assertNull(result.error)
        assertEquals(.005,result.readings.getValue(first.id).current,1e-6)
        assertEquals(.005,result.readings.getValue(second.id).current,1e-6)
        val meter=Multimeter().current(circuit,circuit.wires[0].id)
        assertNull(meter.message)
        assertEquals(.005,kotlin.math.abs(meter.value!!),1e-5)
        assertNotNull(Multimeter().current(circuit,circuit.wires[1].id).message)
    }
}
