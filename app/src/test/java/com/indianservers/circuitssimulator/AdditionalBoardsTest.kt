package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import com.indianservers.circuitssimulator.simulation.embedded.PinMode
import org.junit.Assert.*
import org.junit.Test

class AdditionalBoardsTest {
    private fun definition(kind:Kind)=BoardRegistry.boards.getValue(kind)
    private fun part(kind:Kind)=PlacedComponent(kind=kind,reference="B1",x=0f,y=0f,
        parameters=mapOf("usbPower" to 1.0))
    private fun ref(board:PlacedComponent,name:String)=TerminalRef(board.id,definition(board.kind).index(name))
    private fun base(kind:Kind):Pair<Circuit,PlacedComponent> {
        val board=part(kind)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="G1",x=0f,y=0f)
        return Circuit("Board acceptance",listOf(board,ground),listOf(
            Wire(start=ref(board,"GND"),end=TerminalRef(ground.id,0)))) to board
    }

    @Test fun eightNewBoardsHaveAuditedDistinctProfilesAndCorrectHeaderCounts() {
        val counts=mapOf(Kind.ARDUINO_LEONARDO to 38,Kind.ARDUINO_MICRO to 40,
            Kind.ARDUINO_DUE to 92,Kind.ARDUINO_UNO_R4_MINIMA to 38,
            Kind.RASPBERRY_PICO_2_W to 41,Kind.ESP32_S3_DEVKIT to 44,
            Kind.ESP32_C6_DEVKIT to 32,Kind.ESP32_S2_SAOLA to 42)
        assertEquals(counts.keys,AdditionalBoards.kinds)
        assertEquals(8,counts.keys.map { definition(it).hardware.profileId }.distinct().size)
        counts.forEach { (kind,count) ->
            val board=definition(kind)
            assertEquals(kind.name,count,board.pins.size)
            assertEquals(count,part(kind).terminalCount)
            assertTrue(board.references.all { it.lastVerified=="2026-10-12" && it.sourceUrl!!.startsWith("https://") })
            assertTrue(ComponentRegistry.search(board.product).any { it.kind==kind })
            val coordinates=board.pins.filter { it.connectable }.map { it.nx to it.ny }
            assertTrue("$kind has unlocated pads",coordinates.all { it.first!=null && it.second!=null })
            assertEquals("$kind pad overlap",coordinates.size,coordinates.distinct().size)
            val numbered=board.pins.filter { it.physicalNumber!=null }
            assertEquals(numbered.size,numbered.map { it.connector to it.physicalNumber }.distinct().size)
        }
    }

    @Test fun officialEspressifConnectorSequencesAndModuleRestrictions() {
        val c6=definition(Kind.ESP32_C6_DEVKIT)
        assertEquals(listOf("3V3","RST","IO4","IO5","IO6","IO7","IO0","IO1","IO8","IO10","IO11","IO2","IO3","5V","GND","NC"),
            c6.pins.filter { it.connector=="J1" }.map { it.name })
        assertEquals(listOf("GND","TX","RX","IO15","IO23","IO22","IO21","IO20","IO19","IO18","IO9","GND","IO13","IO12","GND","NC"),
            c6.pins.filter { it.connector=="J3" }.map { it.name })
        assertEquals("TX",c6.resolve(16));assertEquals("RX",c6.resolve(17))
        assertEquals("IO23" to "IO22",c6.hardware.i2cPins)
        assertEquals(listOf("IO19","IO20","IO21","IO18"),c6.hardware.spiPins)
        val s3=definition(Kind.ESP32_S3_DEVKIT)
        assertEquals(listOf("3V3","3V3","RST","IO4","IO5","IO6","IO7","IO15","IO16","IO17","IO18","IO8","IO3","IO46","IO9","IO10","IO11","IO12","IO13","IO14","5V","GND"),
            s3.pins.filter { it.connector=="J1" }.map { it.name })
        assertEquals(listOf("GND","TX","RX","IO1","IO2","IO42","IO41","IO40","IO39","IO38","IO37","IO36","IO35","IO0","IO45","IO48","IO47","IO21","IO20","IO19","GND","GND"),
            s3.pins.filter { it.connector=="J3" }.map { it.name })
        for(n in 35..37) assertEquals(setOf(PinCapability.RESERVED),s3.pin("IO$n")!!.capabilities)
        assertTrue(PinCapability.DIGITAL_OUTPUT in s3.pin("IO46")!!.capabilities)
        assertTrue(s3.pin("IO38")!!.functions.any { it.contains("RGB") })
        assertNull(s3.ledBuiltin)
        assertEquals("IO1",s3.resolve("A0",true))
        assertEquals(0,s3.pin("IO11")!!.adcChannel)
        assertTrue("ADC2_CH0" in s3.pin("IO11")!!.functions)
        val s2=definition(Kind.ESP32_S2_SAOLA)
        assertEquals(listOf("GND","RST","IO46","IO45","IO44","IO43","IO42","IO41","IO40","IO39","IO38","IO37","IO36","IO35","IO34","IO33","IO26","IO21","IO20","IO19","IO18"),
            s2.pins.filter { it.connector=="J3" }.map { it.name })
        assertEquals(17,s2.pin("IO26")!!.physicalNumber)
        assertEquals(setOf(PinCapability.RESERVED),s2.pin("IO26")!!.capabilities)
        assertTrue(s2.pin("IO46")!!.inputOnly)
        assertFalse(PinCapability.DIGITAL_OUTPUT in s2.pin("IO46")!!.capabilities)
        assertFalse(PinCapability.PWM in s2.pin("IO46")!!.capabilities)
        assertFalse(s2.capabilities.bluetooth.hardware)
        assertEquals(13,s2.adcBits)
        assertTrue(s2.pin("IO18")!!.functions.any { it.contains("RGB") })
    }

    @Test fun arduinoAliasesAdcChannelsAndSerialMatchOfficialVariants() {
        for(kind in listOf(Kind.ARDUINO_LEONARDO,Kind.ARDUINO_MICRO)) {
            val board=definition(kind)
            assertEquals("D2" to "D3",board.hardware.i2cPins)
            assertEquals(7,board.pin("A0")!!.adcChannel)
            assertEquals("D4",board.resolve("A6",true));assertEquals("D4",board.resolve(6,true))
            assertEquals("D4",board.resolve(24));assertEquals("D4",board.resolve("D24"))
            assertEquals("D12",board.resolve("A11",true))
            assertEquals(setOf("D3","D5","D6","D9","D10","D11","D13"),
                board.pins.filter { PinCapability.PWM in it.capabilities }.map { it.name }.toSet())
            val (c,b)=base(kind)
            assertEquals(listOf("Serial1","Serial"),FirmwareBoard(c,b.id).serialPorts().map { it.name })
            assertEquals(2560,board.hardware.sramBytes)
        }
        assertEquals("MOSI",definition(Kind.ARDUINO_LEONARDO).resolve(16))
        assertEquals("D16",definition(Kind.ARDUINO_MICRO).resolve(16))
        val due=definition(Kind.ARDUINO_DUE)
        assertEquals(3.3,due.logicVoltage,0.0)
        assertEquals("A11",due.resolve(65,true))
        assertEquals("MISO",due.resolve(74));assertEquals("MOSI",due.resolve(75))
        assertFalse(PinCapability.SPI in due.pin("D50")!!.capabilities)
        assertEquals("D20",due.resolve("SDA"));assertEquals("SDA1",due.resolve(70))
        assertNotEquals(due.pin("SDA1")!!.signal,due.pin("D20")!!.signal)
        val r4=definition(Kind.ARDUINO_UNO_R4_MINIMA)
        assertEquals(5.0,r4.logicVoltage,0.0)
        assertEquals(10,r4.adcBits)
        assertEquals(8.0,r4.hardware.recommendedPinCurrentMa!!,0.0)
        assertEquals("A4" to "A5",r4.hardware.i2cPins)
        assertEquals("D13",r4.ledBuiltin)
        val pico=definition(Kind.RASPBERRY_PICO_2_W)
        assertNull(pico.pin("GP25"));assertEquals("WL_LED",pico.resolve("LED_BUILTIN"))
        assertEquals(150,pico.hardware.clockMhz)
        assertEquals(4096,pico.hardware.flashKb);assertEquals(520,pico.hardware.sramKb)
    }

    @Test fun everyNewBoardExecutesGpioAdcAndPwmAgainstSolvedVoltages() {
        AdditionalBoards.kinds.forEach { kind ->
            val def=definition(kind)
            val (base,b)=base(kind)
            val input=def.resolve("A0",true)!!
            val output=if(def.family.usesArduinoPinNumbers) "D9" else if(def.family==BoardFamily.RP2350) "GP15" else "IO4"
            val source=PlacedComponent(kind=Kind.SOURCE,reference="ADC",x=0f,y=0f,
                parameters=mapOf("voltage" to def.adcReferenceVoltage/2))
            val c=base.copy(components=base.components+source,wires=base.wires+listOf(
                Wire(start=TerminalRef(source.id,0),end=ref(b,input)),
                Wire(start=TerminalRef(source.id,1),end=ref(b,"GND"))))
            val runtime=FirmwareBoard(c,b.id)
            val program=ArduinoSubset.compile("""void setup(){pinMode($output,OUTPUT);int sample=analogRead(A0);}
                void loop(){digitalWrite($output,HIGH);delay(1);digitalWrite($output,LOW);delay(1);}""",runtime)
            val session=FirmwareSession(program,runtime)
            val high=session.advance(0)
            assertNull("$kind: ${high.error}",high.error)
            assertTrue(high.variables.getValue("sample").toDouble() in ((1 shl def.adcBits)/2-2).toDouble()..((1 shl def.adcBits)/2+2).toDouble())
            assertTrue(high.frames.last().result.nodeVoltages.getValue(ref(b,output))>def.logicVoltage*.9)
            val low=session.advance(1000)
            assertNull("$kind: ${low.error}",low.error)
            assertTrue(low.frames.last().result.nodeVoltages.getValue(ref(b,output))<.1)
            runtime.pwmWrite(output,128,1000.0)
            val pwmHigh=runtime.settle(1000)
            val pwmLow=runtime.settle(750)
            assertTrue(pwmHigh.circuitResult.nodeVoltages.getValue(ref(b,output))>def.logicVoltage*.9)
            assertTrue(pwmLow.circuitResult.nodeVoltages.getValue(ref(b,output))<.1)
        }
    }

    @Test fun reservedInputOnlyAndPowerPinsCannotBeDriven() {
        for((kind,pin) in listOf(Kind.ESP32_S3_DEVKIT to "IO35",Kind.ESP32_S2_SAOLA to "IO26",
            Kind.ESP32_S2_SAOLA to "IO46",Kind.ESP32_C6_DEVKIT to "NC",Kind.ARDUINO_MICRO to "NC1")) {
            val (c,b)=base(kind)
            assertTrue("$kind $pin",runCatching { FirmwareBoard(c,b.id).setPinMode(pin,PinMode.OUTPUT) }.isFailure)
        }
        AdditionalBoards.kinds.forEach { kind ->
            val (c,b)=base(kind)
            assertTrue(runCatching { FirmwareBoard(c,b.id).setPinMode("GND",PinMode.OUTPUT) }.isFailure)
            val off=b.copy(parameters=mapOf("usbPower" to 0.0))
            val unpowered=c.copy(components=c.components.map { if(it.id==b.id) off else it })
            assertTrue(runCatching { FirmwareBoard(unpowered,b.id).settle() }.isFailure)
        }
    }

    @Test fun newBoardBusInitializersClaimTheirVerifiedPhysicalPins() {
        AdditionalBoards.kinds.forEach { kind ->
            val (c,b)=base(kind)
            val runtime=FirmwareBoard(c,b.id)
            val source="void setup(){Wire.begin();SPI.begin();Serial.begin(9600);}void loop(){delay(100);}"
            val session=FirmwareSession(ArduinoSubset.compile(source,runtime),runtime)
            assertNull("$kind",session.advance(0).error)
            val i2c=runtime.definition.hardware.i2cPins!!
            assertTrue(runCatching { runtime.setPinMode(i2c.first,PinMode.OUTPUT) }.isFailure)
            val spi=runtime.definition.hardware.spiPins
            assertTrue(runCatching { runtime.setPinMode(spi[0],PinMode.OUTPUT) }.isFailure)
        }
    }

    @Test fun allNewBoardsReadAPoweredTemperaturePeripheralOnTheirOwnI2cPins() {
        AdditionalBoards.kinds.forEach { kind ->
            val (base,b)=base(kind)
            val def=definition(kind)
            val sensor=PlacedComponent(kind=Kind.I2C_TEMP_SENSOR,reference="TMP1",x=0f,y=0f)
            val (sda,scl)=def.hardware.i2cPins!!
            val rail=def.supplyPins.first()
            val c=base.copy(components=base.components+sensor,wires=base.wires+listOf(
                Wire(start=ref(b,rail),end=TerminalRef(sensor.id,0)),
                Wire(start=ref(b,"GND"),end=TerminalRef(sensor.id,1)),
                Wire(start=ref(b,sda),end=TerminalRef(sensor.id,2)),
                Wire(start=ref(b,scl),end=TerminalRef(sensor.id,3))))
            val runtime=FirmwareBoard(c,b.id)
            val code="""void setup(){Wire.begin();Wire.beginTransmission(72);Wire.write(0);
                int ack=Wire.endTransmission();int count=Wire.requestFrom(72,2);
                int high=Wire.read();int low=Wire.read();}void loop(){delay(100);}"""
            val result=FirmwareSession(ArduinoSubset.compile(code,runtime),runtime).advance(10000)
            assertNull("$kind: ${result.error}",result.error)
            assertEquals("$kind ACK",0.0,result.variables.getValue("ack").toDouble(),0.0)
            assertEquals("$kind bytes",2.0,result.variables.getValue("count").toDouble(),0.0)
            assertEquals("$kind temperature",25.0,result.variables.getValue("high").toDouble(),0.0)
            assertEquals("$kind fractional temperature",0.0,result.variables.getValue("low").toDouble(),0.0)
        }
    }

    @Test fun newBoardsUseTheWiredSpiHeaderAndRejectDirect5VFlashLogic() {
        AdditionalBoards.kinds.forEach { kind ->
            val (base,b)=base(kind)
            val def=definition(kind)
            val flash=PlacedComponent(kind=Kind.SPI_MEMORY,reference="MEM1",x=0f,y=0f)
            val spi=def.hardware.spiPins
            val rail=if(def.family==BoardFamily.RP2350) "3V3_OUT" else "3V3"
            val c=base.copy(components=base.components+flash,wires=base.wires+listOf(
                Wire(start=ref(b,rail),end=TerminalRef(flash.id,0)),
                Wire(start=ref(b,"GND"),end=TerminalRef(flash.id,1)),
                Wire(start=ref(b,spi[0]),end=TerminalRef(flash.id,2)),
                Wire(start=ref(b,spi[1]),end=TerminalRef(flash.id,3)),
                Wire(start=ref(b,spi[2]),end=TerminalRef(flash.id,4)),
                Wire(start=ref(b,spi[3]),end=TerminalRef(flash.id,5))))
            val runtime=FirmwareBoard(c,b.id)
            val code="void setup(){pinMode(${spi[3]},OUTPUT);digitalWrite(${spi[3]},LOW);SPI.begin();SPI.transfer(159);int id=SPI.transfer(0);}void loop(){delay(100);}"
            val result=FirmwareSession(ArduinoSubset.compile(code,runtime),runtime).advance(1000)
            assertNull("$kind: ${result.error}",result.error)
            assertEquals("$kind JEDEC/voltage check",if(def.logicVoltage>3.6) 255.0 else 239.0,result.variables.getValue("id").toDouble(),0.0)
        }
    }
}
