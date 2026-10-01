package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent
import com.indianservers.circuitssimulator.ui.canvas.terminalName
import com.indianservers.circuitssimulator.ui.canvas.terminalPosition
import org.junit.Assert.*
import org.junit.Test

class ComponentTerminalsTest {
    @Test fun everyComponentPinHasUniqueVisibleLocationAndName() {
        Kind.entries.forEach { kind ->
            val part=PlacedComponent(kind=kind,reference="T1",x=300f,y=400f)
            val pins=(0 until part.terminalCount).map { terminalPosition(part,it) }
            assertEquals("$kind",part.terminalCount,pins.distinct().size)
            if(kind!=Kind.JUNCTION) (0 until part.terminalCount).forEach {
                assertTrue("$kind pin $it",terminalName(part,it).isNotEmpty())
            }
        }
    }

    @Test fun polarityAndThreeTerminalPinsMatchElectricalIndices() {
        val battery=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=300f,y=400f)
        assertEquals("+",terminalName(battery,0))
        assertEquals("−",terminalName(battery,1))
        assertTrue(terminalPosition(battery,0).y < terminalPosition(battery,1).y)
        val opAmp=PlacedComponent(kind=Kind.OPAMP,reference="U1",x=300f,y=400f)
        assertEquals(listOf("+","−","OUT"),(0..2).map { terminalName(opAmp,it) })
        assertTrue(terminalPosition(opAmp,0).y < terminalPosition(opAmp,1).y)
        assertTrue(terminalPosition(opAmp,2).x > terminalPosition(opAmp,1).x)
    }

    @Test fun rotationMovesElectricalPinWithArtwork() {
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=300f,y=400f)
        val rotated=resistor.copy(rotation=90)
        assertEquals(300f,terminalPosition(rotated,0).x,0.001f)
        assertTrue(terminalPosition(rotated,0).y < 400f)
        assertTrue(terminalPosition(rotated,1).y > 400f)
    }
}
