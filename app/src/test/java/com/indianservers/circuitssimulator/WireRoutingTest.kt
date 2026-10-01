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
}
