package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.EnvironmentState
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PinCapability
import com.indianservers.circuitssimulator.simulation.DcResult
import com.indianservers.circuitssimulator.simulation.embedded.GpioReading
import com.indianservers.circuitssimulator.simulation.embedded.PinMode
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import java.util.ArrayDeque
import kotlin.math.ceil
import kotlin.math.roundToInt

data class FirmwareFrame(val timeMicros:Long,val result:DcResult,val pins:Map<String,GpioReading>)
data class FirmwareAdvance(val frames:List<FirmwareFrame>,val console:List<String>,
                           val error:FirmwareDiagnostic?=null,val timeMicros:Long=0,
                           val currentLine:Int=0,val variables:Map<String,String> = emptyMap())

/** Executes the parsed subset against solved circuit nodes on one deterministic virtual clock. */
class FirmwareSession(val program:FirmwareProgram,val board:FirmwareBoard,
                      val fabric:ProtocolFabric=ProtocolFabric(),
                      var environment:EnvironmentState=board.circuit.environment,
                      val peripherals:PeripheralRuntime=PeripheralRuntime()) {
    private val pending=ArrayDeque<Statement>()
    private val values=mutableMapOf<String,Any?>()
    private val console=ArrayDeque<String>()
    private var setupDone=false
    private var wakeAt=0L
    private var stopped=false
    private val serialBauds=mutableMapOf<String,Int>()
    private val serialLine=StringBuilder()
    private var i2cTx:MutableList<Int>?=null
    private var i2cRx=ArrayDeque<Int>()
    private var i2cAddr=0
    private val servoPins=mutableMapOf<String,String>()
    private var pulseWait:PulseWait?=null
    private data class Interrupt(val function:String,val edge:String,var previous:LogicState?)
    private val interrupts=mutableMapOf<String,Interrupt>()
    private var interruptedWakeAt:Long?=null
    var timeMicros=0L
        private set
    private var bootAtMicros=0L
    var currentLine=0
        private set
    private data class PulseWait(val pin:String,val high:Boolean,val timeoutAt:Long,var phase:Int,var startedAt:Long)

    init { enqueue(program.setup) }
    fun reset(startTimeMicros:Long=0) {
        pending.clear();values.clear();console.clear();board.reset()
        setupDone=false;wakeAt=startTimeMicros;stopped=false;serialBauds.clear();serialLine.clear();timeMicros=startTimeMicros;bootAtMicros=startTimeMicros;currentLine=0
        board.synchronizeClock(startTimeMicros)
        i2cTx=null;i2cRx.clear();servoPins.clear();pulseWait=null
        interrupts.clear()
        interruptedWakeAt=null
        fabric.uart.entries.removeAll { it.value.ownerId==board.boardId }
        fabric.i2cRoutes.remove(board.boardId)
        enqueue(program.setup)
    }
    fun stop() {
        stopped=true;board.reset()
        fabric.uart.entries.removeAll { it.value.ownerId==board.boardId }
        fabric.i2cRoutes.remove(board.boardId)
    }
    fun consoleLines()=console.toList()+if(serialLine.isNotEmpty()) listOf(serialLine.toString()) else emptyList()
    fun clearConsole() { console.clear();serialLine.clear() }
    fun variables()=values.mapValues { (_,value) -> value.toString() }
    private fun enqueue(body:List<Statement>) { body.asReversed().forEach { pending.addFirst(it) } }
    private fun statements(stmt:Statement)=if(stmt is Statement.Block) stmt.body else listOf(stmt)
    private fun number(value:Any?):Double=when(value) {
        is Number -> value.toDouble()
        is Boolean -> if(value) 1.0 else 0.0
        is String -> value.toDoubleOrNull() ?: error("Expected a number, got '$value'.")
        else -> error("Expected a number.")
    }
    private fun truth(value:Any?)=when(value) { is Boolean -> value;is Number -> value.toDouble()!=0.0;
        is String -> value.isNotEmpty();else -> false }
    private fun eval(expr:Expression):Any?=when(expr) {
        is Expression.Literal -> expr.value
        is Expression.Name -> when(expr.name) {
            "HIGH","OUTPUT","true","True","Pin.OUT" -> 1.0
            "LOW","INPUT","false","False","Pin.IN" -> 0.0
            "None" -> null
            "Pin.PULL_UP" -> 2.0
            "Pin.PULL_DOWN" -> 3.0
            "MSBFIRST" -> 1.0
            "LSBFIRST" -> 0.0
            "SPI_MODE0","SPI_MODE1","SPI_MODE2","SPI_MODE3" -> expr.name.last().digitToInt().toDouble()
            "SPI_CLOCK_DIV2","SPI_CLOCK_DIV4","SPI_CLOCK_DIV8","SPI_CLOCK_DIV16","SPI_CLOCK_DIV32","SPI_CLOCK_DIV64","SPI_CLOCK_DIV128" -> expr.name.removePrefix("SPI_CLOCK_DIV").toDouble()
            "SERIAL_8N1" -> 801.0
            "SERIAL_8E1" -> 811.0
            "SERIAL_8O1" -> 821.0
            "SERIAL_7E1" -> 711.0
            "SERIAL_8N2" -> 802.0
            "INPUT_PULLUP" -> 2.0
            "INPUT_PULLDOWN" -> 3.0
            "LED_BUILTIN" -> board.definition.ledBuiltin ?: error("This board has no LED_BUILTIN.")
            "SDA","SCL","MOSI","MISO","SCK","SS","TX","RX" -> board.resolve(expr.name)
            else -> values[expr.name] ?: if(expr.name.matches(Regex("[A-Za-z]+[0-9]+"))) expr.name
                else error("Unknown variable '${expr.name}'.")
        }
        is Expression.Unary -> if(expr.op=="!") if(truth(eval(expr.value))) 0.0 else 1.0
            else -number(eval(expr.value))
        is Expression.Binary -> {
            val left=eval(expr.left)
            if(expr.op=="&&" && !truth(left)) 0.0
            else if(expr.op=="||" && truth(left)) 1.0
            else {
                val right=eval(expr.right)
                when(expr.op) {
                    "+" -> if(left is String || right is String) "$left$right" else number(left)+number(right)
                    "-" -> number(left)-number(right)
                    "*" -> number(left)*number(right)
                    "/" -> number(left)/number(right)
                    "%" -> number(left)%number(right)
                    "==" -> if(left==right || runCatching { number(left)==number(right) }.getOrDefault(false)) 1.0 else 0.0
                    "!=" -> if(left==right || runCatching { number(left)==number(right) }.getOrDefault(false)) 0.0 else 1.0
                    "<" -> if(number(left)<number(right)) 1.0 else 0.0
                    ">" -> if(number(left)>number(right)) 1.0 else 0.0
                    "<=" -> if(number(left)<=number(right)) 1.0 else 0.0
                    ">=" -> if(number(left)>=number(right)) 1.0 else 0.0
                    "&&" -> if(truth(right)) 1.0 else 0.0
                    "||" -> if(truth(right)) 1.0 else 0.0
                    else -> error("Unsupported operator ${expr.op}")
                }
            }
        }
        is Expression.Call -> call(expr)
    }
    private fun pin(expr:Expression,analog:Boolean=false)=board.resolve(eval(expr),analog)
    private fun line(text:String) {
        if(console.size>=300) console.removeFirst()
        console.addLast(text)
    }
    fun enqueueSerial(text:String) {
        fabric.monitorSend(board.circuit,board.boardId,"Serial",fabric.uart[board.boardId]?.baud ?: 9600,text)
    }
    private fun apiName(name:String)=ArduinoSubset.canonicalApi(name)
    private fun call(call:Expression.Call):Any? {
        if(program.microPython && call.name !in program.functions) return pythonCall(call)
        val args=call.args
        val api=apiName(call.name)
        val portName=call.name.substringBefore('.').takeIf { it.startsWith("Serial") } ?: "Serial"
        val portId=if(portName=="Serial") board.boardId else "${board.boardId}:$portName"
        if(call.name in program.functions) { enqueue(program.functions.getValue(call.name));return 0.0 }
        return when(api) {
            "__resumeInterruptDelay" -> {
                wakeAt=maxOf(timeMicros,interruptedWakeAt ?: timeMicros);interruptedWakeAt=null;0.0
            }
            "digitalPinToInterrupt" -> {
                val name=pin(args[0])
                require(PinCapability.INTERRUPT in board.definition.pin(name)!!.capabilities) { "$name has no external GPIO interrupt on ${board.definition.product}." }
                name
            }
            "attachInterrupt" -> {
                val name=board.resolve(eval(args[0]))
                require(PinCapability.INTERRUPT in board.definition.pin(name)!!.capabilities) { "$name has no external GPIO interrupt." }
                val handler=(args.getOrNull(1) as? Expression.Name)?.name ?: error("Interrupt handler must be a named void function.")
                require(handler in program.functions) { "Unknown interrupt handler $handler." }
                val edge=(args.getOrNull(2) as? Expression.Name)?.name ?: error("Use RISING, FALLING or CHANGE.")
                require(edge in setOf("RISING","FALLING","CHANGE")) { "Unsupported interrupt edge $edge." }
                board.settle(extra=extraDrives());interrupts[name]=Interrupt(handler,edge,board.digital(name));0.0
            }
            "detachInterrupt" -> { interrupts.remove(board.resolve(eval(args[0])));0.0 }
            "delayMicroseconds" -> {
                val micros=number(eval(args[0]));require(micros in 0.0..60_000_000.0) { "delayMicroseconds out of range." }
                wakeAt=timeMicros+micros.toLong();0.0
            }
            "pinMode" -> {
                val mode=when(number(eval(args[1])).toInt()) {
                    0 -> PinMode.INPUT;1 -> PinMode.OUTPUT;2 -> PinMode.INPUT_PULLUP;3 -> PinMode.INPUT_PULLDOWN
                    else -> error("pinMode accepts INPUT, OUTPUT, or INPUT_PULLUP.")
                }
                val resolved=pin(args[0])
                board.requireGpio(resolved)
                board.mode(resolved,mode);board.settle(extra=extraDrives());resolved
            }
            "digitalWrite" -> {
                val resolved=pin(args[0]);board.requireGpio(resolved)
                board.write(resolved,number(eval(args[1]))!=0.0);board.settle(extra=extraDrives());0.0
            }
            "digitalRead" -> {
                board.settle(extra=extraDrives())
                when(board.digital(pin(args[0]))) { LogicState.HIGH -> 1.0;LogicState.LOW -> 0.0;
                    else -> error("Digital input is undefined between LOW and HIGH thresholds.") }
            }
            "analogRead" -> { board.settle(extra=extraDrives());board.analog(pin(args[0],analog=true)).toDouble() }
            "analogWrite" -> {
                board.pwm(pin(args[0]),number(eval(args[1])).toInt());board.settle(extra=extraDrives());0.0
            }
            "delay" -> {
                val milliseconds=number(eval(args[0]))
                require(milliseconds.isFinite() && milliseconds in 0.0..60000.0) {
                    "delay must be between 0 and 60,000 ms." }
                wakeAt=timeMicros+(milliseconds*1000).toLong();0.0
            }
            "delaySeconds" -> {
                val seconds=number(eval(args[0]))
                require(seconds.isFinite() && seconds in 0.0..60.0) { "sleep must be between 0 and 60 s." }
                wakeAt=timeMicros+(seconds*1_000_000).toLong();0.0
            }
            "millis" -> (timeMicros-bootAtMicros)/1000.0
            "micros" -> (timeMicros-bootAtMicros).toDouble()
            "map" -> {
                val x=number(eval(args[0]));val inMin=number(eval(args[1]));val inMax=number(eval(args[2]))
                val outMin=number(eval(args[3]));val outMax=number(eval(args[4]))
                outMin+(x-inMin)*(outMax-outMin)/(inMax-inMin)
            }
            "constrain" -> number(eval(args[0])).coerceIn(number(eval(args[1])),number(eval(args[2])))
            "hex" -> number(eval(args[0])).toLong().toString(16)
            "tone" -> {
                val frequency=number(eval(args[1]))
                require(frequency in 20.0..20000.0) { "tone frequency must be 20–20,000 Hz." }
                board.pwmExact(pin(args[0]),0.5,frequency);board.settle(extra=extraDrives());0.0
            }
            "noTone" -> { board.write(pin(args[0]),false);board.settle(extra=extraDrives());0.0 }
            "pulseIn" -> {
                val resolved=pin(args[0])
                val high=number(eval(args[1]))!=0.0
                val timeout=if(args.size>2) number(eval(args[2])).toLong() else 1_000_000L
                pulseWait=PulseWait(resolved,high,timeMicros+timeout,0,0)
                wakeAt=timeMicros+1;0.0
            }
            "Serial.begin" -> {
                val baud=number(eval(args[0])).toInt()
                require(baud in 300..2_000_000) { "Serial baud must be 300–2,000,000." }
                serialBauds[portName]=baud
                val ports=board.serialPorts().firstOrNull { it.name==portName }
                    ?: error("${board.definition.product} has no $portName hardware UART.")
                val framing=if(args.size>1) number(eval(args[1])).toInt() else 801
                val bits=framing/100;val parity=framing/10%10;val stops=framing%10
                require(bits in 5..8 && parity in 0..2 && stops in 1..2) { "Unsupported UART framing." }
                fabric.uart[portId]=UartEndpoint(board.boardId,
                        ports.txPin.takeIf { it.isNotBlank() }?.let { com.indianservers.circuitssimulator.domain.TerminalRef(board.boardId,board.definition.index(it)) },
                        ports.rxPin.takeIf { it.isNotBlank() }?.let { com.indianservers.circuitssimulator.domain.TerminalRef(board.boardId,board.definition.index(it)) },baud,bits,parity,stops)
                board.claim(portName,listOf(ports.txPin,ports.rxPin).filter { it.isNotBlank() })
                if(ports.txPin.isNotBlank()) { board.mode(ports.txPin,PinMode.INPUT) }
                if(ports.rxPin.isNotBlank()) board.mode(ports.rxPin,PinMode.INPUT)
                fabric.electricalResult=board.settle(extra=extraDrives()).circuitResult
                0.0
            }
            "Serial.print","Serial.println","Serial.write" -> {
                require(serialBauds[portName]!=null) { "Call $portName.begin before printing." }
                val value=if(args.isEmpty()) "" else eval(args[0])
                val rendered=when {
                    api=="Serial.write" && value is Number -> (value.toInt() and 0xFF).toChar().toString()
                    value is Double && value%1.0==0.0 -> value.toLong().toString()
                    else -> value.toString()
                }
                if(api=="Serial.println") {
                    line(serialLine.append(rendered).toString());serialLine.clear()
                } else serialLine.append(rendered)
                fabric.uart[portId]?.enqueueTx(if(api=="Serial.println") rendered+"\n" else rendered)
                0.0
            }
            "Serial.available" -> fabric.uart[portId]?.available()?.toDouble() ?: 0.0
            "Serial.read" -> fabric.uart[portId]?.read()?.toDouble() ?: -1.0
            "Wire.begin" -> {
                val defaults=board.definition.hardware.i2cPins ?: error("No default I2C pins for ${board.definition.product}.")
                val sda=if(args.size==2) board.resolve(eval(args[0])) else defaults.first
                val scl=if(args.size==2) board.resolve(eval(args[1])) else defaults.second
                board.requireI2cMapping(sda,scl)
                board.claim("I2C",listOf(sda,scl))
                board.mode(sda,PinMode.INPUT_PULLUP);board.mode(scl,PinMode.INPUT_PULLUP)
                fabric.i2cRoutes[board.boardId]=sda to scl
                fabric.rebuildDevices(board.circuit);0.0
            }
            "Wire.beginTransmission" -> { i2cAddr=number(eval(args[0])).toInt();i2cTx=mutableListOf();0.0 }
            "Wire.write" -> { require(i2cTx!=null) { "Call Wire.beginTransmission first." }
                i2cTx!!+=number(eval(args[0])).toInt() and 0xFF;0.0 }
            "Wire.endTransmission" -> {
                val bytes=i2cTx ?: emptyList();i2cTx=null
                fabric.electricalResult=board.settle(extra=extraDrives()).circuitResult
                fabric.i2cWrite(board.circuit,board.boardId,i2cAddr,bytes,timeMicros,args.firstOrNull()?.let { truth(eval(it)) } ?: true).toDouble()
            }
            "Wire.requestFrom" -> {
                val address=number(eval(args[0])).toInt();val count=number(eval(args[1])).toInt().coerceIn(1,32)
                i2cRx.clear()
                fabric.electricalResult=board.settle(extra=extraDrives()).circuitResult
                fabric.i2cRead(board.circuit,board.boardId,address,count,environment,timeMicros).forEach { i2cRx.addLast(it) }
                i2cRx.size.toDouble()
            }
            "Wire.read" -> if(i2cRx.isEmpty()) -1.0 else i2cRx.removeFirst().toDouble()
            "Wire.available" -> i2cRx.size.toDouble()
            "Wire.scan" -> {
                fabric.electricalResult=board.settle(extra=extraDrives()).circuitResult
                val found=fabric.i2cScan(board.circuit,board.boardId)
                found.forEach { line("Found device at 0x${it.toString(16)}") }
                found.size.toDouble()
            }
            "SPI.begin" -> {
                val pins=board.definition.hardware.spiPins
                require(pins.size==4) { "No SPI mapping for ${board.definition.product}." }
                board.claim("SPI",pins.take(3));fabric.rebuildDevices(board.circuit);0.0
            }
            "SPI.transfer" -> {
                board.settle(extra=extraDrives())
                fabric.electricalResult=board.latest
                val byte=number(eval(args[0])).toInt() and 0xFF
                val selected=fabric.selectedSpiDevices(board.latest)
                fabric.spiTransfer(board.circuit,board.boardId,byte,selected,timeMicros).toDouble()
            }
            "SPI.setDataMode" -> {
                val mode=number(eval(args[0])).toInt();require(mode in 0..3)
                fabric.spiConfigurations[board.boardId]=(fabric.spiConfigurations[board.boardId] ?: SpiConfiguration()).copy(mode=mode);0.0
            }
            "SPI.setClockDivider" -> {
                val divider=number(eval(args[0])).toInt();require(divider in 2..128)
                fabric.spiConfigurations[board.boardId]=(fabric.spiConfigurations[board.boardId] ?: SpiConfiguration()).copy(
                    frequencyHz=((board.definition.hardware.clockMhz ?: 16)*1_000_000/divider));0.0
            }
            "SPI.setBitOrder" -> {
                fabric.spiConfigurations[board.boardId]=(fabric.spiConfigurations[board.boardId] ?: SpiConfiguration()).copy(msbFirst=number(eval(args[0]))==1.0);0.0
            }
            "Servo.attach" -> {
                val instance=call.name.substringBefore('.',"servo")
                val resolved=pin(args[0])
                servoPins[instance]=resolved
                board.pwmExact(resolved,0.075,50.0);board.settle(extra=extraDrives());0.0
            }
            "Servo.write" -> {
                val instance=call.name.substringBefore('.',"servo")
                val resolved=servoPins[instance] ?: error("Call Servo.attach before write.")
                val angle=number(eval(args[0])).coerceIn(0.0,180.0)
                values["${instance}.angle"]=angle
                val pulseUs=1000.0+angle/180.0*1000.0
                board.pwmExact(resolved,pulseUs/20000.0,50.0);board.settle(extra=extraDrives());angle
            }
            "Servo.read" -> values["${call.name.substringBefore('.',"servo")}.angle"] ?: 0.0
            else -> {
                if(call.name.endsWith(".value")) {
                    val target=values[call.name.substringBefore('.')] ?: error("Unknown Pin '${call.name}'.")
                    if(args.isEmpty()) {
                        board.settle(extra=extraDrives())
                        return when(board.digital(board.resolve(target))) {
                            LogicState.HIGH -> 1.0;LogicState.LOW -> 0.0
                            else -> error("Digital input is undefined between LOW and HIGH thresholds.")
                        }
                    }
                    board.write(board.resolve(target),number(eval(args[0]))!=0.0);board.settle(extra=extraDrives());0.0
                } else if(call.name.endsWith("read_u16") || call.name.endsWith(".read")) {
                    val target=values[call.name.substringBefore('.')] ?: error("Unknown ADC '${call.name}'.")
                    board.settle(extra=extraDrives())
                    val raw=board.analog(board.resolve(target))
                    if(call.name.endsWith("read_u16")) raw*64.0 else raw.toDouble()
                } else error("Unsupported API ${call.name}.")
            }
        }
    }
    private fun extraDrives(at:Long=timeMicros):List<com.indianservers.circuitssimulator.simulation.ExternalDrive> {
        val signals=fabric.waveforms.drives(at)
        val refs=signals.map { it.terminal }.toSet()
        return (peripherals.drives(board.circuit,board.latest,at)+fabric.uartIdleDrives(board.circuit,board.latest))
            .filter { it.terminal !in refs }+signals
    }
    private data class PythonPin(val name:String)
    private data class PythonAdc(val name:String)
    private data class PythonPwm(val name:String,var frequency:Double=1000.0,var duty:Int=0)
    private data class PythonBus(val type:String,val id:String)
    private fun pythonCall(call:Expression.Call):Any? {
        val positional=call.args.filterNot { it is Expression.Call && it.name.startsWith("__kw_") }.map(::eval)
        val keywords=call.args.filterIsInstance<Expression.Call>().filter { it.name.startsWith("__kw_") }
            .associate { it.name.removePrefix("__kw_") to eval(it.args.single()) }
        fun arg(index:Int,key:String,default:Any?=null)=keywords[key] ?: positional.getOrNull(index) ?: default
        fun pinName(value:Any?,analog:Boolean=false)=if(value is PythonPin) value.name else board.resolve(value,analog)
        fun bytes(value:Any?):List<Int> = when(value) {
            is String -> value.map { it.code and 255 }
            is List<*> -> value.map { number(it).toInt() and 255 }
            else -> error("Expected bytes, string or byte list.")
        }
        fun snapshot() { fabric.electricalResult=board.settle(extra=extraDrives()).circuitResult }
        fun sleep(value:Any?,scale:Double):Double {
            val micros=number(value)*scale
            require(micros.isFinite() && micros in 0.0..60_000_000.0) { "Sleep must be 0–60 seconds." }
            wakeAt=timeMicros+micros.toLong();return 0.0
        }
        val name=call.name
        return when(name) {
            "__bytes","bytes","bytearray" -> if(positional.size==1 && positional[0] is List<*>) bytes(positional[0])
                else positional.map { number(it).toInt() and 255 }
            "__index" -> (positional[0] as? List<*>)?.get(number(positional[1]).toInt()) ?: error("Index requires a byte list.")
            "len" -> when(val value=positional.single()) { is List<*> -> value.size.toDouble();is String -> value.length.toDouble();else -> error("len requires bytes or text.") }
            "Pin" -> {
                val pin=pinName(arg(0,"id"))
                val mode=number(arg(1,"mode",0.0)).toInt()
                val pull=arg(2,"pull")
                board.mode(pin,when { mode==1 -> PinMode.OUTPUT;pull==2.0 -> PinMode.INPUT_PULLUP
                    pull==3.0 -> PinMode.INPUT_PULLDOWN;else -> PinMode.INPUT })
                keywords["value"]?.let { board.write(pin,number(it)!=0.0) }
                snapshot();PythonPin(pin)
            }
            "ADC" -> {
                val requested=arg(0,"id")
                val channel=(requested as? Number)?.toInt()
                val pin=if(board.definition.family in setOf(com.indianservers.circuitssimulator.domain.BoardFamily.RP2040,
                    com.indianservers.circuitssimulator.domain.BoardFamily.RP2350) && channel in 0..2) board.resolve(26+channel!!,true)
                    else pinName(requested,true)
                require(PinCapability.ANALOG_INPUT in board.definition.pin(pin)!!.capabilities) { "$pin has no ADC." }
                PythonAdc(pin)
            }
            "PWM" -> {
                val handle=PythonPwm(pinName(arg(0,"pin")),number(arg(1,"freq",1000.0)),number(arg(2,"duty_u16",0.0)).toInt())
                require(handle.duty in 0..65535) { "PWM duty_u16 must be 0–65535." }
                board.pwmExact(handle.name,handle.duty/65535.0,handle.frequency);snapshot();handle
            }
            "sleep","time.sleep" -> sleep(positional.single(),1_000_000.0)
            "sleep_ms","time.sleep_ms" -> sleep(positional.single(),1000.0)
            "sleep_us","time.sleep_us" -> sleep(positional.single(),1.0)
            "ticks_ms","time.ticks_ms" -> (timeMicros-bootAtMicros)/1000.0
            "ticks_us","time.ticks_us" -> (timeMicros-bootAtMicros).toDouble()
            "print" -> { line(positional.joinToString(" ") { if(it is Double && it%1==0.0) it.toLong().toString() else it.toString() });0.0 }
            "I2C" -> {
                val defaults=board.definition.hardware.i2cPins ?: error("No I2C mapping.")
                val sda=pinName(keywords["sda"] ?: defaults.first);val scl=pinName(keywords["scl"] ?: defaults.second)
                board.requireI2cMapping(sda,scl)
                board.claim("I2C",listOf(sda,scl));board.mode(sda,PinMode.INPUT_PULLUP);board.mode(scl,PinMode.INPUT_PULLUP)
                fabric.i2cRoutes[board.boardId]=sda to scl;fabric.rebuildDevices(board.circuit);snapshot()
                PythonBus("I2C",board.boardId)
            }
            "UART" -> {
                val id=number(arg(0,"id",0.0)).toInt()
                val profile=board.definition.hardware
                val physicalIndex=if(profile.uartControllerIds.isEmpty()) id else profile.uartControllerIds.indexOf(id)
                val port=board.serialPorts().filter { it.txPin.isNotBlank() }.getOrNull(physicalIndex) ?: error("UART$id is unavailable.")
                val tx=pinName(keywords["tx"] ?: port.txPin);val rx=if(port.rxPin.isBlank()) "" else pinName(keywords["rx"] ?: port.rxPin)
                require(tx==port.txPin && rx==port.rxPin) { "UART$id currently requires ${port.txPin}/${port.rxPin}." }
                val baud=number(arg(1,"baudrate",9600.0)).toInt()
                require(baud in 300..2_000_000) { "Unsupported UART baud." }
                val bits=number(keywords["bits"] ?: 8).toInt();val stops=number(keywords["stop"] ?: 1).toInt()
                val parity=if(keywords["parity"]==null)0 else number(keywords["parity"]).toInt()+1
                require(bits in 5..8 && stops in 1..2 && parity in 0..2) { "Unsupported UART framing." }
                val endpoint="${board.boardId}:UART$id"
                board.claim("UART$id",listOf(tx,rx).filter { it.isNotBlank() });board.mode(tx,PinMode.INPUT)
                if(rx.isNotBlank()) board.mode(rx,PinMode.INPUT)
                fabric.uart[endpoint]=UartEndpoint(board.boardId,
                    com.indianservers.circuitssimulator.domain.TerminalRef(board.boardId,board.definition.index(tx)),
                    rx.takeIf { it.isNotBlank() }?.let { com.indianservers.circuitssimulator.domain.TerminalRef(board.boardId,board.definition.index(it)) },baud,
                    bits,parity,stops)
                snapshot();PythonBus("UART",endpoint)
            }
            "SPI" -> {
                require(keywords.keys.all { it in setOf("baudrate","polarity","phase","firstbit") }) { "Custom SPI pins are currently unsupported; use the board default pin mapping." }
                require(board.definition.hardware.spiPins.size==4) { "No SPI mapping." }
                val mode=number(keywords["polarity"] ?: 0).toInt()*2+number(keywords["phase"] ?: 0).toInt()
                require(mode in 0..3)
                fabric.spiConfigurations[board.boardId]=SpiConfiguration(number(keywords["baudrate"] ?: 1000000).toInt(),mode,number(keywords["firstbit"] ?: 0)==0.0)
                board.claim("SPI",board.definition.hardware.spiPins.take(3));fabric.rebuildDevices(board.circuit);PythonBus("SPI",board.boardId)
            }
            else -> {
                val target=values[name.substringBefore('.')] ?: error("Unknown hardware object $name.")
                val method=name.substringAfter('.')
                when(target) {
                    is PythonPin -> when(method) {
                        "value" -> if(positional.isEmpty()) {
                            snapshot();when(board.digital(target.name)) { LogicState.HIGH -> 1.0;LogicState.LOW -> 0.0;else -> error("Undefined digital input.") }
                        } else { board.write(target.name,number(positional.single())!=0.0);snapshot();0.0 }
                        "on","off","toggle" -> {
                            val high=when(method) { "on" -> true;"off" -> false;else -> { snapshot();board.digital(target.name)!=LogicState.HIGH } }
                            board.write(target.name,high);snapshot();0.0
                        }
                        else -> error("Unsupported Pin method $method.")
                    }
                    is PythonAdc -> {
                        snapshot();val raw=board.analog(target.name)
                        if(method=="read_u16") (raw.toDouble()/((1 shl board.definition.adcBits)-1)*65535).roundToInt().toDouble()
                        else if(method=="read") raw.toDouble() else error("Unsupported ADC method.")
                    }
                    is PythonPwm -> {
                        when(method) {
                            "freq" -> if(positional.isEmpty()) return target.frequency else target.frequency=number(positional.single())
                            "duty_u16" -> if(positional.isEmpty()) return target.duty.toDouble() else {
                                target.duty=number(positional.single()).toInt();require(target.duty in 0..65535) { "PWM duty_u16 must be 0–65535." }
                            }
                            "deinit" -> { board.write(target.name,false);snapshot();return 0.0 }
                            else -> error("Unsupported PWM method.")
                        }
                        board.pwmExact(target.name,target.duty/65535.0,target.frequency);snapshot();0.0
                    }
                    is PythonBus -> {
                        snapshot()
                        when(target.type) {
                            "I2C" -> when(method) {
                                "scan" -> fabric.i2cScan(board.circuit,board.boardId).map { it.toDouble() }
                                "writeto" -> {
                                    val data=bytes(positional[1])
                                    require(fabric.i2cWrite(board.circuit,board.boardId,number(positional[0]).toInt(),data,timeMicros)==0) { "I2C NACK: check SDA, SCL, power and ground." };data.size.toDouble()
                                }
                                "readfrom" -> {
                                    val data=fabric.i2cRead(board.circuit,board.boardId,number(positional[0]).toInt(),
                                        number(positional[1]).toInt().also { require(it in 1..32) },environment,timeMicros)
                                    require(data.isNotEmpty()) { "I2C NACK: check SDA, SCL, power and ground." };data.map { it.toDouble() }
                                }
                                else -> error("Unsupported I2C method.")
                            }
                            "UART" -> {
                                val endpoint=fabric.uart.getValue(target.id)
                                when(method) {
                                    "write" -> { val data=bytes(positional.single());data.forEach { endpoint.txBytes.addLast(it) };data.size.toDouble() }
                                    "any" -> endpoint.available().toDouble()
                                    "read" -> {
                                        val count=if(positional.isEmpty()) endpoint.available() else number(positional[0]).toInt().coerceIn(0,1024)
                                        (0 until minOf(count,endpoint.available())).map { endpoint.read().toDouble() }
                                    }
                                    else -> error("Unsupported UART method.")
                                }
                            }
                            "SPI" -> {
                                val data=if(method=="write") bytes(positional.single()) else List(number(positional.single()).toInt().also { require(it in 1..32) }) { 255 }
                                var byteTime=timeMicros
                                val read=data.map {
                                    val received=fabric.spiTransfer(board.circuit,board.boardId,it,fabric.selectedSpiDevices(board.latest),byteTime).toDouble()
                                    byteTime=fabric.completionTimes[board.boardId] ?: byteTime
                                    received
                                }
                                if(method=="read") read else data.size.toDouble()
                            }
                            else -> error("Unsupported bus.")
                        }
                    }
                    else -> error("Unknown hardware object $name.")
                }
            }
        }
    }
    private fun capture():FirmwareFrame {
        val step=board.settle(extra=extraDrives())
        fabric.observe(board.circuit,step.circuitResult,timeMicros)
        if(step.circuitResult.error!=null) error(step.circuitResult.error!!)
        peripherals.stepServos(board.circuit,step.circuitResult,0.0,board.pwmPins())
        return FirmwareFrame(timeMicros,step.circuitResult,step.pins)
    }
    private fun nextPwmEdge(limit:Long):Long {
        fabric.waveforms.drives(timeMicros)
        var edge=fabric.waveforms.next(timeMicros,if(interrupts.isEmpty()) limit else minOf(limit,timeMicros+100))
        board.nextDigitalEventMicros(limit)?.takeIf { it>timeMicros && it<=limit }?.let { edge=minOf(edge,it) }
        board.pwmPins().values.forEach { (duty,frequency) ->
            if(duty<=0.0 || duty>=1.0) return@forEach
            val period=1_000_000.0/frequency
            val phase=timeMicros%period
            val until=if(phase<duty*period) duty*period-phase else period-phase
            val candidate=timeMicros+ceil(until).toLong().coerceAtLeast(1)
            if(candidate<edge) edge=candidate
        }
        return edge
    }
    private fun advanceElectrical(target:Long,frames:MutableList<FirmwareFrame>) {
        if(board.latest==null) frames+=capture()
        while(timeMicros<target) {
            if(frames.size>=5000) error("Waveform frame limit reached; reduce runtime speed or PWM frequency.")
            val previousTime=timeMicros
            val next=nextPwmEdge(target).coerceAtLeast(timeMicros+1)
            val step=board.settle(next-timeMicros,extraDrives(next))
            timeMicros=next
            if(step.circuitResult.error!=null) error(step.circuitResult.error!!)
            frames+=FirmwareFrame(timeMicros,step.circuitResult,step.pins)
            fabric.observe(board.circuit,step.circuitResult,timeMicros)
            fabric.exchangeUart(board.circuit,timeMicros)
            peripherals.stepServos(board.circuit,step.circuitResult,(next-previousTime)/1_000_000.0,board.pwmPins())
            interrupts.forEach { (pin,interrupt) ->
                val level=board.digital(pin)
                val prior=interrupt.previous
                val changed=prior!=null && prior!=level && level!=LogicState.UNKNOWN
                val triggered=changed && (interrupt.edge=="CHANGE" || interrupt.edge=="RISING" && level==LogicState.HIGH ||
                    interrupt.edge=="FALLING" && level==LogicState.LOW)
                interrupt.previous=level
                if(triggered && interruptedWakeAt==null) {
                    interruptedWakeAt=wakeAt
                    pending.addFirst(Statement.Evaluate(Expression.Call("__resumeInterruptDelay",emptyList(),currentLine),currentLine))
                    enqueue(program.functions.getValue(interrupt.function));wakeAt=timeMicros
                }
            }
            if(interruptedWakeAt!=null && wakeAt==timeMicros) return
        }
    }
    fun advance(deltaMicros:Long):FirmwareAdvance {
        require(deltaMicros in 0..10_000_000) { "Advance interval must be 0–10 seconds." }
        val frames=mutableListOf<FirmwareFrame>()
        if(stopped) return FirmwareAdvance(emptyList(),consoleLines(),timeMicros=timeMicros,
            currentLine=currentLine,variables=variables())
        val target=timeMicros+deltaMicros
        var operations=0
        var diagnostic:FirmwareDiagnostic?=null
        try {
            if(board.latest==null) frames+=capture()
            while(timeMicros<=target && operations++<10000) {
                if(wakeAt>timeMicros) {
                    advanceElectrical(minOf(target,wakeAt),frames)
                    if(timeMicros<wakeAt) break
                }
                if(pending.isEmpty()) {
                    if(!setupDone) { setupDone=true;enqueue(program.loop) }
                    else enqueue(program.loop)
                    if(pending.isEmpty()) error("loop() is empty; add executable statements and delay().")
                }
                val waiting=pulseWait
                if(waiting!=null) {
                    val level=board.digital(waiting.pin)
                    val high=level==LogicState.HIGH
                    if(waiting.phase==0 && high==waiting.high) { waiting.phase=1;waiting.startedAt=timeMicros }
                    else if(waiting.phase==1 && high!=waiting.high) {
                        values["_pulseIn"]=(timeMicros-waiting.startedAt).toDouble()
                        pulseWait=null
                    } else if(timeMicros>=waiting.timeoutAt) {
                        values["_pulseIn"]=0.0;pulseWait=null
                    } else { wakeAt=timeMicros+20;advanceElectrical(minOf(target,wakeAt),frames);continue }
                }
                val stmt=pending.removeFirst();currentLine=stmt.line
                when(stmt) {
                    is Statement.Block -> enqueue(stmt.body)
                    is Statement.Assign -> values[stmt.name]=eval(stmt.value)
                    is Statement.Evaluate -> eval(stmt.call)
                    is Statement.Branch -> enqueue(statements(if(truth(eval(stmt.condition))) stmt.yes
                        else stmt.no ?: Statement.Block(emptyList(),stmt.line)))
                    is Statement.While -> if(truth(eval(stmt.condition))) {
                        pending.addFirst(stmt);enqueue(statements(stmt.body))
                    }
                    is Statement.For -> {
                        stmt.init?.let { init ->
                            when(init) {
                                is Statement.Assign -> values[init.name]=eval(init.value)
                                is Statement.Evaluate -> eval(init.call)
                                else -> enqueue(statements(init))
                            }
                        }
                        pending.addFirst(Statement.While(stmt.condition,Statement.Block(
                            statements(stmt.body)+listOfNotNull(stmt.step),stmt.line),stmt.line))
                    }
                }
                fabric.exchangeUart(board.circuit,timeMicros)
                if(program.microPython || stmt is Statement.Evaluate && ArduinoSubset.canonicalApi(stmt.call.name) in
                    setOf("pinMode","digitalWrite","analogWrite","tone","Servo.write","Servo.attach","Serial.print","Serial.println","Serial.write","SPI.transfer","Wire.endTransmission","Wire.requestFrom")) {
                    require(frames.size<5000) { "Waveform frame limit reached; add delay or reduce runtime speed." }
                    frames+=capture()
                }
                fabric.exchangeUart(board.circuit,timeMicros)
                wakeAt=maxOf(wakeAt,fabric.completionTimes[board.boardId] ?: timeMicros)
                if(timeMicros==target && wakeAt>target) break
            }
            if(operations>=10000) error("Execution halted: program exceeded runtime instruction budget.")
        } catch(e:Exception) {
            stopped=true
            diagnostic=FirmwareDiagnostic(currentLine,e.message ?: "Firmware execution failed")
        }
        return FirmwareAdvance(frames,consoleLines(),diagnostic,timeMicros,currentLine,variables())
    }
}
