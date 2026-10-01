package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Circuit
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.simulation.digital.AnalogDigitalBridge
import com.indianservers.circuitssimulator.simulation.digital.DigitalEngine
import com.indianservers.circuitssimulator.simulation.digital.LogicFamily
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import kotlin.math.min

data class MixedSignalFrame(val timeSeconds:Double,val analog:DcResult,
                            val digital:Map<String,LogicState>,
                            val digitalDriveCurrentsAmps:Map<String,Double>)
data class MixedSignalRun(val frames:List<MixedSignalFrame>,val error:String?=null,
                          val contentionTerminals:Set<TerminalRef> = emptySet())

/** Event-boundary digital + DC analog coupling for memoryless circuits. */
class MixedSignalDcScheduler(private val solver:DcSolver=DcSolver()) {
    fun run(circuit:Circuit,engine:DigitalEngine,
            analogInputs:Map<String,TerminalRef>,analogOutputs:Map<String,TerminalRef>,
            family:LogicFamily,durationSeconds:Double,maxStepSeconds:Double=.001):MixedSignalRun {
        require(durationSeconds>0 && maxStepSeconds>0 && durationSeconds.isFinite() && maxStepSeconds.isFinite())
        if(circuit.components.any { it.kind in setOf(Kind.CAPACITOR,Kind.ELECTROLYTIC,
                Kind.INDUCTOR,Kind.FUNCTION_GENERATOR,Kind.FUSE) })
            return MixedSignalRun(emptyList(),"Reactive or time-varying circuits need the transient mixed-signal scheduler.")
        if(engine.nowSeconds!=0.0)
            return MixedSignalRun(emptyList(),"Use a fresh digital engine for a new run.")
        val terminals=circuit.components.flatMap { p -> (0 until p.terminalCount).map { TerminalRef(p.id,it) } }.toSet()
        if((analogInputs.values+analogOutputs.values).any { it !in terminals })
            return MixedSignalRun(emptyList(),"A digital bridge is attached to an invalid terminal.")
        val frames=mutableListOf<MixedSignalFrame>()
        val contention=mutableSetOf<TerminalRef>()
        val nodes=(analogInputs.keys+analogOutputs.keys).toSet()
        var now=0.0
        while(true) {
            try { engine.advanceTo(now) }
            catch(e:IllegalStateException) { return MixedSignalRun(frames,e.message ?: "Digital event limit reached.",contention) }
            var result:DcResult?=null
            var settled=false
            for(iteration in 0 until 16) {
                val drives=analogOutputs.mapNotNull { (node,terminal) ->
                    AnalogDigitalBridge.drive(engine.state(node),family)?.let { (voltage,resistance) ->
                        ExternalDrive(terminal,voltage,resistance)
                    }
                }
                drives.groupBy { it.terminal }.forEach { (terminal,drivers) ->
                    if(drivers.maxOf { it.voltage }-drivers.minOf { it.voltage }>
                        family.vih-family.vil) contention+=terminal
                }
                val solved=solver.solve(circuit,drives)
                if(solved.error!=null) return MixedSignalRun(frames,solved.error,contention)
                result=solved
                var inputChanged=false
                analogInputs.forEach { (node,terminal) ->
                    val sensed=AnalogDigitalBridge.sense(solved.nodeVoltages.getValue(terminal),family)
                    if(sensed!=engine.state(node)) {
                        engine.drive(node,sensed,now)
                        inputChanged=true
                    }
                }
                if(inputChanged) {
                    try { engine.advanceTo(now) }
                    catch(e:IllegalStateException) { return MixedSignalRun(frames,e.message ?: "Digital event limit reached.",contention) }
                } else { settled=true;break }
            }
            if(!settled) return MixedSignalRun(frames,"Mixed-signal bridge did not settle at $now s.",contention)
            val finalAnalog=result!!
            val outputCurrents=analogOutputs.mapNotNull { (node,terminal) ->
                AnalogDigitalBridge.drive(engine.state(node),family)?.let { (voltage,resistance) ->
                    node to (voltage-finalAnalog.nodeVoltages.getValue(terminal))/resistance
                }
            }.toMap()
            frames+=MixedSignalFrame(now,finalAnalog,nodes.associateWith(engine::state),outputCurrents)
            if(now>=durationSeconds || frames.size>=10000) break
            val nextEvent=engine.events.peekTime()
            val next=min(durationSeconds,min(now+maxStepSeconds,
                nextEvent?.takeIf { it>now } ?: Double.POSITIVE_INFINITY))
            if(next<=now) return MixedSignalRun(frames,"Mixed-signal time did not advance.",contention)
            now=next
        }
        return MixedSignalRun(frames,if(frames.size>=10000 && now<durationSeconds) "Mixed-signal frame limit reached." else null,contention)
    }
}
