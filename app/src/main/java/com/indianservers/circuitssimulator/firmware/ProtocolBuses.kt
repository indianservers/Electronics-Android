package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.intelligence.CircuitFacts
import java.util.ArrayDeque

enum class BusKind { UART, I2C, SPI }
data class BusEvent(val timeMicros:Long,val kind:BusKind,val summary:String,val ack:Boolean?=null)

class UartEndpoint(val ownerId:String,val tx:TerminalRef?,val rx:TerminalRef?,val baud:Int=9600) {
    val rxBytes=ArrayDeque<Int>()
    val txBytes=ArrayDeque<Int>()
    fun enqueueTx(text:String) { text.forEach { txBytes.addLast(it.code and 0xFF) } }
    fun read():Int=if(rxBytes.isEmpty()) -1 else rxBytes.removeFirst()
    fun available()=rxBytes.size
}

class I2cDeviceModel(val address:Int,val ownerId:String,val kind:Kind,
                     val memory:MutableMap<Int,Int> = mutableMapOf(),
                     var pointer:Int=0,var display:StringBuilder=StringBuilder()) {
    fun write(bytes:List<Int>):Boolean {
        if(bytes.isEmpty()) return true
        when(kind) {
            Kind.I2C_TEMP_SENSOR -> pointer=bytes[0] and 0xFF
            Kind.I2C_EEPROM -> {
                pointer=bytes[0] and 0xFF
                bytes.drop(1).forEach { memory[pointer]=it and 0xFF;pointer=(pointer+1) and 0xFF }
            }
            Kind.I2C_LCD,Kind.OLED_SSD1306 -> bytes.forEach { byte ->
                val ch=byte and 0xFF
                if(ch in 32..126) {
                    if(display.length>=32) display.deleteAt(0)
                    display.append(ch.toChar())
                } else if(ch==0x01 || ch==0x80) display.clear()
            }
            else -> bytes.forEach { memory[pointer]=it and 0xFF;pointer=(pointer+1) and 0xFF }
        }
        return true
    }
    fun read(count:Int,environment:EnvironmentState):List<Int> = when(kind) {
        Kind.I2C_TEMP_SENSOR -> {
            val milli=(environment.temperatureC*100).toInt().coerceIn(-4000,15000)
            listOf((milli shr 8) and 0xFF,milli and 0xFF).take(count)
        }
        else -> (0 until count).map { memory[pointer+it] ?: 0 }
    }
}

class SpiDeviceModel(val ownerId:String,val cs:TerminalRef,val memory:MutableMap<Int,Int> = mutableMapOf(),
                     var pointer:Int=0,val shift:IntArray=IntArray(8)) {
    fun transfer(byte:Int):Int {
        val previous=memory[pointer] ?: 0
        memory[pointer]=byte and 0xFF
        pointer=(pointer+1) and 0xFF
        return previous
    }
}

/** Event-based UART/I²C/SPI. Bytes move when nets match; physical bit timing is not modeled. */
class ProtocolFabric {
    val log=ArrayDeque<BusEvent>()
    val uart=mutableMapOf<String,UartEndpoint>()
    val i2cDevices=mutableListOf<I2cDeviceModel>()
    val spiDevices=mutableListOf<SpiDeviceModel>()
    var autoI2cPullups=true
        private set
    fun reset() {
        log.clear();uart.values.forEach { it.rxBytes.clear();it.txBytes.clear() }
        i2cDevices.forEach { it.memory.clear();it.pointer=0;it.display.clear() }
        spiDevices.forEach { it.memory.clear();it.pointer=0 }
    }
    private fun record(time:Long,kind:BusKind,summary:String,ack:Boolean?=null) {
        if(log.size>=400) log.removeFirst()
        log.addLast(BusEvent(time,kind,summary,ack))
    }
    fun attachUart(id:String,tx:TerminalRef?,rx:TerminalRef?,baud:Int=9600) {
        uart[id]=UartEndpoint(id,tx,rx,baud)
    }
    fun rebuildDevices(circuit:Circuit) {
        i2cDevices.clear();spiDevices.clear()
        circuit.components.forEach { part ->
            when(part.kind) {
                Kind.I2C_TEMP_SENSOR,Kind.I2C_LCD,Kind.OLED_SSD1306,Kind.I2C_EEPROM ->
                    i2cDevices+=I2cDeviceModel(part.value("address").toInt(),part.id,part.kind)
                Kind.SPI_MEMORY -> spiDevices+=SpiDeviceModel(part.id,TerminalRef(part.id,5))
                Kind.SHIFT_74HC595 -> spiDevices+=SpiDeviceModel(part.id,TerminalRef(part.id,11))
                else -> Unit
            }
        }
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
        val present=sda.any { pulled(it) } && scl.any { pulled(it) }
        autoI2cPullups=!present
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
    fun exchangeUart(circuit:Circuit,timeMicros:Long) {
        val facts=CircuitFacts(circuit,null)
        uart.values.forEach { source ->
            val tx=source.tx ?: return@forEach
            while(source.txBytes.isNotEmpty()) {
                val byte=source.txBytes.removeFirst()
                uart.values.filter { it.ownerId!=source.ownerId && it.rx!=null && facts.same(tx,it.rx!!) }
                    .forEach { it.rxBytes.addLast(byte) }
                circuit.components.filter { it.kind==Kind.SERIAL_TERMINAL }.forEach { terminal ->
                    if(facts.same(tx,TerminalRef(terminal.id,1)))
                        uart.getOrPut(terminal.id) { UartEndpoint(terminal.id,TerminalRef(terminal.id,0),
                            TerminalRef(terminal.id,1)) }.rxBytes.addLast(byte)
                }
                record(timeMicros,BusKind.UART,"${source.ownerId.take(8)} TX 0x${byte.toString(16).uppercase().padStart(2,'0')}")
            }
        }
    }
    fun i2cWrite(circuit:Circuit,controllerId:String,address:Int,bytes:List<Int>,timeMicros:Long):Int {
        val matches=i2cDevices.filter { it.address==address }
        if(matches.size>1) record(timeMicros,BusKind.I2C,"Address conflict 0x${address.toString(16)}",false)
        val target=matches.firstOrNull()
        val ack=matches.size==1 && target?.write(bytes)==true
        record(timeMicros,BusKind.I2C,"$controllerId → 0x${address.toString(16)} [${bytes.joinToString { "0x"+it.toString(16) }}]",ack)
        return when {
            matches.isEmpty() -> 2
            matches.size>1 -> 4
            else -> 0
        }
    }
    fun i2cRead(circuit:Circuit,controllerId:String,address:Int,count:Int,environment:EnvironmentState,timeMicros:Long):List<Int> {
        val target=i2cDevices.singleOrNull { it.address==address }
        val data=target?.read(count,environment) ?: List(count) { 0xFF }
        record(timeMicros,BusKind.I2C,"$controllerId ← 0x${address.toString(16)} [${data.joinToString { "0x"+it.toString(16) }}]",target!=null)
        return data
    }
    fun i2cScan(environment:EnvironmentState):List<Int> = i2cDevices.map { it.address }.distinct().sorted()
    fun duplicateAddresses():List<Int> = i2cDevices.groupBy { it.address }.filter { it.value.size>1 }.keys.toList()
    fun selectedSpiDevices(result:com.indianservers.circuitssimulator.simulation.DcResult?):List<String> {
        if(result==null || result.error!=null) return emptyList()
        return spiDevices.filter { device ->
            val voltage=result.nodeVoltages[device.cs]
            voltage!=null && voltage<0.8
        }.map { it.ownerId }
    }
    fun spiTransfer(circuit:Circuit,controllerId:String,mosi:Int,selected:List<String>,timeMicros:Long):Int {
        val unique=selected.distinct()
        if(unique.size>1) record(timeMicros,BusKind.SPI,"Multiple CS lines low; MISO contention",false)
        val targets=if(unique.size==1) spiDevices.filter { it.ownerId==unique.single() } else emptyList()
        val rx=targets.fold(0) { acc,device -> device.transfer(mosi) }
        record(timeMicros,BusKind.SPI,"CS ${if(unique.isEmpty()) "HIGH" else "LOW"}  TX: 0x${mosi.toString(16)}  RX: 0x${rx.toString(16)}")
        return rx
    }
    fun displayText():Map<String,String> = i2cDevices.associate { it.ownerId to it.display.toString() }
}
