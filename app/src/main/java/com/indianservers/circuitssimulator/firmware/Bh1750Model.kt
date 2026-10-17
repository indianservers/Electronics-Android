package com.indianservers.circuitssimulator.firmware

import kotlin.math.roundToInt

/** Explicit nominal subset of ROHM BH1750FVI Rev.D, high-resolution MTreg=69. */
class Bh1750Model {
    var lux=300.0
    private var active=false
    private var continuous=false
    private var awake=false
    private var deadline=Long.MAX_VALUE
    private var sample:Int?=0 // Rev.D page 4: data register initial value is zero.
    fun reset(){active=false;continuous=false;awake=false;deadline=Long.MAX_VALUE;sample=0}
    fun advance(time:Long){
        if(active && time>=deadline){
            sample=if(lux.isFinite() && lux>=0) (lux*1.2).coerceIn(0.0,65535.0).roundToInt() else null
            if(continuous)deadline+=((time-deadline)/120000+1)*120000
            else {active=false;awake=false;deadline=Long.MAX_VALUE}
        }
    }
    fun write(bytes:List<Int>,time:Long):Boolean {
        advance(time)
        if(bytes.any {it !in listOf(0,1,7,0x10,0x20)})return false
        for(command in bytes)when(command){
            0->{awake=false;active=false;deadline=Long.MAX_VALUE}
            1->awake=true
            7->if(awake)sample=0 else return false
            0x10,0x20->{awake=true;active=true;continuous=command==0x10;deadline=time+120000}
        }
        return true
    }
    fun read(count:Int,time:Long):List<Int> {
        advance(time);val value=sample ?: return emptyList()
        return listOf(value shr 8,value and 255).take(count.coerceIn(0,2))
    }
}
