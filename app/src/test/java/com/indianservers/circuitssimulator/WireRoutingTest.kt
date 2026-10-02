package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.routedWirePoints
import com.indianservers.circuitssimulator.ui.canvas.terminalPosition
import org.junit.Assert.*
import org.junit.Test

class WireRoutingTest {
    @Test fun returnWireAvoidsBatteryAndResistorBodies() {
        val battery=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=230f,y=380f)
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=600f,y=370f)
        val wire=Wire(start=TerminalRef(resistor.id,1),end=TerminalRef(battery.id,1))
        val points=routedWirePoints(wire,mapOf(battery.id to battery,resistor.id to resistor))
        assertEquals(terminalPosition(resistor,1),points.first())
        assertEquals(terminalPosition(battery,1),points.last())
        assertTrue(points.size>=4)
        // The horizontal return path must run below both component bodies.
        assertTrue(points.zipWithNext().filter { it.first.y==it.second.y && it.first.x!=it.second.x }
            .any { it.first.y>450f })
    }

    @Test fun routedWireEndsFollowResizedLedDuringRotation() {
        val led=PlacedComponent(kind=Kind.LED,reference="D1",x=700f,y=450f,sizeScale=1.6f)
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=450f,y=250f)
        val wire=Wire(start=TerminalRef(resistor.id,1),end=TerminalRef(led.id,0))
        val parts=mapOf(led.id to led,resistor.id to resistor)
        val points=routedWirePoints(wire,parts,mapOf(led.id to 45f))
        assertEquals(terminalPosition(resistor,1),points.first())
        assertEquals(terminalPosition(led,0,45f),points.last())
    }
}
