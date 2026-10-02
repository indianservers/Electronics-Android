package com.indianservers.circuitssimulator.simulation.embedded

import com.indianservers.circuitssimulator.domain.Circuit
import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.simulation.DcResult
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.simulation.ExternalDrive
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import kotlin.math.abs
import kotlin.math.floor

enum class PinMode { INPUT, OUTPUT, INPUT_PULLUP }
enum class PinLevel { LOW, HIGH }

data class BoardElectricalSpec(
    val supplyVoltage: Double = 5.0,
    val outputResistanceOhms: Double = 50.0,
    val maxPinCurrentAmps: Double = 0.02,
    val inputLowMaxFraction: Double = 0.3,
    val inputHighMinFraction: Double = 0.7,
    val adcBits: Int = 10,
    val adcReferenceVoltage: Double = supplyVoltage,
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
                       val overCurrent: Boolean, val damaged: Boolean,
                       val pwmDuty:Double?=null,val pwmFrequencyHz:Double?=null)
data class McuStep(val circuitResult: DcResult, val pins: Map<String,GpioReading>,
                   val timeSeconds: Double)

/** DC electrical GPIO/ADC foundation. Firmware, buses and transient scheduling are separate work. */
class EducationalMcu(private val bindings: Map<String,TerminalRef>,
                     val spec: BoardElectricalSpec = BoardElectricalSpec(),
                     private val solver: DcSolver = DcSolver()) {
    private data class Pin(var mode:PinMode=PinMode.INPUT,var level:PinLevel=PinLevel.LOW,
                           var i2t:Double=0.0,var damaged:Boolean=false,
                           var pwmDuty:Double?=null,var pwmFrequencyHz:Double=490.0)
    private val pins=bindings.keys.associateWith { Pin() }.toMutableMap()
    private var latest: DcResult?=null
    var timeSeconds:Double=0.0
        private set

    fun reset() {
        pins.values.forEach { it.mode=PinMode.INPUT;it.level=PinLevel.LOW;it.i2t=0.0;it.damaged=false;
            it.pwmDuty=null }
        latest=null
        timeSeconds=0.0
    }
    fun configure(name:String,mode:PinMode) { pins.getValue(name).apply { this.mode=mode;pwmDuty=null } }
    fun write(name:String,level:PinLevel) { pins.getValue(name).apply { this.level=level;pwmDuty=null } }
    fun pwmWrite(name:String,duty:Double,frequencyHz:Double=490.0) {
        require(duty in 0.0..1.0 && frequencyHz.isFinite() && frequencyHz>0)
        pins.getValue(name).apply { mode=PinMode.OUTPUT;pwmDuty=duty;pwmFrequencyHz=frequencyHz }
    }
    fun pwmPins():Map<String,Pair<Double,Double>> = pins.mapNotNull { (name,pin) ->
        pin.pwmDuty?.let { name to (it to pin.pwmFrequencyHz) } }.toMap()
    fun readAdc(name:String):Int {
        val result=latest ?: error("Run an electrical step before sampling ADC.")
        require(result.error==null) { result.error ?: "Circuit solve failed" }
        val voltage=result.nodeVoltages.getValue(bindings.getValue(name))
        val levels=1 shl spec.adcBits
        val ref=if(spec.adcReferenceVoltage>0) spec.adcReferenceVoltage else spec.supplyVoltage
        return floor(voltage.coerceIn(0.0,ref)/ref*levels)
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
    fun levelAt(name:String,sampleTime:Double):PinLevel {
        val pin=pins.getValue(name)
        return pin.pwmDuty?.let { duty ->
            if((sampleTime*pin.pwmFrequencyHz)%1.0<duty) PinLevel.HIGH else PinLevel.LOW
        } ?: pin.level
    }
    fun collectDrives(sampleTime:Double):List<ExternalDrive> = bindings.mapNotNull { (name,terminal) ->
        val pin=pins.getValue(name)
        when {
            pin.damaged -> null
            pin.mode==PinMode.INPUT_PULLUP -> ExternalDrive(terminal,spec.supplyVoltage,30000.0)
            pin.mode==PinMode.OUTPUT -> ExternalDrive(terminal,
                if(levelAt(name,sampleTime)==PinLevel.HIGH) spec.supplyVoltage else 0.0,spec.outputResistanceOhms)
            else -> null
        }
    }
    fun observe(result:DcResult,stepSeconds:Double,sampleTime:Double):Map<String,GpioReading> {
        val currents=mutableMapOf<String,Double>()
        bindings.forEach { (name,terminal) ->
            val pin=pins.getValue(name)
            val current=if(pin.mode==PinMode.OUTPUT && !pin.damaged) {
                val target=if(levelAt(name,sampleTime)==PinLevel.HIGH) spec.supplyVoltage else 0.0
                (target-(result.nodeVoltages[terminal] ?: 0.0))/spec.outputResistanceOhms
            } else 0.0
            currents[name]=current
            if(abs(current)>spec.maxPinCurrentAmps) {
                pin.i2t+=current*current*stepSeconds
                if(pin.i2t>=spec.damageI2tAmpSquaredSeconds) pin.damaged=true
            }
        }
        if(result.error==null) { latest=result;timeSeconds=sampleTime }
        return pins.mapValues { (name,pin) ->
            val amps=if(pin.damaged) 0.0 else currents.getValue(name)
            GpioReading(pin.mode,levelAt(name,sampleTime),amps,abs(amps)>spec.maxPinCurrentAmps,pin.damaged,
                pin.pwmDuty,pin.pwmDuty?.let { pin.pwmFrequencyHz })
        }
    }
    fun step(circuit:Circuit,stepSeconds:Double):McuStep {
        require(stepSeconds.isFinite() && stepSeconds>=0)
        val sampleTime=timeSeconds+stepSeconds
        var result=solver.solve(circuit,collectDrives(sampleTime))
        if(result.error!=null) return McuStep(result,emptyMap(),timeSeconds)
        var readings=observe(result,stepSeconds,sampleTime)
        if(readings.values.any { it.damaged }) {
            result=solver.solve(circuit,collectDrives(sampleTime))
            if(result.error==null) readings=observe(result,0.0,sampleTime)
        }
        return McuStep(result,readings,timeSeconds)
    }
}
