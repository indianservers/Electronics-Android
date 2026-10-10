package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcResult
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.simulation.embedded.*
import com.indianservers.circuitssimulator.simulation.digital.LogicState

enum class FirmwareTier { SUPPORTED_SUBSET, STATIC_EXAMPLE }
enum class BoardPowerState { OFF, BOOTING, RUNNING, RESET, FAULT }
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
        adcReferenceVoltage=definition.adcReferenceVoltage,
        maxPinCurrentAmps=(definition.hardware.recommendedPinCurrentMa ?: 20.0)/1000.0)
    private val pins=definition.pins.associate { it.name to TerminalRef(part.id,definition.index(it.name)) }
    private val mcu=EducationalMcu(pins,spec,maxInputVoltages=definition.pins.filter {
        PinCapability.DIGITAL_INPUT in it.capabilities || PinCapability.ANALOG_INPUT in it.capabilities
    }.associate { it.name to (it.maxVoltage ?: definition.logicVoltage) })
    var powerState=BoardPowerState.OFF
        private set
    var powerMessage:String?=null
        private set
    private var bootChecked=false
    private var neutralResult:DcResult?=null
    private var neutralCircuit=circuit.copy(components=circuit.components.map { item ->
        if(item.id==boardId) item.copy(parameters=item.parameters.filterKeys { !it.startsWith("gpio_") }) else item })
    var latest:DcResult?=null
        private set
    var latestPins:Map<String,GpioReading> = emptyMap()
        private set
    var peerDrives:((Double)->List<com.indianservers.circuitssimulator.simulation.ExternalDrive>)?=null
    private var digitalSession=com.indianservers.circuitssimulator.simulation.CanvasDigitalSession(circuit,com.indianservers.circuitssimulator.simulation.TransientSolver(),firmwareManaged=true)
    private val claims=mutableMapOf<String,String>()
    fun claim(function:String,names:List<String>) {
        names.forEach { name -> require(claims[name]==null || claims[name]==function) {
            "$name peripheral conflict: ${claims[name]} and $function." } }
        names.forEach { claims[it]=function }
    }
    fun requireGpio(name:String) { require(claims[name]==null) { "$name is owned by ${claims[name]}; GPIO access conflicts with that peripheral." } }
    fun requireI2cMapping(sda:String,scl:String) {
        require(listOf(sda,scl).all { PinCapability.I2C in definition.pin(it)!!.capabilities }) { "Invalid I2C pin mapping." }
        if(definition.family in setOf(BoardFamily.RP2040,BoardFamily.RP2350)) {
            val a=definition.pin(sda)!!.gpioNumber!!;val b=definition.pin(scl)!!.gpioNumber!!
            require(a%2==0 && b%2==1 && (a/2)%2==(b/2)%2) { "$sda/$scl are not matching SDA/SCL pins for one I2C controller." }
        }
    }
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
            difference in definition.hardware.minimumLogicSupply..definition.hardware.maximumLogicSupply ||
                definition.hardware.regulatedInputs[definition.pins[supply].name]?.contains(difference)==true
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
        neutralResult=null
        latest=null
    }

    fun resolve(value:Any?,analog:Boolean=false):String {
        val mapped=definition.resolve(value,analog)
            ?: value?.toString()?.uppercase()?.takeIf { it in pins }
            ?: throw IllegalArgumentException("${definition.product} has no pin ${value}.")
        require(mapped in pins) { "${definition.product} has no pin $mapped." }
        return definition.pin(mapped)?.signal?.takeIf { signal -> signal in pins &&
            PinCapability.DIGITAL_INPUT in definition.pin(signal)!!.capabilities } ?: mapped
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
        if(mode==PinMode.INPUT_PULLUP) require(definition.pin(name)!!.pullUpSupported) {
            "$name on ${definition.product} has no internal pull-up." }
        if(mode==PinMode.INPUT_PULLDOWN) require(definition.pin(name)!!.pullDownSupported) {
            "$name has no internal pull-down." }
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
            definition.family==BoardFamily.ESP8266 -> 1000.0
            else -> 500.0
        }
    }
    fun pwm(name:String,value:Int,frequencyHz:Double=pwmFrequencyHz(name)) {
        requireCapability(name,PinCapability.PWM)
        require(value in 0..255) { "analogWrite duty must be 0–255." }
        pwmExact(name,value/255.0,frequencyHz)
    }
    fun pwmExact(name:String,duty:Double,frequencyHz:Double) {
        requireCapability(name,PinCapability.PWM)
        require(claims[name]==null) { "$name peripheral conflict: ${claims[name]} and PWM." }
        val slice=definition.pin(name)?.pwmResource
        if(slice!=null) mcu.pwmPins().forEach { (other,pwm) ->
            require(other==name || definition.pin(other)?.pwmResource!=slice || kotlin.math.abs(pwm.second-frequencyHz)<.001) {
                "$slice frequency conflict between $name and $other." }
        }
        mcu.pwmWrite(name,duty.coerceIn(0.0,1.0),frequencyHz)
    }
    fun usbDrives():List<com.indianservers.circuitssimulator.simulation.ExternalDrive> {
        val usb=usbPowerEnabled()
        val neutral=if(usb) null else neutralResult ?: DcSolver().solve(neutralCircuit).also { neutralResult=it }
        val ground=definition.pins.indexOfFirst { it.type==PinType.GROUND }
        val groundVoltage=neutral?.nodeVoltages?.get(TerminalRef(boardId,ground)) ?: 0.0
        val regulator=definition.hardware.regulatedInputs.any { (name,range) ->
            val index=definition.index(name)
            index>=0 && neutral!=null && neutral.error==null && ((neutral.nodeVoltages[TerminalRef(boardId,index)] ?: 0.0)-groundVoltage) in range
        }
        if(!usb && !regulator) return emptyList()
        return definition.pins.mapIndexedNotNull { index,pin ->
            val voltage=when {
                pin.name in definition.supplyPins -> definition.logicVoltage
                pin.name in setOf("3V3","3V3_OUT") -> 3.3
                usb && pin.name in setOf("5V","VBUS","VU") -> 5.0
                else -> null
            }
            voltage?.let { com.indianservers.circuitssimulator.simulation.ExternalDrive(TerminalRef(boardId,index),it,0.8) }
        }
    }
    fun collectDrives(sampleTime:Double)=mcu.collectDrives(sampleTime)+usbDrives()+
        if(powered || usbPowerEnabled()) definition.pins.mapIndexedNotNull { index,pin ->
            val bootstrap=definition.hardware.bootLevels[pin.name]
            val high=bootstrap ?: if(pin.type in setOf(PinType.RESET,PinType.ENABLE)) true else null
            high?.let { com.indianservers.circuitssimulator.simulation.ExternalDrive(
                TerminalRef(boardId,index),if(it) definition.logicVoltage else 0.0,12000.0) }
        } else emptyList()
    fun observe(result:DcResult,dt:Double,sampleTime:Double) {
        latest=result
        val readings=mcu.observe(result,dt,sampleTime)
        latestPins=readings.mapValues { (name,reading) -> readings[definition.pin(name)?.signal] ?: reading }
    }
    fun settle(deltaMicros:Long=0,extra:List<com.indianservers.circuitssimulator.simulation.ExternalDrive> = emptyList()):McuStep {
        require(deltaMicros>=0)
        powered=checkPower() || usbDrives().isNotEmpty()
        if(!powered) { powerState=BoardPowerState.OFF;bootChecked=false }
        require(powered) {
            "${definition.product} is unpowered. Enable USB Power or connect ${definition.supplyPins.joinToString()} and GND." }
        val sampleTime=mcu.timeSeconds+deltaMicros/1_000_000.0
        val live=sensedCircuit()
        val net=live.copy(components=live.components.map { item ->
            if(item.id==boardId) item.copy(parameters=item.parameters.filterKeys { !it.startsWith("gpio_") }) else item })
        var result=digitalSession.settleDc(net,collectDrives(sampleTime)+extra+peerDrives?.invoke(sampleTime).orEmpty(),sampleTime)
        if(result.error==null) {
            val ground=definition.pins.indexOfFirst { it.type==PinType.GROUND }
            val g=result.nodeVoltages[TerminalRef(boardId,ground)] ?: 0.0
            val connectedPins=circuit.wires.flatMap { listOf(it.start,it.end) }.toSet()
            val heldReset=definition.pins.mapIndexedNotNull { i,pin ->
                val ref=TerminalRef(boardId,i)
                if(pin.type in setOf(PinType.RESET,PinType.ENABLE) && ref in connectedPins &&
                    (result.nodeVoltages[ref] ?: 0.0)-g<.8) pin.name else null
            }
            if(heldReset.isNotEmpty()) {
                mcu.reset();bootChecked=false;powerState=BoardPowerState.RESET
                powerMessage="Board held in reset/disabled: ${heldReset.joinToString()}."
                error(powerMessage!!)
            }
            val rail=definition.supplyPins.maxOf { (result.nodeVoltages[TerminalRef(boardId,definition.index(it))] ?: 0.0)-g }
            if(rail !in definition.hardware.minimumLogicSupply..definition.hardware.maximumLogicSupply) {
                powerState=BoardPowerState.FAULT;bootChecked=false
                powerMessage="Brownout / invalid supply: ${definition.product} logic rail is %.2f V.".format(rail)
                mcu.reset();error(powerMessage!!)
            }
            if(!bootChecked) {
                powerState=BoardPowerState.BOOTING
                val connected=circuit.wires.flatMap { listOf(it.start,it.end) }.toSet()
                val wrong=definition.hardware.bootLevels.filter { (name,high) ->
                    val ref=TerminalRef(boardId,definition.index(name))
                    ref in connected && ((result.nodeVoltages[ref] ?: 0.0)-g>=definition.logicVoltage*.5)!=high
                }
                if(wrong.isNotEmpty()) {
                    powerState=BoardPowerState.FAULT
                    powerMessage="Boot failed: ${wrong.keys.joinToString()} startup levels do not match normal flash boot requirements."
                    error(powerMessage!!)
                }
                bootChecked=true
            }
            powerState=BoardPowerState.RUNNING;powerMessage=null
        }
        val readings=if(result.error==null) mcu.observe(result,deltaMicros/1_000_000.0,sampleTime) else emptyMap()
        definition.hardware.gpioTotalCurrentMa?.let { limit ->
            val source=readings.values.sumOf { it.currentAmps.coerceAtLeast(0.0) }*1000
            val sink=readings.values.sumOf { (-it.currentAmps).coerceAtLeast(0.0) }*1000
            if(maxOf(source,sink)>limit) {
                powerState=BoardPowerState.FAULT
                powerMessage="Aggregate GPIO current exceeds $limit mA on ${definition.product}."
                mcu.reset();error(powerMessage!!)
            }
        }
        latest=result
        latestPins=readings.mapValues { (name,reading) -> readings[definition.pin(name)?.signal] ?: reading }
        return McuStep(result,latestPins,mcu.timeSeconds)
    }
    override fun setPinMode(pin:String,mode:PinMode)=mode(pin,mode)
    override fun digitalWrite(pin:String,high:Boolean)=write(pin,high)
    override fun digitalRead(pin:String)=digital(pin)
    override fun analogRead(pin:String)=analog(pin)
    override fun pwmWrite(pin:String,value:Int,frequencyHz:Double)=pwm(pin,value,frequencyHz)
    override fun serialPorts():List<UartPortSpec> {
        return definition.hardware.uartPins.mapIndexed { i,pair ->
            UartPortSpec(definition.hardware.uartNames.getOrNull(i) ?: if(i==0) "Serial" else "Serial$i",pair.first,pair.second) }+
            if(definition.hardware.usbSerial) listOf(UartPortSpec("Serial","","")) else emptyList()
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
    fun nextDigitalEventMicros(limit:Long)=digitalSession.nextElectricalEvent(limit/1_000_000.0)?.let { kotlin.math.ceil(it*1_000_000).toLong() }
    fun pwmPins()=mcu.pwmPins()
    override fun reset() { digitalSession=com.indianservers.circuitssimulator.simulation.CanvasDigitalSession(circuit,com.indianservers.circuitssimulator.simulation.TransientSolver(),firmwareManaged=true);mcu.reset();latest=null;latestPins=emptyMap();claims.clear();powered=checkPower();bootChecked=false;powerState=BoardPowerState.RESET;powerMessage=null }
    fun millis()=(mcu.timeSeconds*1000).toLong()
    fun synchronizeClock(micros:Long)=mcu.synchronizeClock(micros/1_000_000.0)
}
