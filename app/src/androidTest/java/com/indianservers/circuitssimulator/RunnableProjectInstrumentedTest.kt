package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class RunnableProjectInstrumentedTest {
    private fun run(circuit:Circuit,us:Long=50000):Pair<FirmwareSession,FirmwareAdvance> {
        val attachment=circuit.firmware.single()
        val board=FirmwareBoard(circuit,attachment.boardId)
        val session=FirmwareSession(ArduinoSubset.compile(attachment.source,board),board)
        val result=session.advance(us)
        assertNull("${circuit.name}: ${result.error}",result.error)
        return session to result
    }
    @Test fun everyProjectHasCodeAndProducesExecutedSerialOutput() {
        val projects=listOf(SampleCircuits.blinkingLed(),SampleCircuits.buttonLed(),SampleCircuits.trafficLight(),
            SampleCircuits.servoControl(),SampleCircuits.temperatureMonitor(),SampleCircuits.i2cLab(),
            SampleCircuits.esp32Oled(),SampleCircuits.picoAdcPwm(),SampleCircuits.nodeMcuLdr(),
            FirmwareExamples.streetLightCircuit(),FirmwareExamples.irrigationCircuit())
        projects.forEach { circuit ->
            assertTrue(circuit.name,circuit.firmware.single().source.contains("void loop"))
            val (session,result)=run(circuit)
            assertTrue("${circuit.name}: ${result.console}",session.consoleLines().isNotEmpty())
        }
    }
    @Test fun oledProjectWritesFramebufferAndReportsSuccessfulWireTransfer() {
        val (session,result)=run(SampleCircuits.esp32Oled())
        assertTrue(result.console.toString(),session.consoleLines().any { it.contains("rectangle drawn") })
        assertFalse(session.consoleLines().any { it.contains("not responding") })
        assertTrue(session.fabric.framebuffers().values.single().any { it==255 })
    }
    @Test fun ldrProjectChangesOutputWithEnvironmentAndKeepsAdcSafe() {
        val circuit=SampleCircuits.nodeMcuLdr()
        val (dark,darkResult)=run(circuit.copy(environment=EnvironmentState(illuminanceLux=1.0)))
        val (light,lightResult)=run(circuit.copy(environment=EnvironmentState(illuminanceLux=10000.0)))
        assertTrue(darkResult.console.toString(),dark.consoleLines().any { it.contains("Lamp ON") })
        assertTrue(lightResult.console.toString(),light.consoleLines().any { it.contains("Lamp OFF") })
        val ref=TerminalRef(light.board.boardId,light.board.definition.index("A0"))
        assertTrue(lightResult.frames.last().result.nodeVoltages.getValue(ref)<1.0)
    }
    @Test fun picoPotentiometerChangesActualPwmAndSerialSample() {
        val circuit=SampleCircuits.picoAdcPwm()
        fun position(value:Double)=circuit.copy(components=circuit.components.map {
            if(it.kind==Kind.POTENTIOMETER) it.copy(parameters=it.parameters+("position" to value)) else it })
        val (low,_)=run(position(.2));val (high,_)=run(position(.8))
        assertNotEquals(low.consoleLines(),high.consoleLines())
        assertNotEquals(low.board.pwmPins(),high.board.pwmPins())
    }
    @Test fun irrigationUsesWiredPotentiometerAndActualServoCommands() {
        val circuit=FirmwareExamples.irrigationCircuit()
        fun position(value:Double)=circuit.copy(components=circuit.components.map {
            if(it.kind==Kind.POTENTIOMETER) it.copy(parameters=it.parameters+("position" to value)) else it })
        val (a,_)=run(position(.2));val (b,_)=run(position(.8))
        assertTrue(a.consoleLines().toString()+b.consoleLines(),
            (a.consoleLines()+b.consoleLines()).any { it.contains("Valve OPEN") })
        assertTrue((a.consoleLines()+b.consoleLines()).any { it.contains("Valve CLOSED") })
        assertNotEquals(a.board.pwmPins(),b.board.pwmPins())
    }
}
