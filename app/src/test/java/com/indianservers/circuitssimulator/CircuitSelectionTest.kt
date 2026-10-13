package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import org.junit.Assert.*
import org.junit.Test

class CircuitSelectionTest {
    private val a=PlacedComponent(id="a",kind=Kind.RESISTOR,reference="R1",x=10f,y=20f)
    private val b=PlacedComponent(id="b",kind=Kind.RESISTOR,reference="R2",x=80f,y=20f)
    private val c=PlacedComponent(id="c",kind=Kind.RESISTOR,reference="R3",x=150f,y=20f)
    private val wire=Wire(id="ab",start=TerminalRef("a",1),end=TerminalRef("b",0),label="BUS",
        waypoints=listOf(WirePoint(50f,40f)))
    private val circuit=Circuit("Test",listOf(a,b,c),listOf(wire,
        Wire(id="bc",start=TerminalRef("b",1),end=TerminalRef("c",0))))

    @Test fun selectionExcludesExternalConnections() {
        val block=CircuitSelection.extract(circuit,setOf("a","b"))
        assertEquals(listOf(wire),block.wires)
        assertEquals(2,block.components.size)
    }
    @Test fun copyRemapsConnectivityAndIsolatesNamedNets() {
        val next=CircuitSelection.paste(circuit,CircuitSelection.extract(circuit,setOf("a","b")),64f,32f)
        val copies=next.components.takeLast(2)
        val copiedWire=next.wires.last()
        assertFalse(copies.any { it.id in setOf("a","b","c") })
        assertEquals(copies[0].id,copiedWire.start.componentId)
        assertEquals(copies[1].id,copiedWire.end.componentId)
        assertEquals(1,copiedWire.start.index)
        assertEquals("BUS_1",copiedWire.label)
        assertEquals(WirePoint(114f,72f),copiedWire.waypoints.single())
        assertEquals(next.components.size,next.components.map { it.reference }.distinct().size)
        assertEquals(74f,copies[0].x,0f)
    }
    @Test fun boardProgramAndMemoryMoveToNewOwners() {
        val board=PlacedComponent(id="board",kind=Kind.ARDUINO_UNO,reference="UNO1",x=0f,y=0f)
        val memory=PlacedComponent(id="memory",kind=Kind.I2C_EEPROM,reference="EE1",x=100f,y=0f)
        val block=Circuit("Board",listOf(board,memory),emptyList(),
            firmware=listOf(FirmwareAttachment("board",FirmwareLanguage.ARDUINO_SUBSET,"void loop() {}")),
            peripheralMemory=mapOf("memory" to mapOf(3 to 42)))
        val pasted=CircuitSelection.paste(Circuit("Empty",emptyList(),emptyList()),block)
        assertEquals(pasted.components[0].id,pasted.firmware.single().boardId)
        assertEquals(mapOf(3 to 42),pasted.peripheralMemory[pasted.components[1].id])
        val deleted=CircuitSelection.delete(pasted,pasted.components.map { it.id }.toSet())
        assertTrue(deleted.firmware.isEmpty())
        assertTrue(deleted.peripheralMemory.isEmpty())
    }
    @Test fun namedNetMatchingIsCaseInsensitiveAndAvoidsExistingSuffixes() {
        val block=circuit.copy(wires=listOf(wire,wire.copy(id="two",start=TerminalRef("b",1),end=TerminalRef("c",0),label="bus")))
        val target=circuit.copy(wires=circuit.wires+wire.copy(id="occupied",label="BUS_1"))
        val copied=CircuitSelection.paste(target,block).wires.takeLast(2)
        assertEquals(listOf("BUS_2","BUS_2"),copied.map { it.label })
    }
    @Test fun snapHandlesNegativeCoordinates() {
        assertEquals(-32f,CircuitSelection.snap(-20f,true,32f),0f)
        assertEquals(-20f,CircuitSelection.snap(-20f,false,32f),0f)
    }
}
