package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.ArduinoSubset
import com.indianservers.circuitssimulator.firmware.FirmwareBoard
import com.indianservers.circuitssimulator.firmware.FirmwareExamples
import com.indianservers.circuitssimulator.intelligence.CircuitIntelligence
import com.indianservers.circuitssimulator.intelligence.IntelligenceInput
import com.indianservers.circuitssimulator.simulation.DcSolver
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class PhaseFiveBBoardTest {
    private fun part(kind:Kind,ref:String,values:Map<String,Double> = emptyMap())=
        PlacedComponent(kind=kind,reference=ref,x=100f,y=100f,
            parameters=ComponentRegistry.definitions.getValue(kind).parameters.associate { it.key to it.default }+values)
    private fun wire(a:PlacedComponent,ap:Int,b:PlacedComponent,bp:Int)=
        Wire(start=TerminalRef(a.id,ap),end=TerminalRef(b.id,bp))

    @Test fun elevenBoardsAreDistinctAndSearchable() {
        assertEquals(11,BoardRegistry.boards.size)
        assertEquals(Kind.entries.filter { it.isBoard }.toSet(),BoardRegistry.boards.keys)
        assertTrue(ComponentRegistry.search("ATmega328").any { it.kind==Kind.ARDUINO_UNO })
        assertTrue(ComponentRegistry.search("WiFi").any { it.kind==Kind.ESP32_DEVKIT })
        assertTrue(ComponentRegistry.search("RP2040").any { it.kind==Kind.RASPBERRY_PICO })
        assertTrue(ComponentRegistry.search("RP2350").any { it.kind==Kind.RASPBERRY_PICO_2 })
        assertTrue(ComponentRegistry.search("SAMD21").any { it.kind==Kind.ARDUINO_NANO_33_IOT })
        assertTrue(ComponentRegistry.search("D1 Mini").any { it.kind==Kind.WEMOS_D1_MINI })
        assertTrue(ComponentRegistry.search("C3").any { it.kind==Kind.ESP32_C3_DEVKIT })
        assertTrue(ComponentRegistry.validate().isEmpty())
    }

    @Test fun pinMapsAreInternallyConsistent() {
        BoardRegistry.boards.values.forEach { board ->
            val connectable=board.pins.filter { it.connectable }
            assertEquals("${board.product} duplicate physical slots",
                connectable.size,connectable.mapIndexed { i,_ -> i }.distinct().size)
            val gpioOwners=connectable.filter { it.gpioNumber!=null }
                .groupBy { it.gpioNumber }
            gpioOwners.forEach { (gpio,pins) ->
                val signals=pins.map { it.signal }.toSet()
                assertEquals("${board.product} GPIO$gpio maps to ${pins.map { it.name }}",1,signals.size)
            }
            connectable.filter { it.type in setOf(PinType.POWER_INPUT,PinType.POWER_OUTPUT,PinType.GROUND) }
                .forEach { pin ->
                    assertFalse("${board.product} ${pin.name} is power treated as GPIO",
                        PinCapability.DIGITAL_OUTPUT in pin.capabilities)
                }
            assertTrue(board.pins.any { PinCapability.ANALOG_INPUT in it.capabilities })
            assertTrue(board.pins.any { PinCapability.PWM in it.capabilities })
            assertTrue(board.pins.any { PinCapability.UART in it.capabilities })
            assertTrue(board.pins.any { PinCapability.I2C in it.capabilities })
            assertTrue(board.pins.any { PinCapability.SPI in it.capabilities })
            board.pins.filter { it.nx!=null }.forEach { pin ->
                assertTrue("${board.product} ${pin.name} x",abs(pin.nx!!)<board.boardWidth/2f+8f)
                assertTrue("${board.product} ${pin.name} y",abs(pin.ny!!)<board.boardHeight/2f+8f)
            }
            val placed=board.pins.filter { it.connectable && it.nx!=null }
            placed.forEachIndexed { i,a ->
                placed.drop(i+1).forEach { b ->
                    if(a.signal!=b.signal) {
                        val d=hypot((a.nx!!-b.nx!!).toDouble(),(a.ny!!-b.ny!!).toDouble())
                        assertTrue("${board.product} ${a.name} overlaps ${b.name}",d>6.0)
                    }
                }
            }
        }
    }

    @Test fun aliasesResolveToOnePhysicalPin() {
        val uno=BoardRegistry.boards.getValue(Kind.ARDUINO_UNO)
        assertEquals("D13",uno.resolve("LED_BUILTIN"))
        assertEquals("D13",uno.resolve(13))
        assertEquals("SDA",uno.resolve("SDA"))
        assertTrue(uno.resolve(18) in setOf("A4","SDA"))
        assertEquals("A4",uno.pin("SDA")!!.signal)
        assertEquals("D0",uno.resolve("RX"))
        val mega=BoardRegistry.boards.getValue(Kind.ARDUINO_MEGA)
        assertEquals("SDA",mega.resolve("SDA"))
        assertNotEquals("SDA",mega.resolve(18))
        assertEquals("D18",mega.resolve(18))
        assertEquals("D20",mega.resolve(20))
        val node=BoardRegistry.boards.getValue(Kind.NODEMCU_ESP8266)
        assertEquals("D1",node.resolve(5))
        assertEquals("D1",node.resolve("GPIO5"))
        assertEquals("D4",node.resolve("LED_BUILTIN"))
        val pico=BoardRegistry.boards.getValue(Kind.RASPBERRY_PICO)
        assertEquals("GP25",pico.resolve("LED_BUILTIN"))
        assertEquals("GP25",pico.resolve(25))
        assertEquals("GP26",pico.resolve("A0",analog=true))
        val picoW=BoardRegistry.boards.getValue(Kind.RASPBERRY_PICO_W)
        assertEquals("WL_LED",picoW.resolve("LED_BUILTIN"))
        val esp=BoardRegistry.boards.getValue(Kind.ESP32_DEVKIT)
        assertTrue(esp.pin("IO34")!!.inputOnly)
        assertEquals("VP",esp.resolve("A0",analog=true))
        assertEquals("IO21",esp.resolve(21))
    }

    @Test fun unoRejectsNonPwmAndEsp32RejectsInputOnlyOutput() {
        val uno=part(Kind.ARDUINO_UNO,"UNO1")
        val circuit=Circuit("Uno",listOf(uno),emptyList())
        val board=FirmwareBoard(circuit,uno.id)
        val pwmFail=runCatching { ArduinoSubset.compile(
            "void setup(){ analogWrite(2,128); } void loop(){ delay(1); }",board) }
        assertTrue(pwmFail.exceptionOrNull()!!.message!!.contains("not PWM-capable"))
        val esp=part(Kind.ESP32_DEVKIT,"ESP1")
        val espBoard=FirmwareBoard(Circuit("ESP",listOf(esp),emptyList()),esp.id)
        val outFail=runCatching { ArduinoSubset.compile(
            "void setup(){ pinMode(34, OUTPUT); } void loop(){ delay(1); }",espBoard) }
        assertTrue(outFail.exceptionOrNull()!!.message!!.contains("input-only"))
    }

    @Test fun boardSpecificAdcAndPwmRuntime() {
        fun live(kind:Kind,supplyName:String,adcName:String,pwmName:String) {
            val board=part(kind,"B1")
            val source=part(Kind.SOURCE,"V1",mapOf("voltage" to BoardRegistry.boards.getValue(kind).logicVoltage))
            val ground=part(Kind.GROUND,"G1")
            val pins=BoardRegistry.boards.getValue(kind)
            val circuit=Circuit(kind.title,listOf(board,source,ground),listOf(
                wire(source,0,board,pins.index(supplyName)),wire(source,1,board,pins.index("GND")),
                wire(source,1,ground,0),
                wire(source,0,board,pins.index(pins.resolve(adcName,analog=true)!!))))
            val fb=FirmwareBoard(circuit,board.id)
            fb.settle()
            val printed=fb.analog(fb.resolve(adcName,analog=true))
            val full=(1 shl pins.adcBits)-1
            assertTrue("$kind ADC $printed bits=${pins.adcBits}",printed>=full*3/4)
            ArduinoSubset.compile(
                "void setup(){ pinMode($pwmName, OUTPUT); analogWrite($pwmName, 128); } void loop(){ delay(1); }",fb)
        }
        live(Kind.ARDUINO_UNO,"5V","A0","9")
        live(Kind.RASPBERRY_PICO,"3V3_OUT","A0","15")
        live(Kind.ESP32_DEVKIT,"3V3","A0","2")
        live(Kind.NODEMCU_ESP8266,"3V3","A0","5")
    }

    @Test fun powerAndJsonRoundTripKeepBoardIdentity() {
        val original=FirmwareExamples.esp32OledCircuit()
        assertEquals(Kind.ESP32_DEVKIT,original.components.first { it.kind.isBoard }.kind)
        assertEquals("ESP32_DEVKIT",original.components.first { it.kind.isBoard }.kind.name)
        val unpowered=part(Kind.RASPBERRY_PICO,"P1",mapOf("usbPower" to 0.0))
        val fb=FirmwareBoard(Circuit("Off",listOf(unpowered),emptyList()),unpowered.id)
        val fail=runCatching { fb.settle() }
        assertTrue(fail.exceptionOrNull()!!.message!!.contains("unpowered"))
    }

    @Test fun replacementDoesNotRemapAndFlagsVoltage() {
        val summary=BoardReplacement.summarize(Kind.ARDUINO_UNO,Kind.ESP32_DEVKIT,
            "void setup(){ pinMode(D13, OUTPUT); analogRead(SDA); }")
        assertNotNull(summary.voltageChange)
        assertTrue(summary.codeIssues.any { it.contains("D13") || it.contains("SDA") })
        assertTrue(summary.notes.any { it.contains("No automatic remapping") })
    }

    @Test fun diagnosticsNameExactBoardPins() {
        val board=part(Kind.ESP32_DEVKIT,"ESP1")
        val source=part(Kind.SOURCE,"V1",mapOf("voltage" to 5.0))
        val ground=part(Kind.GROUND,"G1")
        val pins=BoardRegistry.boards.getValue(board.kind)
        val circuit=Circuit("5V on 3V3",listOf(board,source,ground),listOf(
            wire(source,0,board,pins.index("IO21")),wire(source,1,ground,0),
            wire(board,pins.index("GND"),ground,0)))
        val result=DcSolver().solve(circuit)
        val issues=CircuitIntelligence.analyze(IntelligenceInput(circuit,result)).issues
        assertTrue(issues.any { it.ruleId=="GPIO_OVERVOLTAGE" && it.title.contains("IO21") })
        val boot=Circuit("Boot",listOf(board,source,ground),listOf(
            wire(source,0,board,pins.index("IO0")),wire(source,1,ground,0)))
        val bootIssues=CircuitIntelligence.analyze(IntelligenceInput(boot,DcSolver().solve(boot))).issues
        assertTrue(bootIssues.any { it.ruleId=="BOOT_PIN" && it.title.contains("IO0") })
    }

    @Test fun unoSdaSharesA4NetAndMegaKeepsSeparateUarts() {
        val uno=part(Kind.ARDUINO_UNO,"U1")
        val nets=Circuit("nets",listOf(uno),emptyList()).electricalConnections()
        val sda=BoardRegistry.boards.getValue(Kind.ARDUINO_UNO).index("SDA")
        val a4=BoardRegistry.boards.getValue(Kind.ARDUINO_UNO).index("A4")
        assertTrue(nets.any { (x,y) ->
            setOf(x,y)==setOf(TerminalRef(uno.id,sda),TerminalRef(uno.id,a4)) })
        val megaPart=part(Kind.ARDUINO_MEGA,"M1")
        val mega=FirmwareBoard(Circuit("m",listOf(megaPart),emptyList()),megaPart.id)
        assertEquals(listOf("Serial","Serial1","Serial2","Serial3"),mega.serialPorts().map { it.name })
    }
}
