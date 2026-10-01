package com.indianservers.circuitssimulator.simulation.digital

import java.util.PriorityQueue

enum class LogicState { LOW, HIGH, HIGH_Z, UNKNOWN }
data class LogicFamily(val name:String,val vil:Double,val vih:Double,val vol:Double,val voh:Double,
                       val outputResistanceOhm:Double) {
    init { require(vil>=vol && vih>vil && voh>=vih && outputResistanceOhm>0) }
    companion object {
        val TTL_5V=LogicFamily("5 V TTL-like",0.8,2.0,0.4,4.5,50.0)
        val CMOS_3V3=LogicFamily("3.3 V CMOS-like",0.99,2.31,0.2,3.1,50.0)
    }
}
data class DigitalSignal(val node:String,val state:LogicState,val timeSeconds:Double)
data class DigitalEvent(val timeSeconds:Double,val node:String,val state:LogicState,val order:Long)
data class PropagationDelay(val riseSeconds:Double=10e-9,val fallSeconds:Double=10e-9) {
    init { require(riseSeconds>=0 && fallSeconds>=0) }
}
interface DigitalDevice {
    val inputs:List<String>
    val output:String
    val delay:PropagationDelay
    fun evaluate(states:List<LogicState>):LogicState
}
enum class GateType { NOT, BUFFER, AND, OR, NAND, NOR, XOR, XNOR }
data class LogicGate(override val inputs:List<String>,override val output:String,val type:GateType,
                     override val delay:PropagationDelay=PropagationDelay()):DigitalDevice {
    init { require(inputs.isNotEmpty()); require(type!=GateType.NOT && type!=GateType.BUFFER || inputs.size==1) }
    override fun evaluate(states:List<LogicState>):LogicState {
        fun invert(s:LogicState)=when(s) {
            LogicState.LOW->LogicState.HIGH;LogicState.HIGH->LogicState.LOW
            else->LogicState.UNKNOWN
        }
        val a=states.map { if(it==LogicState.HIGH_Z) LogicState.UNKNOWN else it }
        return when(type) {
            GateType.NOT->invert(a[0]);GateType.BUFFER->a[0]
            GateType.AND,GateType.NAND-> {
                val result=when { LogicState.LOW in a->LogicState.LOW
                    a.all { it==LogicState.HIGH }->LogicState.HIGH else->LogicState.UNKNOWN }
                if(type==GateType.NAND) invert(result) else result
            }
            GateType.OR,GateType.NOR-> {
                val result=when { LogicState.HIGH in a->LogicState.HIGH
                    a.all { it==LogicState.LOW }->LogicState.LOW else->LogicState.UNKNOWN }
                if(type==GateType.NOR) invert(result) else result
            }
            GateType.XOR,GateType.XNOR-> {
                val result=if(a.any { it==LogicState.UNKNOWN }) LogicState.UNKNOWN
                    else if(a.count { it==LogicState.HIGH }%2==1) LogicState.HIGH else LogicState.LOW
                if(type==GateType.XNOR) invert(result) else result
            }
        }
    }
}

class EventQueue {
    private var sequence=0L
    private val queue=PriorityQueue<DigitalEvent>(compareBy<DigitalEvent> { it.timeSeconds }.thenBy { it.order })
    fun schedule(timeSeconds:Double,node:String,state:LogicState) {
        require(timeSeconds.isFinite() && timeSeconds>=0)
        queue.add(DigitalEvent(timeSeconds,node,state,sequence++))
    }
    fun poll():DigitalEvent?=queue.poll()
    fun peekTime():Double?=queue.peek()?.timeSeconds
    val size get()=queue.size
}

/** Digital events are independent of analog timesteps; the coordinator advances both at event boundaries. */
class DigitalEngine(private val devices:List<DigitalDevice>) {
    val events=EventQueue()
    private val states=mutableMapOf<String,LogicState>()
    private val history=mutableMapOf<String,ArrayDeque<DigitalSignal>>()
    var nowSeconds:Double=0.0;private set
    fun state(node:String)=states[node] ?: LogicState.HIGH_Z
    fun trace(node:String):List<DigitalSignal> = history[node]?.toList().orEmpty()
    fun drive(node:String,state:LogicState,atSeconds:Double) { events.schedule(atSeconds,node,state) }
    fun advanceTo(endSeconds:Double,maxEvents:Int=100000) {
        require(endSeconds>=nowSeconds)
        var processed=0
        while(events.peekTime()?.let { it<=endSeconds }==true) {
            check(++processed<=maxEvents) { "Digital event limit exceeded" }
            val event=events.poll()!!;nowSeconds=event.timeSeconds
            if(states[event.node]==event.state) continue
            states[event.node]=event.state
            val buffer=history.getOrPut(event.node) { ArrayDeque() }
            buffer.addLast(DigitalSignal(event.node,event.state,nowSeconds))
            if(buffer.size>10000) buffer.removeFirst()
            devices.filter { event.node in it.inputs }.forEach { gate ->
                val result=gate.evaluate(gate.inputs.map(::state))
                val delay=if(result==LogicState.HIGH) gate.delay.riseSeconds else gate.delay.fallSeconds
                events.schedule(nowSeconds+delay,gate.output,result)
            }
        }
        nowSeconds=endSeconds
    }
}

object AnalogDigitalBridge {
    fun sense(voltage:Double,family:LogicFamily):LogicState=when {
        !voltage.isFinite()->LogicState.UNKNOWN
        voltage<=family.vil->LogicState.LOW
        voltage>=family.vih->LogicState.HIGH
        else->LogicState.UNKNOWN
    }
    /** The returned Thevenin source always has finite resistance, so an analog load can pull it down. */
    fun drive(state:LogicState,family:LogicFamily):Pair<Double,Double>?=when(state) {
        LogicState.HIGH->family.voh to family.outputResistanceOhm
        LogicState.LOW->family.vol to family.outputResistanceOhm
        LogicState.HIGH_Z,LogicState.UNKNOWN->null
    }
}

data class DigitalClock(val frequencyHz:Double,val duty:Double=.5,val phaseSeconds:Double=0.0) {
    init { require(frequencyHz>0 && frequencyHz.isFinite() && duty>0 && duty<1) }
    fun schedule(engine:DigitalEngine,node:String,untilSeconds:Double) {
        val period=1/frequencyHz
        var cycle=0
        while(true) {
            val rise=phaseSeconds+cycle*period
            if(rise>untilSeconds) break
            if(rise>=0) engine.drive(node,LogicState.HIGH,rise)
            val fall=rise+duty*period
            if(fall<=untilSeconds && fall>=0) engine.drive(node,LogicState.LOW,fall)
            cycle++
            require(cycle<1000000) { "Clock event limit exceeded" }
        }
    }
}
