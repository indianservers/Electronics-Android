package com.indianservers.circuitssimulator.ui.canvas

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.Health
import com.indianservers.circuitssimulator.ui.SimulatorState
import kotlin.math.*

enum class RotationDirection { CLOCKWISE, COUNTER_CLOCKWISE }
data class MotorVisualState(val rpm: Float = 0f, val isPowered: Boolean = false) {
    val direction get() = if(rpm < 0f) RotationDirection.COUNTER_CLOCKWISE else RotationDirection.CLOCKWISE
}

/** Read-only presentation data. Never feeds values back into the solver or persisted circuit. */
data class ComponentVisualState(
    val motor: MotorVisualState = MotorVisualState(),
    val rotorDegrees: Float = 0f,
    val servoDegrees: Float = 90f, val servoDriven:Boolean = false,
    val red: Float = 0f, val green: Float = 0f, val blue: Float = 0f,
    val brightness: Float = 0f, val relayClosed: Boolean = false,
    val display: String = "", val pixels:List<Int> = emptyList(),val phase: Float = 0f, val failed: Boolean = false,
    val segmentMask:Int = 0, val reducedMotion: Boolean = false
)

object ComponentVisualAdapter {
    fun preview(p:PlacedComponent)=ComponentVisualState(display=when(p.kind) {
        Kind.SOURCE,Kind.VARIABLE_DC_SUPPLY -> EngineeringUnits.format(p.value("voltage"),"V")
        Kind.DC_CURRENT_SOURCE -> EngineeringUnits.format(p.value("current"),"A")
        Kind.FUNCTION_GENERATOR,Kind.AC_VOLTAGE_SOURCE,Kind.SINE_GENERATOR,Kind.SQUARE_GENERATOR,Kind.PULSE_GENERATOR -> EngineeringUnits.format(p.value("frequency"),"Hz")
        else -> ""
    })
    fun read(p: PlacedComponent, state: SimulatorState): ComponentVisualState {
        val reading=state.result.readings[p.id]
        val valid=state.result.error==null && reading?.health !in setOf(Health.FAILED,Health.FAILED_OPEN,Health.FAILED_SHORT)
        fun voltage(pin: Int)=state.result.nodeVoltages[TerminalRef(p.id,pin)] ?: 0.0
        val current=if(valid) reading?.current ?: 0.0 else 0.0
        val forward=max(0.0,current)
        val brightness=when(p.kind) {
            Kind.LED,Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED -> (forward/.02).coerceIn(0.0,1.0)
            Kind.LAMP -> if(valid) (max(0.0,reading?.power ?: 0.0)/p.value("rating").coerceAtLeast(.01)).coerceIn(0.0,1.0) else 0.0
            Kind.BUZZER -> if(abs(reading?.voltage ?: 0.0)>=p.value("startVoltage")) (abs(current)/.05).coerceIn(0.0,1.0) else 0.0
            Kind.SPEAKER -> (abs(current)/.05).coerceIn(0.0,1.0)
            Kind.PIR_SENSOR -> if(valid && voltage(1)>2.0) 1.0 else 0.0
            Kind.LOGIC_OUTPUT -> if(valid && voltage(0)>2.0) 1.0 else 0.0
            else -> if(p.kind.isBoard) {
                val spec=BoardRegistry.boards.getValue(p.kind)
                val pin=spec.ledBuiltin?.let(spec::index) ?: -1
                if(pin>=0) {
                    val activeLow=spec.family==BoardFamily.ESP8266
                    if(if(activeLow) voltage(pin)<.8 && spec.supplyPins.any { voltage(spec.index(it))>2.5 } else voltage(pin)>2.0) 1.0 else 0.0
                } else if(spec.supplyPins.any { voltage(spec.index(it))>2.5 }) 1.0 else 0.0
            } else 0.0
        }.toFloat()
        val omega=state.transient?.checkpoint?.motorSpeeds?.get(p.id)
            ?: if(abs(current) > 1e-7) p.value("motorConstant")*current/p.value("friction").coerceAtLeast(1e-6) else 0.0
        val rpm=if(p.kind==Kind.DC_MOTOR && valid) (omega*60.0/(2.0*PI)).toFloat() else 0f
        val common=if(p.kind==Kind.RGB_LED) voltage(3) else 0.0
        fun channel(pin:Int,threshold:Double)=if(valid && p.kind==Kind.RGB_LED)
            ((voltage(pin)-common-threshold)/p.value("seriesResistance").coerceAtLeast(1.0)/.02).coerceIn(0.0,1.0).toFloat() else 0f
        val supply=voltage(0)-voltage(1)
        val servo=if(p.kind==Kind.SERVO_MOTOR && valid && supply>=4.5) servoCommand(p,state) else null
        val text=when(p.kind) {
            Kind.I2C_LCD,Kind.OLED_SSD1306 -> if(supply>=2.5 && valid) state.firmware.displays[p.id].orEmpty() else ""
            Kind.SERIAL_TERMINAL -> state.firmware.displays[p.id].orEmpty()
            Kind.VOLTMETER -> reading?.takeIf { valid }?.let { EngineeringUnits.format(it.voltage,"V") } ?: "—"
            Kind.AMMETER -> reading?.takeIf { valid }?.let { EngineeringUnits.format(it.current,"A") } ?: "—"
            Kind.SOURCE,Kind.VARIABLE_DC_SUPPLY,Kind.DC_CURRENT_SOURCE -> EngineeringUnits.format(p.value(if(p.kind==Kind.DC_CURRENT_SOURCE) "current" else "voltage"),if(p.kind==Kind.DC_CURRENT_SOURCE) "A" else "V")
            Kind.FUNCTION_GENERATOR,Kind.SINE_GENERATOR,Kind.SQUARE_GENERATOR,Kind.PULSE_GENERATOR,Kind.AC_VOLTAGE_SOURCE -> EngineeringUnits.format(p.value("frequency"),"Hz")
            else -> ""
        }
        return ComponentVisualState(motor=MotorVisualState(rpm,abs(current)>1e-6),
            servoDegrees=servo ?: 90f,servoDriven=servo!=null,red=channel(0,1.8),green=channel(1,2.2),blue=channel(2,3.0),
            brightness=brightness,relayClosed=valid && abs(supply)>=p.value("pullInVoltage"),
            display=text,pixels=state.firmware.framebuffers[p.id].orEmpty(),failed=!valid)
    }

    /** Only a PWM pin electrically connected to SIG can command this servo. */
    fun servoCommand(p:PlacedComponent,state:SimulatorState):Float? {
        val seen=mutableSetOf(TerminalRef(p.id,2))
        var changed=true
        while(changed) {
            changed=false
            state.circuit.wires.forEach { wire ->
                if(wire.start in seen || wire.end in seen) {
                    if(seen.add(wire.start)) changed=true
                    if(seen.add(wire.end)) changed=true
                }
            }
        }
        val drive=seen.firstNotNullOfOrNull { ref ->
            val board=state.circuit.components.firstOrNull { it.id==ref.componentId && it.kind.isBoard }
            val spec=board?.let { BoardRegistry.boards[it.kind] }
            val pins=state.firmware.boardPins[ref.componentId] ?: if(ref.componentId==state.firmware.boardId) state.firmware.pins else emptyMap()
            spec?.pins?.getOrNull(ref.index)?.name?.let(pins::get)
        } ?: return null
        val duty=drive.pwmDuty ?: return null
        val frequency=drive.pwmFrequencyHz ?: return null
        if(frequency !in 40.0..60.0) return null
        return ((duty*1_000_000.0/frequency-1000.0)*.18).coerceIn(0.0,180.0).toFloat()
    }
}

/** One instance per canvas, advanced by one shared frame clock, only for visible devices. */
class ComponentMotion {
    private data class Pose(var rpm:Float=0f,var rotor:Float=0f,var servo:Float=90f)
    private val poses=mutableMapOf<String,Pose>()
    fun retain(ids:Set<String>) { poses.keys.retainAll(ids) }
    fun servoMoving(id:String,target:Float)=abs((poses[id]?.servo ?: 90f)-target)>.3f
    fun moving(id:String)=poses[id]?.let { abs(it.rpm)>.2f } ?: false
    fun advance(id:String,target:ComponentVisualState,dt:Float,running:Boolean,reduced:Boolean):ComponentVisualState {
        val pose=poses.getOrPut(id) { Pose() }
        if(running && !reduced) {
            val alpha=1f-exp(-dt.coerceIn(0f,.05f)/.18f)
            pose.rpm+=(target.motor.rpm-pose.rpm)*alpha
            if(abs(pose.rpm)<.2f && abs(target.motor.rpm)<.2f) pose.rpm=0f
            // Compress very high RPM while preserving speed ordering and signed direction.
            val speed=sign(pose.rpm)*1200f*(1f-exp(-abs(pose.rpm)/2400f))
            pose.rotor=(pose.rotor+speed*dt)%360f
            if(target.servoDriven) pose.servo+=(target.servoDegrees-pose.servo)*(1f-exp(-dt.coerceIn(0f,.05f)/.08f))
        } else if(reduced) { pose.rpm=target.motor.rpm;if(target.servoDriven) pose.servo=target.servoDegrees }
        return target.copy(rotorDegrees=pose.rotor,servoDegrees=pose.servo,reducedMotion=reduced)
    }
}

/** Gentle halo modulation; the powered lens stays continuously lit. */
fun ledGlowPulse(visual:ComponentVisualState):Float =
    if(visual.reducedMotion) 1f else .88f+.12f*sin(visual.phase*4f*PI.toFloat())
