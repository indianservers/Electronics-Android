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
                      var environment:EnvironmentState=EnvironmentState(),
                      val peripherals:PeripheralRuntime=PeripheralRuntime()) {
    private val pending=ArrayDeque<Statement>()
    private val values=mutableMapOf<String,Any?>()
    private val console=ArrayDeque<String>()
    private var setupDone=false
    private var wakeAt=0L
    private var stopped=false
    private var serialBaud:Int?=null
    private val serialLine=StringBuilder()
    private var i2cTx:MutableList<Int>?=null
    private var i2cRx=ArrayDeque<Int>()
    private var i2cAddr=0
    private val servoPins=mutableMapOf<String,String>()
    private var pulseWait:PulseWait?=null
    var timeMicros=0L
        private set
    var currentLine=0
        private set
    private data class PulseWait(val pin:String,val high:Boolean,val timeoutAt:Long,var phase:Int,var startedAt:Long)

    init { enqueue(program.setup) }
    fun reset() {
        pending.clear();values.clear();console.clear();board.reset()
        setupDone=false;wakeAt=0;stopped=false;serialBaud=null;serialLine.clear();timeMicros=0;currentLine=0
        i2cTx=null;i2cRx.clear();servoPins.clear();pulseWait=null
        fabric.uart[board.boardId]?.rxBytes?.clear()
        enqueue(program.setup)
    }
    fun stop() { stopped=true;board.reset() }
    fun consoleLines()=console.toList()
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
            "HIGH","OUTPUT","true" -> 1.0
            "LOW","INPUT","false" -> 0.0
            "INPUT_PULLUP" -> 2.0
            "LED_BUILTIN" -> board.definition.ledBuiltin ?: error("This board has no LED_BUILTIN.")
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
        val port=fabric.uart.getOrPut(board.boardId) { UartEndpoint(board.boardId,null,null) }
        text.forEach { port.rxBytes.addLast(it.code and 0xFF) }
    }
    private fun apiName(name:String)=ArduinoSubset.canonicalApi(name)
    private fun call(call:Expression.Call):Any? {
        val args=call.args
        val api=apiName(call.name)
        return when(api) {
            "pinMode" -> {
                val mode=when(number(eval(args[1])).toInt()) {
                    0 -> PinMode.INPUT;1 -> PinMode.OUTPUT;2 -> PinMode.INPUT_PULLUP
                    else -> error("pinMode accepts INPUT, OUTPUT, or INPUT_PULLUP.")
                }
                val resolved=pin(args[0])
                board.mode(resolved,mode);board.settle();resolved
            }
            "digitalWrite" -> {
                board.write(pin(args[0]),number(eval(args[1]))!=0.0);board.settle();0.0
            }
            "digitalRead" -> {
                board.settle()
                when(board.digital(pin(args[0]))) { LogicState.HIGH -> 1.0;LogicState.LOW -> 0.0;
                    else -> error("Digital input is undefined between LOW and HIGH thresholds.") }
            }
            "analogRead" -> { board.settle();board.analog(pin(args[0],analog=true)).toDouble() }
            "analogWrite" -> {
                board.pwm(pin(args[0]),number(eval(args[1])).toInt());board.settle();0.0
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
            "millis" -> timeMicros/1000.0
            "micros" -> timeMicros.toDouble()
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
                board.pwmExact(pin(args[0]),0.5,frequency);board.settle();0.0
            }
            "noTone" -> { board.write(pin(args[0]),false);board.settle();0.0 }
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
                serialBaud=baud
                val ports=board.serialPorts().firstOrNull()
                if(ports!=null) fabric.attachUart(board.boardId,
                    com.indianservers.circuitssimulator.domain.TerminalRef(board.boardId,board.definition.index(ports.txPin)),
                    com.indianservers.circuitssimulator.domain.TerminalRef(board.boardId,board.definition.index(ports.rxPin)),baud)
                0.0
            }
            "Serial.print","Serial.println","Serial.write" -> {
                require(serialBaud!=null) { "Call Serial.begin before printing." }
                val value=if(args.isEmpty()) "" else eval(args[0])
                val rendered=when {
                    api=="Serial.write" && value is Number -> (value.toInt() and 0xFF).toChar().toString()
                    value is Double && value%1.0==0.0 -> value.toLong().toString()
                    else -> value.toString()
                }
                if(api=="Serial.println") {
                    line(serialLine.append(rendered).toString());serialLine.clear()
                } else serialLine.append(rendered)
                fabric.uart[board.boardId]?.enqueueTx(if(api=="Serial.println") rendered+"\n" else rendered)
                0.0
            }
            "Serial.available" -> fabric.uart[board.boardId]?.available()?.toDouble() ?: 0.0
            "Serial.read" -> fabric.uart[board.boardId]?.read()?.toDouble() ?: -1.0
            "Wire.begin" -> {
                val sda=board.definition.pins.firstOrNull { PinCapability.I2C in it.capabilities && it.name in setOf("SDA","A4","D20","D2") }
                val scl=board.definition.pins.firstOrNull { PinCapability.I2C in it.capabilities && it.name in setOf("SCL","A5","D21","D1") }
                if(sda!=null) board.mode(sda.name,PinMode.INPUT_PULLUP)
                if(scl!=null) board.mode(scl.name,PinMode.INPUT_PULLUP)
                fabric.rebuildDevices(board.circuit);0.0
            }
            "Wire.beginTransmission" -> { i2cAddr=number(eval(args[0])).toInt();i2cTx=mutableListOf();0.0 }
            "Wire.write" -> { require(i2cTx!=null) { "Call Wire.beginTransmission first." }
                i2cTx!!+=number(eval(args[0])).toInt() and 0xFF;0.0 }
            "Wire.endTransmission" -> {
                val bytes=i2cTx ?: emptyList();i2cTx=null
                fabric.i2cWrite(board.circuit,board.boardId,i2cAddr,bytes,timeMicros).toDouble()
            }
            "Wire.requestFrom" -> {
                val address=number(eval(args[0])).toInt();val count=number(eval(args[1])).toInt().coerceIn(1,32)
                i2cRx.clear()
                fabric.i2cRead(board.circuit,board.boardId,address,count,environment,timeMicros).forEach { i2cRx.addLast(it) }
                i2cRx.size.toDouble()
            }
            "Wire.read" -> if(i2cRx.isEmpty()) -1.0 else i2cRx.removeFirst().toDouble()
            "Wire.available" -> i2cRx.size.toDouble()
            "Wire.scan" -> {
                val found=fabric.i2cScan(environment)
                found.forEach { line("Found device at 0x${it.toString(16)}") }
                found.size.toDouble()
            }
            "SPI.begin" -> 0.0
            "SPI.transfer" -> {
                board.settle()
                val byte=number(eval(args[0])).toInt() and 0xFF
                val selected=fabric.selectedSpiDevices(board.latest)
                fabric.spiTransfer(board.circuit,board.boardId,byte,selected,timeMicros).toDouble()
            }
            "Servo.attach" -> {
                val instance=call.name.substringBefore('.',"servo")
                val resolved=pin(args[0])
                servoPins[instance]=resolved
                board.pwmExact(resolved,0.075,50.0);board.settle();0.0
            }
            "Servo.write" -> {
                val instance=call.name.substringBefore('.',"servo")
                val resolved=servoPins[instance] ?: error("Call Servo.attach before write.")
                val angle=number(eval(args[0])).coerceIn(0.0,180.0)
                values["${instance}.angle"]=angle
                val pulseUs=1000.0+angle/180.0*1000.0
                board.pwmExact(resolved,pulseUs/20000.0,50.0);board.settle();angle
            }
            "Servo.read" -> values["${call.name.substringBefore('.',"servo")}.angle"] ?: 0.0
            else -> {
                if(call.name.endsWith(".value")) {
                    val target=values[call.name.substringBefore('.')] ?: error("Unknown Pin '${call.name}'.")
                    if(args.isEmpty()) {
                        board.settle()
                        return when(board.digital(board.resolve(target))) {
                            LogicState.HIGH -> 1.0;LogicState.LOW -> 0.0
                            else -> error("Digital input is undefined between LOW and HIGH thresholds.")
                        }
                    }
                    board.write(board.resolve(target),number(eval(args[0]))!=0.0);board.settle();0.0
                } else if(call.name.endsWith("read_u16") || call.name.endsWith(".read")) {
                    val target=values[call.name.substringBefore('.')] ?: error("Unknown ADC '${call.name}'.")
                    board.settle()
                    val raw=board.analog(board.resolve(target))
                    if(call.name.endsWith("read_u16")) raw*64.0 else raw.toDouble()
                } else error("Unsupported API ${call.name}.")
            }
        }
    }
    private fun extraDrives()=peripherals.drives(board.circuit,board.latest,timeMicros)
    private fun capture():FirmwareFrame {
        val step=board.settle(extra=extraDrives())
        if(step.circuitResult.error!=null) error(step.circuitResult.error!!)
        peripherals.stepServos(board.circuit,step.circuitResult,0.0,board.pwmPins())
        return FirmwareFrame(timeMicros,step.circuitResult,step.pins)
    }
    private fun nextPwmEdge(limit:Long):Long {
        var edge=limit
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
            if(frames.size>=2500) error("Waveform frame limit reached; reduce runtime speed or PWM frequency.")
            val next=nextPwmEdge(target).coerceAtLeast(timeMicros+1)
            val step=board.settle(next-timeMicros,extraDrives())
            timeMicros=next
            if(step.circuitResult.error!=null) error(step.circuitResult.error!!)
            frames+=FirmwareFrame(timeMicros,step.circuitResult,step.pins)
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
                if(stmt is Statement.Evaluate && ArduinoSubset.canonicalApi(stmt.call.name) in
                    setOf("pinMode","digitalWrite","analogWrite","tone","Servo.write","Servo.attach"))
                    frames+=capture()
                fabric.exchangeUart(board.circuit,timeMicros)
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
