package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Circuit
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.domain.isLogicGate
import com.indianservers.circuitssimulator.simulation.digital.AnalogDigitalBridge
import com.indianservers.circuitssimulator.simulation.digital.DigitalEngine
import com.indianservers.circuitssimulator.simulation.digital.GateType
import com.indianservers.circuitssimulator.simulation.digital.LogicFamily
import com.indianservers.circuitssimulator.simulation.digital.LogicGate
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import com.indianservers.circuitssimulator.simulation.digital.PropagationDelay
import kotlin.math.floor
import kotlin.math.abs

/** Connects placed gate pins to the event engine and transient analog solver. */
class CanvasDigitalSimulator(private val transient:TransientSolver = TransientSolver()) {
    fun session(circuit:Circuit,startTimeSeconds:Double=0.0)=
        CanvasDigitalSession(circuit,transient,startTimeSeconds)

    fun simulate(circuit:Circuit,durationSeconds:Double,stepSeconds:Double,
                 initialCapacitorVoltages:Map<String,Double> = emptyMap(),
                 initialInductorCurrents:Map<String,Double> = emptyMap()):TransientResult =
        session(circuit).advance(durationSeconds,stepSeconds,null,
            initialCapacitorVoltages,initialInductorCurrents)
}

/** Retains logic events, including clock phase, across successive live slices. */
class CanvasDigitalSession(private val circuit:Circuit,private val transient:TransientSolver,
                           private val startTimeSeconds:Double=0.0,private val firmwareManaged:Boolean=false) {
    private val family=LogicFamily.CMOS_3V3
    private val gates=circuit.components.filter { it.kind.isLogicGate }
    private val customCombinational=circuit.components.filter { it.kind in setOf(
        Kind.TRI_STATE_BUFFER,Kind.MULTIPLEXER_2,Kind.DEMULTIPLEXER_2,
        Kind.ENCODER_4,Kind.DECODER_2) }
    private val oscillators=circuit.components.filter { it.kind==Kind.CLOCK || it.kind==Kind.TIMER_555 }
    private val sequential=circuit.components.filter { it.kind in setOf(
        Kind.D_FLIP_FLOP,Kind.T_FLIP_FLOP,Kind.COUNTER_4,
        Kind.SR_LATCH,Kind.D_LATCH,Kind.JK_FLIP_FLOP) }
    private val adcs=circuit.components.filter { !firmwareManaged && it.kind==Kind.ADC_2 }
    private val dacs=circuit.components.filter { !firmwareManaged && it.kind==Kind.DAC_2 }
    private val logicInputs=circuit.components.filter { it.kind==Kind.LOGIC_INPUT }
    private val logicOutputs=circuit.components.filter { it.kind==Kind.LOGIC_OUTPUT }
    private val shifts=circuit.components.filter { !firmwareManaged && it.kind==Kind.SHIFT_74HC595 }
    private val decades=circuit.components.filter { it.kind==Kind.COUNTER_CD4017 }
    private fun outputNode(part:com.indianservers.circuitssimulator.domain.PlacedComponent,pin:Int)=
        if(part.kind.isLogicGate) part.id+":out" else "${part.id}:out$pin"
    private val inputPins=((gates+customCombinational).flatMap { gate ->
        val outputCount=if(gate.kind in com.indianservers.circuitssimulator.domain.DigitalParts.pinNames)
            com.indianservers.circuitssimulator.domain.DigitalParts.outputs(gate.kind).count() else 1
        (0 until gate.terminalCount-outputCount).map { pin -> "${gate.id}:in$pin" to TerminalRef(gate.id,pin) }
    } + sequential.flatMap { part ->
        (0 until if(part.kind==Kind.COUNTER_4) 1 else part.terminalCount-1).map { pin ->
            "${part.id}:in$pin" to TerminalRef(part.id,pin) }
    } + dacs.flatMap { part -> (0..1).map { pin ->
        "${part.id}:in$pin" to TerminalRef(part.id,pin) } } +
        logicOutputs.map { "${it.id}:in0" to TerminalRef(it.id,0) } +
        oscillators.filter { it.kind==Kind.TIMER_555 }.map { "${it.id}:reset" to TerminalRef(it.id,0) }+
        shifts.flatMap { part -> listOf(9,10,11,12,13).map { pin ->
            "${part.id}:in$pin" to TerminalRef(part.id,pin) } }+
        decades.flatMap { part -> listOf(12,13,14).map { pin ->
            "${part.id}:in$pin" to TerminalRef(part.id,pin) } }).toMap()
    private val outputPins=(gates+customCombinational).flatMap { part ->
        val pins=if(part.kind in com.indianservers.circuitssimulator.domain.DigitalParts.pinNames)
            com.indianservers.circuitssimulator.domain.DigitalParts.outputs(part.kind)
        else (part.terminalCount-1)..(part.terminalCount-1)
        pins.map { outputNode(part,it) to TerminalRef(part.id,it) }
    }.toMap() +
        (oscillators.filter { it.kind==Kind.CLOCK }+
            sequential.filter { it.kind!=Kind.COUNTER_4 }).associate {
            it.id+":out" to TerminalRef(it.id,it.terminalCount-1)
        } + sequential.filter { it.kind==Kind.COUNTER_4 }.flatMap { part ->
            (1..4).map { pin -> "${part.id}:q${pin-1}" to TerminalRef(part.id,pin) }
        }.toMap() + adcs.flatMap { part -> (0..1).map { bit ->
            "${part.id}:d$bit" to TerminalRef(part.id,bit+2) } }.toMap() +
        oscillators.filter { it.kind==Kind.TIMER_555 }.associate {
            it.id+":out" to TerminalRef(it.id,2) } +
        logicInputs.associate { it.id+":out" to TerminalRef(it.id,0) }+
        shifts.flatMap { part -> (listOf(0,1,2,3,4,5,6,8,14)).map { pin ->
            "${part.id}:pin$pin" to TerminalRef(part.id,pin) } }.toMap()+
        decades.flatMap { part -> listOf(0,1,2,3,4,5,6,8,9,10,11).map { pin ->
            "${part.id}:pin$pin" to TerminalRef(part.id,pin) } }.toMap()
    private val wiredPins=circuit.wires.flatMap { listOf(it.start,it.end) }.toSet()
    private val standardDevices=gates.map { gate ->
        val type=when(gate.kind) {
            Kind.NOT_GATE -> GateType.NOT; Kind.BUFFER_GATE -> GateType.BUFFER
            Kind.AND_GATE -> GateType.AND; Kind.OR_GATE -> GateType.OR
            Kind.NAND_GATE -> GateType.NAND; Kind.NOR_GATE -> GateType.NOR
            Kind.XOR_GATE -> GateType.XOR; Kind.XNOR_GATE -> GateType.XNOR
            else -> error("Unsupported gate")
        }
        LogicGate((0 until gate.terminalCount-1).map { "${gate.id}:in$it" },
            gate.id+":out",type,PropagationDelay(gate.value("delay"),gate.value("delay")))
    }
    private val customDevices=customCombinational.flatMap { part ->
        com.indianservers.circuitssimulator.domain.DigitalParts.outputs(part.kind).map { outputPin ->
            object:com.indianservers.circuitssimulator.simulation.digital.DigitalDevice {
                override val inputs=(0 until part.terminalCount-
                    com.indianservers.circuitssimulator.domain.DigitalParts.outputs(part.kind).count())
                    .map { "${part.id}:in$it" }
                override val output=outputNode(part,outputPin)
                override val delay=PropagationDelay(part.value("delay"),part.value("delay"))
                override fun evaluate(states:List<LogicState>)=
                    evaluateCustomLogic(part.kind,states,outputPin)
            }
        }
    }
    private val devices=standardDevices+customDevices
    private val engine=DigitalEngine(devices)
    private val nextCycles=oscillators.associate { it.id to
        floor(startTimeSeconds*it.value("frequency")).toLong().coerceAtLeast(0L) }.toMutableMap()
    private val previousClocks=mutableMapOf<String,LogicState>()
    private val storedBits=mutableMapOf<String,LogicState>()
    private val counterValues=mutableMapOf<String,Int>()
    private val shiftValues=mutableMapOf<String,Int>()
    private val latchValues=mutableMapOf<String,Int>()
    private val timerReset=mutableMapOf<String,LogicState>()
    private val groundedDevices=mutableSetOf<String>()
    private var restored=false

    init {
        require(startTimeSeconds>=0 && startTimeSeconds.isFinite())
        logicInputs.forEach { input -> engine.drive(input.id+":out",
            if(input.value("state")>=.5) LogicState.HIGH else LogicState.LOW,startTimeSeconds) }
        oscillators.forEach { clock ->
            val period=1.0/clock.value("frequency")
            val phase=(startTimeSeconds/period)%1.0
            engine.drive(clock.id+(if(clock.kind==Kind.CLOCK) ":out" else ":osc"),
                if(phase<clock.value("duty")) LogicState.HIGH else LogicState.LOW,
                startTimeSeconds)
        }
        sequential.filter { it.kind==Kind.COUNTER_4 }.forEach { part ->
            counterValues[part.id]=0
            (0..3).forEach { bit -> engine.drive("${part.id}:q$bit",LogicState.LOW,startTimeSeconds) }
        }
        engine.advanceTo(startTimeSeconds)
    }

    private fun scheduleClocksThrough(stopTime:Double) {
        oscillators.forEach { clock ->
            val node=clock.id+(if(clock.kind==Kind.CLOCK) ":out" else ":osc")
            val frequency=clock.value("frequency")
            val period=1.0/frequency
            var cycle=nextCycles.getValue(clock.id)
            var count=0
            while(cycle*period<=stopTime+1e-12) {
                val rise=cycle*period
                val fall=rise+clock.value("duty")*period
                if(rise>engine.nowSeconds+1e-12) engine.drive(node,LogicState.HIGH,rise)
                if(fall>engine.nowSeconds+1e-12)
                    engine.drive(node,LogicState.LOW,fall)
                cycle++
                check(++count<=100000) { "Clock event limit exceeded" }
            }
            nextCycles[clock.id]=cycle
        }
    }

    fun advance(durationSeconds:Double,stepSeconds:Double,checkpoint:TransientCheckpoint?=null,
                initialCapacitorVoltages:Map<String,Double> = emptyMap(),
                initialInductorCurrents:Map<String,Double> = emptyMap()):TransientResult = runCatching {
        if(!restored && checkpoint!=null) {
            if(checkpoint.digitalNodes.isNotEmpty())
                engine.restore(checkpoint.timeSeconds,checkpoint.digitalNodes,checkpoint.digitalEvents)
            storedBits.putAll(checkpoint.digitalBits)
            counterValues.putAll(checkpoint.digitalCounters)
            shiftValues.putAll(checkpoint.digitalShiftRegisters)
            latchValues.putAll(checkpoint.digitalLatchRegisters)
            previousClocks.putAll(checkpoint.digitalClockInputs)
            timerReset.putAll(checkpoint.digitalTimerReset)
            groundedDevices.addAll(checkpoint.digitalGrounded)
            restored=true
        }
        val stopTime=(checkpoint?.timeSeconds ?: startTimeSeconds)+durationSeconds
        scheduleClocksThrough(stopTime)
        val result=transient.simulate(circuit,durationSeconds,stepSeconds,initialCapacitorVoltages,
            initialInductorCurrents,
            externalDrivesAt={ time -> electricalDrives(time) },
            nextEventAfter={ engine.events.peekTime() },
            digitalAt={ time,voltages -> observeElectrical(time,voltages) },checkpoint=checkpoint)
        val snapshot=result.checkpoint
        if(snapshot==null) result else result.copy(checkpoint=snapshot.copy(
            digitalNodes=(inputPins.keys+outputPins.keys+oscillators.filter {
                it.kind==Kind.TIMER_555 }.map { it.id+":osc" }).associateWith(engine::state),
            digitalBits=storedBits.toMap(),digitalCounters=counterValues.toMap(),
            digitalShiftRegisters=shiftValues.toMap(),digitalLatchRegisters=latchValues.toMap(),
            digitalClockInputs=previousClocks.toMap(),digitalTimerReset=timerReset.toMap(),
            digitalGrounded=groundedDevices.toSet(),digitalEvents=engine.events.snapshot()))
    }.getOrElse { TransientResult(error=it.message ?: "Digital simulation failed.") }
    private fun electricalDrives(time:Double):List<ExternalDrive> {
                engine.advanceTo(time)
                val digital=outputPins.mapNotNull { (node,pin) ->
                    val owner=pin.componentId
                    val requiresGround=circuit.components.any { it.id==owner &&
                        it.kind in setOf(Kind.TIMER_555,Kind.ADC_2,Kind.SHIFT_74HC595,Kind.COUNTER_CD4017) }
                    val state=if(requiresGround && owner !in groundedDevices) LogicState.HIGH_Z
                    else if(node.endsWith(":out") && circuit.components.any {
                            it.kind==Kind.TIMER_555 && node==it.id+":out" }) {
                        val id=node.removeSuffix(":out")
                        when(timerReset[id]) {
                            LogicState.LOW -> LogicState.LOW
                            LogicState.HIGH -> engine.state(id+":osc")
                            else -> LogicState.UNKNOWN
                        }
                    } else engine.state(node)
                    AnalogDigitalBridge.drive(state,family)?.let { (voltage,resistance) ->
                        ExternalDrive(pin,voltage,resistance)
                    }
                }
                val analog=dacs.mapNotNull { part ->
                    if(part.id !in groundedDevices) return@mapNotNull null
                    val low=engine.state("${part.id}:in0")
                    val high=engine.state("${part.id}:in1")
                    if(low !in setOf(LogicState.LOW,LogicState.HIGH) ||
                        high !in setOf(LogicState.LOW,LogicState.HIGH)) null
                    else ExternalDrive(TerminalRef(part.id,2),1.1*((if(low==LogicState.HIGH) 1 else 0)+
                        (if(high==LogicState.HIGH) 2 else 0)),50.0)
                }
                return digital+analog

    }
    private fun observeElectrical(time:Double,voltages:Map<TerminalRef,Double>):Map<TerminalRef,LogicState> {
                (adcs+dacs+oscillators.filter { it.kind==Kind.TIMER_555 }).forEach { part ->
                    val groundPin=TerminalRef(part.id,if(part.kind==Kind.DAC_2) 3 else 1)
                    if(groundPin in wiredPins && abs(voltages.getValue(groundPin))<.1)
                        groundedDevices+=part.id else groundedDevices-=part.id
                }
                (shifts+decades).forEach { part ->
                    val ground=TerminalRef(part.id,7)
                    val supply=TerminalRef(part.id,15)
                    if(ground in wiredPins && supply in wiredPins &&
                        abs(voltages.getValue(ground))<.1 &&
                        voltages.getValue(supply)-voltages.getValue(ground)>=2.7)
                        groundedDevices+=part.id else groundedDevices-=part.id
                }
                inputPins.forEach { (node,pin) ->
                    val sensed=if(pin in wiredPins) AnalogDigitalBridge.sense(voltages.getValue(pin),family)
                        else LogicState.UNKNOWN
                    if(sensed!=engine.state(node)) engine.drive(node,sensed,time)
                }
                engine.advanceTo(time)
                oscillators.filter { it.kind==Kind.TIMER_555 }.forEach { timer ->
                    timerReset[timer.id]=engine.state("${timer.id}:reset")
                }
                sequential.forEach { part ->
                    val prior=storedBits[part.id] ?: LogicState.LOW
                    val next=when(part.kind) {
                        Kind.SR_LATCH -> {
                            val set=engine.state("${part.id}:in0")
                            val reset=engine.state("${part.id}:in1")
                            when {
                                set==LogicState.HIGH && reset==LogicState.LOW -> LogicState.HIGH
                                reset==LogicState.HIGH && set==LogicState.LOW -> LogicState.LOW
                                set==LogicState.LOW && reset==LogicState.LOW -> prior
                                else -> LogicState.UNKNOWN
                            }
                        }
                        Kind.D_LATCH -> when(engine.state("${part.id}:in1")) {
                            LogicState.HIGH -> engine.state("${part.id}:in0")
                            LogicState.LOW -> prior
                            else -> LogicState.UNKNOWN
                        }
                        else -> {
                            val clockIndex=if(part.kind==Kind.JK_FLIP_FLOP) 2
                                else if(part.kind==Kind.COUNTER_4) 0 else 1
                            val clk=engine.state("${part.id}:in$clockIndex")
                            val previous=previousClocks[part.id] ?: LogicState.LOW
                            previousClocks[part.id]=clk
                            if(previous!=LogicState.HIGH && clk==LogicState.HIGH) {
                                if(part.kind==Kind.COUNTER_4) {
                                    val value=((counterValues[part.id] ?: 0)+1) and 15
                                    counterValues[part.id]=value
                                    (0..3).forEach { bit -> engine.drive("${part.id}:q$bit",
                                        if(value and (1 shl bit)!=0) LogicState.HIGH else LogicState.LOW,
                                        time+part.value("delay")) }
                                    prior
                                } else if(part.kind==Kind.JK_FLIP_FLOP) {
                                    val j=engine.state("${part.id}:in0")
                                    val k=engine.state("${part.id}:in1")
                                    when {
                                        j==LogicState.LOW && k==LogicState.LOW -> prior
                                        j==LogicState.HIGH && k==LogicState.LOW -> LogicState.HIGH
                                        j==LogicState.LOW && k==LogicState.HIGH -> LogicState.LOW
                                        j==LogicState.HIGH && k==LogicState.HIGH ->
                                            if(prior==LogicState.HIGH) LogicState.LOW else LogicState.HIGH
                                        else -> LogicState.UNKNOWN
                                    }
                                } else {
                                    val data=engine.state("${part.id}:in0")
                                    if(part.kind==Kind.D_FLIP_FLOP) data else when(data) {
                                        LogicState.HIGH -> if(prior==LogicState.HIGH) LogicState.LOW else LogicState.HIGH
                                        LogicState.LOW -> prior
                                        else -> LogicState.UNKNOWN
                                    }
                                }
                            } else prior
                        }
                    }
                    if(part.kind!=Kind.COUNTER_4 && next!=prior) {
                        storedBits[part.id]=next
                        engine.drive(part.id+":out",next,time+part.value("delay"))
                    }
                }
                adcs.forEach { part ->
                    val input=voltages.getValue(TerminalRef(part.id,0))-
                        voltages.getValue(TerminalRef(part.id,1))
                    val code=kotlin.math.floor((input/3.3*4.0).coerceIn(0.0,3.0)).toInt()
                    (0..1).forEach { bit -> engine.drive("${part.id}:d$bit",
                        if(code and (1 shl bit)!=0) LogicState.HIGH else LogicState.LOW,time) }
                }
                shifts.forEach { part ->
                    val id=part.id
                    if(id !in groundedDevices) {
                        shiftValues[id]=0;latchValues[id]=0
                    } else {
                        if(engine.state("$id:in9")==LogicState.LOW) shiftValues[id]=0
                        val priorShift=shiftValues[id] ?: 0
                        val serialClock=engine.state("$id:in10")
                        val serialRising=previousClocks["$id:srclk"]!=LogicState.HIGH &&
                            serialClock==LogicState.HIGH
                        if(serialRising &&
                            engine.state("$id:in9")==LogicState.HIGH) {
                            shiftValues[id]=((shiftValues[id] ?: 0) shl 1 or
                                (if(engine.state("$id:in13")==LogicState.HIGH) 1 else 0)) and 255
                        }
                        previousClocks["$id:srclk"]=serialClock
                        val latchClock=engine.state("$id:in11")
                        if(previousClocks["$id:rclk"]!=LogicState.HIGH && latchClock==LogicState.HIGH)
                            latchValues[id]=if(serialRising) priorShift else shiftValues[id] ?: 0
                        previousClocks["$id:rclk"]=latchClock
                    }
                    val latch=latchValues[id] ?: 0
                    listOf(0,1,2,3,4,5,6,14).forEach { pin ->
                        val bit=if(pin==14) 0 else pin+1
                        val level=if(engine.state("$id:in12")==LogicState.HIGH) LogicState.HIGH_Z
                            else if(latch and (1 shl bit)!=0) LogicState.HIGH else LogicState.LOW
                        engine.drive("$id:pin$pin",level,time)
                    }
                    engine.drive("$id:pin8",if((shiftValues[id] ?: 0) and 128!=0)
                        LogicState.HIGH else LogicState.LOW,time)
                }
                decades.forEach { part ->
                    val id=part.id
                    if(id !in groundedDevices || engine.state("$id:in14")==LogicState.HIGH)
                        counterValues[id]=0
                    else {
                        val clock=engine.state("$id:in13")
                        if(previousClocks["$id:clk"]!=LogicState.HIGH && clock==LogicState.HIGH &&
                            engine.state("$id:in12")==LogicState.LOW)
                            counterValues[id]=((counterValues[id] ?: 0)+1)%10
                        previousClocks["$id:clk"]=clock
                    }
                    val count=counterValues[id] ?: 0
                    mapOf(0 to 5,1 to 1,2 to 0,3 to 2,4 to 6,5 to 7,6 to 3,
                        8 to 8,9 to 4,10 to 9).forEach { (pin,position) ->
                        engine.drive("$id:pin$pin",if(count==position)
                            LogicState.HIGH else LogicState.LOW,time)
                    }
                    engine.drive("$id:pin11",if(count<5) LogicState.HIGH else LogicState.LOW,time)
                }
                engine.advanceTo(time)
                return (inputPins+outputPins).map { (node,pin) ->
                    pin to if(pin.componentId !in groundedDevices && circuit.components.any {
                            it.id==pin.componentId && it.kind in setOf(Kind.TIMER_555,Kind.ADC_2,
                                Kind.SHIFT_74HC595,Kind.COUNTER_CD4017)
                        }) LogicState.HIGH_Z
                    else if(node.endsWith(":out") && oscillators.any {
                            it.kind==Kind.TIMER_555 && node==it.id+":out" }) {
                        val id=node.removeSuffix(":out")
                        when(timerReset[id]) {
                            LogicState.LOW -> LogicState.LOW
                            LogicState.HIGH -> engine.state(id+":osc")
                            else -> LogicState.UNKNOWN
                        }
                    } else engine.state(node)
                }.toMap()

    }
    /** Reuses the canvas event engine for firmware GPIO-to-logic connections. */
    fun nextElectricalEvent(through:Double):Double? { scheduleClocksThrough(through);return engine.events.peekTime() }
    fun settleDc(net:Circuit,drives:List<ExternalDrive>,time:Double):DcResult {
        if(time<engine.nowSeconds) error("Digital clock moved backwards; reset the board session.")
        if(outputPins.isEmpty()) return DcSolver().solve(net,drives)
        scheduleClocksThrough(time)
        var solved=DcSolver().solve(net,drives+electricalDrives(time))
        repeat(16) {
            if(solved.error!=null) return solved
            val before=electricalDrives(time)
            observeElectrical(time,solved.nodeVoltages)
            val after=electricalDrives(time)
            if(before==after) return solved
            solved=DcSolver().solve(net,drives+after)
        }
        return solved
    }

}
