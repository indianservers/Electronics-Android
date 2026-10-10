package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*
import kotlin.math.roundToInt

class I2cDeviceModel(var address:Int,override val ownerId:String,val kind:Kind,
    val memory:MutableMap<Int,Int> = mutableMapOf(),var pointer:Int=0,
    var display:StringBuilder=StringBuilder()):EmbeddedPeripheral {
    override val profile=EmbeddedProfiles.profiles.getValue(kind)
    var powered=false
    var busyUntil=0L
    var writeProtected=false
    val oled=OledController()
    val lcd=LcdBackpack()
    private var temperatureWord=25 shl 8
    override fun powerChanged(powered:Boolean,timeMicros:Long) {
        if(this.powered && !powered) {
            pointer=0;busyUntil=0;display.clear();oled.reset();lcd.reset()
            if(kind!=Kind.I2C_EEPROM) memory.clear()
        }
        if(!this.powered && powered && kind==Kind.I2C_TEMP_SENSOR) {
            memory[1]=0;memory[2]=75 shl 8;memory[3]=80 shl 8
        }
        this.powered=powered
    }
    fun ready(time:Long)=time>=busyUntil
    fun write(bytes:List<Int>,timeMicros:Long=0):Boolean {
        if(!ready(timeMicros)) return false
        if(bytes.isEmpty()) return true
        when(kind) {
            Kind.I2C_TEMP_SENSOR -> {
                pointer=bytes[0] and 3
                if(bytes.size>1 && pointer!=0) memory[pointer]=if(pointer==1) bytes[1] and 0x1F
                    else ((bytes[1] shl 8) or bytes.getOrElse(2){0}) and 0xFF80
            }
            Kind.I2C_EEPROM -> {
                pointer=bytes[0] and 255
                if(bytes.size>1 && !writeProtected) {
                    val page=pointer and 0xF8
                    bytes.drop(1).forEach { memory[pointer]=it and 255;pointer=page or ((pointer+1) and 7) }
                    busyUntil=timeMicros+5000
                }
            }
            Kind.OLED_SSD1306 -> oled.write(bytes)
            Kind.I2C_LCD -> { bytes.forEach(lcd::write);display=StringBuilder(lcd.text()) }
            else -> return false
        }
        return true
    }
    fun read(count:Int,environment:EnvironmentState,timeMicros:Long=0):List<Int> {
        if(!ready(timeMicros)) return emptyList()
        return when(kind) {
            Kind.I2C_TEMP_SENSOR -> {
                if((memory[1] ?: 0) and 1==0) temperatureWord=(environment.temperatureC.coerceIn(-55.0,125.0)*2).roundToInt() shl 7
                val value=if(pointer==0) temperatureWord else memory[pointer] ?: 0
                if(pointer==1) List(count) { value and 255 }
                else List(count) { if(it%2==0) (value shr 8) and 255 else value and 255 }
            }
            Kind.I2C_EEPROM -> List(count) { val b=memory[pointer] ?: 255;pointer=(pointer+1) and 255;b }
            Kind.I2C_LCD -> List(count) { lcd.port }
            else -> emptyList() // SSD1306 serial interface is write-only.
        }
    }
    override fun inspect(timeMicros:Long)=mapOf("Family" to profile.family,"Address" to "0x${address.toString(16)}",
        "Power" to powered.toString(),"Pointer" to "0x${pointer.toString(16)}","Busy" to (!ready(timeMicros)).toString(),
        "Registers / memory" to memory.toSortedMap().entries.take(64).joinToString { "${it.key.toString(16)}=${it.value.toString(16)}" })+
        when(kind) { Kind.OLED_SSD1306 -> mapOf("Display on" to oled.on.toString(),"RAM bytes" to oled.ram.count { it!=0 }.toString())
            Kind.I2C_LCD -> mapOf("DDRAM" to lcd.text());else -> emptyMap() }
}

/** GDDRAM pages are bytes of eight vertical pixels. No text/string inference. */
class OledController {
    val ram=IntArray(1024)
    var on=false;private set
    var inverse=false;private set
    private var allOn=false
    private var mode=2
    private var column=0;private var page=0
    private var c0=0;private var c1=127;private var p0=0;private var p1=7
    private var command=-1;private var needed=0;private val args=mutableListOf<Int>()
    fun reset() { ram.fill(0);on=false;inverse=false;allOn=false;mode=2;column=0;page=0;c0=0;c1=127;p0=0;p1=7;command=-1;needed=0;args.clear() }
    fun write(bytes:List<Int>) {
        var at=0
        while(at<bytes.size) {
            val control=bytes[at++]
            val one=control and 128!=0
            val end=if(one) minOf(at+1,bytes.size) else bytes.size
            while(at<end) { val value=bytes[at++] and 255;if(control and 64!=0) data(value) else cmd(value) }
        }
    }
    private fun data(value:Int) {
        ram[page*128+column]=value
        if(mode==1) { page++;if(page>p1){page=p0;column++;if(column>c1) column=c0} }
        else { column++;if(column>(if(mode==2)127 else c1)){column=if(mode==2)0 else c0;if(mode!=2){page++;if(page>p1)page=p0}} }
    }
    private fun cmd(value:Int) {
        if(needed>0) {
            args+=value;needed--
            if(needed==0) when(command) {
                0x20 -> mode=args[0].coerceIn(0,2)
                0x21 -> { c0=args[0].coerceIn(0,127);c1=args[1].coerceIn(c0,127);column=c0 }
                0x22 -> { p0=args[0].coerceIn(0,7);p1=args[1].coerceIn(p0,7);page=p0 }
            }
            return
        }
        when(value) {
            0xAE -> on=false;0xAF -> on=true;0xA6 -> inverse=false;0xA7 -> inverse=true
            0xA4 -> allOn=false;0xA5 -> allOn=true
            in 0..15 -> column=(column and 0x70) or value
            in 0x10..0x1F -> column=(column and 15) or ((value and 7) shl 4)
            in 0xB0..0xB7 -> page=value and 7
            0x21,0x22 -> { command=value;needed=2;args.clear() }
            0x20,0x81,0x8D,0xA8,0xD3,0xD5,0xD9,0xDA,0xDB -> { command=value;needed=1;args.clear() }
        }
    }
    fun pixels():List<Int> = List(1024) { if(!on)0 else if(allOn)255 else if(inverse) ram[it] xor 255 else ram[it] }
}

/** PCF8574 output edges drive the HD44780 four-bit latch. */
class LcdBackpack {
    var port=255;private set
    private var highNibble:Int?=null
    private var nibbleRs=false
    private var cursor=0
    private var displayOn=false
    private var increment=true
    private val ddram=IntArray(128){32}
    private val cgram=IntArray(64)
    private var custom=false
    private var fourBit=false
    fun reset() { port=255;highNibble=null;cursor=0;displayOn=false;increment=true;custom=false;fourBit=false;ddram.fill(32);cgram.fill(0) }
    fun write(byte:Int) {
        val previous=port;port=byte and 255
        if(previous and 4!=0 && port and 4==0 && port and 2==0) {
            val nibble=(port shr 4) and 15;val rs=port and 1!=0
            val high=highNibble
            if(high==null) {
                // HD44780 initial 8-bit function-set pulses before switching to four-bit mode.
                if(!fourBit && !rs && nibble==3) return
                if(!fourBit && !rs && nibble==2) { fourBit=true;highNibble=null;return }
                highNibble=nibble;nibbleRs=rs
            } else { highNibble=null;execute((high shl 4) or nibble,nibbleRs) }
        }
    }
    private fun execute(byte:Int,data:Boolean) {
        if(data) { if(custom)cgram[cursor and 63]=byte else ddram[cursor and 127]=byte;cursor=(cursor+if(increment)1 else -1) and 127;return }
        when {
            byte==1 -> { ddram.fill(32);cursor=0;custom=false }
            byte==2 -> { cursor=0;custom=false }
            byte and 128!=0 -> { cursor=byte and 127;custom=false }
            byte and 64!=0 -> { cursor=byte and 63;custom=true }
            byte and 0xF8==8 -> displayOn=byte and 4!=0
            byte and 0xFC==4 -> increment=byte and 2!=0
        }
    }
    fun text()=if(!displayOn || port and 8==0) "" else listOf(0,64).joinToString("\n") { start ->
        (start until start+16).map { ddram[it].takeIf { b -> b in 32..126 }?.toChar() ?: '□' }.joinToString("") }
}

class SpiDeviceModel(override val ownerId:String,val cs:TerminalRef,
    val memory:MutableMap<Int,Int> = mutableMapOf(),var pointer:Int=0,val shift:IntArray=IntArray(8)):EmbeddedPeripheral {
    override val profile=EmbeddedProfiles.profiles.getValue(Kind.SPI_MEMORY)
    var powered=false
    var busyUntil=0L
    var writeEnable=false;private set
    private var protection=0
    private var selected=false
    private var command=-1
    private val bytes=mutableListOf<Int>()
    private var address=0
    fun select(active:Boolean,time:Long) {
        if(selected && !active) finish(time)
        if(!selected && active) { command=-1;bytes.clear();address=0;pointer=0 }
        selected=active
    }
    override fun powerChanged(powered:Boolean,timeMicros:Long) {
        if(this.powered && !powered) { selected=false;writeEnable=false;busyUntil=0;command=-1;bytes.clear() }
        this.powered=powered
    }
    private fun finish(time:Long) {
        if(time<busyUntil) return
        when(command) {
            6 -> writeEnable=true
            4 -> writeEnable=false
            1 -> if(writeEnable && bytes.isNotEmpty()) { protection=bytes[0] and 0xFC;writeEnable=false;busyUntil=time+1000 }
            2 -> if(writeEnable && protection==0 && bytes.size>3) {
                val page=address and 0x3FFF00
                val addresses=bytes.drop(3).indices.map { page or ((address+it) and 255) }
                require(memory.size+addresses.distinct().count { it !in memory }<=65536) { "Flash storage budget reached (65,536 programmed bytes); erase data before writing more." }
                bytes.drop(3).forEachIndexed { i,b -> val a=page or ((address+i) and 255);memory[a]=(memory[a] ?: 255) and b }
                writeEnable=false;busyUntil=time+3000
            }
            0x20,0x52,0xD8 -> if(writeEnable && protection==0 && bytes.size==3) {
                val size=when(command){0x20->4096;0x52->32768;else->65536};val start=address and (size-1).inv()
                memory.keys.removeAll { it in start until start+size };writeEnable=false;busyUntil=time+400000
            }
            0xC7,0x60 -> if(writeEnable && protection==0) { memory.clear();writeEnable=false;busyUntil=time+2_000_000 }
        }
    }
    fun transfer(byte:Int,time:Long=0):Int {
        if(!selected) return 255
        if(command<0) { command=byte and 255;return 255 }
        if(command==5) return (if(time<busyUntil)1 else 0) or (if(writeEnable)2 else 0) or protection
        if(time<busyUntil) return 255
        if(command==0x9F) return listOf(0xEF,0x40,0x16).getOrElse(pointer++){255}
        require(bytes.size<65536) { "SPI command byte budget reached; deassert CS." }
        bytes+=byte and 255
        if(command in setOf(2,3,0x0B,0x20,0x52,0xD8) && bytes.size<=3) {
            address=((address shl 8) or (byte and 255)) and 0x3FFFFF
            if(bytes.size==3)pointer=address
            return 255
        }
        if(command==3 || command==0x0B && bytes.size>4) {
            val value=memory[pointer] ?: 255;pointer=(pointer+1) and 0x3FFFFF;return value
        }
        return 255
    }
    override fun inspect(timeMicros:Long)=mapOf("Family" to profile.family,"Power" to powered.toString(),
        "WEL" to writeEnable.toString(),"BUSY" to (timeMicros<busyUntil).toString(),"Command" to "0x${command.toString(16)}",
        "Programmed bytes" to memory.size.toString(),"Protection" to "0x${protection.toString(16)}")
}
