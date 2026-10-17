package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*
import java.util.Locale

/** Atlas EZO single-reading I2C subset. A busy device ACKs and returns status 254. */
class ChemistrySensorModel(kind:Kind) {
    private val profile=ChemistrySensorParts.profiles.getValue(kind)
    var stimuli:Map<String,Double> = emptyMap()
    private var readyAt=0L
    private var response:List<Int>?=null
    fun reset() { readyAt=0;response=null }
    fun write(bytes:List<Int>,time:Long):Boolean {
        if(bytes.isEmpty()) return true // I2C address probe
        val command=bytes.takeWhile { it!=0 }.map { it.toChar() }.joinToString("")
        if(command.equals("R",true)) {
            val value=stimuli[profile.key] ?: profile.normal
            response=if(value.isFinite() && value in profile.minimum..profile.maximum)
                listOf(1)+String.format(Locale.US,"%.${profile.decimals}f",value).map { it.code }+listOf(0)
            else listOf(2,0)
            readyAt=time+profile.conversionMicros
        } else { response=listOf(2,0);readyAt=time+300000 }
        return true // Command syntax is reported in the response, not an address NACK.
    }
    fun read(count:Int,time:Long):List<Int> {
        if(count<=0) return emptyList()
        val bytes=if(time<readyAt) listOf(254) else response ?: listOf(255)
        if(time>=readyAt) response=null
        return List(count) { bytes.getOrElse(it){0} }
    }
}
