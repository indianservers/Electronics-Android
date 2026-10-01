package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.Multimeter
import org.junit.Assert.*
import org.junit.Test

class MultimeterTest {
    private fun wire(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))

    @Test fun voltageCurrentAndResistanceComeFromCircuitModel() {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=0f,y=0f,
            parameters=mapOf("voltage" to 10.0))
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=0f,y=0f,
            parameters=mapOf("resistance" to 1000.0,"rating" to .25))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=0f,y=0f)
        val feed=wire(source,0,resistor,0)
        val circuit=Circuit("Meter",listOf(source,resistor,ground),listOf(feed,
            wire(resistor,1,source,1),wire(source,1,ground,0)))
        val meter=Multimeter()
        val plus=TerminalRef(resistor.id,0);val common=TerminalRef(resistor.id,1)
        val voltage=meter.voltage(circuit,plus,common)
        assertNull(voltage.message)
        assertEquals(10.0,voltage.value!!,.001)
        val current=meter.current(circuit,feed.id)
        assertNull(current.message)
        assertEquals(.01,kotlin.math.abs(current.value!!),1e-5)
        val ohms=meter.resistance(circuit,plus,common)
        assertNull(ohms.message)
        assertEquals(0.0,ohms.value!!,.01) // Deactivated ideal source shorts this branch.
        val standalone=Circuit("Resistor only",listOf(resistor,ground),listOf(
            wire(resistor,1,ground,0)))
        val standaloneOhms=meter.resistance(standalone,plus,common)
        assertNull(standaloneOhms.message)
        assertEquals(1000.0,standaloneOhms.value!!,1.0)
    }

    @Test fun continuityRespondsToSwitchContact() {
        val switch=PlacedComponent(kind=Kind.SWITCH,reference="SW1",x=0f,y=0f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=0f,y=0f)
        val probes=TerminalRef(switch.id,0) to TerminalRef(switch.id,1)
        val closed=Circuit("Contact",listOf(switch,ground),listOf(
            wire(switch,1,ground,0)))
        val meter=Multimeter()
        assertEquals("Closed",meter.continuity(closed,probes.first,probes.second).unit)
        val open=closed.copy(components=listOf(switch.copy(closed=false),ground))
        assertEquals("Open",meter.continuity(open,probes.first,probes.second).unit)
    }

    @Test fun meterSamplesFunctionGeneratorAtCurrentTime() {
        val generator=PlacedComponent(kind=Kind.FUNCTION_GENERATOR,reference="FG1",x=0f,y=0f,
            parameters=mapOf("amplitude" to 2.5,"frequency" to 10.0,"offset" to 0.0,
                "waveform" to 0.0,"phase" to 0.0))
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=0f,y=0f,
            parameters=mapOf("resistance" to 1000.0,"rating" to .25))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=0f,y=0f)
        val feed=wire(generator,0,resistor,0)
        val circuit=Circuit("Signal meter",listOf(generator,resistor,ground),listOf(feed,
            wire(generator,1,resistor,1),wire(generator,1,ground,0)))
        val meter=Multimeter()
        val plus=TerminalRef(resistor.id,0);val common=TerminalRef(resistor.id,1)
        assertEquals(2.5,meter.voltage(circuit,plus,common,.025).value!!,.001)
        assertEquals(-2.5,meter.voltage(circuit,plus,common,.075).value!!,.001)
        assertEquals(.0025,kotlin.math.abs(meter.current(circuit,feed.id,.025).value!!),1e-5)
    }
}
