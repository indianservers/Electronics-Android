package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Assert.*
import org.junit.Test

class FirmwareRuntimeTest {
    private fun part(kind:Kind,ref:String,values:Map<String,Double> = emptyMap())=
        PlacedComponent(kind=kind,reference=ref,x=100f,y=100f,
            parameters=ComponentRegistry.definitions.getValue(kind).parameters.associate { it.key to it.default }+values)
    private fun wire(a:PlacedComponent,ap:Int,b:PlacedComponent,bp:Int)=
        Wire(start=TerminalRef(a.id,ap),end=TerminalRef(b.id,bp))
    private fun boardCircuit():Pair<Circuit,PlacedComponent> {
        val board=part(Kind.ARDUINO_UNO,"UNO1")
        val source=part(Kind.SOURCE,"V1",mapOf("voltage" to 5.0))
        val ground=part(Kind.GROUND,"GND1")
        val resistor=part(Kind.RESISTOR,"R1",mapOf("resistance" to 330.0))
        val led=part(Kind.LED,"D1")
        val pins=BoardRegistry.boards.getValue(board.kind)
        return Circuit("Firmware LED",listOf(board,source,ground,resistor,led),listOf(
            wire(source,0,board,pins.index("5V")),wire(source,1,board,pins.index("GND")),
            wire(source,1,ground,0),wire(board,pins.index("D13"),resistor,0),
            wire(resistor,1,led,0),wire(led,1,ground,0))) to board
    }
    private fun session(code:String):Pair<FirmwareSession,Circuit> {
        val (c,b)=boardCircuit();val board=FirmwareBoard(c,b.id)
        return FirmwareSession(ArduinoSubset.compile(code,board),board) to c
    }

    @Test fun parsedBlinkDrivesActualLedCurrentOnVirtualTime() {
        val (firmware,c)=session("""
            void setup() { pinMode(13, OUTPUT); }
            void loop() { digitalWrite(13, HIGH); delay(500); digitalWrite(13, LOW); delay(500); }
        """.trimIndent())
        val led=c.components.first { it.reference=="D1" }
        val first=firmware.advance(0)
        assertNull(first.error)
        assertTrue("${first.frames}",first.frames.last().result.readings.getValue(led.id).current>.001)
        val later=firmware.advance(500_000)
        assertNull(later.error)
        assertTrue(later.frames.last().result.readings.getValue(led.id).current<1e-5)
        val repeat=firmware.advance(500_000)
        assertNull(repeat.error)
        assertTrue(repeat.frames.last().result.readings.getValue(led.id).current>.001)
    }

    @Test fun parserRejectsUnsupportedAndInvalidPwmPin() {
        val (c,b)=boardCircuit();val board=FirmwareBoard(c,b.id)
        val invalid="void setup() { analogWrite(2, 128); } void loop() { delay(1); }"
        assertTrue(runCatching { ArduinoSubset.compile(invalid,board) }.exceptionOrNull()!!.message!!
            .contains("Line 1"))
        val unsupported="void setup() { WiFi.begin(1); } void loop() { delay(1); }"
        assertTrue(runCatching { ArduinoSubset.compile(unsupported,board) }.exceptionOrNull()!!.message!!
            .contains("Unsupported API"))
    }

    @Test fun pwmGeneratesHighAndLowCircuitFrames() {
        val (firmware,c)=session("""
            void setup() { pinMode(13, OUTPUT); analogWrite(9, 128); }
            void loop() { delay(100); }
        """.trimIndent())
        val board=c.components.first { it.kind==Kind.ARDUINO_UNO }
        val pin=TerminalRef(board.id,BoardRegistry.boards.getValue(board.kind).index("D9"))
        val result=firmware.advance(10_000)
        assertNull(result.error)
        assertTrue(result.frames.size>4)
        assertTrue(result.frames.mapNotNull { it.result.nodeVoltages[pin] }.max()>3.0)
        assertTrue(result.frames.mapNotNull { it.result.nodeVoltages[pin] }.min()<1.0)
    }

    @Test fun serialOutputComesFromExecutedBranches() {
        val (firmware,_)=session("""
            void setup() { Serial.begin(9600); }
            void loop() { int x = 2; if (x > 1) { Serial.println("HIGH"); } else { Serial.println("LOW"); } delay(1000); }
        """.trimIndent())
        val result=firmware.advance(0)
        assertNull(result.error)
        assertEquals(listOf("HIGH"),result.console)
    }

    @Test fun internalPullupAndPhysicalButtonDriveDigitalRead() {
        val (base,part)=boardCircuit()
        val button=part(Kind.SWITCH,"SW1").copy(closed=false)
        val ground=base.components.first { it.kind==Kind.GROUND }
        val input=BoardRegistry.boards.getValue(part.kind).index("D2")
        val circuit=base.copy(components=base.components+button,wires=base.wires+listOf(
            wire(part,input,button,0),wire(button,1,ground,0)))
        val board=FirmwareBoard(circuit,part.id)
        val code="""void setup() { pinMode(2, INPUT_PULLUP); pinMode(13, OUTPUT); }
            void loop() { if (digitalRead(2) == LOW) { digitalWrite(13, HIGH); }
                else { digitalWrite(13, LOW); } delay(10); }"""
        val runtime=FirmwareSession(ArduinoSubset.compile(code,board),board)
        val led=base.components.first { it.kind==Kind.LED }
        val open=runtime.advance(0)
        assertNull(open.error)
        assertTrue(open.frames.last().result.readings.getValue(led.id).current<1e-5)
        board.updateCircuit(circuit.copy(components=circuit.components.map {
            if(it.id==button.id) it.copy(closed=true) else it }))
        val closed=runtime.advance(10_000)
        assertNull(closed.error)
        assertTrue(closed.frames.last().result.readings.getValue(led.id).current>.001)
    }

    @Test fun adcSamplesRealPotentiometerNodeAndQuantizes() {
        val (base,part)=boardCircuit()
        val supply=base.components.first { it.kind==Kind.SOURCE }
        val ground=base.components.first { it.kind==Kind.GROUND }
        val pot=part(Kind.POTENTIOMETER,"RV1",mapOf("position" to .5))
        val a0=BoardRegistry.boards.getValue(part.kind).index("A0")
        val circuit=base.copy(components=base.components+pot,wires=base.wires+listOf(
            wire(supply,0,pot,0),wire(pot,2,ground,0),wire(pot,1,part,a0)))
        val board=FirmwareBoard(circuit,part.id)
        val code="""void setup() { Serial.begin(9600); }
            void loop() { int sample = analogRead(A0); Serial.println(sample); delay(100); }"""
        val runtime=FirmwareSession(ArduinoSubset.compile(code,board),board)
        val first=runtime.advance(0)
        assertNull(first.error)
        assertTrue("${first.console}",first.console.single().toInt() in 480..544)
    }
}
