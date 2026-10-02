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
    fun reset() {
        echoUntil.clear();echoStart.clear();lastTrig.clear();servoAngle.clear();pirUntil.clear()
    }
    fun applyEnvironment(circuit:Circuit):Circuit {
        val env=circuit.environment
        return circuit.copy(components=circuit.components.map { part ->
            when(part.kind) {
                Kind.LM35_SENSOR,Kind.THERMISTOR,Kind.PTC_THERMISTOR ->
                    part.copy(parameters=part.parameters+("temperatureC" to env.temperatureC))
                Kind.LDR -> part.copy(parameters=part.parameters+("illuminanceLux" to env.illuminanceLux))
                else -> part
            }
        },environment=env)
    }
    fun drives(circuit:Circuit,result:DcResult?,timeMicros:Long):List<ExternalDrive> {
        val out=mutableListOf<ExternalDrive>()
        circuit.components.forEach { part ->
            when(part.kind) {
                Kind.ULTRASONIC -> {
                    val trig=(result?.nodeVoltages?.get(TerminalRef(part.id,2)) ?: 0.0)>2.0
                    val previous=lastTrig[part.id]==true
                    if(previous && !trig && timeMicros !in (echoStart[part.id] ?: -1)..(echoUntil[part.id] ?: -2)) {
                        val width=(circuit.environment.distanceCm*58.0).toLong().coerceIn(116,23200)
                        echoStart[part.id]=timeMicros+200
                        echoUntil[part.id]=timeMicros+200+width
                    }
                    lastTrig[part.id]=trig
                    val active=timeMicros>=(echoStart[part.id] ?: Long.MAX_VALUE) &&
                        timeMicros<(echoUntil[part.id] ?: -1)
                    val supply=(result?.nodeVoltages?.get(TerminalRef(part.id,0)) ?: 0.0)-
                        (result?.nodeVoltages?.get(TerminalRef(part.id,1)) ?: 0.0)
                    if(active && supply>4.0) out+=ExternalDrive(TerminalRef(part.id,3),5.0,220.0)
                }
                Kind.PIR_SENSOR -> {
                    if(circuit.environment.motion) pirUntil[part.id]=timeMicros+(part.value("delayMs")*1000).toLong()
                    val supply=(result?.nodeVoltages?.get(TerminalRef(part.id,0)) ?: 0.0)-
                        (result?.nodeVoltages?.get(TerminalRef(part.id,2)) ?: 0.0)
                    if(supply>3.0 && timeMicros<(pirUntil[part.id] ?: -1))
                        out+=ExternalDrive(TerminalRef(part.id,1),supply,1000.0)
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
            val pulse=pwm.values.firstOrNull { it.second in 40.0..60.0 }?.let { (duty,freq) ->
                duty*(1_000_000.0/freq)
            }
            val target=when {
                pulse!=null -> ((pulse-1000.0)/1000.0*180.0).coerceIn(0.0,180.0)
                sig>2.0 -> 180.0
                else -> servoAngle[servo.id] ?: 90.0
            }
            val current=servoAngle[servo.id] ?: 90.0
            val alpha=1.0-exp(-(dtSeconds.coerceAtLeast(1e-6))/0.08)
            servoAngle[servo.id]=current+(target-current)*alpha
        }
    }
    fun angles()=servoAngle.toMap()
}
