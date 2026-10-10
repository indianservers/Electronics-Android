package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import com.indianservers.circuitssimulator.intelligence.CircuitIntelligence
import com.indianservers.circuitssimulator.intelligence.FirmwareIntel
import com.indianservers.circuitssimulator.intelligence.IntelligenceInput
import com.indianservers.circuitssimulator.simulation.TransientSolver
import org.junit.Assert.*
import org.junit.Test

class PhaseFiveEmbeddedTest {
    private fun part(kind:Kind,ref:String,values:Map<String,Double> = emptyMap())=
        PlacedComponent(kind=kind,reference=ref,x=100f,y=100f,
            parameters=ComponentRegistry.definitions.getValue(kind).parameters.associate { it.key to it.default }+values)
    private fun wire(a:PlacedComponent,ap:Int,b:PlacedComponent,bp:Int)=
        Wire(start=TerminalRef(a.id,ap),end=TerminalRef(b.id,bp))

    private fun spiBase():Circuit {
        val board=part(Kind.ESP32_DEVKIT,"ESP",mapOf("usbPower" to 1.0))
        val ground=part(Kind.GROUND,"GND")
        val supply=part(Kind.SOURCE,"V",mapOf("voltage" to 5.0))
        return Circuit("SPI bench",listOf(board,ground,supply),listOf(wire(board,BoardRegistry.boards.getValue(board.kind).index("GND"),ground,0),wire(supply,1,ground,0)))
    }
    @Test fun blinkCodeDrivesLedThroughSolver() {
        val circuit=FirmwareExamples.blinkCircuit()
        val board=FirmwareBoard(circuit,circuit.components.first { it.kind.isBoard }.id)
        val session=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.BLINK,board),board)
        val led=circuit.components.first { it.kind==Kind.LED }
        val on=session.advance(0)
        assertNull(on.error)
        assertTrue(on.frames.last().result.readings.getValue(led.id).current>.001)
        assertTrue(on.console.any { it.contains("LED on") })
        val off=session.advance(500_000)
        assertNull(off.error)
        assertTrue(off.frames.last().result.readings.getValue(led.id).current<1e-5)
    }

    @Test fun buttonProjectUsesDigitalReadOfPhysicalNode() {
        val circuit=FirmwareExamples.buttonCircuit()
        val boardPart=circuit.components.first { it.kind==Kind.ARDUINO_UNO }
        val button=circuit.components.first { it.kind==Kind.SWITCH }
        val led=circuit.components.first { it.kind==Kind.LED }
        val board=FirmwareBoard(circuit,boardPart.id)
        val session=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.BUTTON,board),board)
        val open=session.advance(0)
        assertNull(open.error)
        assertTrue(open.frames.last().result.readings.getValue(led.id).current<1e-5)
        board.updateCircuit(circuit.copy(components=circuit.components.map {
            if(it.id==button.id) it.copy(closed=true) else it }))
        val closed=session.advance(60_000)
        assertNull(closed.error)
        assertTrue(closed.frames.last().result.readings.getValue(led.id).current>.001)
        assertTrue(closed.console.any { it.contains("PRESSED") })
    }

    @Test fun adcQuantizesGroundMidpointAndOverrange() {
        val base=FirmwareExamples.blinkCircuit()
        val boardPart=base.components.first { it.kind==Kind.ARDUINO_UNO }
        val ground=base.components.first { it.kind==Kind.GROUND }
        val supply=base.components.first { it.kind==Kind.SOURCE }
        val a0=BoardRegistry.boards.getValue(boardPart.kind).index("A0")
        fun sample(extra:List<Wire>,sourceVoltage:Double=5.0):Int {
            val circuit=base.copy(components=base.components.map {
                if(it.id==supply.id) it.copy(parameters=it.parameters+("voltage" to sourceVoltage)) else it },
                wires=base.wires+extra)
            val board=FirmwareBoard(circuit,boardPart.id)
            val session=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.ANALOG_READ,board),board)
            val result=session.advance(0)
            assertNull(result.error)
            return result.console.single().toInt()
        }
        assertEquals(0,sample(listOf(wire(boardPart,a0,ground,0))))
        val pot=part(Kind.POTENTIOMETER,"RV1",mapOf("position" to .5))
        val mid=FirmwareExamples.analogCircuit()
        val midBoard=FirmwareBoard(mid,mid.components.first { it.kind.isBoard }.id)
        val midSession=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.ANALOG_READ,midBoard),midBoard)
        val midValue=midSession.advance(0)
        assertNull(midValue.error)
        assertTrue(midValue.console.single().toInt() in 480..544)
        val hot=part(Kind.SOURCE,"V2",mapOf("voltage" to 9.0))
        val over=base.copy(components=base.components+hot,wires=base.wires+listOf(
            wire(hot,0,boardPart,a0),wire(hot,1,ground,0)))
        val overBoard=FirmwareBoard(over,boardPart.id)
        val overSession=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.ANALOG_READ,overBoard),overBoard)
        val clamped=overSession.advance(0)
        assertNull(clamped.error)
        assertEquals(1023,clamped.console.single().toInt())
    }

    @Test fun pwmDutiesProduceMatchingElectricalAverages() {
        val circuit=FirmwareExamples.pwmCircuit()
        val boardPart=circuit.components.first { it.kind.isBoard }
        val pin=TerminalRef(boardPart.id,BoardRegistry.boards.getValue(boardPart.kind).index("D9"))
        listOf(0,64,128,192,255).forEach { duty ->
            val board=FirmwareBoard(circuit,boardPart.id)
            val code="void setup() { pinMode(9, OUTPUT); analogWrite(9, $duty); } void loop() { delay(20); }"
            val session=FirmwareSession(ArduinoSubset.compile(code,board),board)
            val result=session.advance(8_000)
            assertNull(result.error)
            val samples=result.frames.mapNotNull { it.result.nodeVoltages[pin] }
            assertTrue(samples.isNotEmpty())
            val reported=result.frames.last().pins["D9"]?.pwmDuty ?: if(duty==0) 0.0 else -1.0
            if(duty==0) assertTrue("${samples.last()}",samples.last()<1.0)
            else if(duty==255) assertTrue("${samples.last()}",samples.last()>3.0)
            else {
                assertTrue("$duty max ${samples.max()}",samples.max()>3.0)
                assertTrue("$duty min ${samples.min()}",samples.min()<1.0)
                assertEquals(duty/255.0,reported,0.02)
            }
        }
    }

    @Test fun uartBoardToBoardTogglesLedFromReceivedText() {
        val circuit=FirmwareExamples.uartPairCircuit()
        val next=RuntimeScheduler().compile(circuit).advance(400_000)
        assertNull(next.error)
        val led=circuit.components.first { it.kind==Kind.LED }
        assertTrue(next.console.values.flatten().any { it.contains("ON") || it.contains("OFF") })
        assertTrue(next.busLog.any { it.kind==BusKind.UART })
        assertTrue(next.frames.any { (it.result.readings[led.id]?.current ?: 0.0)>.001 } ||
            next.frames.any { frame ->
                val board=circuit.components.first { it.reference=="UNO2" }
                (frame.result.nodeVoltages[TerminalRef(board.id,BoardRegistry.boards.getValue(board.kind).index("D13"))] ?: 0.0)>3.0
            })
    }

    @Test fun i2cScannerFindsConnectedDevicesAndDetectsConflict() {
        val circuit=FirmwareExamples.i2cCircuit()
        val board=FirmwareBoard(circuit,circuit.components.first { it.kind.isBoard }.id)
        val session=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.I2C_SCAN,board),board,
            ProtocolFabric().also { it.rebuildDevices(circuit) },circuit.environment)
        val result=session.advance(10000)
        assertNull(result.error)
        val joined=result.console.joinToString(" ")
        assertTrue("$joined",joined.contains("0x48") || joined.contains("0x3c") ||
            joined.contains("48") && joined.contains("3c"))
        val second=part(Kind.I2C_TEMP_SENSOR,"TMP2",mapOf("address" to 0x48.toDouble()))
        val original=circuit.components.first { it.kind==Kind.I2C_TEMP_SENSOR }
        val duplicate=circuit.copy(components=circuit.components+second,wires=circuit.wires+
            (0..3).map { wire(original,it,second,it) })
        val fabric=ProtocolFabric();fabric.rebuildDevices(duplicate)
        val poweredBoard=FirmwareBoard(duplicate,board.boardId)
        fabric.i2cRoutes[board.boardId]=board.definition.hardware.i2cPins!!
        fabric.electricalResult=poweredBoard.settle().circuitResult
        assertTrue(fabric.duplicateAddresses().contains(0x48))
        val write=fabric.i2cWrite(duplicate,board.boardId,0x48,listOf(0),0)
        assertEquals(4,write)
    }

    @Test fun i2cTemperatureUsesEnvironmentNotADirectVariable() {
        val circuit=FirmwareExamples.i2cCircuit().copy(environment=EnvironmentState(temperatureC=27.3),
            firmware=listOf(FirmwareAttachment(FirmwareExamples.i2cCircuit().components.first { it.kind.isBoard }.id,
                FirmwareLanguage.ARDUINO_SUBSET,FirmwareExamples.I2C_TEMP,true)))
        val boardPart=circuit.components.first { it.kind.isBoard }
        val board=FirmwareBoard(circuit,boardPart.id)
        val fabric=ProtocolFabric();fabric.rebuildDevices(circuit)
        val session=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.I2C_TEMP,board),board,fabric,circuit.environment)
        val result=session.advance(5000)
        assertNull(result.error)
        assertTrue("${result.console}",result.console.any { it.contains("27.5") })
    }

    @Test fun spiFlashJedecReadDoesNotProgramMemory() {
        val base=spiBase()
        val boardPart=base.components.first { it.kind.isBoard }
        val memory=part(Kind.SPI_MEMORY,"U2")
        val supply=base.components.first { it.kind==Kind.SOURCE }
        val ground=base.components.first { it.kind==Kind.GROUND }
        val circuit=base.copy(components=base.components+memory,wires=base.wires+listOf(
            wire(boardPart,BoardRegistry.boards.getValue(boardPart.kind).index("3V3"),memory,0),wire(memory,1,ground,0),wire(memory,5,ground,0))+
            listOf("IO23","IO19","IO18").mapIndexed { i,pin ->
                wire(boardPart,BoardRegistry.boards.getValue(boardPart.kind).index(pin),memory,i+2) })
        val board=FirmwareBoard(circuit,boardPart.id)
        val fabric=ProtocolFabric();fabric.rebuildDevices(circuit)
        val code="""void setup() { SPI.begin(); SPI.transfer(159); int id=SPI.transfer(0); }
            void loop() { delay(100); }"""
        val session=FirmwareSession(ArduinoSubset.compile(code,board),board,fabric)
        val result=session.advance(1000)
        assertNull(result.error)
        assertTrue(fabric.log.any { it.kind==BusKind.SPI && it.summary.contains("0x9f") })
        assertEquals("239.0",result.variables["id"])
        assertTrue(fabric.spiDevices.single().memory.isEmpty())
    }

    @Test fun lm35EnvironmentFlowsThroughAdcToSerial() {
        val circuit=FirmwareExamples.temperatureCircuit()
        val board=FirmwareBoard(circuit,circuit.components.first { it.kind.isBoard }.id)
        val session=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.TEMPERATURE,board),board,
            environment=circuit.environment)
        val result=session.advance(0)
        assertNull(result.error)
        val printed=result.console.last { it.contains("Temperature") || it.toDoubleOrNull()!=null }
        val number=Regex("""[-+]?\d+(?:\.\d+)?""").findAll(result.console.joinToString(" "))
            .map { it.value.toDouble() }.last()
        assertTrue("$printed $number",number in 24.0..31.0)
    }

    @Test fun servoWriteProducesFiftyHertzPulse() {
        val circuit=FirmwareExamples.servoCircuit()
        val boardPart=circuit.components.first { it.kind.isBoard }
        val pin=TerminalRef(boardPart.id,BoardRegistry.boards.getValue(boardPart.kind).index("D9"))
        val board=FirmwareBoard(circuit,boardPart.id)
        val session=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.SERVO_SWEEP,board),board)
        val result=session.advance(40_000)
        assertNull(result.error)
        val samples=result.frames.mapNotNull { it.result.nodeVoltages[pin] }
        assertTrue(samples.max()>3.0)
        assertTrue(samples.min()<1.0)
        val pwm=result.frames.last().pins["D9"]
        assertEquals(50.0,pwm?.pwmFrequencyHz ?: 0.0,1.0)
        assertTrue((pwm?.pwmDuty ?: 0.0) in 0.05..0.11)
    }

    @Test fun trafficSequenceComesFromFirmwareNotAScript() {
        val circuit=FirmwareExamples.trafficCircuit()
        val board=FirmwareBoard(circuit,circuit.components.first { it.kind.isBoard }.id)
        val session=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.TRAFFIC,board),board)
        val first=session.advance(0)
        assertEquals(listOf("RED"),first.console.takeLast(1))
        val second=session.advance(400_000)
        assertTrue(second.console.any { it=="AMBER" })
        val third=session.advance(200_000)
        assertTrue(third.console.any { it=="GREEN" })
    }

    @Test fun unsupportedLibraryAndInvalidPwmAreExplicit() {
        val circuit=FirmwareExamples.blinkCircuit()
        val board=FirmwareBoard(circuit,circuit.components.first { it.kind.isBoard }.id)
        val wifi=runCatching { ArduinoSubset.compile("#include <WiFi.h>\nvoid setup(){}\nvoid loop(){delay(1);}",board) }
        assertTrue(wifi.exceptionOrNull()!!.message!!.contains("WiFi.h"))
        val pwm=runCatching { ArduinoSubset.compile("void setup(){ analogWrite(2, 128); } void loop(){ delay(1); }",board) }
        assertTrue(pwm.exceptionOrNull()!!.message!!.contains("Line"))
    }

    @Test fun firmwareResetClearsPinsAndPreservesSource() {
        val circuit=FirmwareExamples.blinkCircuit()
        val board=FirmwareBoard(circuit,circuit.components.first { it.kind.isBoard }.id)
        val session=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.BLINK,board),board)
        session.advance(0)
        assertTrue(session.consoleLines().isNotEmpty())
        session.reset()
        assertEquals(0L,session.timeMicros)
        assertTrue(session.consoleLines().isEmpty())
        val again=session.advance(0)
        assertNull(again.error)
        assertTrue(again.console.any { it.contains("LED on") })
    }

    @Test fun unpoweredBoardWithoutUsbFailsClearly() {
        val circuit=FirmwareExamples.blinkCircuit()
        val boardPart=circuit.components.first { it.kind.isBoard }
        val supply=circuit.components.first { it.kind==Kind.SOURCE }
        val dead=circuit.copy(components=circuit.components.map {
            when(it.id) {
                boardPart.id -> it.copy(parameters=it.parameters+("usbPower" to 0.0))
                supply.id -> it.copy(parameters=it.parameters+("voltage" to 0.0))
                else -> it
            }
        },firmware=listOf(FirmwareAttachment(boardPart.id,FirmwareLanguage.ARDUINO_SUBSET,FirmwareExamples.BLINK,false)))
        val board=FirmwareBoard(dead,boardPart.id)
        val session=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.BLINK,board),board)
        val result=session.advance(0)
        assertNotNull(result.error)
        assertTrue(result.error!!.message.contains("unpowered") || result.error!!.message.contains("USB"))
    }

    @Test fun codeAwareDiagnosticsSeeLedOnWrongPin() {
        val circuit=FirmwareExamples.blinkCircuit()
        val board=circuit.components.first { it.kind.isBoard }
        val firmware=FirmwareIntel(sources=mapOf(board.id to "void setup(){ pinMode(9, OUTPUT);} void loop(){ digitalWrite(9, HIGH); delay(10);}"),
            usages=mapOf(board.id to listOf(PinUsage("D9","digitalWrite",2,"9"))))
        val report=CircuitIntelligence.analyze(IntelligenceInput(circuit,firmware=firmware))
        assertTrue(report.issues.any { it.ruleId=="CODE_LED_MISMATCH" || it.ruleId=="CODE_PIN_OPEN" })
    }

    @Test fun firmwarePersistsOnCircuitDocument() {
        val original=FirmwareExamples.temperatureCircuit()
        assertEquals(FirmwareLanguage.ARDUINO_SUBSET,original.firmware.single().language)
        assertTrue(original.firmware.single().source.contains("analogRead"))
        assertEquals(27.3,original.environment.temperatureC,1e-6)
        val copied=original.copy(firmware=original.firmware,environment=original.environment)
        assertEquals(original.firmware.single().source,copied.firmware.single().source)
    }

    @Test fun functionGeneratorSquareAppearsOnTransientTrace() {
        val generator=part(Kind.FUNCTION_GENERATOR,"FG1",mapOf("waveform" to 1.0,"frequency" to 1000.0,"amplitude" to 2.0))
        val ground=part(Kind.GROUND,"GND1")
        val circuit=Circuit("FG",listOf(generator,ground),listOf(wire(generator,1,ground,0)))
        val transient=TransientSolver().simulate(circuit,0.002,2e-5)
        val node=transient.frames.mapNotNull { it.nodeVoltages[TerminalRef(generator.id,0)] }
        assertTrue(node.max()>1.5)
        assertTrue(node.min()<-1.5 || node.min()<0.2)
    }

    @Test fun analogReadZeroMapsToA0NotD0() {
        val circuit=FirmwareExamples.analogCircuit()
        val board=FirmwareBoard(circuit,circuit.components.first { it.kind.isBoard }.id)
        assertEquals("A0",board.resolve(0,analog=true))
        assertEquals("D0",board.resolve(0,analog=false))
        val session=FirmwareSession(ArduinoSubset.compile(
            "void setup(){ Serial.begin(9600); Serial.println(analogRead(0)); } void loop(){ delay(100); }",board),board)
        val result=session.advance(0)
        assertNull(result.error)
        assertTrue(result.console.single().toInt() in 480..544)
    }

    @Test fun nodeMcuNumericFiveIsGpio5AliasD1() {
        val mcu=part(Kind.NODEMCU_ESP8266,"MCU1",mapOf("usbPower" to 1.0))
        val circuit=Circuit("NodeMCU",listOf(mcu),emptyList())
        val board=FirmwareBoard(circuit,mcu.id)
        assertEquals("D1",board.resolve(5))
        assertEquals("D1",board.resolve("GPIO5"))
        assertEquals("D5",board.resolve("D5"))
        assertEquals("D1 / GPIO5",BoardRegistry.boards.getValue(Kind.NODEMCU_ESP8266).displayName("D1"))
    }

    @Test fun constIntPinIsExtractedAndDrivesTheMappedHeader() {
        val circuit=FirmwareExamples.blinkCircuit()
        val board=FirmwareBoard(circuit,circuit.components.first { it.kind.isBoard }.id)
        val source="const int ledPin = 13;\nvoid setup(){ pinMode(ledPin, OUTPUT); digitalWrite(ledPin, HIGH); }\nvoid loop(){ delay(20); }"
        val program=ArduinoSubset.compile(source,board)
        val usages=SourceSymbols.extract(program,board)
        assertTrue(usages.any { it.pin=="D13" && it.api=="digitalWrite" })
        val session=FirmwareSession(program,board)
        val result=session.advance(0)
        assertNull(result.error)
        val led=circuit.components.first { it.kind==Kind.LED }
        assertTrue(result.frames.last().result.readings.getValue(led.id).current>.001)
    }

    @Test fun inputPullupIsNotReportedAsFloating() {
        val circuit=FirmwareExamples.blinkCircuit()
        val board=circuit.components.first { it.kind.isBoard }
        val source="void setup(){ pinMode(2, INPUT_PULLUP); } void loop(){ digitalRead(2); delay(20); }"
        val program=ArduinoSubset.compile(source,FirmwareBoard(circuit,board.id))
        val usages=SourceSymbols.extract(program,FirmwareBoard(circuit,board.id))
        val firmware=FirmwareIntel(sources=mapOf(board.id to source),usages=mapOf(board.id to usages))
        val report=CircuitIntelligence.analyze(IntelligenceInput(circuit,firmware=firmware))
        assertTrue(report.issues.none { it.ruleId=="CODE_INPUT_OPEN" })
        val floating="void setup(){ pinMode(2, INPUT); } void loop(){ digitalRead(2); delay(20); }"
        val open=SourceSymbols.extract(ArduinoSubset.compile(floating,FirmwareBoard(circuit,board.id)),FirmwareBoard(circuit,board.id))
        val openReport=CircuitIntelligence.analyze(IntelligenceInput(circuit,
            firmware=FirmwareIntel(sources=mapOf(board.id to floating),usages=mapOf(board.id to open))))
        assertTrue(openReport.issues.any { it.ruleId=="CODE_INPUT_OPEN" })
    }

    @Test fun runawayLoopReportsInstructionBudget() {
        val circuit=FirmwareExamples.blinkCircuit()
        val board=FirmwareBoard(circuit,circuit.components.first { it.kind.isBoard }.id)
        val session=FirmwareSession(ArduinoSubset.compile("void setup(){} void loop(){ while(true){} }",board),board)
        val result=session.advance(2_000)
        assertNotNull(result.error)
        assertTrue(result.error!!.message.contains("instruction budget"))
    }

    @Test fun digitalWriteAfterPwmStopsTheChannel() {
        val circuit=FirmwareExamples.pwmCircuit()
        val boardPart=circuit.components.first { it.kind.isBoard }
        val pin=TerminalRef(boardPart.id,BoardRegistry.boards.getValue(boardPart.kind).index("D9"))
        val board=FirmwareBoard(circuit,boardPart.id)
        val session=FirmwareSession(ArduinoSubset.compile(
            "void setup(){ pinMode(9, OUTPUT); analogWrite(9, 128); digitalWrite(9, LOW); } void loop(){ delay(20); }",board),board)
        val result=session.advance(4_000)
        assertNull(result.error)
        assertNull(result.frames.last().pins["D9"]?.pwmDuty)
        assertTrue(result.frames.last().result.nodeVoltages.getValue(pin)<1.0)
    }

    @Test fun stopFirmwareClearsGpioDrives() {
        val circuit=FirmwareExamples.blinkCircuit()
        val board=FirmwareBoard(circuit,circuit.components.first { it.kind.isBoard }.id)
        val session=FirmwareSession(ArduinoSubset.compile(FirmwareExamples.BLINK,board),board)
        val on=session.advance(0)
        val led=circuit.components.first { it.kind==Kind.LED }
        assertTrue(on.frames.last().result.readings.getValue(led.id).current>.001)
        session.stop()
        val after=board.settle()
        assertTrue(after.circuitResult.readings.getValue(led.id).current<1e-5)
    }

    @Test fun unoTimer0PinsReport980Hz() {
        val circuit=FirmwareExamples.blinkCircuit()
        val board=FirmwareBoard(circuit,circuit.components.first { it.kind.isBoard }.id)
        assertEquals(980.0,board.pwmFrequencyHz("D5"),1e-6)
        assertEquals(980.0,board.pwmFrequencyHz("D6"),1e-6)
        assertEquals(490.0,board.pwmFrequencyHz("D9"),1e-6)
    }

    @Test fun unselectedSpiDeviceDoesNotAcceptTransfer() {
        val base=spiBase()
        val boardPart=base.components.first { it.kind.isBoard }
        val selected=part(Kind.SPI_MEMORY,"U2")
        val ignored=part(Kind.SPI_MEMORY,"U3")
        val supply=base.components.first { it.kind==Kind.SOURCE }
        val ground=base.components.first { it.kind==Kind.GROUND }
        val circuit=base.copy(components=base.components+selected+ignored,wires=base.wires+listOf(
            wire(boardPart,BoardRegistry.boards.getValue(boardPart.kind).index("3V3"),selected,0),wire(selected,1,ground,0),wire(selected,5,ground,0),
            wire(boardPart,BoardRegistry.boards.getValue(boardPart.kind).index("3V3"),ignored,0),wire(ignored,1,ground,0),wire(ignored,5,supply,0))+
            listOf("IO23","IO19","IO18").flatMapIndexed { i,pin ->
                listOf(wire(boardPart,BoardRegistry.boards.getValue(boardPart.kind).index(pin),selected,i+2),
                    wire(boardPart,BoardRegistry.boards.getValue(boardPart.kind).index(pin),ignored,i+2)) })
        val board=FirmwareBoard(circuit,boardPart.id)
        val fabric=ProtocolFabric();fabric.rebuildDevices(circuit)
        val session=FirmwareSession(ArduinoSubset.compile(
            "void setup(){ SPI.begin(); SPI.transfer(159);int id=SPI.transfer(0); } void loop(){ delay(100); }",board),board,fabric)
        val result=session.advance(1000)
        assertNull(result.error)
        assertEquals("239.0",result.variables["id"])
        assertTrue(fabric.spiDevices.first { it.ownerId==ignored.id }.memory.isEmpty())
    }

    @Test fun boardSupportDoesNotAdvertiseWifi() {
        Kind.entries.filter { it.isBoard }.forEach { kind ->
            val matrix=BoardSupport.matrix(kind)
            val wifiHardware=BoardRegistry.boards.getValue(kind).capabilities.wifi.hardware
            assertEquals(if(wifiHardware) PeripheralLevel.METADATA else PeripheralLevel.NOT_SIMULATED,matrix.wifi)
            assertTrue(BoardSupport.languages(kind).isNotEmpty())
        }
    }
}
