package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class EmbeddedEcosystemInstrumentedTest {

    @Test fun firmwareGpioTraversesCanvasGateAndClockedFlipFlop() {
        val board=p(Kind.ESP32_DEVKIT,"UNO",mapOf("usbPower" to 1.0));val gate=p(Kind.NOT_GATE,"INV");val ff=p(Kind.D_FLIP_FLOP,"FF");val ground=p(Kind.GROUND,"G")
        val circuit=Circuit("Digital integration",listOf(board,gate,ff,ground),listOf(w(r(board,"GND"),TerminalRef(ground.id,0)),
            w(r(board,"IO2"),TerminalRef(gate.id,0)),w(TerminalRef(gate.id,1),TerminalRef(ff.id,0)),
            w(r(board,"IO4"),TerminalRef(ff.id,1)),w(TerminalRef(ff.id,2),r(board,"IO16"))))
        val runtime=FirmwareBoard(circuit,board.id)
        val s=FirmwareSession(ArduinoSubset.compile("void setup(){pinMode(2,OUTPUT);pinMode(4,OUTPUT);pinMode(16,INPUT);digitalWrite(2,LOW);digitalWrite(4,LOW);delayMicroseconds(10);digitalWrite(4,HIGH);delayMicroseconds(10);int value=digitalRead(16);}void loop(){delay(100);}",runtime),runtime)
        val result=s.advance(1000);assertNull(result.error);assertEquals("1.0",result.variables["value"])
        s.reset();assertNull(s.advance(1000).error)
    }
    @Test fun pythonSpiByteListProducesSeparateClockBursts() {
        val bench=flash();val board=FirmwareBoard(bench.circuit,bench.board.id)
        val source="""from machine import Pin, SPI
cs=Pin(5, Pin.OUT)
cs.value(1)
spi=SPI(1, baudrate=1000000, polarity=0, phase=0)
cs.value(0)
spi.write([159])
identity=spi.read(3)
cs.value(1)
"""
        val s=FirmwareSession(MicroPythonSubset.compile(source,board),board);val result=s.advance(1000)
        assertNull(result.error);assertTrue(result.variables.toString(),result.variables["identity"]?.contains("239") == true)
        val decode=ProtocolDecoder.spi(result.frames.map { ProtocolDecoder.Sample(it.timeMicros,it.result.nodeVoltages) },
            r(bench.board,"IO23"),r(bench.board,"IO19"),r(bench.board,"IO18"),r(bench.board,"IO5"))
        assertEquals(decode.joinToString(),4,decode.count { it.text.startsWith("MOSI") })
    }

    @Test fun safeAudioRequiresRealPowerOrChangingSpeakerVoltage() {
        val buzzer=p(Kind.BUZZER,"BZ");val speaker=p(Kind.SPEAKER,"SP")
        fun frames(voltage:Double)=List(10) { i -> TransientFrame(i*.005,mapOf(TerminalRef(buzzer.id,0) to voltage,
            TerminalRef(buzzer.id,1) to 0.0,TerminalRef(speaker.id,0) to voltage,TerminalRef(speaker.id,1) to 0.0),emptyMap()) }
        assertTrue(BenchAudio.pcm(Circuit("Off",listOf(buzzer),emptyList()),frames(0.0)).all { it==0.toShort() })
        val on=BenchAudio.pcm(Circuit("On",listOf(buzzer),emptyList()),frames(5.0))
        assertTrue(on.any { it>0 });assertTrue(on.any { it<0 });assertTrue(on.all { kotlin.math.abs(it.toInt())<7000 })
        assertTrue(BenchAudio.pcm(Circuit("DC",listOf(speaker),emptyList()),frames(5.0)).all { it==0.toShort() })
    }
    private fun p(kind:Kind,name:String,values:Map<String,Double> = emptyMap())=PlacedComponent(kind=kind,reference=name,x=0f,y=0f,parameters=values)
    private fun r(part:PlacedComponent,pin:String)=TerminalRef(part.id,BoardRegistry.boards.getValue(part.kind).let { it.index(it.resolve(pin) ?: pin) })
    private fun w(a:TerminalRef,b:TerminalRef)=Wire(start=a,end=b)
    private data class Bench(val circuit:Circuit,val board:PlacedComponent,val device:PlacedComponent)
    private fun i2c(kind:Kind,pullups:Boolean=true,voltage:Double=3.3):Bench {
        val board=p(Kind.ARDUINO_UNO,"UNO",mapOf("usbPower" to 1.0));val device=p(kind,"DEVICE")
        val g=p(Kind.GROUND,"G");val rail=r(board,if(voltage==3.3)"3V3" else "5V")
        val components=mutableListOf(board,device,g)
        val wires=mutableListOf(w(r(board,"GND"),TerminalRef(g.id,0)),w(rail,TerminalRef(device.id,0)),w(r(board,"GND"),TerminalRef(device.id,1)),
            w(r(board,"A4"),TerminalRef(device.id,2)),w(r(board,"A5"),TerminalRef(device.id,3)))
        if(pullups) repeat(2) { i -> val resistor=p(Kind.RESISTOR,"R$i",mapOf("resistance" to 4700.0));components+=resistor
            wires+=w(TerminalRef(resistor.id,0),r(board,if(i==0)"A4" else "A5"));wires+=w(TerminalRef(resistor.id,1),rail) }
        return Bench(Circuit("I2C acceptance",components,wires),board,device)
    }
    private fun flash():Bench {
        val board=p(Kind.ESP32_DEVKIT,"ESP",mapOf("usbPower" to 1.0));val device=p(Kind.SPI_MEMORY,"FLASH");val g=p(Kind.GROUND,"G")
        return Bench(Circuit("SPI acceptance",listOf(board,device,g),listOf(w(r(board,"GND"),TerminalRef(g.id,0)),
            w(r(board,"3V3"),TerminalRef(device.id,0)),w(r(board,"GND"),TerminalRef(device.id,1)))+
            listOf("IO23","IO19","IO18","IO5").mapIndexed { i,pin -> w(r(board,pin),TerminalRef(device.id,i+2)) }),board,device)
    }
    private fun session(b:Bench,code:String):FirmwareSession {
        val board=FirmwareBoard(b.circuit,b.board.id)
        return FirmwareSession(ArduinoSubset.compile(code,board),board)
    }
    @Test fun fullAddressScanCapturesElectricalProbesWithoutExhaustingFrameBudget() {
        val bench=i2c(Kind.I2C_TEMP_SENSOR,false)
        val s=session(bench,"void setup(){Wire.begin();}void loop(){delay(100);}")
        assertNull(s.advance(1000).error)
        val found=s.fabric.i2cScan(bench.circuit,bench.board.id)
        assertEquals(listOf(72),found)
        assertNull(s.advance(20000).error)
    }
    @Test fun lm75RegistersAreSignedHalfDegreesAndShutdownFreezesSample() {
        val bench=i2c(Kind.I2C_TEMP_SENSOR,false).let { it.copy(circuit=it.circuit.copy(environment=EnvironmentState(temperatureC=-10.5))) }
        val s=session(bench,"void setup(){Wire.begin();Wire.beginTransmission(72);Wire.write(0);Wire.endTransmission(false);Wire.requestFrom(72,2);int hi=Wire.read();int lo=Wire.read();}void loop(){delay(100);}")
        val result=s.advance(2000);assertNull(result.error)
        assertEquals("245.0",result.variables["hi"]);assertEquals("128.0",result.variables["lo"])
        val model=s.fabric.i2cDevices.single();assertTrue(model.write(listOf(1,1),2000));model.write(listOf(0),2000)
        assertEquals(listOf(245,128),model.read(2,EnvironmentState(temperatureC=70.0),2000))
        assertTrue(s.fabric.log.any { it.summary=="REPEATED START" })
        assertTrue(result.frames.any { (it.result.nodeVoltages[r(bench.board,"A5")] ?: 9.0)<.2 })
        val decode=ProtocolDecoder.i2c(result.frames.map { ProtocolDecoder.Sample(it.timeMicros,it.result.nodeVoltages) },r(bench.board,"A4"),r(bench.board,"A5"))
        assertTrue(decode.joinToString(),decode.any { it.text=="ADDR 0x48 W ACK" })
        assertTrue(decode.any { it.text=="REPEATED START" })
    }
    @Test fun rawEepromRequiresPullupsAndMissingGroundOrSwappedBusFails() {
        val noPull=i2c(Kind.I2C_EEPROM,false,5.0)
        val source="void setup(){Wire.begin();Wire.beginTransmission(80);int err=Wire.endTransmission();}void loop(){delay(100);}"
        val missing=session(noPull,source).advance(1000);assertNull(missing.error);assertEquals("2.0",missing.variables["err"])
        val good=i2c(Kind.I2C_EEPROM,true,5.0)
        assertEquals("0.0",session(good,source).advance(1000).variables["err"])
        val noGround=good.copy(circuit=good.circuit.copy(wires=good.circuit.wires.filterNot { it.start==TerminalRef(good.device.id,1) || it.end==TerminalRef(good.device.id,1) }))
        assertEquals("2.0",session(noGround,source).advance(1000).variables["err"])
    }
    @Test fun eepromPageWrapBusySequentialReadAndPowerCycleRetainData() {
        val bench=i2c(Kind.I2C_EEPROM,true,5.0)
        val source="""void setup(){Wire.begin();Wire.beginTransmission(80);Wire.write(7);Wire.write(171);Wire.write(66);Wire.endTransmission();
            Wire.beginTransmission(80);int busy=Wire.endTransmission();delay(6);
            Wire.beginTransmission(80);Wire.write(7);Wire.endTransmission(false);Wire.requestFrom(80,1);int value=Wire.read();}void loop(){delay(100);} """
        val s=session(bench,source);val result=s.advance(10000);assertNull(result.error)
        assertEquals("2.0",result.variables["busy"]);assertEquals("171.0",result.variables["value"])
        val model=s.fabric.i2cDevices.single();assertEquals(66,model.memory[0]);assertEquals(171,model.memory[7])
        model.powerChanged(false,10000);model.powerChanged(true,20000);assertEquals(171,model.memory[7])
        s.reset(20000);assertEquals(171,model.memory[7])
    }
    @Test fun eepromWriteProtectionAndProjectMemoryRoundTrip() {
        val bench=i2c(Kind.I2C_EEPROM,true,5.0)
        val model=I2cDeviceModel(80,bench.device.id,Kind.I2C_EEPROM);model.powerChanged(true,0);model.writeProtected=true
        model.write(listOf(16,66),0);assertNull(model.memory[16]);model.writeProtected=false;model.write(listOf(16,66),0)
        val circuit=bench.circuit.copy(peripheralMemory=mapOf(bench.device.id to model.memory.toMap()))
        val restored=com.indianservers.circuitssimulator.data.CircuitJson.decode(com.indianservers.circuitssimulator.data.CircuitJson.encode(circuit))
        val fabric=ProtocolFabric();fabric.rebuildDevices(restored);assertEquals(66,fabric.i2cDevices.single().memory[16])
    }
    @Test fun flashJedecWriteEnableBusyProgramEraseAndPersistenceThroughActualCs() {
        val bench=flash()
        val source="""void setup(){pinMode(5,OUTPUT);digitalWrite(5,HIGH);SPI.begin();
            digitalWrite(5,LOW);SPI.transfer(159);int manufacturer=SPI.transfer(0);int type=SPI.transfer(0);int capacity=SPI.transfer(0);digitalWrite(5,HIGH);
            digitalWrite(5,LOW);SPI.transfer(6);digitalWrite(5,HIGH);
            digitalWrite(5,LOW);SPI.transfer(2);SPI.transfer(0);SPI.transfer(0);SPI.transfer(16);SPI.transfer(66);digitalWrite(5,HIGH);
            digitalWrite(5,LOW);SPI.transfer(5);int busy=SPI.transfer(0);digitalWrite(5,HIGH);delay(4);
            digitalWrite(5,LOW);SPI.transfer(3);SPI.transfer(0);SPI.transfer(0);SPI.transfer(16);int value=SPI.transfer(0);digitalWrite(5,HIGH);
            }void loop(){delay(100);} """
        val s=session(bench,source);val result=s.advance(10000);assertNull(result.error)
        assertEquals("239.0",result.variables["manufacturer"]);assertEquals("64.0",result.variables["type"]);assertEquals("22.0",result.variables["capacity"])
        assertEquals("1.0",result.variables["busy"]);assertEquals("66.0",result.variables["value"])
        val model=s.fabric.spiDevices.single();model.powerChanged(false,10000);model.powerChanged(true,20000);assertEquals(66,model.memory[16])
        model.select(true,20000);model.transfer(6,20000);model.select(false,20000)
        model.select(true,20000);listOf(32,0,0,16).forEach { model.transfer(it,20000) };model.select(false,20000)
        assertNull(model.memory[16]);assertTrue(model.busyUntil>20000)
        assertTrue(result.frames.any { (it.result.nodeVoltages[r(bench.board,"IO18")] ?: 0.0)>2.0 })
        val decode=ProtocolDecoder.spi(result.frames.map { ProtocolDecoder.Sample(it.timeMicros,it.result.nodeVoltages) },
            r(bench.board,"IO23"),r(bench.board,"IO19"),r(bench.board,"IO18"),r(bench.board,"IO5"))
        assertTrue(decode.joinToString(),decode.any { it.text.contains("MISO 0xef") })
    }
    @Test fun flashRejectsWriteWithoutWelWrongModeAndMissingClock() {
        val bench=flash();val code="void setup(){pinMode(5,OUTPUT);digitalWrite(5,LOW);SPI.begin();SPI.transfer(2);SPI.transfer(0);SPI.transfer(0);SPI.transfer(16);SPI.transfer(66);digitalWrite(5,HIGH);}void loop(){delay(100);}"
        val s=session(bench,code);assertNull(s.advance(1000).error);assertTrue(s.fabric.spiDevices.single().memory.isEmpty())
        val wrong=session(bench,"void setup(){pinMode(5,OUTPUT);digitalWrite(5,LOW);SPI.begin();SPI.setDataMode(SPI_MODE1);SPI.transfer(159);int id=SPI.transfer(0);}void loop(){delay(100);}")
        assertEquals("255.0",wrong.advance(1000).variables["id"]);assertTrue(wrong.fabric.log.any { it.summary.contains("mode") && it.ack==false })
    }
    @Test fun oledFirmwareUpdatesPixelsNotPrintableTextAndPowerLossClearsRam() {
        val bench=i2c(Kind.OLED_SSD1306,false)
        val code="""void setup(){Wire.begin();Wire.beginTransmission(60);Wire.write(0);Wire.write(175);Wire.write(32);Wire.write(0);Wire.write(33);Wire.write(0);Wire.write(127);Wire.write(34);Wire.write(0);Wire.write(7);Wire.endTransmission();
            Wire.beginTransmission(60);Wire.write(64);Wire.write(65);Wire.write(255);Wire.endTransmission();}void loop(){delay(100);} """
        val s=session(bench,code);assertNull(s.advance(5000).error)
        val model=s.fabric.i2cDevices.single();assertTrue(model.oled.on);assertEquals(65,model.oled.ram[0]);assertEquals(255,model.oled.ram[1]);assertTrue(model.display.isEmpty())
        model.powerChanged(false,5000);assertFalse(model.oled.on);assertEquals(0,model.oled.ram.sum())
    }
    @Test fun lcdBackpackOnlyLatchesNibblesOnEnableEdge() {
        val lcd=LcdBackpack();lcd.write(0x3C);lcd.write(0x38);lcd.write(0x2C);lcd.write(0x28)
        fun send(value:Int,data:Boolean=false) { listOf(value shr 4,value and 15).forEach { nibble ->
            val byte=(nibble shl 4) or 8 or if(data)1 else 0;lcd.write(byte or 4);lcd.write(byte) } }
        send(0x0C);send(0x80);"HELLO".forEach { send(it.code,true) }
        assertTrue(lcd.text().startsWith("HELLO"));lcd.write(72);assertTrue(lcd.text().startsWith("HELLO"));send(1);assertFalse(lcd.text().contains("HELLO"))
    }
    @Test fun pirWarmupMotionHoldAndLossOfPowerDriveActualOutput() {
        val device=p(Kind.PIR_SENSOR,"PIR",mapOf("warmupMs" to 100.0,"delayMs" to 50.0));val power=p(Kind.SOURCE,"V",mapOf("voltage" to 5.0));val g=p(Kind.GROUND,"G")
        val circuit=Circuit("PIR",listOf(device,power,g),listOf(w(TerminalRef(power.id,0),TerminalRef(device.id,0)),w(TerminalRef(power.id,1),TerminalRef(g.id,0)),w(TerminalRef(device.id,2),TerminalRef(g.id,0))),environment=EnvironmentState(motion=true))
        val runtime=PeripheralRuntime();val solver=DcSolver();val base=solver.solve(circuit)
        fun output(c:Circuit,time:Long)=solver.solve(c,runtime.drives(c,solver.solve(c),time)).nodeVoltages[TerminalRef(device.id,1)] ?: -1.0
        assertTrue(output(circuit,0)<.1);assertTrue(output(circuit,100000)>3.0)
        val still=circuit.copy(environment=circuit.environment.copy(motion=false));assertTrue(output(still,120000)>3.0);assertTrue(output(still,160000)<.1)
        val off=circuit.copy(wires=circuit.wires.filterNot { it.start==TerminalRef(power.id,0) });assertTrue(output(off,170000)<.1)
    }
    @Test fun ultrasonicRejectsShortTriggerAndEchoWidthComesFromDistance() {
        val device=p(Kind.ULTRASONIC,"US");val g=p(Kind.GROUND,"G");val power=p(Kind.SOURCE,"V",mapOf("voltage" to 5.0))
        val circuit=Circuit("Echo",listOf(device,g,power),listOf(w(TerminalRef(power.id,0),TerminalRef(device.id,0)),w(TerminalRef(power.id,1),TerminalRef(g.id,0)),w(TerminalRef(device.id,1),TerminalRef(g.id,0))),environment=EnvironmentState(distanceCm=100.0))
        val solver=DcSolver();val runtime=PeripheralRuntime()
        fun result(high:Boolean)=solver.solve(circuit,listOf(ExternalDrive(TerminalRef(device.id,2),if(high)5.0 else 0.0,100.0)))
        runtime.drives(circuit,result(true),0);runtime.drives(circuit,result(false),5)
        assertEquals(0.0,runtime.drives(circuit,result(false),300).single().voltage,0.01)
        runtime.drives(circuit,result(true),1000);runtime.drives(circuit,result(false),1010)
        assertEquals(5.0,runtime.drives(circuit,result(false),1210).single().voltage,0.01)
        assertEquals(0.0,runtime.drives(circuit,result(false),7010).single().voltage,0.01)
    }
    @Test fun terminalReceivesOnlyAfterFrameTimeAndChecksBaudAndGround() {
        val board=p(Kind.ARDUINO_UNO,"UNO",mapOf("usbPower" to 1.0));val term=p(Kind.SERIAL_TERMINAL,"TERM");val g=p(Kind.GROUND,"G")
        val c=Circuit("Terminal",listOf(board,term,g),listOf(w(r(board,"GND"),TerminalRef(g.id,0)),w(r(board,"GND"),TerminalRef(term.id,2)),w(r(board,"TX"),TerminalRef(term.id,1)),w(r(board,"RX"),TerminalRef(term.id,0))))
        val bench=Bench(c,board,term);val s=session(bench,"void setup(){Serial.begin(9600);Serial.print(\"HI\");}void loop(){delay(10);}")
        assertNull(s.advance(0).error);assertEquals("",s.fabric.displayText()[term.id])
        val later=s.advance(3000);assertNull(later.error);assertEquals(s.fabric.log.joinToString(),"HI",s.fabric.displayText()[term.id])
        s.fabric.terminalSend(c,term.id,"A");s.advance(3000);assertEquals(65,s.fabric.uart[board.id]!!.read())
        assertTrue(later.frames.any { (it.result.nodeVoltages[r(board,"TX")] ?: 5.0)<.1 })
    }
    @Test fun shiftRegisterClocksAndLatchAreActualElectricalPins() {
        val board=p(Kind.ARDUINO_UNO,"UNO",mapOf("usbPower" to 1.0));val device=p(Kind.SHIFT_74HC595,"SHIFT");val g=p(Kind.GROUND,"G")
        val circuit=Circuit("Shift",listOf(board,device,g),listOf(w(r(board,"GND"),TerminalRef(g.id,0)),w(r(board,"GND"),TerminalRef(device.id,7)),
            w(r(board,"5V"),TerminalRef(device.id,15)),w(r(board,"5V"),TerminalRef(device.id,9)),w(r(board,"GND"),TerminalRef(device.id,12)),
            w(r(board,"D2"),TerminalRef(device.id,13)),w(r(board,"D3"),TerminalRef(device.id,10)),w(r(board,"D4"),TerminalRef(device.id,11)),w(r(board,"D5"),TerminalRef(device.id,14))))
        val code="void setup(){pinMode(2,OUTPUT);pinMode(3,OUTPUT);pinMode(4,OUTPUT);pinMode(5,INPUT);digitalWrite(2,HIGH);digitalWrite(3,HIGH);digitalWrite(3,LOW);digitalWrite(4,HIGH);digitalWrite(4,LOW);int q=digitalRead(5);}void loop(){delay(100);}"
        val result=session(Bench(circuit,board,device),code).advance(1000);assertNull(result.error);assertEquals("1.0",result.variables["q"])
    }
}
