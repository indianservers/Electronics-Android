package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.digital.*

data class TruthRow(val inputs:List<LogicState>,val outputs:List<LogicState>)
data class CircuitTruthTable(val inputs:List<String>,val outputs:List<String>,val rows:List<TruthRow>)

/** Exhaustive, delay-independent combinational analysis using the simulator's gate functions. */
object TruthTableAnalyzer {
    private val custom=setOf(Kind.TRI_STATE_BUFFER,Kind.MULTIPLEXER_2,Kind.DEMULTIPLEXER_2,Kind.ENCODER_4,Kind.DECODER_2)
    fun analyze(circuit:Circuit):CircuitTruthTable {
        require(circuit.components.size<=128) { "Truth tables support up to 128 parts." }
        val unsupported=circuit.components.filterNot { it.kind.isLogicGate || it.kind in custom ||
            it.kind in setOf(Kind.LOGIC_INPUT,Kind.LOGIC_OUTPUT,Kind.GROUND,Kind.JUNCTION) }
        require(unsupported.isEmpty()) { "Combinational tables do not support ${unsupported.firstOrNull()?.kind?.title}." }
        val inputs=circuit.components.filter { it.kind==Kind.LOGIC_INPUT }.sortedBy { it.reference }
        val outputs=circuit.components.filter { it.kind==Kind.LOGIC_OUTPUT }.sortedBy { it.reference }
        require(inputs.size in 1..8) { "Place 1–8 Logic inputs." }
        require(outputs.isNotEmpty()) { "Place at least one Logic output." }
        val refs=circuit.components.flatMap { part -> (0 until part.terminalCount).map { TerminalRef(part.id,it) } }
        val parents=refs.associateWith { it }.toMutableMap()
        fun root(ref:TerminalRef):TerminalRef {
            val parent=parents[ref] ?: error("Invalid wire terminal.")
            if(parent==ref) return ref
            return root(parent).also { parents[ref]=it }
        }
        circuit.electricalConnections().forEach { (a,b) -> parents[root(a)]=root(b) }
        data class Driver(val part:PlacedComponent,val pin:Int,val dependencies:List<TerminalRef>)
        val gates=circuit.components.filter { it.kind.isLogicGate || it.kind in custom }
        val drivers=gates.flatMap { part ->
            val pins=if(part.kind in custom) DigitalParts.outputs(part.kind).toList() else listOf(part.terminalCount-1)
            pins.map { pin -> Driver(part,pin,(0 until part.terminalCount-pins.size).map { root(TerminalRef(part.id,it)) }) }
        }
        val byNet=drivers.groupBy { root(TerminalRef(it.part.id,it.pin)) }
        val visiting=mutableSetOf<Driver>();val visited=mutableSetOf<Driver>();val order=mutableListOf<Driver>()
        fun visit(driver:Driver) {
            if(driver in visited) return
            require(visiting.add(driver)) { "Feedback circuits require sequential simulation, not a combinational table." }
            driver.dependencies.flatMap { byNet[it].orEmpty() }.forEach(::visit)
            visiting.remove(driver);visited.add(driver);order+=driver
        }
        drivers.forEach(::visit)
        val rows=(0 until (1 shl inputs.size)).map { code ->
            val values=inputs.indices.map { bit -> if(code and (1 shl (inputs.size-1-bit))!=0) LogicState.HIGH else LogicState.LOW }
            val netDrivers=mutableMapOf<TerminalRef,MutableMap<String,LogicState>>()
            fun drive(ref:TerminalRef,key:String,value:LogicState) { netDrivers.getOrPut(root(ref)) { mutableMapOf() }[key]=value }
            fun sense(ref:TerminalRef):LogicState {
                val driven=netDrivers[root(ref)].orEmpty().values.filterNot { it==LogicState.HIGH_Z }.distinct()
                return if(driven.isEmpty()) LogicState.HIGH_Z else if(driven.size==1) driven.single() else LogicState.UNKNOWN
            }
            inputs.forEachIndexed { index,part -> drive(TerminalRef(part.id,0),part.id,values[index]) }
            circuit.components.filter { it.kind==Kind.GROUND }.forEach { drive(TerminalRef(it.id,0),it.id,LogicState.LOW) }
            order.forEach { driver ->
                val states=driver.dependencies.map(::sense)
                val kind=driver.part.kind
                val value=if(kind in custom) evaluateCustomLogic(kind,states,driver.pin) else {
                    val type=when(kind) {
                        Kind.NOT_GATE -> GateType.NOT;Kind.BUFFER_GATE -> GateType.BUFFER
                        Kind.AND_GATE -> GateType.AND;Kind.NAND_GATE -> GateType.NAND
                        Kind.OR_GATE -> GateType.OR;Kind.NOR_GATE -> GateType.NOR
                        Kind.XOR_GATE -> GateType.XOR;Kind.XNOR_GATE -> GateType.XNOR
                        else -> error("Unsupported gate")
                    }
                    LogicGate(states.indices.map { it.toString() },"out",type).evaluate(states)
                }
                drive(TerminalRef(driver.part.id,driver.pin),"${driver.part.id}:${driver.pin}",value)
            }
            TruthRow(values,outputs.map { sense(TerminalRef(it.id,0)) })
        }
        return CircuitTruthTable(inputs.map { it.reference },outputs.map { it.reference },rows)
    }
}
