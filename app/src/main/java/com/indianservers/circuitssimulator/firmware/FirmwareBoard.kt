package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcResult
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.simulation.embedded.*
import com.indianservers.circuitssimulator.simulation.digital.LogicState

enum class FirmwareTier { SUPPORTED_SUBSET, STATIC_EXAMPLE }
data class FirmwareDiagnostic(val line:Int,val message:String)

/** Maps parsed pin identifiers to actual board terminals and reuses the finite-impedance MCU model. */
class FirmwareBoard(circuit:Circuit,override val boardId:String):BoardRuntime {
    var circuit:Circuit=circuit
        private set
    val part:PlacedComponent get()=circuit.components.firstOrNull { it.id==boardId && it.kind.isBoard }
        ?: throw IllegalArgumentException("Select a board placed in the circuit.")
    val definition=BoardRegistry.boards.getValue(
        circuit.components.first { it.id==boardId }.kind)
    val spec=BoardElectricalSpec(supplyVoltage=definition.logicVoltage,adcBits=definition.adcBits,
        adcReferenceVoltage=definition.adcReferenceVoltage)
    private val pins=definition.pins.associate { it.name to TerminalRef(part.id,definition.index(it.name)) }
    private val mcu=EducationalMcu(pins,spec)
    private var neutralCircuit=circuit.copy(components=circuit.components.map { item ->
        if(item.id==boardId) item.copy(parameters=item.parameters.filterKeys { !it.startsWith("gpio_") }) else item })
    var latest:DcResult?=null
        private set
    private var powered=false
    init { powered=checkPower() }

    fun usbPowerEnabled()=part.value("usbPower")>=.5 ||
        circuit.firmware.any { it.boardId==boardId && it.usbPower }
    private fun checkPower():Boolean {
        if(usbPowerEnabled()) return true
        val wired=neutralCircuit.wires.flatMap { listOf(it.start,it.end) }.toSet()
        val grounds=definition.pins.indices.filter { index ->
            PinCapability.GROUND in definition.pins[index].capabilities && TerminalRef(boardId,index) in wired }
        val supplies=definition.pins.indices.filter { index ->
            definition.pins[index].name in definition.supplyPins && TerminalRef(boardId,index) in wired }
        if(grounds.isEmpty() || supplies.isEmpty()) return false
        val result=DcSolver().solve(neutralCircuit)
        if(result.error!=null) return false
        return grounds.any { ground -> supplies.any { supply ->
            val difference=(result.nodeVoltages[TerminalRef(boardId,supply)] ?: 0.0)-
                (result.nodeVoltages[TerminalRef(boardId,ground)] ?: 0.0)
            kotlin.math.abs(difference-definition.logicVoltage)<.45
        } }
    }

    private fun sensedCircuit():Circuit {
        val env=circuit.environment
        return circuit.copy(components=circuit.components.map { part ->
            when(part.kind) {
                Kind.LM35_SENSOR,Kind.THERMISTOR,Kind.PTC_THERMISTOR ->
                    part.copy(parameters=part.parameters+("temperatureC" to env.temperatureC))
                Kind.LDR -> part.copy(parameters=part.parameters+("illuminanceLux" to env.illuminanceLux))
                else -> part
            }
        })
    }
    fun updateCircuit(updated:Circuit) {
        require(updated.components.map { it.id to it.kind }==circuit.components.map { it.id to it.kind } &&
            updated.wires==circuit.wires) { "Circuit topology changed; recompile firmware." }
        circuit=updated
        neutralCircuit=updated.copy(components=updated.components.map { item ->
            if(item.id==boardId) item.copy(parameters=item.parameters.filterKeys { !it.startsWith("gpio_") }) else item })
        powered=checkPower()
        latest=null
    }

    fun resolve(value:Any?,analog:Boolean=false):String {
        val mapped=definition.resolve(value,analog)
            ?: value?.toString()?.uppercase()?.takeIf { it in pins }
            ?: throw IllegalArgumentException("${definition.product} has no pin ${value}.")
        require(mapped in pins) { "${definition.product} has no pin $mapped." }
        return mapped
    }
    private fun requireCapability(name:String,capability:PinCapability) {
        val pin=definition.pin(name)!!
        if(capability in pin.capabilities) return
        throw IllegalArgumentException(when {
            pin.inputOnly && capability==PinCapability.DIGITAL_OUTPUT ->
                "$name on this ${definition.product} is input-only and cannot be configured as an output."
            capability==PinCapability.PWM ->
                "$name on this ${definition.product} is not a PWM-capable pin. analogWrite is rejected here."
            capability==PinCapability.ANALOG_INPUT ->
                "$name on this ${definition.product} is not an ADC input."
            else -> "$name on ${definition.product} does not support ${capability.name.lowercase().replace('_',' ')}."
        })
    }
    fun mode(name:String,mode:PinMode) {
        requireCapability(name,if(mode==PinMode.OUTPUT) PinCapability.DIGITAL_OUTPUT else PinCapability.DIGITAL_INPUT)
        mcu.configure(name,mode)
    }
    fun write(name:String,high:Boolean) {
        requireCapability(name,PinCapability.DIGITAL_OUTPUT)
        mcu.write(name,if(high) PinLevel.HIGH else PinLevel.LOW)
    }
    fun pwmFrequencyHz(name:String):Double {
        val kind=part.kind
        return when {
            kind in setOf(Kind.ARDUINO_UNO,Kind.ARDUINO_NANO) && name in setOf("D5","D6") -> 980.0
            kind==Kind.ARDUINO_MEGA && name in setOf("D4","D13") -> 980.0
            kind in setOf(Kind.ARDUINO_UNO,Kind.ARDUINO_NANO,Kind.ARDUINO_MEGA) -> 490.0
            else -> 500.0
        }
    }
    fun pwm(name:String,value:Int,frequencyHz:Double=pwmFrequencyHz(name)) {
        requireCapability(name,PinCapability.PWM)
        require(value in 0..255) { "analogWrite duty must be 0–255." }
        mcu.pwmWrite(name,value/255.0,frequencyHz)
    }
    fun pwmExact(name:String,duty:Double,frequencyHz:Double) {
        requireCapability(name,PinCapability.PWM)
        mcu.pwmWrite(name,duty.coerceIn(0.0,1.0),frequencyHz)
    }
    fun usbDrives():List<com.indianservers.circuitssimulator.simulation.ExternalDrive> {
        if(!usbPowerEnabled()) return emptyList()
        val supply=definition.pins.indexOfFirst { it.name in definition.supplyPins }.takeIf { it>=0 } ?: return emptyList()
        return listOf(com.indianservers.circuitssimulator.simulation.ExternalDrive(
            TerminalRef(boardId,supply),definition.logicVoltage,0.8))
    }
    fun collectDrives(sampleTime:Double)=mcu.collectDrives(sampleTime)+usbDrives()
    fun observe(result:DcResult,dt:Double,sampleTime:Double) {
        latest=result
        mcu.observe(result,dt,sampleTime)
    }
    fun settle(deltaMicros:Long=0,extra:List<com.indianservers.circuitssimulator.simulation.ExternalDrive> = emptyList()):McuStep {
        require(deltaMicros>=0)
        powered=checkPower()
        require(powered) {
            "${definition.product} is unpowered. Enable USB Power or connect ${definition.supplyPins.joinToString()} and GND." }
        val sampleTime=mcu.timeSeconds+deltaMicros/1_000_000.0
        val live=sensedCircuit()
        val net=live.copy(components=live.components.map { item ->
            if(item.id==boardId) item.copy(parameters=item.parameters.filterKeys { !it.startsWith("gpio_") }) else item })
        val solver=DcSolver()
        var result=solver.solve(net,collectDrives(sampleTime)+extra)
        val readings=if(result.error==null) mcu.observe(result,deltaMicros/1_000_000.0,sampleTime) else emptyMap()
        latest=result
        return McuStep(result,readings,mcu.timeSeconds)
    }
    override fun setPinMode(pin:String,mode:PinMode)=mode(pin,mode)
    override fun digitalWrite(pin:String,high:Boolean)=write(pin,high)
    override fun digitalRead(pin:String)=digital(pin)
    override fun analogRead(pin:String)=analog(pin)
    override fun pwmWrite(pin:String,value:Int,frequencyHz:Double)=pwm(pin,value,frequencyHz)
    override fun serialPorts():List<UartPortSpec> {
        val tx=definition.pins.firstOrNull { it.name in setOf("TX","D1") && PinCapability.UART in it.capabilities }?.name
        val rx=definition.pins.firstOrNull { it.name in setOf("RX","D0") && PinCapability.UART in it.capabilities }?.name
        val ports=mutableListOf<UartPortSpec>()
        if(tx!=null && rx!=null) ports+=UartPortSpec("Serial",tx,rx)
        if(part.kind==Kind.ARDUINO_MEGA) {
            ports+=UartPortSpec("Serial1","D18","D19")
            ports+=UartPortSpec("Serial2","D16","D17")
            ports+=UartPortSpec("Serial3","D14","D15")
        }
        return ports
    }
    fun digital(name:String):LogicState {
        requireCapability(name,PinCapability.DIGITAL_INPUT)
        if(latest==null) settle()
        return mcu.readDigital(name)
    }
    fun analog(name:String):Int {
        requireCapability(name,PinCapability.ANALOG_INPUT)
        if(latest==null) settle()
        return mcu.readAdc(name)
    }
    fun voltage(name:String):Double?=latest?.nodeVoltages?.get(pins[name])
    fun pwmPins()=mcu.pwmPins()
    override fun reset() { mcu.reset();latest=null;powered=checkPower() }
    fun millis()=(mcu.timeSeconds*1000).toLong()
}
