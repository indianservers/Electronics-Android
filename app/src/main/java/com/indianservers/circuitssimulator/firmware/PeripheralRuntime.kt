package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcResult
import com.indianservers.circuitssimulator.simulation.ExternalDrive
import kotlin.math.exp

/** Time-varying peripheral outputs that participate in the same electrical solve as MCU GPIO. */
class PeripheralRuntime {
    private val echoUntil=mutableMapOf<String,Long>()
    private val echoStart=mutableMapOf<String,Long>()
    private val lastTrig=mutableMapOf<String,Boolean>()
    private val servoAngle=mutableMapOf<String,Double>()
    private val pirUntil=mutableMapOf<String,Long>()
    private val poweredSince=mutableMapOf<String,Long>()
    private val trigSince=mutableMapOf<String,Long>()
    private val previousMotion=mutableMapOf<String,Boolean>()
    private data class Shift(var bits:Int=0,var latch:Int=0,var clock:Boolean=false,var latchClock:Boolean=false)
    private val shifts=mutableMapOf<String,Shift>()
    private val servoHighAt=mutableMapOf<String,Long>()
    private val servoRiseAt=mutableMapOf<String,Long>()
    private val servoPeriod=mutableMapOf<String,Long>()
    private val servoPulse=mutableMapOf<String,Long>()
    private val servoLevel=mutableMapOf<String,Boolean>()
    fun reset() {
        echoUntil.clear();echoStart.clear();lastTrig.clear();servoAngle.clear();pirUntil.clear();poweredSince.clear();trigSince.clear();previousMotion.clear()
        shifts.clear();servoHighAt.clear();servoRiseAt.clear();servoPeriod.clear();servoPulse.clear();servoLevel.clear()
    }
    fun applyEnvironment(circuit:Circuit):Circuit {
        val env=circuit.environment
        return circuit.copy(components=circuit.components.map { part ->
            when(part.kind) {
                Kind.LM35_SENSOR,Kind.THERMISTOR,Kind.PTC_THERMISTOR ->
                    part.copy(parameters=part.parameters+("temperatureC" to env.temperatureC))
                Kind.LDR -> part.copy(parameters=part.parameters+("illuminanceLux" to env.illuminanceLux))
                Kind.PHOTODIODE,Kind.PHOTOTRANSISTOR -> part.copy(parameters=part.parameters+("illuminanceLux" to env.illuminanceLux))
                Kind.HALL_SENSOR -> part.copy(parameters=part.parameters+("fieldMilliTesla" to env.magneticFieldMilliTesla))
                Kind.REED_SWITCH -> part.copy(parameters=part.parameters+("field" to if(kotlin.math.abs(env.magneticFieldMilliTesla)>.5)1.0 else 0.0))
                else -> part
            }
        },environment=env)
    }
    fun drives(circuit:Circuit,result:DcResult?,timeMicros:Long):List<ExternalDrive> {
        val out=mutableListOf<ExternalDrive>()
        circuit.components.forEach { part ->
            when(part.kind) {
                Kind.SHIFT_74HC595 -> {
                    fun v(index:Int)=(result?.nodeVoltages?.get(TerminalRef(part.id,index)) ?: 0.0)
                    val ground=v(7);val supply=v(15)-ground
                    if(supply !in 2.0..6.0) shifts.remove(part.id)
                    else {
                        val state=shifts.getOrPut(part.id){Shift()}
                        fun high(index:Int)=v(index)-ground>=supply*.7
                        val clock=high(10);val latch=high(11)
                        if(!high(9))state.bits=0
                        else if(clock && !state.clock)state.bits=((state.bits shl 1) or if(high(13))1 else 0) and 255
                        if(latch && !state.latchClock)state.latch=state.bits
                        state.clock=clock;state.latchClock=latch
                        out+=ExternalDrive(TerminalRef(part.id,8),ground+if(state.bits and 128!=0)supply else 0.0,100.0)
                        if(!high(12)) listOf(14,0,1,2,3,4,5,6).forEachIndexed { bit,pin ->
                            out+=ExternalDrive(TerminalRef(part.id,pin),ground+if(state.latch and (1 shl bit)!=0)supply else 0.0,100.0)
                        }
                    }
                }
                Kind.ADC_2,Kind.DAC_2 -> {
                    fun v(pin:Int)=result?.nodeVoltages?.get(TerminalRef(part.id,pin)) ?: 0.0
                    val groundPin=if(part.kind==Kind.ADC_2)1 else 3;val ground=v(groundPin)
                    if(circuit.wires.any { it.start==TerminalRef(part.id,groundPin) || it.end==TerminalRef(part.id,groundPin) }) {
                        if(part.kind==Kind.ADC_2) {
                            val code=((v(0)-ground)/3.3*4).toInt().coerceIn(0,3)
                            repeat(2) { bit -> out+=ExternalDrive(TerminalRef(part.id,bit+2),ground+if(code and (1 shl bit)!=0)3.3 else 0.0,100.0) }
                        } else {
                            val code=(if(v(0)-ground>2.0)1 else 0)+(if(v(1)-ground>2.0)2 else 0)
                            out+=ExternalDrive(TerminalRef(part.id,2),ground+code*1.1,50.0)
                        }
                    }
                }
                Kind.SERVO_MOTOR -> {
                    val ground=result?.nodeVoltages?.get(TerminalRef(part.id,1)) ?: 0.0
                    val high=(result?.nodeVoltages?.get(TerminalRef(part.id,2)) ?: 0.0)-ground>2.0
                    val previous=servoLevel[part.id]==true
                    if(high && !previous) {
                        servoRiseAt[part.id]?.let { last -> servoPeriod[part.id]=timeMicros-last }
                        servoRiseAt[part.id]=timeMicros;servoHighAt[part.id]=timeMicros
                    }
                    if(!high && previous)servoHighAt.remove(part.id)?.let { servoPulse[part.id]=timeMicros-it }
                    servoLevel[part.id]=high
                }
                Kind.ULTRASONIC -> {
                    val trig=(result?.nodeVoltages?.get(TerminalRef(part.id,2)) ?: 0.0)>2.0
                    val previous=lastTrig[part.id]==true
                    if(trig && !previous) trigSince[part.id]=timeMicros
                    if(previous && !trig && timeMicros-(trigSince[part.id] ?: timeMicros)>=10 &&
                        timeMicros !in (echoStart[part.id] ?: -1)..(echoUntil[part.id] ?: -2)) {
                        val width=(circuit.environment.distanceCm*58.0).toLong().coerceIn(116,23200)
                        echoStart[part.id]=timeMicros+200
                        echoUntil[part.id]=timeMicros+200+width
                    }
                    lastTrig[part.id]=trig
                    val active=timeMicros>=(echoStart[part.id] ?: Long.MAX_VALUE) &&
                        timeMicros<(echoUntil[part.id] ?: -1)
                    val supply=(result?.nodeVoltages?.get(TerminalRef(part.id,0)) ?: 0.0)-
                        (result?.nodeVoltages?.get(TerminalRef(part.id,1)) ?: 0.0)
                    val ground=result?.nodeVoltages?.get(TerminalRef(part.id,1)) ?: 0.0
                    if(supply in 4.5..5.5) out+=ExternalDrive(TerminalRef(part.id,3),ground+if(active)5.0 else 0.0,220.0)
                    else { echoStart.remove(part.id);echoUntil.remove(part.id);trigSince.remove(part.id) }
                }
                Kind.PIR_SENSOR -> {
                    val supply=(result?.nodeVoltages?.get(TerminalRef(part.id,0)) ?: 0.0)-
                        (result?.nodeVoltages?.get(TerminalRef(part.id,2)) ?: 0.0)
                    if(supply in 4.5..12.0) {
                        val since=poweredSince.getOrPut(part.id){timeMicros}
                        val ready=timeMicros-since>=(part.value("warmupMs")*1000).toLong()
                        val motion=circuit.environment.motion
                        if(ready && motion && (part.value("retrigger")==1.0 || previousMotion[part.id]!=true))
                            pirUntil[part.id]=timeMicros+(part.value("delayMs")*1000).toLong()
                        previousMotion[part.id]=if(ready)motion else false
                        val active=ready && timeMicros<(pirUntil[part.id] ?: -1)
                        val ground=result?.nodeVoltages?.get(TerminalRef(part.id,2)) ?: 0.0
                        out+=ExternalDrive(TerminalRef(part.id,1),ground+if(active)3.3 else 0.0,1000.0)
                    } else { poweredSince.remove(part.id);pirUntil.remove(part.id);previousMotion.remove(part.id) }
                }
                Kind.SERIAL_TERMINAL -> {
                    val ground=result?.nodeVoltages?.get(TerminalRef(part.id,2)) ?: 0.0
                    out+=ExternalDrive(TerminalRef(part.id,0),ground+5.0,100.0)
                }
                else -> Unit
            }
        }
        return out
    }
    fun stepServos(circuit:Circuit,result:DcResult?,dtSeconds:Double,pwm:Map<String,Pair<Double,Double>>) {
        circuit.components.filter { it.kind==Kind.SERVO_MOTOR }.forEach { servo ->
            val sig=result?.nodeVoltages?.get(TerminalRef(servo.id,2)) ?: 0.0
            val supply=(result?.nodeVoltages?.get(TerminalRef(servo.id,0)) ?: 0.0)-
                (result?.nodeVoltages?.get(TerminalRef(servo.id,1)) ?: 0.0)
            if(supply<4.5) return@forEach
            val period=servoPeriod[servo.id]
            val pulse=servoPulse[servo.id]?.takeIf { period!=null && period in 16666..25000 && it in 500..2500 }?.toDouble()
            val target=when {
                pulse!=null -> ((pulse-1000.0)/1000.0*180.0).coerceIn(0.0,180.0)
                else -> servoAngle[servo.id] ?: 90.0
            }
            val current=servoAngle[servo.id] ?: 90.0
            val alpha=1.0-exp(-(dtSeconds.coerceAtLeast(1e-6))/0.08)
            servoAngle[servo.id]=current+(target-current)*alpha
        }
    }
    fun angles()=servoAngle.toMap()
}
