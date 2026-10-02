package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PhaseOnePrimitiveTest {
    private fun part(kind:Kind,ref:String)=PlacedComponent(kind=kind,reference=ref,x=0f,y=0f)
    private fun wire(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))
    private val newKinds=PhaseOneParts.definitions.map { it.kind }

    @Test fun everyNewPartHasPinsModelAndSearchIdentity() {
        assertEquals(17,newKinds.size)
        assertEquals(newKinds.size,newKinds.distinct().size)
        for(kind in newKinds) {
            val definition=ComponentRegistry.definitions.getValue(kind)
            assertTrue(kind.title,definition.id.isNotBlank())
            assertTrue(kind.title,definition.modelId.isNotBlank())
            assertEquals(kind.title,2,part(kind,"X1").terminalCount)
            assertTrue(kind.title,ComponentRegistry.search(kind.title).any { it.kind==kind })
        }
    }

    @Test fun everyNewPartSolvesInAConnectedCircuit() {
        for(kind in newKinds) {
            val device=part(kind,"X1")
            val supply=part(Kind.SOURCE,"V1").copy(parameters=mapOf("voltage" to 5.0))
            val resistor=part(Kind.RESISTOR,"R1").copy(parameters=mapOf("resistance" to 330.0))
            val ground=part(Kind.GROUND,"G1")
            val sourceKind=kind in setOf(Kind.SINGLE_CELL,Kind.BATTERY_PACK,Kind.VARIABLE_DC_SUPPLY,
                Kind.DC_CURRENT_SOURCE,Kind.AC_VOLTAGE_SOURCE,Kind.SINE_GENERATOR,
                Kind.SQUARE_GENERATOR,Kind.PULSE_GENERATOR)
            val circuit=if(sourceKind) Circuit(kind.title,listOf(device,resistor,ground),listOf(
                wire(device,0,resistor,0),wire(resistor,1,ground,0),wire(device,1,ground,0)))
            else Circuit(kind.title,listOf(supply,resistor,device,ground),listOf(
                wire(supply,0,resistor,0),wire(resistor,1,device,0),
                wire(device,1,ground,0),wire(supply,1,ground,0)))
            val dc=DcSolver().solve(circuit)
            assertNull("$kind DC ${dc.error}",dc.error)
            assertTrue("$kind DC finite",dc.readings.getValue(device.id).current.isFinite())
            val transient=TransientSolver().simulate(circuit,.002,.001)
            assertNull("$kind transient ${transient.error}",transient.error)
            assertTrue("$kind transient finite",transient.frames.last().readings.getValue(device.id).current.isFinite())
        }
    }

    @Test fun distinctElectricalEffectsAreObservable() {
        val cell=part(Kind.SINGLE_CELL,"C1")
        val pack=part(Kind.BATTERY_PACK,"P1")
        assertEquals(1.5,cell.electricalPrimitive().value("voltage"),1e-9)
        assertEquals(6.0,pack.electricalPrimitive().value("voltage"),1e-9)
        val rheostat=part(Kind.RHEOSTAT,"RV1")
        val low=rheostat.copy(parameters=rheostat.parameters+("position" to 0.0)).electricalPrimitive().value("resistance")
        val high=rheostat.copy(parameters=rheostat.parameters+("position" to 1.0)).electricalPrimitive().value("resistance")
        assertTrue(high>low*1000)
        val ptc=part(Kind.PTC_THERMISTOR,"TH1")
        val hot=ptc.copy(parameters=ptc.parameters+("temperatureC" to 100.0))
        assertTrue(hot.electricalPrimitive().value("resistance")>ptc.electricalPrimitive().value("resistance"))
        val cap=part(Kind.VARIABLE_CAPACITOR,"CV1")
        assertTrue(cap.copy(parameters=cap.parameters+("position" to 1.0)).electricalPrimitive().value("capacitance")>
            cap.copy(parameters=cap.parameters+("position" to 0.0)).electricalPrimitive().value("capacitance"))
        assertTrue(part(Kind.NC_PUSH_BUTTON,"PB1").closed)
    }
}
