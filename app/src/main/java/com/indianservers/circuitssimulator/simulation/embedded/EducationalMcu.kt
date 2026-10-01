package com.indianservers.circuitssimulator.simulation.embedded

import com.indianservers.circuitssimulator.domain.Circuit
import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.simulation.DcResult
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.simulation.ExternalDrive
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import kotlin.math.abs
import kotlin.math.floor

enum class PinMode { INPUT, OUTPUT }
enum class PinLevel { LOW, HIGH }

data class BoardElectricalSpec(
    val supplyVoltage: Double = 5.0,
    val outputResistanceOhms: Double = 50.0,
    val maxPinCurrentAmps: Double = 0.02,
    val inputLowMaxFraction: Double = 0.3,
    val inputHighMinFraction: Double = 0.7,
    val adcBits: Int = 10,
    val damageI2tAmpSquaredSeconds: Double = 0.005
) {
    init {
        require(supplyVoltage.isFinite() && supplyVoltage>0)
        require(outputResistanceOhms.isFinite() && outputResistanceOhms>0)
        require(maxPinCurrentAmps.isFinite() && maxPinCurrentAmps>0)
        require(inputLowMaxFraction in 0.0..1.0 && inputHighMinFraction in 0.0..1.0 &&
            inputLowMaxFraction<inputHighMinFraction)
        require(adcBits in 1..16)
        require(damageI2tAmpSquaredSeconds.isFinite() && damageI2tAmpSquaredSeconds>0)
    }
}

data class GpioReading(val mode: PinMode, val level: PinLevel, val currentAmps: Double,
                       val overCurrent: Boolean, val damaged: Boolean)
data class McuStep(val circuitResult: DcResult, val pins: Map<String,GpioReading>,
                   val timeSeconds: Double)

/** DC electrical GPIO/ADC foundation. Firmware, buses and transient scheduling are separate work. */
class EducationalMcu(private val bindings: Map<String,TerminalRef>,
                     val spec: BoardElectricalSpec = BoardElectricalSpec(),
                     private val solver: DcSolver = DcSolver()) {
    private data class Pin(var mode:PinMode=PinMode.INPUT,var level:PinLevel=PinLevel.LOW,
                           var i2t:Double=0.0,var damaged:Boolean=false)
    private val pins=bindings.keys.associateWith { Pin() }.toMutableMap()
    private var latest: DcResult?=null
    var timeSeconds:Double=0.0
        private set

    fun reset() {
        pins.values.forEach { it.mode=PinMode.INPUT;it.level=PinLevel.LOW;it.i2t=0.0;it.damaged=false }
        latest=null
        timeSeconds=0.0
    }
    fun configure(name:String,mode:PinMode) { pins.getValue(name).mode=mode }
    fun write(name:String,level:PinLevel) { pins.getValue(name).level=level }
    fun readAdc(name:String):Int {
        val result=latest ?: error("Run an electrical step before sampling ADC.")
        require(result.error==null) { result.error ?: "Circuit solve failed" }
        val voltage=result.nodeVoltages.getValue(bindings.getValue(name))
        val levels=1 shl spec.adcBits
        return floor(voltage.coerceIn(0.0,spec.supplyVoltage)/spec.supplyVoltage*levels)
            .toInt().coerceIn(0,levels-1)
    }
    fun readDigital(name:String):LogicState {
        val result=latest ?: error("Run an electrical step before reading GPIO.")
        require(result.error==null) { result.error ?: "Circuit solve failed" }
        val voltage=result.nodeVoltages.getValue(bindings.getValue(name))
        return when {
            voltage<=spec.supplyVoltage*spec.inputLowMaxFraction -> LogicState.LOW
            voltage>=spec.supplyVoltage*spec.inputHighMinFraction -> LogicState.HIGH
            else -> LogicState.UNKNOWN
        }
    }
    fun step(circuit:Circuit,stepSeconds:Double):McuStep {
        require(stepSeconds.isFinite() && stepSeconds>0)
        fun drives()=bindings.mapNotNull { (name,terminal) ->
            val pin=pins.getValue(name)
            if(pin.mode!=PinMode.OUTPUT || pin.damaged) null else ExternalDrive(terminal,
                if(pin.level==PinLevel.HIGH) spec.supplyVoltage else 0.0,spec.outputResistanceOhms)
        }
        var result=solver.solve(circuit,drives())
        if(result.error!=null) return McuStep(result,emptyMap(),timeSeconds)
        val currents=mutableMapOf<String,Double>()
        var newlyDamaged=false
        bindings.forEach { (name,terminal) ->
            val pin=pins.getValue(name)
            val current=if(pin.mode==PinMode.OUTPUT && !pin.damaged) {
                val target=if(pin.level==PinLevel.HIGH) spec.supplyVoltage else 0.0
                (target-result.nodeVoltages.getValue(terminal))/spec.outputResistanceOhms
            } else 0.0
            currents[name]=current
            if(abs(current)>spec.maxPinCurrentAmps) {
                pin.i2t+=current*current*stepSeconds
                if(pin.i2t>=spec.damageI2tAmpSquaredSeconds) {
                    pin.damaged=true
                    newlyDamaged=true
                }
            }
        }
        if(newlyDamaged) result=solver.solve(circuit,drives())
        if(result.error==null) { latest=result;timeSeconds+=stepSeconds }
        return McuStep(result,pins.mapValues { (name,pin) ->
            val amps=if(pin.damaged) 0.0 else currents.getValue(name)
            GpioReading(pin.mode,pin.level,amps,abs(amps)>spec.maxPinCurrentAmps,pin.damaged)
        },timeSeconds)
    }
}
