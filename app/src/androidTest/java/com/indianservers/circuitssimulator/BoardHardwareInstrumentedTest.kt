package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class BoardHardwareInstrumentedTest {
    private fun p(kind:Kind,ref:String,parameters:Map<String,Double> = emptyMap())=
        PlacedComponent(kind=kind,reference=ref,x=100f,y=100f,parameters=parameters)
    private fun ref(p:PlacedComponent,pin:String)=TerminalRef(p.id,BoardRegistry.boards.getValue(p.kind).index(pin))
    private fun w(a:TerminalRef,b:TerminalRef)=Wire(start=a,end=b)
    private fun base(kind:Kind):Pair<Circuit,PlacedComponent> {
        val board=p(kind,"BOARD",mapOf("usbPower" to 1.0));val ground=p(Kind.GROUND,"G")
        return Circuit("Hardware acceptance",listOf(board,ground),listOf(w(ref(board,"GND"),TerminalRef(ground.id,0)))) to board
    }
    private fun run(c:Circuit,b:PlacedComponent,source:String,python:Boolean=false,time:Long=0):Pair<FirmwareSession,FirmwareAdvance> {
        val board=FirmwareBoard(c,b.id)
        val program=if(python) MicroPythonSubset.compile(source,board) else ArduinoSubset.compile(source,board)
        val session=FirmwareSession(program,board)
        return session to session.advance(time)
    }
    @Test fun sevenProfilesHaveStablePhysicalElectricalAndFirmwareMapping() {
        val mandatory=listOf(Kind.ARDUINO_UNO,Kind.ARDUINO_MEGA,Kind.ARDUINO_NANO,Kind.NODEMCU_ESP8266,
            Kind.ESP32_DEVKIT,Kind.RASPBERRY_PICO,Kind.RASPBERRY_PICO_W)
        assertEquals(7,mandatory.map { BoardRegistry.boards.getValue(it).hardware.profileId }.toSet().size)
        mandatory.forEach { kind ->
            val definition=BoardRegistry.boards.getValue(kind)
            definition.pins.forEach { pin ->
                assertTrue(definition.index(pin.name)>=0)
                assertEquals(pin.signal,definition.pin(definition.resolve(pin.name)!!)!!.signal)
            }
            val (c,b)=base(kind)
            val pin=definition.pins.first { PinCapability.DIGITAL_OUTPUT in it.capabilities && !it.name.startsWith("TX") }.name
            val (_,result)=run(c,b,"void setup(){pinMode($pin,OUTPUT);digitalWrite($pin,HIGH);} void loop(){delay(100);}")
            assertNull("$kind: ${result.error}",result.error)
            assertTrue(result.frames.last().result.nodeVoltages.getValue(ref(b,pin))>definition.logicVoltage*.9)
        }
    }
    @Test fun megaExtraPinsAnalog10AndIndependentSerial1WorkButUnoRejectsThem() {
        val (base,b)=base(Kind.ARDUINO_MEGA)
        val voltage=p(Kind.SOURCE,"ADC",mapOf("voltage" to 2.5))
        val ground=base.components.last()
        val c=base.copy(components=base.components+voltage,wires=base.wires+listOf(
            w(TerminalRef(voltage.id,0),ref(b,"A10")),w(TerminalRef(voltage.id,1),TerminalRef(ground.id,0))))
        val (session,result)=run(c,b,"""void setup(){pinMode(22,OUTPUT);pinMode(40,OUTPUT);
            digitalWrite(22,HIGH);digitalWrite(40,HIGH);Serial.begin(9600);Serial1.begin(115200);
            Serial1.println("MEGA");int sample=analogRead(A10);}void loop(){delay(100);}""")
        assertNull(result.error);assertTrue(result.variables.getValue("sample").toDouble() in 511.0..512.0)
        assertTrue(result.frames.last().result.nodeVoltages.getValue(ref(b,"D22"))>4.9)
        assertTrue(result.frames.last().result.nodeVoltages.getValue(ref(b,"D40"))>4.9)
        assertEquals(2,session.fabric.uart.size)
        assertEquals(115200,session.fabric.uart.getValue("${b.id}:Serial1").baud)
        val (uno,u)=base(Kind.ARDUINO_UNO)
        listOf("pinMode(22,OUTPUT)","pinMode(40,OUTPUT)","analogRead(A10)","Serial1.begin(9600)").forEach {
            assertTrue(runCatching { ArduinoSubset.compile("void setup(){$it;}void loop(){delay(1);}",FirmwareBoard(uno,u.id)) }.isFailure)
        }
    }
    @Test fun picoPythonImportsIndentationAndToggleDriveActualNodes() {
        val (c,b)=base(Kind.RASPBERRY_PICO)
        val (session,first)=run(c,b,"""from machine import Pin
            |from time import sleep_ms
            |led = Pin(15, Pin.OUT)
            |count = 0
            |while True:
            |    led.toggle()
            |    count += 1
            |    if count == 1:
            |        print("HIGH")
            |    else:
            |        print("LOW")
            |    sleep_ms(10)
        """.trimMargin(),true)
        assertNull(first.error);assertEquals(listOf("HIGH"),first.console)
        assertTrue(first.frames.last().result.nodeVoltages.getValue(ref(b,"GP15"))>3.2)
        val later=session.advance(10_000)
        assertNull(later.error);assertEquals(listOf("HIGH","LOW"),later.console)
        assertTrue(later.frames.last().result.nodeVoltages.getValue(ref(b,"GP15"))<.1)
    }
    @Test fun picoPythonAdcReadsPhysicalVoltageAndPwmIsPulsed() {
        val (base,b)=base(Kind.RASPBERRY_PICO)
        val source=p(Kind.SOURCE,"ADC",mapOf("voltage" to 1.65))
        val c=base.copy(components=base.components+source,wires=base.wires+listOf(
            w(TerminalRef(source.id,0),ref(b,"GP26")),w(TerminalRef(source.id,1),TerminalRef(base.components.last().id,0))))
        val (_,result)=run(c,b,"""from machine import Pin, ADC, PWM
            |from time import sleep_ms
            |adc = ADC(26)
            |sample = adc.read_u16()
            |pwm = PWM(Pin(15), freq=1000, duty_u16=32768)
            |while True:
            |    sleep_ms(100)
        """.trimMargin(),true,5000)
        assertNull(result.error)
        assertTrue(result.variables.getValue("sample").toDouble() in 32740.0..32790.0)
        val voltages=result.frames.mapNotNull { it.result.nodeVoltages[ref(b,"GP15")] }
        assertTrue(voltages.max()>3.2);assertTrue(voltages.min()<.1)
    }
    @Test fun pythonRejectsUnavailablePinsUnsafeImportsAndUnsupportedObjectMethods() {
        val (c,b)=base(Kind.RASPBERRY_PICO);val board=FirmwareBoard(c,b.id)
        listOf("import os","from machine import ADC\na=ADC(15)","from machine import Pin\np=Pin(99)",
            "from machine import ADC\na=ADC(26)\na.toggle()").forEach {
            assertTrue(it,runCatching { MicroPythonSubset.compile(it,board) }.isFailure)
        }
        val (_,result)=run(c,b,"from machine import PWM\np=PWM(14,freq=1000)\nq=PWM(15,freq=500)",true)
        assertTrue(result.error?.message.orEmpty().contains("frequency conflict"))
    }
    @Test fun nodeD1IsGpio5AndGroundedBootstrapPreventsFlashBoot() {
        val (c,b)=base(Kind.NODEMCU_ESP8266)
        assertEquals("D1",BoardRegistry.boards.getValue(b.kind).resolve(5))
        val (_,ok)=run(c,b,"void setup(){pinMode(5,OUTPUT);digitalWrite(5,HIGH);}void loop(){delay(100);}")
        assertNull(ok.error);assertTrue(ok.frames.last().result.nodeVoltages.getValue(ref(b,"D1")) in 3.2..3.4)
        val failed=c.copy(wires=c.wires+w(ref(b,"D3"),TerminalRef(c.components.last().id,0)))
        assertTrue(run(failed,b,"void setup(){}void loop(){delay(100);}").second.error?.message.orEmpty().contains("Boot failed"))
    }
    @Test fun espInputOnlyAndInternalPullRestrictionsAreEnforcedAndFiveVoltsDamagesInput() {
        val (base,b)=base(Kind.ESP32_DEVKIT);val board=FirmwareBoard(base,b.id)
        assertNull(board.definition.ledBuiltin)
        assertTrue(runCatching { ArduinoSubset.compile("void setup(){pinMode(34,OUTPUT);}void loop(){delay(1);}",board) }.isFailure)
        assertTrue(run(base,b,"void setup(){pinMode(34,INPUT_PULLUP);}void loop(){delay(1);}").second.error?.message.orEmpty().contains("pull-up"))
        val source=p(Kind.SOURCE,"UNSAFE",mapOf("voltage" to 5.0))
        val c=base.copy(components=base.components+source,wires=base.wires+listOf(
            w(TerminalRef(source.id,0),ref(b,"IO34")),w(TerminalRef(source.id,1),TerminalRef(base.components.last().id,0))))
        val (_,result)=run(c,b,"void setup(){pinMode(34,INPUT);}void loop(){delay(1);}")
        assertNull(result.error);assertTrue(result.frames.last().pins.getValue("IO34").damaged)
        assertTrue(result.frames.last().pins.getValue("IO34").overVoltage)
    }
    @Test fun picoWLedIsWirelessLedAndNotHeaderGp25() {
        val definition=BoardRegistry.boards.getValue(Kind.RASPBERRY_PICO_W)
        assertEquals("WL_LED",definition.resolve("LED"));assertNull(definition.resolve(25))
        val (c,b)=base(Kind.RASPBERRY_PICO_W)
        val (_,result)=run(c,b,"from machine import Pin\nled=Pin(\"LED\",Pin.OUT)\nled.on()",true)
        assertNull(result.error);assertTrue(result.frames.last().result.nodeVoltages.getValue(ref(b,"WL_LED"))>3.2)
    }
    @Test fun i2cRequiresSdaPowerAndGroundAndPythonReadsConnectedSensor() {
        val c=FirmwareExamples.i2cCircuit();val b=c.components.first { it.kind.isBoard }
        val fabric=ProtocolFabric();fabric.rebuildDevices(c)
        val board=FirmwareBoard(c,b.id)
        val program=ArduinoSubset.compile(FirmwareExamples.I2C_TEMP,board)
        val session=FirmwareSession(program,board,fabric,c.environment)
        assertNull(session.advance(0).error)
        assertEquals(0,fabric.i2cWrite(c,b.id,0x48,listOf(0),0))
        val sda=ref(b,board.definition.hardware.i2cPins!!.first)
        val disconnected=c.copy(wires=c.wires.filter { it.start!=sda && it.end!=sda })
        assertEquals(2,fabric.i2cWrite(disconnected,b.id,0x48,listOf(0),0))
        val unpowered=c.copy(wires=c.wires.filterNot { it.start.componentId==c.components.first { p -> p.kind==Kind.I2C_TEMP_SENSOR }.id && it.start.index==0 ||
            it.end.componentId==c.components.first { p -> p.kind==Kind.I2C_TEMP_SENSOR }.id && it.end.index==0 })
        fabric.electricalResult=com.indianservers.circuitssimulator.simulation.DcSolver().solve(unpowered,board.usbDrives())
        assertEquals(2,fabric.i2cWrite(unpowered,b.id,0x48,listOf(0),0))
    }
    @Test fun multiBoardUartHelloTraversesDividerAndNeedsSharedGroundAndMatchingBaud() {
        val uno=p(Kind.ARDUINO_UNO,"UNO",mapOf("usbPower" to 1.0))
        val esp=p(Kind.ESP32_DEVKIT,"ESP",mapOf("usbPower" to 1.0))
        val ground=p(Kind.GROUND,"G")
        val r1=p(Kind.RESISTOR,"R1",mapOf("resistance" to 1000.0));val r2=p(Kind.RESISTOR,"R2",mapOf("resistance" to 2000.0))
        val codeA="void setup(){Serial.begin(9600);}void loop(){delay(5);Serial.println(\"HELLO\");}"
        val codeB="void setup(){Serial.begin(9600);}void loop(){if(Serial.available()){int byte=Serial.read();Serial.write(byte);}delay(1);}"
        val c=Circuit("UART hardware",listOf(uno,esp,ground,r1,r2),listOf(
            w(ref(uno,"GND"),TerminalRef(ground.id,0)),w(ref(esp,"GND"),TerminalRef(ground.id,0)),
            w(ref(uno,"D1"),TerminalRef(r1.id,0)),w(TerminalRef(r1.id,1),ref(esp,"RX")),
            w(ref(esp,"RX"),TerminalRef(r2.id,0)),w(TerminalRef(r2.id,1),TerminalRef(ground.id,0))),
            firmware=listOf(FirmwareAttachment(uno.id,FirmwareLanguage.ARDUINO_SUBSET,codeA,true),
                FirmwareAttachment(esp.id,FirmwareLanguage.ARDUINO_SUBSET,codeB,true)))
        val scheduler=RuntimeScheduler().compile(c)
        val start=scheduler.advance(0);assertEquals(0,start.timeMicros)
        val result=scheduler.advance(20_000)
        assertNull(result.error);assertEquals(20_000,result.timeMicros)
        assertTrue(result.busLog.any { it.kind==BusKind.UART && it.ack==true })
        assertTrue(result.console.getValue(esp.id).joinToString("").contains("HELLO"))
        assertTrue(result.pins.getValue(esp.id).keys.contains("IO34"))
        assertFalse(result.pins.getValue(uno.id).keys.contains("IO34"))
        val noGround=c.copy(wires=c.wires.filterNot { it.start==ref(esp,"GND") || it.end==ref(esp,"GND") })
        val invalid=RuntimeScheduler().compile(noGround).advance(20_000)
        assertFalse(invalid.busLog.any { it.kind==BusKind.UART && it.ack==true })
        val mismatch=c.copy(firmware=c.firmware.map { if(it.boardId==esp.id) it.copy(source=it.source.replace("9600","115200")) else it })
        assertFalse(RuntimeScheduler().compile(mismatch).advance(20_000).busLog.any { it.kind==BusKind.UART && it.ack==true })
    }
    @Test fun powerAbsentAndResetHeldPreventFirmwareAndVinRegulatorWorks() {
        val (base,b)=base(Kind.ARDUINO_UNO)
        val off=b.copy(parameters=mapOf("usbPower" to 0.0))
        assertTrue(run(base.copy(components=listOf(off,base.components.last())),off,"void setup(){}void loop(){delay(100);}").second.error?.message.orEmpty().contains("unpowered"))
        val reset=base.copy(wires=base.wires+w(ref(b,"RESET"),TerminalRef(base.components.last().id,0)))
        assertTrue(run(reset,b,"void setup(){}void loop(){delay(100);}").second.error?.message.orEmpty().contains("reset"))
        val supply=p(Kind.SOURCE,"VIN",mapOf("voltage" to 9.0))
        val c=base.copy(components=listOf(off,base.components.last(),supply),wires=base.wires+listOf(
            w(TerminalRef(supply.id,0),ref(off,"VIN")),w(TerminalRef(supply.id,1),TerminalRef(base.components.last().id,0))))
        assertNull(run(c,off,"void setup(){pinMode(13,OUTPUT);digitalWrite(13,HIGH);}void loop(){delay(100);}").second.error)
    }
    @Test fun gpioInterruptRunsNamedHandlerFromPhysicalInputEdgeAndPreservesDelay() {
        val (base,b)=base(Kind.ARDUINO_UNO)
        val source=p(Kind.SOURCE,"EDGE",mapOf("voltage" to 0.0))
        val c=base.copy(components=base.components+source,wires=base.wires+listOf(
            w(TerminalRef(source.id,0),ref(b,"D2")),w(TerminalRef(source.id,1),TerminalRef(base.components.last().id,0))))
        val code="""volatile int edges=0;void onEdge(){edges++;}
            void setup(){pinMode(2,INPUT);attachInterrupt(digitalPinToInterrupt(2),onEdge,RISING);}
            void loop(){delay(100);int ticks=millis();}"""
        val (session,first)=run(c,b,code)
        assertNull(first.error);assertEquals("0.0",first.variables["edges"])
        session.board.updateCircuit(c.copy(components=c.components.map { if(it.id==source.id) it.copy(parameters=mapOf("voltage" to 5.0)) else it }))
        val edge=session.advance(1000)
        assertNull(edge.error);assertEquals("1.0",edge.variables["edges"])
        assertFalse(edge.variables.containsKey("ticks"))
        val end=session.advance(99_000)
        assertNull(end.error);assertEquals("100.0",end.variables["ticks"])
    }
    @Test fun picoMicroPythonI2cUsesRealSensorAndNacksDisconnectedSda() {
        val (base,b)=base(Kind.RASPBERRY_PICO)
        val sensor=p(Kind.I2C_TEMP_SENSOR,"TEMP",mapOf("address" to 72.0))
        val c=base.copy(components=base.components+sensor,wires=base.wires+listOf(
            w(ref(b,"3V3_OUT"),TerminalRef(sensor.id,0)),w(ref(b,"GND"),TerminalRef(sensor.id,1)),
            w(ref(b,"GP4"),TerminalRef(sensor.id,2)),w(ref(b,"GP5"),TerminalRef(sensor.id,3))),
            environment=EnvironmentState(temperatureC=27.3))
        val code="""from machine import Pin, I2C
            |bus=I2C(0,sda=Pin(4),scl=Pin(5))
            |bus.writeto(72,bytes([0]))
            |data=bus.readfrom(72,2)
            |temperature=(data[0]*256+data[1])/256
            |print(temperature)
        """.trimMargin()
        val (_,result)=run(c,b,code,true,1000)
        assertNull(result.error);assertEquals("27.5",result.variables["temperature"])
        val disconnected=c.copy(wires=c.wires.filterNot { it.start==ref(b,"GP4") || it.end==ref(b,"GP4") })
        assertTrue(run(disconnected,b,code,true).second.error?.message.orEmpty().contains("NACK"))
    }
    @Test fun esp32Serial2AndSoftwarePwmUseMappedPinsAndNodeSerial1IsTxOnly() {
        val (esp,b)=base(Kind.ESP32_DEVKIT)
        val (session,next)=run(esp,b,"void setup(){Serial2.begin(115200);analogWrite(25,128);int sample=analogRead(34);}void loop(){delay(100);}",time=5000)
        assertNull(next.error)
        assertEquals(ref(b,"IO17"),session.fabric.uart.getValue("${b.id}:Serial2").tx)
        assertEquals(ref(b,"IO16"),session.fabric.uart.getValue("${b.id}:Serial2").rx)
        val voltage=next.frames.mapNotNull { it.result.nodeVoltages[ref(b,"IO25")] }
        assertTrue(voltage.max()>3.2);assertTrue(voltage.min()<.1)
        val (node,n)=base(Kind.NODEMCU_ESP8266)
        val (nodeSession,nodeNext)=run(node,n,"void setup(){Serial1.begin(9600);Serial1.print(\"TX\");analogWrite(16,128);}void loop(){delay(100);}",time=5000)
        assertNull(nodeNext.error);assertNull(nodeSession.fabric.uart.getValue("${n.id}:Serial1").rx)
        assertEquals(ref(n,"D4"),nodeSession.fabric.uart.getValue("${n.id}:Serial1").tx)
    }
    @Test fun unoBlinkDrivesLedCurrentAndPicoAdcTracksPotentiometer() {
        val c=FirmwareExamples.blinkCircuit();val b=c.components.first { it.kind.isBoard }
        val (blink,first)=run(c,b,"void setup(){pinMode(13,OUTPUT);}void loop(){digitalWrite(13,HIGH);delay(500);digitalWrite(13,LOW);delay(500);}")
        val led=c.components.first { it.kind==Kind.LED }
        assertNull(first.error);assertTrue(first.frames.last().result.readings.getValue(led.id).current>.001)
        assertTrue(blink.advance(500_000).frames.last().result.readings.getValue(led.id).current<1e-5)
        val (base,pico)=base(Kind.RASPBERRY_PICO)
        val pot=p(Kind.POTENTIOMETER,"POT",mapOf("position" to .25,"resistance" to 10000.0))
        val potCircuit=base.copy(components=base.components+pot,wires=base.wires+listOf(
            w(ref(pico,"3V3_OUT"),TerminalRef(pot.id,0)),w(ref(pico,"GND"),TerminalRef(pot.id,2)),
            w(ref(pico,"GP26"),TerminalRef(pot.id,1))))
        val code="from machine import ADC\nadc=ADC(0)\nsample=adc.read_u16()"
        val (runtime,quarter)=run(potCircuit,pico,code,true)
        assertNull(quarter.error)
        val before=quarter.variables.getValue("sample").toDouble()
        runtime.board.updateCircuit(potCircuit.copy(components=potCircuit.components.map { if(it.id==pot.id) it.copy(parameters=it.parameters+("position" to .75)) else it }))
        val after=runtime.board.settle().let { runtime.board.analog("GP26") }
        assertTrue(kotlin.math.abs(after/4095.0-before/65535.0)>.4)
    }
    @Test fun espSpiMemoryRespondsOnlyWithCorrectMosiMisoClockCsAndPower() {
        val (base,b)=base(Kind.ESP32_DEVKIT);val memory=p(Kind.SPI_MEMORY,"MEM")
        val c=base.copy(components=base.components+memory,wires=base.wires+listOf(
            w(ref(b,"3V3"),TerminalRef(memory.id,0)),w(ref(b,"GND"),TerminalRef(memory.id,1)),
            w(ref(b,"IO23"),TerminalRef(memory.id,2)),w(ref(b,"IO19"),TerminalRef(memory.id,3)),
            w(ref(b,"IO18"),TerminalRef(memory.id,4)),w(ref(b,"IO5"),TerminalRef(memory.id,5))))
        val code="void setup(){pinMode(5,OUTPUT);digitalWrite(5,LOW);SPI.begin();SPI.transfer(159);int id=SPI.transfer(0);}void loop(){delay(100);}"
        val (runtime,result)=run(c,b,code,time=1000)
        assertNull(result.error);assertEquals("239.0",result.variables["id"]);assertTrue(runtime.fabric.spiDevices.single().memory.isEmpty())
        val disconnected=c.copy(wires=c.wires.filterNot { it.start==ref(b,"IO18") || it.end==ref(b,"IO18") })
        val (invalid,next)=run(disconnected,b,code,time=1000)
        assertNull(next.error);assertTrue(invalid.fabric.spiDevices.single().memory.isEmpty())
        assertTrue(invalid.fabric.log.any { it.kind==BusKind.SPI && it.ack==false })
        val noCs=c.copy(wires=c.wires.filterNot { it.start==ref(b,"IO5") || it.end==ref(b,"IO5") })
        val (floating,missingCs)=run(noCs,b,code,time=1000)
        assertNull(missingCs.error);assertTrue(floating.fabric.spiDevices.single().memory.isEmpty())
    }
    @Test fun resetOneBoardKeepsGlobalClockAndOtherBoardState() {
        val (base,b)=base(Kind.ARDUINO_UNO)
        val other=p(Kind.ARDUINO_MEGA,"MEGA",mapOf("usbPower" to 1.0))
        val code="int loops=0;void setup(){}void loop(){loops++;delay(10);}"
        val c=base.copy(components=base.components+other,wires=base.wires+w(ref(other,"GND"),ref(b,"GND")),
            firmware=listOf(b,other).map { FirmwareAttachment(it.id,FirmwareLanguage.ARDUINO_SUBSET,code,true) })
        val scheduler=RuntimeScheduler().compile(c);scheduler.advance(20_000)
        val before=scheduler.sessions.getValue(other.id).variables()["loops"]
        scheduler.resetBoard(b.id)
        val reset=scheduler.advance(0)
        assertEquals(20_000,reset.timeMicros);assertEquals(before,reset.variables.getValue(other.id)["loops"])
        assertEquals("1.0",reset.variables.getValue(b.id)["loops"])
        assertEquals(21_000,scheduler.advance(1000).timeMicros)
    }
    @Test fun everyExistingBoardHasExecutableBoardSpecificStarterAndPicoUsbDoesNotDriveUartPins() {
        BoardRegistry.boards.keys.forEach { kind ->
            val (c,b)=base(kind)
            BoardSupport.languages(kind).forEach { language ->
                val (_,result)=run(c,b,FirmwareExamples.starter(kind,"",language),language==FirmwareLanguage.MICROPYTHON_SUBSET)
                assertNull("$kind $language: ${result.error}",result.error)
            }
        }
        val (c,b)=base(Kind.RASPBERRY_PICO)
        val (runtime,result)=run(c,b,"void setup(){Serial.begin(9600);Serial.println(\"USB\");Serial1.begin(9600);}void loop(){delay(100);}")
        assertNull(result.error);assertNull(runtime.fabric.uart.getValue(b.id).tx)
        assertEquals(ref(b,"GP0"),runtime.fabric.uart.getValue("${b.id}:Serial1").tx)
        val (_,python)=run(c,b,"from machine import UART\nu=UART(0)\nu.write(\"UART\")",true)
        assertNull(python.error)
    }
    @Test fun canonicalAliasSharesModeAndCannotBypassPeripheralOwnership() {
        val (c,b)=base(Kind.ARDUINO_UNO)
        val (_,gpio)=run(c,b,"void setup(){pinMode(SDA,OUTPUT);digitalWrite(A4,HIGH);}void loop(){delay(100);}")
        assertNull(gpio.error)
        assertEquals(gpio.frames.last().pins["A4"],gpio.frames.last().pins["SDA"])
        val (_,conflict)=run(c,b,"void setup(){Wire.begin();digitalWrite(SDA,HIGH);}void loop(){delay(100);}")
        assertTrue(conflict.error?.message.orEmpty().contains("owned by I2C"))
    }
    @Test fun picoAggregateOverloadAndTightProgramProduceDiagnostics() {
        val (base,b)=base(Kind.RASPBERRY_PICO)
        val loads=(0..1).map { p(Kind.RESISTOR,"LOAD$it",mapOf("resistance" to 50.0)) }
        val c=base.copy(components=base.components+loads,wires=base.wires+loads.flatMapIndexed { i,r -> listOf(
            w(ref(b,"GP$i"),TerminalRef(r.id,0)),w(ref(b,"GND"),TerminalRef(r.id,1))) })
        val (_,overload)=run(c,b,"void setup(){pinMode(0,OUTPUT);pinMode(1,OUTPUT);digitalWrite(0,HIGH);digitalWrite(1,HIGH);}void loop(){delay(100);}")
        assertTrue(overload.error?.message.orEmpty().contains("Aggregate GPIO current"))
        val (_,busy)=run(base,b,"from machine import Pin\np=Pin(15,Pin.OUT)\nwhile True:\n    p.toggle()",true)
        assertTrue(busy.error?.message.orEmpty().contains("limit"))
        assertTrue(runCatching { MicroPythonSubset.compile("x=1@2",FirmwareBoard(base,b.id)) }.isFailure)
    }
}
