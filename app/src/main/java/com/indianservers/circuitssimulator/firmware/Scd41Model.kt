package com.indianservers.circuitssimulator.firmware
import kotlin.math.roundToInt
/** Command-level periodic subset. Samples advance on a fixed 5 s schedule, never on every read. */
class Scd41Model {
    var stimuli:Map<String,Double> = emptyMap()
    private var running=false
    private var nextSample=Long.MAX_VALUE
    private var consumed=true
    private var sample:List<Int> = emptyList()
    private var command=0
    private var readyAt=0L
    fun reset(){running=false;nextSample=Long.MAX_VALUE;consumed=true;sample=emptyList();command=0;readyAt=0}
    fun advance(time:Long) = update(time)
    private fun update(time:Long) {
        if(!running || time<nextSample) return
        val c=stimuli["co2Ppm"] ?: 800.0;val t=stimuli["temperatureC"] ?: 25.0;val h=stimuli["humidityPercent"] ?: 50.0
        sample=if(c.isFinite() && c in 0.0..40000.0 && t.isFinite() && t in -10.0..60.0 && h.isFinite() && h in 0.0..95.0)
            words(listOf(c.roundToInt(),((t+45)/175*65535).roundToInt(),(h/100*65535).roundToInt())) else emptyList()
        consumed=sample.isEmpty()
        nextSample+=((time-nextSample)/5_000_000+1)*5_000_000
    }
    fun write(bytes:List<Int>,time:Long):Boolean {
        if(bytes.isEmpty()) return true
        if(bytes.size!=2 || time<readyAt) return false
        update(time)
        val c=(bytes[0] shl 8) or bytes[1]
        when(c) {
            0x21B1 -> {if(running)return false;running=true;nextSample=time+5_000_000;consumed=true}
            0x3F86 -> {if(!running)return false;running=false;consumed=true;sample=emptyList();readyAt=time+500000}
            0xE4B8,0xEC05 -> {if(!running)return false;readyAt=time+1000}
            else -> return false
        }
        command=c;return true
    }
    fun read(count:Int,time:Long):List<Int> {
        update(time)
        if(time<readyAt) return emptyList()
        val data=when(command) {
            0xE4B8 -> words(listOf(if(consumed)0 else 1))
            0xEC05 -> if(consumed)emptyList() else sample.also {consumed=true}
            else -> emptyList()
        }
        command=0
        return data.take(count.coerceAtLeast(0))
    }
    companion object {
        fun crc(bytes:List<Int>):Int {var c=255;bytes.forEach { c=c xor it;repeat(8){c=if(c and 128!=0)(c shl 1) xor 0x31 else c shl 1;c=c and 255} };return c}
        fun words(values:List<Int>)=values.flatMap { val b=listOf((it shr 8) and 255,it and 255);b+crc(b) }
    }
}
