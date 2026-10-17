package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.roundToInt

/** Application fixtures exercise firmware, wiring, ADC/bus readings and live physical-input edits. */
@RunWith(Parameterized::class)
class IotThirtyProjectsTest(private val name: String, private val kind: Kind, private val mode: Int) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun projects(): List<Array<Any>> = buildList {
            IotSensorParts.profiles.values.forEach { p ->
                listOf("Cold storage", "Room monitor", "Heat alarm").forEachIndexed { i, application ->
                    add(arrayOf("${p.part} $application", p.kind, i))
                }
            }
            MotionSensorParts.profiles.values.forEach { add(arrayOf("${it.part} motion telemetry", it.kind, 3)) }
            add(arrayOf("Automatic street lighting", Kind.LDR, 4))
            add(arrayOf("Irrigation valve controller", Kind.POTENTIOMETER, 5))
        }
    }

    private fun start(circuit: Circuit): FirmwareSession {
        val attachment = circuit.firmware.single()
        val board = FirmwareBoard(circuit, attachment.boardId)
        return FirmwareSession(ArduinoSubset.compile(attachment.source, board), board)
    }

    private fun advance(session: FirmwareSession, duration: Long): FirmwareAdvance {
        val result = session.advance(duration)
        assertNull("$name: ${result.error}", result.error)
        assertTrue("$name: no solved frames", result.frames.isNotEmpty())
        result.frames.forEach { frame ->
            assertNull("$name: electrical solver failed", frame.result.error)
            assertTrue("$name: nonfinite voltage", frame.result.nodeVoltages.values.all { it.isFinite() })
        }
        return result
    }

    @Test fun projectRespondsToLiveInputAndResets() {
        when (mode) {
            in 0..2 -> analogProject()
            3 -> motionProject()
            4 -> lightingProject()
            5 -> irrigationProject()
        }
    }

    private fun analogProject() {
        val p = IotSensorParts.profiles.getValue(kind)
        val independent=p.stimulusKey!="temperatureC"
        val temperature = if(independent)25.0 else listOf(maxOf(p.temperatureMin, 15.0), 25.0, minOf(p.temperatureMax, 70.0))[mode]
        val physicalInput=if(independent)p.stimulusMin+(p.stimulusMax-p.stimulusMin)*listOf(.2,.5,.8)[mode] else temperature
        val humidity = listOf(15.0, 50.0, 85.0)[mode]
        val supply = if (kind == Kind.AD22103_SENSOR) 3.3 else 5.0
        fun part(k: Kind, ref: String, params: Map<String, Double> = emptyMap()) =
            PlacedComponent(kind=k, reference=ref, x=0f, y=0f, parameters=params)
        val board = part(Kind.ARDUINO_UNO, "UNO", mapOf("usbPower" to 1.0))
        val sensor = part(kind, "S1", mapOf("temperatureC" to temperature, "humidityPercent" to humidity)+(if(independent)mapOf(p.stimulusKey to physicalInput) else emptyMap()))
        val source = part(Kind.SOURCE, "V1", mapOf("voltage" to supply))
        val ground = part(Kind.GROUND, "GND")
        val definition = BoardRegistry.boards.getValue(board.kind)
        fun wire(a: PlacedComponent, ai: Int, b: PlacedComponent, bi: Int) =
            Wire(start=TerminalRef(a.id, ai), end=TerminalRef(b.id, bi))
        val code = """void setup() { Serial.begin(9600); }
            void loop() { Serial.println(analogRead(A0)); delay(10); }"""
        val circuit = Circuit(name, listOf(board, sensor, source, ground), listOf(
            wire(source, 0, sensor, p.supplyPin), wire(source, 1, ground, 0),
            wire(sensor, p.groundPin, ground, 0), wire(board, definition.index("GND"), ground, 0),
            wire(sensor, p.outputPin, board, definition.index("A0"))),
            firmware=listOf(FirmwareAttachment(board.id, FirmwareLanguage.ARDUINO_SUBSET, code, true)))
        val session = start(circuit)
        fun check(t: Double, rh: Double, input:Double) {
            val result = advance(session, 20_000)
            // Independent nominal transfer, followed by the board's ADC quantization.
            val stimulus = if(independent)input else if (kind == Kind.HIH4030_SENSOR) rh * (1.0546 - .00216*t) else t
            val voltage = (p.offset + p.slope*stimulus) * (p.referenceSupply?.let { supply/it } ?: 1.0)
            assertEquals(voltage, result.frames.last().result.nodeVoltages.getValue(TerminalRef(sensor.id, p.outputPin)), .002)
            val expected = (voltage / definition.adcReferenceVoltage * ((1 shl definition.adcBits)-1)).roundToInt()
            assertEquals("$name ADC", expected.toDouble(), session.consoleLines().last().toDouble(), 2.0)
        }
        check(temperature, humidity, physicalInput)
        val newTemperature = if(independent)temperature else temperature + 5.0
        val newInput=if(independent)physicalInput+(p.stimulusMax-p.stimulusMin)*.05 else newTemperature
        val newHumidity = humidity + 5.0
        session.board.updateCircuit(circuit.copy(components=circuit.components.map {
            if (it.id == sensor.id) it.copy(parameters=it.parameters + mapOf("temperatureC" to newTemperature, "humidityPercent" to newHumidity)+(if(independent)mapOf(p.stimulusKey to newInput) else emptyMap())) else it
        }))
        check(newTemperature, newHumidity, newInput)
        session.reset()
        assertEquals(0L, session.timeMicros)
        check(newTemperature, newHumidity, newInput)
    }

    private fun motionProject() {
        val circuit = MotionSensorExamples.circuit(kind)
        val session = start(circuit)
        advance(session, 30_000)
        val first = session.consoleLines().last().substringAfter(": ").toDouble()
        assertTrue("$name initial sample", first > 0)
        val key = if (MotionSensorParts.profiles.getValue(kind).gyro) "gyroXDps" else "accelXG"
        session.board.updateCircuit(circuit.copy(components=circuit.components.map {
            if (it.kind == kind) it.copy(parameters=it.parameters + (key to -it.value(key))) else it
        }))
        advance(session, 110_000)
        val second = session.consoleLines().last().substringAfter(": ").toDouble()
        assertEquals("$name signed live sample", -first, second, .005)
        session.reset()
        advance(session, 30_000)
        assertEquals(second, session.consoleLines().last().substringAfter(": ").toDouble(), .005)
    }

    private fun lightingProject() {
        val circuit = FirmwareExamples.streetLightCircuit().copy(environment=EnvironmentState(illuminanceLux=1.0))
        val session = start(circuit)
        advance(session, 50_000)
        assertTrue(session.consoleLines().last().contains("Lamp ON"))
        val led = circuit.components.first { it.kind == Kind.LED }
        assertTrue(session.board.latest!!.readings.getValue(led.id).current > .001)
        session.board.updateCircuit(circuit.copy(environment=circuit.environment.copy(illuminanceLux=10000.0)))
        session.environment = session.board.circuit.environment
        advance(session, 1_000_000)
        assertTrue(session.consoleLines().last().contains("Lamp OFF"))
        assertTrue(session.board.latest!!.readings.getValue(led.id).current < 1e-5)
        session.reset()
        advance(session, 50_000)
        assertTrue(session.consoleLines().last().contains("Lamp OFF"))
    }

    private fun irrigationProject() {
        fun position(c: Circuit, value: Double) = c.copy(components=c.components.map {
            if (it.kind == Kind.POTENTIOMETER) it.copy(parameters=it.parameters + ("position" to value)) else it
        })
        val circuit = position(FirmwareExamples.irrigationCircuit(), .2)
        val session = start(circuit)
        advance(session, 50_000)
        val first = session.consoleLines().last()
        val pwm = session.board.pwmPins().toMap()
        session.board.updateCircuit(position(circuit, .8))
        advance(session, 1_000_000)
        val second = session.consoleLines().last()
        assertTrue(listOf(first, second).any { it.contains("Valve OPEN") })
        assertTrue(listOf(first, second).any { it.contains("Valve CLOSED") })
        assertNotEquals(pwm, session.board.pwmPins())
        session.reset()
        advance(session, 50_000)
        assertEquals(second, session.consoleLines().last())
    }
}
