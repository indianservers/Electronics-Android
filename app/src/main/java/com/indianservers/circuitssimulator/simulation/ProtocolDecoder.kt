package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.TerminalRef

data class DecodedPacket(val timeMicros:Long,val text:String)
/** Decodes solver samples, independently of firmware APIs and transaction logs. */
object ProtocolDecoder {
    data class Sample(val micros:Long,val values:Map<TerminalRef,Double>)
    private fun high(sample:Sample,pin:TerminalRef,threshold:Double)=(sample.values[pin] ?: 0.0)>=threshold
    fun i2c(samples:List<Sample>,sda:TerminalRef,scl:TerminalRef,threshold:Double=1.5):List<DecodedPacket> {
        val result=mutableListOf<DecodedPacket>();var active=false;var bits=0;var byte=0;var first=true
        samples.zipWithNext().forEach { (a,b) ->
            val clock=high(b,scl,threshold);val data=high(b,sda,threshold)
            if(high(a,scl,threshold) && clock && high(a,sda,threshold)!=data) {
                if(!data) { result+=DecodedPacket(b.micros,if(active)"REPEATED START" else "START");active=true;bits=0;byte=0;first=true }
                else { result+=DecodedPacket(b.micros,"STOP");active=false }
            } else if(active && !high(a,scl,threshold) && clock) {
                if(bits<8) { byte=(byte shl 1) or if(data)1 else 0;bits++ }
                else {
                    result+=DecodedPacket(b.micros,(if(first)"ADDR 0x${(byte shr 1).toString(16)} ${if(byte and 1==0)"W" else "R"}" else "DATA 0x${byte.toString(16)}")+
                        " ${if(data)"NACK" else "ACK"}")
                    first=false;bits=0;byte=0
                }
            }
        }
        return result
    }
    fun spi(samples:List<Sample>,mosi:TerminalRef,miso:TerminalRef,clock:TerminalRef,cs:TerminalRef,
        mode:Int=0,msbFirst:Boolean=true,threshold:Double=1.5):List<DecodedPacket> {
        val result=mutableListOf<DecodedPacket>();var bits=0;var tx=0;var rx=0
        val rising=mode in setOf(0,3)
        samples.zipWithNext().forEach { (a,b) ->
            if(high(a,cs,threshold)!=high(b,cs,threshold)) {
                bits=0;tx=0;rx=0;result+=DecodedPacket(b.micros,if(high(b,cs,threshold))"CS HIGH" else "CS LOW")
            }
            if(!high(b,cs,threshold) && high(a,clock,threshold)!=high(b,clock,threshold) && high(b,clock,threshold)==rising) {
                val out=if(high(b,mosi,threshold))1 else 0;val input=if(high(b,miso,threshold))1 else 0
                if(msbFirst){tx=(tx shl 1) or out;rx=(rx shl 1) or input}else {tx=tx or (out shl bits);rx=rx or (input shl bits)}
                bits++;if(bits==8){result+=DecodedPacket(b.micros,"MOSI 0x${tx.toString(16)} MISO 0x${rx.toString(16)}");bits=0;tx=0;rx=0}
            }
        }
        return result
    }
    fun uart(samples:List<Sample>,pin:TerminalRef,baud:Int=9600,threshold:Double=1.5):List<DecodedPacket> {
        require(baud>0)
        val result=mutableListOf<DecodedPacket>();var readyAt=Long.MIN_VALUE;val period=1_000_000.0/baud
        fun valueAt(time:Double):Boolean? {
            val last=samples.lastOrNull { it.micros<=time } ?: return null
            if(samples.last().micros<time)return null
            return high(last,pin,threshold)
        }
        samples.zipWithNext().forEach { (a,b) ->
            if(b.micros>=readyAt && high(a,pin,threshold) && !high(b,pin,threshold)) {
                var byte=0;var complete=true
                repeat(8){bit -> val high=valueAt(b.micros+(1.5+bit)*period);if(high==null)complete=false else if(high)byte=byte or (1 shl bit)}
                val stop=valueAt(b.micros+9.5*period)
                if(complete && stop!=null)result+=DecodedPacket(b.micros,if(stop)"RX 0x${byte.toString(16)} '${if(byte in 32..126)byte.toChar() else '.'}'" else "UART framing error")
                readyAt=(b.micros+10*period).toLong()
            }
        }
        return result
    }
}
