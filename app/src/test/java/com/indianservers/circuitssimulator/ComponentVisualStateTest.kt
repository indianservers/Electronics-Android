package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import com.indianservers.circuitssimulator.simulation.embedded.*
import com.indianservers.circuitssimulator.ui.*
import com.indianservers.circuitssimulator.ui.canvas.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ComponentVisualStateTest {
    private fun part(kind:Kind)=PlacedComponent(kind=kind,reference="TEST",x=0f,y=0f)
    private fun state(p:PlacedComponent,current:Double,voltage:Double=5.0)=SimulatorState(
        circuit=Circuit("visual",listOf(p),emptyList()),
        result=DcResult(readings=mapOf(p.id to Reading(voltage,current,voltage*current))))

    @Test fun ledUsesForwardCurrentAndPreservesPausedElectricalState() {
        val led=part(Kind.GREEN_LED)
        assertEquals(0f,ComponentVisualAdapter.read(led,state(led,-.02)).brightness,0f)
        assertEquals(.25f,ComponentVisualAdapter.read(led,state(led,.005)).brightness,.001f)
        assertEquals(1f,ComponentVisualAdapter.read(led,state(led,.02).copy(running=false)).brightness,0f)
    }

    @Test fun motorRpmComesFromCheckpointAndPreservesSign() {
        val motor=part(Kind.DC_MOTOR)
        val checkpoint=TransientCheckpoint(1.0,emptyMap(),emptyMap(),emptyMap(),emptyMap(),emptyMap(),emptyMap(),emptyMap(),emptySet(),emptyList(),motorSpeeds=mapOf(motor.id to -100.0))
        val result=ComponentVisualAdapter.read(motor,state(motor,-.1).copy(transient=TransientResult(checkpoint=checkpoint)))
        assertEquals(-954.93f,result.motor.rpm,.01f)
        assertEquals(RotationDirection.COUNTER_CLOCKWISE,result.motor.direction)
        assertEquals(0f,ComponentVisualAdapter.read(motor,state(motor,0.0)).motor.rpm,0f)
    }

    @Test fun sharedMotionAcceleratesCoastsStopsAndDoesNotMoveWhenPausedOrReduced() {
        val motion=ComponentMotion()
        val target=ComponentVisualState(motor=MotorVisualState(3200f,true))
        val first=motion.advance("motor",target,.016f,true,false)
        assertTrue(first.rotorDegrees>0f)
        var before=first
        repeat(50) { before=motion.advance("motor",target,.016f,true,false) }
        val coast=motion.advance("motor",ComponentVisualState(),.016f,true,false)
        assertNotEquals(before.rotorDegrees,coast.rotorDegrees)
        repeat(200) { motion.advance("motor",ComponentVisualState(),.016f,true,false) }
        assertFalse(motion.moving("motor"))
        val stopped=motion.advance("motor",target,.016f,false,false)
        assertEquals(stopped.rotorDegrees,motion.advance("motor",target,.016f,false,false).rotorDegrees,0f)
        assertEquals(stopped.rotorDegrees,motion.advance("motor",target,.016f,true,true).rotorDegrees,0f)
    }

    @Test fun fasterRpmProducesFasterMotionWithoutUnboundedFrameSteps() {
        fun turn(rpm:Float):Float {
            val motion=ComponentMotion()
            var pose=ComponentVisualState()
            repeat(30) { pose=motion.advance("m",ComponentVisualState(motor=MotorVisualState(rpm,true)),.016f,true,false) }
            val next=motion.advance("m",ComponentVisualState(motor=MotorVisualState(rpm,true)),.016f,true,false)
            return (next.rotorDegrees-pose.rotorDegrees+360f)%360f
        }
        assertTrue(turn(500f)<turn(2000f))
        assertTrue(turn(2000f)<turn(5000f))
        assertTrue(turn(12000f)<20f)
    }

    @Test fun rgbMixUsesEachActualChannelAndFaultsTurnEmissionOff() {
        val rgb=part(Kind.RGB_LED)
        val volts=mapOf(TerminalRef(rgb.id,0) to 3.8,TerminalRef(rgb.id,1) to 4.2,
            TerminalRef(rgb.id,2) to 0.0,TerminalRef(rgb.id,3) to 0.0)
        val state=state(rgb,.04).copy(result=DcResult(nodeVoltages=volts))
        val mixed=ComponentVisualAdapter.read(rgb,state)
        assertEquals(1f,mixed.red,.001f);assertEquals(1f,mixed.green,.001f);assertEquals(0f,mixed.blue,0f)
        assertEquals(0f,ComponentVisualAdapter.read(rgb,state.copy(result=state.result.copy(error="fault"))).red,0f)
    }

    @Test fun servoUsesOnlyItsConnectedPwmPinAndMapsAllCommandAngles() {
        val board=part(Kind.ARDUINO_UNO);val servo=part(Kind.SERVO_MOTOR)
        val circuit=Circuit("servo",listOf(board,servo),listOf(Wire(start=TerminalRef(board.id,BoardRegistry.boards.getValue(board.kind).index("D9")),end=TerminalRef(servo.id,2))))
        for(angle in listOf(0,45,90,135,180)) {
            val duty=(1000.0+angle/180.0*1000.0)/20000.0
            val pins=mapOf("D9" to GpioReading(PinMode.OUTPUT,PinLevel.HIGH,0.0,false,false,duty,50.0),
                "D10" to GpioReading(PinMode.OUTPUT,PinLevel.HIGH,0.0,false,false,.1,50.0))
            val state=SimulatorState(circuit=circuit,firmware=FirmwareUiState(boardId=board.id,pins=pins))
            assertEquals(angle.toFloat(),ComponentVisualAdapter.servoCommand(servo,state)!!,.001f)
            assertNull(ComponentVisualAdapter.servoCommand(servo,state.copy(circuit=circuit.copy(wires=emptyList()))))
        }
    }

    @Test fun liveMeterAndDisplayContentAreReadFromSimulationNotPlaceholders() {
        val meter=part(Kind.VOLTMETER)
        assertTrue(ComponentVisualAdapter.read(meter,state(meter,0.0,3.3)).display.contains("3.3"))
        val lcd=part(Kind.I2C_LCD)
        val result=DcResult(nodeVoltages=mapOf(TerminalRef(lcd.id,0) to 5.0,TerminalRef(lcd.id,1) to 0.0))
        val state=SimulatorState(result=result,firmware=FirmwareUiState(displays=mapOf(lcd.id to "HELLO\nWORLD")))
        assertEquals("HELLO\nWORLD",ComponentVisualAdapter.read(lcd,state).display)
        assertEquals("",ComponentVisualAdapter.read(lcd,state.copy(result=DcResult())).display)
    }
}
