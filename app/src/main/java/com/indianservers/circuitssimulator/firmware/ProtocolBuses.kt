package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.intelligence.CircuitFacts
import java.util.ArrayDeque
import com.indianservers.circuitssimulator.simulation.DcResult
import kotlin.math.roundToInt

enum class BusKind { UART, I2C, SPI, USB, CAN }
data class SpiConfiguration(val frequencyHz:Int=1000000,val mode:Int=0,val msbFirst:Boolean=true)
data class BusEvent(val timeMicros:Long,val kind:BusKind,val summary:String,val ack:Boolean?=null)

class UartEndpoint(val ownerId:String,val tx:TerminalRef?,val rx:TerminalRef?,val baud:Int=9600,
    val dataBits:Int=8,val parity:Int=0,val stopBits:Int=1,val logicVoltage:Double?=null) {
    val rxBytes=ArrayDeque<Int>()
    val txBytes=ArrayDeque<Int>()
    fun enqueueTx(text:String) { text.forEach { txBytes.addLast(it.code and 0xFF) } }
    fun read():Int=if(rxBytes.isEmpty()) -1 else rxBytes.removeFirst()
    fun available()=rxBytes.size
}

/** Event-based UART/I²C/SPI. Bytes move when nets match; physical bit timing is not modeled. */
class ProtocolFabric {
    val log=ArrayDeque<BusEvent>()
    val uart=mutableMapOf<String,UartEndpoint>()
    val i2cDevices=mutableListOf<I2cDeviceModel>()
    val spiDevices=mutableListOf<SpiDeviceModel>()
    var electricalResult:DcResult?=null
    val i2cRoutes=mutableMapOf<String,Pair<String,String>>()
    val spiConfigurations=mutableMapOf<String,SpiConfiguration>()
    var currentTime=0L
    val waveforms=BusWaveforms()
    private val consumers=mutableMapOf<String,Long>()
    fun registerConsumer(id:String,time:Long=0){consumers[id]=time}
    fun consumerAdvanced(id:String,time:Long){consumers[id]=time;waveforms.discardBefore(consumers.values.minOrNull() ?: time)}
    fun unregisterConsumer(id:String){consumers.remove(id)}
    val completionTimes=mutableMapOf<String,Long>()
    private val i2cHeld=mutableSetOf<String>()
    private data class Delivery(val time:Long,val target:UartEndpoint,val byte:Int)
    private val deliveries=java.util.PriorityQueue(compareBy<Delivery>{it.time})
    private val uartBusyUntil=mutableMapOf<String,Long>()
    fun uartIdleDrives(c:Circuit,result:DcResult?)=uart.values.mapNotNull { endpoint ->
        endpoint.tx?.let { pin ->
            val def=board(c,endpoint.ownerId)
            val groundIndex=def?.pins?.indexOfFirst { it.type==PinType.GROUND } ?: 2
            val ground=result?.nodeVoltages?.get(TerminalRef(endpoint.ownerId,groundIndex)) ?: 0.0
            com.indianservers.circuitssimulator.simulation.ExternalDrive(pin,ground+(endpoint.logicVoltage ?: def?.logicVoltage ?: 5.0),100.0)
        }
    }
    private fun i2cWave(c:Circuit,id:String,address:Int,data:List<Int>,read:Boolean,ack:Boolean,time:Long,stop:Boolean=true):Long {
        val def=board(c,id) ?: return time
        val route=i2cRoutes[id] ?: def.hardware.i2cPins ?: return time
        val sda=TerminalRef(id,def.index(route.first));val scl=TerminalRef(id,def.index(route.second))
        val g=electricalResult?.nodeVoltages?.get(TerminalRef(id,def.pins.indexOfFirst { it.type==PinType.GROUND })) ?: 0.0
        var t=maxOf(time,completionTimes[id] ?: time)
        fun line(pin:TerminalRef,low:Boolean) { waveforms.add(t,"I2C:$id:${pin.index}",pin,if(low)g else null) }
        record(t,BusKind.I2C,if(id in i2cHeld)"REPEATED START" else "START")
        line(scl,false);line(sda,false);t+=5;line(sda,true);t+=5
        val bytes=listOf((address shl 1) or if(read)1 else 0)+data
        bytes.forEachIndexed { index,byte ->
            for(bit in 7 downTo 0) {
                line(scl,true);line(sda,byte and (1 shl bit)==0);t+=5;line(scl,false);t+=5
            }
            line(scl,true);line(sda,if(read && index>0) index<bytes.lastIndex else ack);t+=5
            line(scl,false);t+=5;line(scl,true);line(sda,false)
            record(t,BusKind.I2C,if(index==0) "ADDR 0x${address.toString(16)} ${if(read)"R" else "W"} ${if(ack)"ACK" else "NACK"}"
                else "DATA 0x${byte.toString(16)} ${if(ack)"ACK" else "NACK"}",ack)
        }
        if(stop) { line(sda,true);t+=5;line(scl,false);t+=5;line(sda,false);record(t,BusKind.I2C,"STOP");i2cHeld.remove(id) }
        else i2cHeld.add(id)
        completionTimes[id]=t
        return t
    }
    fun observe(circuit:Circuit,result:DcResult?,time:Long) {
        electricalResult=result;currentTime=time
        i2cDevices.forEach { device ->
            val part=circuit.components.firstOrNull { it.id==device.ownerId }
            device.address=part?.value("address")?.toInt() ?: device.address
            device.writeProtected=part?.value("writeProtect")==1.0
            device.localTemperature=part?.takeIf { it.kind==Kind.I2C_TEMP_SENSOR && it.value("useEnvironment")==0.0 }?.value("temperatureC")
            device.motion?.stimuli=part?.parameters.orEmpty().let { values ->
                if((values["vibrationAmplitudeG"] ?: 0.0)==0.0)values else values+("accelXG" to
                    ((values["accelXG"] ?: 0.0)+(values["vibrationAmplitudeG"] ?: 0.0)*
                        kotlin.math.sin(2*Math.PI*(values["vibrationFrequencyHz"] ?: 2.0)*time/1_000_000.0)))
            }
            device.chemistry?.stimuli=part?.parameters.orEmpty()
            if(device.powered)device.gas?.advance(time)
            device.gas?.stimuli=part?.parameters.orEmpty()
            if(device.powered)device.light?.advance(time)
            device.light?.lux=part?.value("illuminanceLux") ?: 300.0
            device.powerMonitor?.let { monitor ->
                if(device.powered)monitor.advance(time)
                val ground=result?.nodeVoltages?.get(TerminalRef(device.ownerId,1)) ?: 0.0
                monitor.positive=(result?.nodeVoltages?.get(TerminalRef(device.ownerId,4)) ?: Double.NaN)-ground
                monitor.negative=(result?.nodeVoltages?.get(TerminalRef(device.ownerId,5)) ?: Double.NaN)-ground
            }
            device.powerChanged(powered(circuit,device.ownerId),time)
        }
        spiDevices.forEach { device ->
            val power=powered(circuit,device.ownerId)
            device.powerChanged(power,time)
            val low=(result?.nodeVoltages?.get(device.cs) ?: 10.0)-
                (result?.nodeVoltages?.get(TerminalRef(device.ownerId,1)) ?: 0.0)<.8
            device.select(power && low && circuit.wires.any { it.start==device.cs || it.end==device.cs },time)
        }
        routeCan(circuit,result,time)
    }
    private fun routeCan(c:Circuit,result:DcResult?,time:Long) {
        if(result==null || result.error!=null)return
        val due=spiDevices.filter { it.powered && it.can?.pending?.due?.let {deadline->time>=deadline}==true }
        if(due.isEmpty())return
        val facts=CircuitFacts(c,null)
        fun ref(d:SpiDeviceModel,pin:Int)=TerminalRef(d.ownerId,pin)
        fun phy(d:SpiDeviceModel):Boolean {
            val supply=(result.nodeVoltages[ref(d,8)] ?: 0.0)-(result.nodeVoltages[ref(d,1)] ?: 0.0)
            return d.powered && supply in 4.5..5.5 && c.wires.any {it.start==ref(d,8) || it.end==ref(d,8)}
        }
        due.forEach {sender ->
            val model=sender.can!!;val frame=model.pending ?: return@forEach
            if(time<frame.due)return@forEach
            if(model.mode==0x40){model.receive(frame);model.complete(true);record(time,BusKind.CAN,"${sender.ownerId} loopback 0x${frame.id.toString(16)}",true);return@forEach}
            val h=ref(sender,6);val l=ref(sender,7)
            val conductance=c.components.filter {it.kind==Kind.RESISTOR && it.value("resistance")>0}.filter {
                (facts.same(TerminalRef(it.id,0),h) && facts.same(TerminalRef(it.id,1),l)) ||
                    (facts.same(TerminalRef(it.id,1),h) && facts.same(TerminalRef(it.id,0),l))
            }.sumOf {1.0/it.value("resistance")}
            val valid=phy(sender) && !facts.same(h,l) && conductance>0 && 1.0/conductance in 50.0..65.0
            val receivers=if(valid)spiDevices.filter {d->d!==sender && d.can?.mode==0 && phy(d) &&
                d.can.bitrate==model.bitrate && sameGround(c,sender.ownerId,d.ownerId,facts) &&
                facts.same(h,ref(d,6)) && facts.same(l,ref(d,7))} else emptyList()
            receivers.forEach {receiver ->
                if(receiver.can!!.registers[0x60] and 0x60==0x60)receiver.can.receive(frame)
            }
            model.complete(receivers.isNotEmpty())
            record(time,BusKind.CAN,"${sender.ownerId} ID 0x${frame.id.toString(16)} [${frame.data.joinToString()}] ${if(receivers.isEmpty())"NO ACK" else "ACK"}",receivers.isNotEmpty())
        }
    }
    var autoI2cPullups=true
        private set
    /** Release only this MCU's queued bus work; other controllers keep running. */
    fun resetController(id:String) {
        val bridgeId="USB_UART:$id"
        val endpoints=uart.values.filter { it.ownerId==id || it.ownerId==bridgeId }.toSet()
        deliveries.removeIf { it.target in endpoints }
        uart.entries.removeAll { it.value in endpoints }
        uartBusyUntil.remove(id)
        uartBusyUntil.remove(bridgeId)
        completionTimes.remove(id)
        i2cRoutes.remove(id)
        i2cHeld.remove(id)
        spiConfigurations.remove(id)
        waveforms.clearController(id)
        waveforms.clearController(bridgeId)
    }
    fun reset() {
        electricalResult=null
        consumers.clear()
        log.clear();uart.clear();i2cRoutes.clear();waveforms.clear();completionTimes.clear();i2cHeld.clear();spiConfigurations.clear();deliveries.clear();uartBusyUntil.clear()
        i2cDevices.forEach { it.powerChanged(false,0) }
        spiDevices.forEach { it.powerChanged(false,0) }
    }
    private fun record(time:Long,kind:BusKind,summary:String,ack:Boolean?=null) {
        if(log.size>=400) log.removeFirst()
        log.addLast(BusEvent(time,kind,summary,ack))
    }
    fun attachUart(id:String,tx:TerminalRef?,rx:TerminalRef?,baud:Int=9600) {
        uart[id]=UartEndpoint(id,tx,rx,baud)
    }
    fun rebuildDevices(circuit:Circuit) {
        val oldI2c=i2cDevices.associateBy { it.ownerId };val oldSpi=spiDevices.associateBy { it.ownerId }
        i2cDevices.clear();spiDevices.clear()
        circuit.components.forEach { part ->
            when(part.kind) {
                Kind.I2C_TEMP_SENSOR,Kind.I2C_LCD,Kind.OLED_SSD1306,Kind.I2C_EEPROM,
                Kind.INA219_POWER,Kind.SCD41_CO2,Kind.BH1750_LIGHT,Kind.EZO_PH,Kind.EZO_EC,Kind.EZO_DO,
        Kind.L3GD20_GYRO,Kind.LSM6DSOX_IMU,Kind.LIS3DH_ACCEL,Kind.ADXL345_ACCEL ->
                    i2cDevices+=oldI2c[part.id]?.takeIf { it.kind==part.kind }
                        ?: I2cDeviceModel(part.value("address").toInt(),part.id,part.kind)
                Kind.SPI_MEMORY,Kind.MCP2515_CAN -> spiDevices+=oldSpi[part.id]?.takeIf {it.kind==part.kind} ?: SpiDeviceModel(part.id,TerminalRef(part.id,5),kind=part.kind)
                // 74HC595 is GPIO clock/latch, not a CS-addressed SPI memory.
                else -> Unit
            }
        }
        i2cDevices.filter { it.kind==Kind.I2C_EEPROM && it.ownerId !in oldI2c }.forEach {
            it.memory.putAll(circuit.peripheralMemory[it.ownerId].orEmpty())
        }
        spiDevices.filter { it.ownerId !in oldSpi }.forEach { it.memory.putAll(circuit.peripheralMemory[it.ownerId].orEmpty()) }
    }
    fun persistentMemory():Map<String,Map<Int,Int>> = i2cDevices.filter { it.kind==Kind.I2C_EEPROM }
        .associate { it.ownerId to it.memory.toMap() }+spiDevices.filter {it.kind==Kind.SPI_MEMORY}.associate { it.ownerId to it.memory.toMap() }
    fun framebuffers()=i2cDevices.filter { it.kind==Kind.OLED_SSD1306 }.associate { it.ownerId to it.oled.pixels() }
    fun inspectors()=(i2cDevices+spiDevices).associate { it.ownerId to it.inspect(currentTime) }
    private fun board(c:Circuit,id:String)=c.components.firstOrNull { it.id==id && it.kind.isBoard }
        ?.let { BoardRegistry.boards.getValue(it.kind) }
    private fun sameGround(c:Circuit,a:String,b:String,facts:CircuitFacts=CircuitFacts(c,null)):Boolean {
        fun grounds(id:String):List<TerminalRef> {
            val part=c.components.firstOrNull { it.id==id } ?: return emptyList()
            return if(part.kind.isBoard) BoardRegistry.boards.getValue(part.kind).pins.mapIndexedNotNull { i,p ->
                if(p.type==PinType.GROUND) TerminalRef(id,i) else null }
            else PhaseFiveParts.pinNames[part.kind].orEmpty().mapIndexedNotNull { i,p ->
                if(p=="GND") TerminalRef(id,i) else null }
        }
        return grounds(a).any { x -> grounds(b).any { facts.same(x,it) } }
    }
    private fun powered(c:Circuit,id:String):Boolean {
        val part=c.components.firstOrNull { it.id==id } ?: return false
        val result=electricalResult ?: return false
        if(result.error!=null) return false
        if(part.kind.isBoard) {
            val def=BoardRegistry.boards.getValue(part.kind)
            val ground=def.pins.indexOfFirst { it.type==PinType.GROUND }
            val g=result.nodeVoltages[TerminalRef(id,ground)] ?: return false
            return def.supplyPins.any { name ->
                val v=(result.nodeVoltages[TerminalRef(id,def.index(name))] ?: 0.0)-g
                v in def.hardware.minimumLogicSupply..def.hardware.maximumLogicSupply
            }
        }
        val labels=PhaseFiveParts.pinNames[part.kind] ?: return false
        val power=labels.indexOfFirst { it=="VCC" || it=="V+" || it=="VDD" }
        val ground=labels.indexOf("GND")
        if(power<0 || ground<0) return false
        if(c.wires.none { it.start==TerminalRef(id,power) || it.end==TerminalRef(id,power) }) return false
        val volts=(result.nodeVoltages[TerminalRef(id,power)] ?: 0.0)-(result.nodeVoltages[TerminalRef(id,ground)] ?: 0.0)
        return volts in (EmbeddedProfiles.profiles[part.kind]?.electrical?.supplyRange ?: (2.5..5.5))
    }
    private fun connectedI2c(c:Circuit,id:String):List<I2cDeviceModel> {
        observe(c,electricalResult,currentTime)
        val def=board(c,id) ?: return emptyList()
        val route=i2cRoutes[id] ?: def.hardware.i2cPins ?: return emptyList()
        val facts=CircuitFacts(c,null)
        val sda=TerminalRef(id,def.index(route.first));val scl=TerminalRef(id,def.index(route.second))
        if(sda.index<0 || scl.index<0 || !powered(c,id) || !validPullup(c,sda,id) || !validPullup(c,scl,id)) {
            record(currentTime,BusKind.I2C,"I2C NACK: power or SDA/SCL pull-up HIGH invalid",false)
            return emptyList()
        }
        return i2cDevices.filter { device ->
            if(device.kind==Kind.BH1750_LIGHT && device.address !in listOf(35,92))return@filter false
            val kind=c.components.firstOrNull { it.id==device.ownerId }?.kind
            val labels=PhaseFiveParts.pinNames[kind].orEmpty()
            facts.same(sda,TerminalRef(device.ownerId,labels.indexOf("SDA"))) &&
                facts.same(scl,TerminalRef(device.ownerId,labels.indexOf("SCL"))) &&
                sameGround(c,id,device.ownerId,facts) && powered(c,device.ownerId) &&
                listOf(2,3).all { pin ->
                    val voltage=(electricalResult?.nodeVoltages?.get(TerminalRef(device.ownerId,pin)) ?: 0.0)-
                        (electricalResult?.nodeVoltages?.get(TerminalRef(device.ownerId,1)) ?: 0.0)
                    val supply=(electricalResult?.nodeVoltages?.get(TerminalRef(device.ownerId,0)) ?: 0.0)-
                        (electricalResult?.nodeVoltages?.get(TerminalRef(device.ownerId,1)) ?: 0.0)
                    if(device.kind in MotionSensorParts.profiles) voltage in -.3..minOf(3.6,supply+.3)
                    else voltage<=device.profile.electrical.inputMaximum+.15
                }
        }
    }
    private fun validPullup(c:Circuit,pin:TerminalRef,controllerId:String):Boolean {
        val result=electricalResult ?: return false
        val facts=CircuitFacts(c,null)
        val def=board(c,controllerId) ?: return false
        val g=result.nodeVoltages[TerminalRef(controllerId,def.pins.indexOfFirst { it.type==PinType.GROUND })] ?: 0.0
        val high=(result.nodeVoltages[pin] ?: 0.0)-g
        val module=c.components.any { p ->
            EmbeddedProfiles.profiles[p.kind]?.electrical?.modulePullupOhms!=null &&
                (p.kind!=Kind.I2C_TEMP_SENSOR || p.value("pullupsEnabled")>=.5) && powered(c,p.id) &&
                listOf(2,3).any { facts.same(pin,TerminalRef(p.id,it)) }
        }
        val resistor=c.components.any { p -> p.kind==Kind.RESISTOR && p.value("resistance") in 1000.0..100000.0 &&
            (0..1).any { end -> facts.same(pin,TerminalRef(p.id,end)) &&
                (result.nodeVoltages[TerminalRef(p.id,1-end)] ?: 0.0)-g >= def.logicVoltage*.7 } }
        return (module || resistor) && (high in def.logicVoltage*.6..def.logicVoltage+.15 || controllerId in i2cHeld)
    }
    fun hasI2cPullups(circuit:Circuit):Boolean {
        val facts=CircuitFacts(circuit,null)
        val sda=i2cNets(circuit,facts,"SDA")
        val scl=i2cNets(circuit,facts,"SCL")
        if(sda.isEmpty() && scl.isEmpty()) return true
        val supplies=circuit.components.filter { it.kind in setOf(Kind.SOURCE,Kind.BATTERY,Kind.VARIABLE_DC_SUPPLY) ||
            it.kind.isBoard }
        fun pulled(net:Set<TerminalRef>)=circuit.components.any { resistor ->
            resistor.kind==Kind.RESISTOR && resistor.value("resistance") in 1000.0..100000.0 &&
                (net.any { facts.same(it,TerminalRef(resistor.id,0)) } &&
                    supplies.any { supply -> (0 until supply.terminalCount).any { facts.same(TerminalRef(resistor.id,1),TerminalRef(supply.id,it)) } } ||
                    net.any { facts.same(it,TerminalRef(resistor.id,1)) } &&
                    supplies.any { supply -> (0 until supply.terminalCount).any { facts.same(TerminalRef(resistor.id,0),TerminalRef(supply.id,it)) } })
        }
        fun modulePull(net:Set<TerminalRef>)=circuit.components.any { p ->
            EmbeddedProfiles.profiles[p.kind]?.electrical?.modulePullupOhms!=null &&
                (p.kind!=Kind.I2C_TEMP_SENSOR || p.value("pullupsEnabled")>=.5) && (2..3).any { TerminalRef(p.id,it) in net }
        }
        val present=sda.all { pulled(it) || modulePull(it) } && scl.all { pulled(it) || modulePull(it) }
        autoI2cPullups=false
        return present
    }
    private fun i2cNets(circuit:Circuit,facts:CircuitFacts,name:String):List<Set<TerminalRef>> {
        val pins=circuit.components.flatMap { part ->
            val names=when {
                part.kind.isBoard -> BoardRegistry.boards.getValue(part.kind).pins.mapIndexed { i,pin ->
                    if(pin.name==name || (name=="SDA" && pin.name in setOf("A4","D20")) ||
                        (name=="SCL" && pin.name in setOf("A5","D21"))) TerminalRef(part.id,i) else null }
                part.kind in PhaseFiveParts.pinNames -> PhaseFiveParts.pinNames.getValue(part.kind).mapIndexed { i,pin ->
                    if(pin==name) TerminalRef(part.id,i) else null }
                else -> emptyList()
            }
            names.filterNotNull()
        }
        return pins.map { facts.onNet(it) }.distinctBy { net -> net.map { "${it.componentId}:${it.index}" }.sorted() }
    }
    fun terminalSend(circuit:Circuit,id:String,text:String) {
        val part=circuit.components.first { it.id==id && it.kind==Kind.SERIAL_TERMINAL }
        val endpoint=uart.getOrPut(id) { UartEndpoint(id,TerminalRef(id,0),TerminalRef(id,1),part.value("baud").toInt(),part.value("dataBits").toInt(),part.value("parity").toInt(),part.value("stopBits").toInt()) }
        require(endpoint.baud==part.value("baud").toInt()) { "Restart terminal after changing baud." }
        endpoint.enqueueTx(text)
        exchangeUart(circuit,currentTime)
    }
    fun clearTerminal(id:String) { terminalHistory[id]="";uart[id]?.rxBytes?.clear() }
    fun monitorSend(c:Circuit,id:String,port:String,baud:Int,text:String) {
        val endpointId=if(port=="Serial")id else "$id:$port"
        val target=uart[endpointId] ?: error("Initialize $port before sending.")
        require(target.baud==baud) { "Monitor baud does not match $port." }
        val part=c.components.first { it.id==id }
        require(part.value("usbPower")>=.5 || c.firmware.any { it.boardId==id && it.usbPower }) {
            "Connect USB power for the onboard monitor, or place and wire a serial terminal."
        }
        if(target.rx==null && target.tx==null) {
            text.forEach { deliveries+=Delivery(currentTime+1000,target,it.code and 255) }
            record(currentTime,BusKind.USB,"USB CDC host data queued",true)
        } else {
            require(port=="Serial") { "The USB bridge connects only to Serial; wire a terminal for $port." }
            val bridgeId="USB_UART:$id"
            val bridge=uart.getOrPut(bridgeId){UartEndpoint(bridgeId,target.rx,null,baud,logicVoltage=board(c,id)?.logicVoltage)}
            bridge.enqueueTx(text);exchangeUart(c,currentTime)
        }
    }
    fun exchangeUart(circuit:Circuit,timeMicros:Long) {
        while(deliveries.peek()?.time?.let { it<=timeMicros }==true) {
            val delivery=deliveries.remove()
            if(delivery.target.ownerId in terminalHistory) terminalHistory[delivery.target.ownerId]=
                (terminalHistory[delivery.target.ownerId].orEmpty()+delivery.byte.toChar()).takeLast(4096)
            else { if(delivery.target.rxBytes.size>=4096)delivery.target.rxBytes.removeFirst();delivery.target.rxBytes.addLast(delivery.byte) }
            record(delivery.time,BusKind.UART,"${delivery.target.ownerId.take(8)} RX 0x${delivery.byte.toString(16)}",true)
        }
        circuit.components.filter { it.kind==Kind.SERIAL_TERMINAL }.forEach { terminal ->
            val existing=uart[terminal.id]
            if(existing==null || existing.baud!=terminal.value("baud").toInt() || existing.dataBits!=terminal.value("dataBits").toInt() || existing.parity!=terminal.value("parity").toInt() || existing.stopBits!=terminal.value("stopBits").toInt())
                uart[terminal.id]=UartEndpoint(terminal.id,TerminalRef(terminal.id,0),TerminalRef(terminal.id,1),terminal.value("baud").toInt(),terminal.value("dataBits").toInt(),terminal.value("parity").toInt(),terminal.value("stopBits").toInt())
            terminalHistory.putIfAbsent(terminal.id,"")
        }
        uart.values.toList().forEach { source ->
            val tx=source.tx ?: return@forEach
            if(source.txBytes.isEmpty())return@forEach
            // Validate the HIGH level with the sender's own in-flight bit released.
            // Sampling a previous START/data LOW incorrectly dropped queued prints.
            // The probe retains physical loads/shorts and other bus drives.
            val highProbe by lazy {
                val signals=waveforms.drives(timeMicros).filter {it.terminal!=tx}
                val occupied=signals.map {it.terminal}.toSet()
                com.indianservers.circuitssimulator.simulation.DcSolver().solve(circuit,
                    signals+uartIdleDrives(circuit,electricalResult).filter {it.terminal !in occupied})
            }
            while(source.txBytes.isNotEmpty()) {
                val byte=source.txBytes.removeFirst() and ((1 shl source.dataBits)-1)
                val start=maxOf(timeMicros,uartBusyUntil[source.ownerId] ?: timeMicros)
                val bitPeriod=maxOf(1L,1_000_000L/source.baud)
                val frame=(listOf(0)+(0 until source.dataBits).map { bit -> (byte shr bit) and 1 }).toMutableList()
                if(source.parity!=0) frame.add((Integer.bitCount(byte) and 1) xor if(source.parity==2)1 else 0)
                repeat(source.stopBits){frame.add(1)}
                val def=board(circuit,source.ownerId)
                val ground=electricalResult?.nodeVoltages?.get(TerminalRef(source.ownerId,def?.pins?.indexOfFirst { it.type==PinType.GROUND } ?: 2)) ?: 0.0
                frame.forEachIndexed { bit,high -> waveforms.add(start+bit*bitPeriod,"UART:${source.ownerId}",tx,
                    ground+if(high==1)(source.logicVoltage ?: def?.logicVoltage ?: 5.0) else 0.0) }
                val finish=start+frame.size*bitPeriod
                waveforms.add(finish,"UART:${source.ownerId}",tx,null)
                uartBusyUntil[source.ownerId]=finish
                uart.values.toList().filter { it.ownerId!=source.ownerId && it.rx!=null && uartPath(circuit,tx,it.rx!!) }.forEach { target ->
                    val sourcePart=circuit.components.firstOrNull { it.id==source.ownerId }
                    val targetPart=circuit.components.firstOrNull { it.id==target.ownerId }
                    val nativeParts=sourcePart!=null && targetPart!=null
                    val targetGround=highProbe.nodeVoltages[TerminalRef(target.ownerId,
                        board(circuit,target.ownerId)?.pins?.indexOfFirst {it.type==PinType.GROUND} ?: 2)] ?: 0.0
                    val voltage=highProbe.nodeVoltages[target.rx]?.minus(targetGround)
                    val max=board(circuit,target.ownerId)?.logicVoltage ?: 5.0
                    val sourcePower=sourcePart?.kind==Kind.SERIAL_TERMINAL || powered(circuit,source.ownerId)
                    val targetPower=targetPart?.kind==Kind.SERIAL_TERMINAL || powered(circuit,target.ownerId)
                    val valid=source.baud==target.baud && source.dataBits==target.dataBits && source.parity==target.parity && source.stopBits==target.stopBits && (!nativeParts ||
                        sameGround(circuit,source.ownerId,target.ownerId) && sourcePower && targetPower &&
                        highProbe.error==null && voltage!=null && voltage in (max*.6)..(max+.15))
                    if(valid) {
                        deliveries+=Delivery(finish,target,byte)
                    } else record(timeMicros,BusKind.UART,"UART mismatch: baud, power, ground or voltage domain",false)
                }
                record(timeMicros,BusKind.UART,"${source.ownerId.take(8)} TX 0x${byte.toString(16).uppercase().padStart(2,'0')}")
            }
        }
    }
    fun i2cWrite(circuit:Circuit,controllerId:String,address:Int,bytes:List<Int>,timeMicros:Long,stop:Boolean=true):Int {
        currentTime=timeMicros
        if(i2cArbitrationLost(circuit,controllerId,timeMicros)) return 4
        val matches=connectedI2c(circuit,controllerId).filter { it.address==address }
        if(matches.size>1) record(timeMicros,BusKind.I2C,"Address conflict 0x${address.toString(16)}",false)
        val target=matches.firstOrNull()
        if(matches.isEmpty())record(timeMicros,BusKind.I2C,"No ACK at 0x${address.toString(16)}: check address, SDA/SCL, supply and common ground",false)
        if(target!=null && !target.ready(timeMicros))record(timeMicros,BusKind.I2C,"EEPROM write cycle busy; address NACK",false)
        val ready=matches.size==1 && target?.ready(timeMicros)==true
        val end=i2cWave(circuit,controllerId,address,if(ready)bytes else emptyList(),false,ready,timeMicros,stop)
        val ack=ready && target?.write(bytes,end)==true
        record(timeMicros,BusKind.I2C,"$controllerId → 0x${address.toString(16)} [${bytes.joinToString { "0x"+it.toString(16) }}]",ack)
        return when {
            matches.isEmpty() -> 2
            matches.size>1 -> 4
            else -> if(ack)0 else 2
        }
    }
    fun i2cRead(circuit:Circuit,controllerId:String,address:Int,count:Int,environment:EnvironmentState,timeMicros:Long):List<Int> {
        currentTime=timeMicros
        if(i2cArbitrationLost(circuit,controllerId,timeMicros)) return emptyList()
        val target=connectedI2c(circuit,controllerId).singleOrNull { it.address==address }
        val data=target?.read(count,environment,timeMicros) ?: emptyList()
        i2cWave(circuit,controllerId,address,data,true,target!=null && data.isNotEmpty(),timeMicros)
        record(timeMicros,BusKind.I2C,"$controllerId ← 0x${address.toString(16)} [${data.joinToString { "0x"+it.toString(16) }}]",target!=null && data.isNotEmpty())
        return data
    }
    private fun i2cArbitrationLost(c:Circuit,id:String,time:Long):Boolean {
        val def=board(c,id) ?: return false
        val route=i2cRoutes[id] ?: def.hardware.i2cPins ?: return false
        val pin=TerminalRef(id,def.index(route.first));val facts=CircuitFacts(c,null)
        val conflict=i2cRoutes.any { (other,pair) -> other!=id && (completionTimes[other] ?: 0)>time &&
            board(c,other)?.let { facts.same(pin,TerminalRef(other,it.index(pair.first))) }==true }
        if(conflict)record(time,BusKind.I2C,"Arbitration lost: another controller owns this wired bus",false)
        return conflict
    }
    fun i2cScan(environment:EnvironmentState):List<Int> = i2cDevices.map { it.address }.distinct().sorted()
    fun i2cScan(circuit:Circuit,controllerId:String):List<Int> {
        val found=mutableListOf<Int>();var time=currentTime
        for(address in 1..126) {
            if(i2cWrite(circuit,controllerId,address,emptyList(),time)==0)found+=address
            time=completionTimes[controllerId] ?: time
        }
        return found
    }
    fun duplicateAddresses():List<Int> = i2cDevices.groupBy { it.address }.filter { it.value.size>1 }.keys.toList()
    fun selectedSpiDevices(result:com.indianservers.circuitssimulator.simulation.DcResult?):List<String> {
        if(result==null || result.error!=null) return emptyList()
        return spiDevices.filter { device ->
            val voltage=result.nodeVoltages[device.cs]
            voltage!=null && voltage<0.8
        }.map { it.ownerId }
    }
    fun spiTransfer(circuit:Circuit,controllerId:String,mosi:Int,selected:List<String>,timeMicros:Long):Int {
        observe(circuit,electricalResult,timeMicros)
        val def=board(circuit,controllerId)
        val facts=CircuitFacts(circuit,null)
        // LOW CS on a different MCU's physically separate SPI bus is not MISO contention.
        val unique=selected.distinct().filter { id ->
            val sck=def?.hardware?.spiPins?.getOrNull(2)
            sck!=null && facts.same(TerminalRef(controllerId,def.index(sck)),TerminalRef(id,4))
        }
        if(unique.size>1) record(timeMicros,BusKind.SPI,"Multiple CS lines low; MISO contention",false)
        val targets=if(unique.size==1 && def!=null && powered(circuit,controllerId)) spiDevices.filter { device ->
            val part=circuit.components.firstOrNull { it.id==device.ownerId }
            val labels=PhaseFiveParts.pinNames[part?.kind].orEmpty()
            device.ownerId==unique.single() && sameGround(circuit,controllerId,device.ownerId,facts) && powered(circuit,device.ownerId) &&
                circuit.wires.any { it.start==device.cs || it.end==device.cs } &&
                listOf("MOSI","MISO","SCK").mapIndexed { i,name ->
                    val target=labels.indexOf(name)
                    target>=0 && facts.same(TerminalRef(controllerId,def.index(def.hardware.spiPins.getOrNull(i) ?: "")),
                        TerminalRef(device.ownerId,target)) }.all { it }
        } else emptyList()
        val config=spiConfigurations[controllerId] ?: SpiConfiguration()
        val target=targets.singleOrNull()
        val supply=target?.let { (electricalResult?.nodeVoltages?.get(TerminalRef(it.ownerId,0)) ?: 0.0)-(electricalResult?.nodeVoltages?.get(TerminalRef(it.ownerId,1)) ?: 0.0) } ?: 0.0
        val compatible=config.mode in setOf(0,3) && config.msbFirst && config.frequencyHz in 1..(target?.profile?.electrical?.maximumClockHz ?: 50000000) &&
            (if(target?.kind==Kind.MCP2515_CAN)(def?.logicVoltage ?: 0.0) in supply*.7..supply+.3 else (def?.logicVoltage ?: 0.0)<=3.6)
        val rx=if(compatible) targets.singleOrNull()?.transfer(mosi,timeMicros) ?: 0xFF else 0xFF
        if(def!=null) {
            val pins=def.hardware.spiPins
            val half=maxOf(1L,500000L/config.frequencyHz.coerceAtLeast(1))
            val g=electricalResult?.nodeVoltages?.get(TerminalRef(controllerId,def.pins.indexOfFirst { it.type==PinType.GROUND })) ?: 0.0
            var t=timeMicros
            val idle=config.mode>=2
            val phase=config.mode%2
            val selectedTarget=targets.singleOrNull()
            for(i in 0..7) {
                val bit=if(config.msbFirst)7-i else i
                if(phase==1) { waveforms.add(t,"SPI:$controllerId:SCK",TerminalRef(controllerId,def.index(pins[2])),g+if(!idle)def.logicVoltage else 0.0);t+=half }
                waveforms.add(t,"SPI:$controllerId:MOSI",TerminalRef(controllerId,def.index(pins[0])),g+if(mosi and (1 shl bit)!=0)def.logicVoltage else 0.0)
                selectedTarget?.let { waveforms.add(t,"SPI:${it.ownerId}:MISO",TerminalRef(it.ownerId,3),g+if(rx and (1 shl bit)!=0)(if(it.kind==Kind.MCP2515_CAN)supply else 3.3) else 0.0) }
                if(phase==0) { t+=half;waveforms.add(t,"SPI:$controllerId:SCK",TerminalRef(controllerId,def.index(pins[2])),g+if(!idle)def.logicVoltage else 0.0) }
                t+=half;waveforms.add(t,"SPI:$controllerId:SCK",TerminalRef(controllerId,def.index(pins[2])),g+if(idle)def.logicVoltage else 0.0)
            }
            completionTimes[controllerId]=t
            selectedTarget?.let { waveforms.add(t,"SPI:${it.ownerId}:MISO",TerminalRef(it.ownerId,3),null) }
        }
        if(!compatible) record(timeMicros,BusKind.SPI,"SPI fault: mode, bit order, clock or logic domain incompatible with ${target?.profile?.family ?: "selected peripheral"}",false)
        record(timeMicros,BusKind.SPI,"CS ${if(unique.isEmpty()) "HIGH" else "LOW"}  TX: 0x${mosi.toString(16)}  RX: 0x${rx.toString(16)}"+
            if(targets.isEmpty()) " · no connected powered target" else "",targets.size==1 && compatible)
        return rx
    }
    /** A resistive divider may translate UART voltage; bytes still need a physical signal path. */
    private fun uartPath(c:Circuit,tx:TerminalRef,rx:TerminalRef):Boolean {
        val facts=CircuitFacts(c,null)
        val blocked=c.components.flatMap { p ->
            when {
                p.kind==Kind.GROUND -> listOf(TerminalRef(p.id,0))
                p.kind.isBoard -> BoardRegistry.boards.getValue(p.kind).pins.mapIndexedNotNull { i,pin ->
                    if(pin.type in setOf(PinType.GROUND,PinType.POWER_INPUT,PinType.POWER_OUTPUT)) TerminalRef(p.id,i) else null }
                else -> emptyList()
            }
        }
        fun safe(ref:TerminalRef)=blocked.none { facts.same(ref,it) }
        val seen=mutableSetOf(tx)
        repeat(c.components.size+1) {
            if(seen.any { facts.same(it,rx) }) return true
            c.components.filter { it.kind==Kind.RESISTOR && it.value("resistance")<=20000.0 }.forEach { r ->
                val a=TerminalRef(r.id,0);val b=TerminalRef(r.id,1)
                if(safe(b) && seen.any { facts.same(it,a) }) seen.add(b)
                if(safe(a) && seen.any { facts.same(it,b) }) seen.add(a)
            }
        }
        return false
    }
    fun displayText():Map<String,String> = i2cDevices.associate { it.ownerId to it.display.toString() }+
        uart.values.filter { it.ownerId in terminalHistory }.associate { it.ownerId to terminalHistory[it.ownerId].orEmpty() }
    private val terminalHistory=mutableMapOf<String,String>()
}
