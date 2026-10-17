package com.indianservers.circuitssimulator.firmware
/** Deliberately bounded standard-frame, accept-all, one-shot MCP2515 polling subset. */
class Mcp2515Model {
    data class Frame(val id:Int,val data:List<Int>,val due:Long)
    val registers=IntArray(128)
    var pending:Frame?=null;private set
    private var selected=false
    private var command=-1
    private var index=0
    private var address=0
    private var mask=0
    init {reset()}
    fun reset(){registers.fill(0);registers[0x0E]=0x80;registers[0x0F]=0x80;pending=null;command=-1;selected=false}
    val mode get()=registers[0x0F] and 0xE0
    val bitrate:Int get() {
        val cnf1=registers[0x2A];val cnf2=registers[0x29];val cnf3=registers[0x28]
        if(cnf2 and 0x80==0)return 0
        val tq=1+(cnf2 and 7)+1+((cnf2 shr 3) and 7)+1+(cnf3 and 7)+1
        return 16_000_000/(2*((cnf1 and 63)+1)*tq)
    }
    fun select(active:Boolean){if(!selected && active){command=-1;index=0};selected=active}
    private fun write(reg:Int,value:Int) {
        when(reg) {
            0x0F -> {if(value and 0xE0 !in listOf(0,0x40,0x80))return;registers[reg]=value;registers[0x0E]=value and 0xE0}
            in 0x28..0x2A -> if(mode==0x80)registers[reg]=value
            0x2B,0x2C,0x30,0x60,0x70,in 0x31..0x3D -> registers[reg]=value
        }
    }
    private fun request(time:Long) {
        val dlc=registers[0x35] and 15
        if(pending!=null || bitrate<=0 || dlc>8 || registers[0x32] and 8!=0 || mode !in listOf(0,0x40) || (mode==0 && registers[0x0F] and 8==0)) {
            registers[0x30]=registers[0x30] or 0x10;return
        }
        registers[0x30]=(registers[0x30] and 0x8F) or 8
        val id=(registers[0x31] shl 3) or (registers[0x32] shr 5)
        pending=Frame(id,List(dlc){registers[0x36+it]},time+(47+8*dlc)*1_000_000L/bitrate)
    }
    fun complete(ack:Boolean){pending=null;registers[0x30]=(registers[0x30] and 8.inv()) or if(ack)0 else 0x50;registers[0x2C]=registers[0x2C] or if(ack)4 else 0x80}
    fun receive(frame:Frame) {
        val slot=if(registers[0x2C] and 1==0)0 else if(registers[0x2C] and 2==0)1 else {registers[0x2D]=registers[0x2D] or 0xC0;return}
        val base=if(slot==0)0x61 else 0x71
        registers[base]=frame.id shr 3;registers[base+1]=(frame.id and 7) shl 5
        registers[base+2]=0;registers[base+3]=0;registers[base+4]=frame.data.size
        frame.data.forEachIndexed {i,b->registers[base+5+i]=b}
        registers[0x2C]=registers[0x2C] or (1 shl slot)
    }
    fun transfer(byte:Int,time:Long):Int {
        if(!selected)return 255
        val b=byte and 255
        if(command<0) {
            command=b;index=0
            if(b==0xC0){reset();selected=true;command=b}
            if(b==0x81)request(time)
            return 0
        }
        index++
        return when(command) {
            3 -> if(index==1){address=b;0} else registers.getOrElse(address++){0}
            2 -> {if(index==1)address=b else write(address++,b);0}
            5 -> {when(index){1->address=b;2->mask=b;3->write(address,(registers.getOrElse(address){0} and mask.inv()) or (b and mask))};0}
            else -> 255
        }
    }
}
