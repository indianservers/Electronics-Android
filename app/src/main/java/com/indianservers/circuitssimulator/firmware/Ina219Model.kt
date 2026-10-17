package com.indianservers.circuitssimulator.firmware
import kotlin.math.abs
import kotlin.math.roundToInt
/** Explicit 12-bit paired continuous-conversion subset, driven by solved sense voltages. */
class Ina219Model {
    var positive=Double.NaN
    var negative=Double.NaN
    private var config=0x399F
    private var calibration=0
    private var pointer=0
    private var next=1064L
    private val registers=IntArray(6)
    init {registers[0]=config}
    fun reset(time:Long){config=0x399F;calibration=0;pointer=0;next=time+1064;registers.fill(0);registers[0]=config}
    fun advance(time:Long) = update(time)
    private fun update(time:Long) {
        if(config and 7==0 || time<next)return
        next+=((time-next)/1064+1)*1064
        if(!positive.isFinite() || !negative.isFinite() || positive !in 0.0..26.0 || negative !in 0.0..26.0){registers[2]=1;return}
        val shunt=((positive-negative)/.00001).roundToInt()
        val bus=(negative/.004).toInt().coerceIn(0,8191)
        val current=(shunt.toLong()*calibration/4096).toInt()
        val power=(current.toLong()*bus/5000).toInt()
        val overflow=abs(positive-negative)>.32 || current !in -32768..32767 || power !in 0..65535
        registers[1]=shunt and 65535;registers[2]=(bus shl 3) or 2 or if(overflow)1 else 0
        registers[3]=power and 65535;registers[4]=current and 65535
    }
    fun write(bytes:List<Int>,time:Long):Boolean {
        update(time)
        if(bytes.isEmpty())return true
        if(bytes[0] !in 0..5 || bytes.size !in listOf(1,3))return false
        if(bytes.size==1){pointer=bytes[0];return true}
        val value=(bytes[1] shl 8) or bytes[2]
        when(bytes[0]) {
            0 -> if(value and 0x8000!=0)reset(time) else {
                if(value !in listOf(0x399F,0x3998))return false
                config=value;registers[0]=value;next=time+1064
            }
            5 -> {calibration=value and 0xFFFE;registers[5]=calibration}
            else -> return false
        }
        pointer=bytes[0];return true
    }
    fun read(count:Int,time:Long):List<Int> {
        update(time)
        val value=registers[pointer]
        val bytes=listOf((value shr 8) and 255,value and 255)
        if(pointer==3)registers[2]=registers[2] and 2.inv()
        return bytes.take(count.coerceAtLeast(0))
    }
}
