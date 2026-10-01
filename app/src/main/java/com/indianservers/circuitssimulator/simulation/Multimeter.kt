package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import kotlin.math.abs

enum class MeterMode { DC_VOLTAGE, DC_CURRENT, RESISTANCE, CONTINUITY }
data class MeterResult(val value:Double?,val unit:String,val message:String?=null)

/** Virtual meter inserts the existing 1 GΩ voltmeter or 1 mΩ ammeter into a copied circuit. */
class Multimeter(private val solver:DcSolver=DcSolver()) {
    private fun wire(a:TerminalRef,b:TerminalRef)=Wire(start=a,end=b)

    private fun sourcesAt(circuit:Circuit,timeSeconds:Double):Circuit = circuit.copy(
        components=circuit.components.map { part ->
            if(part.kind==Kind.FUNCTION_GENERATOR) part.copy(kind=Kind.SOURCE,
                parameters=mapOf("voltage" to sourceWaveform(part).valueAt(timeSeconds)))
            else part
        })

    fun voltage(circuit:Circuit,plus:TerminalRef,common:TerminalRef,timeSeconds:Double=0.0):MeterResult {
        val meter=PlacedComponent(kind=Kind.VOLTMETER,reference="VM_TEST",x=0f,y=0f)
        val snapshot=sourcesAt(circuit,timeSeconds)
        val measured=solver.solve(snapshot.copy(components=snapshot.components+meter,
            wires=circuit.wires+wire(TerminalRef(meter.id,0),plus)+wire(TerminalRef(meter.id,1),common)))
        return MeterResult(measured.readings[meter.id]?.voltage,"V",measured.error)
    }

    fun current(circuit:Circuit,wireId:String,timeSeconds:Double=0.0):MeterResult {
        val selected=circuit.wires.firstOrNull { it.id==wireId }
            ?: return MeterResult(null,"A","Select a wire to insert the meter.")
        if(selected.label!=null) return MeterResult(null,"A",
            "Select an unlabeled branch for a series current measurement.")
        val meter=PlacedComponent(kind=Kind.AMMETER,reference="A_TEST",x=0f,y=0f)
        val snapshot=sourcesAt(circuit,timeSeconds)
        val measured=solver.solve(snapshot.copy(components=snapshot.components+meter,
            wires=circuit.wires.filterNot { it.id==wireId }+
                wire(selected.start,TerminalRef(meter.id,0))+
                wire(TerminalRef(meter.id,1),selected.end)))
        return MeterResult(measured.readings[meter.id]?.current,"A",measured.error)
    }

    fun resistance(circuit:Circuit,plus:TerminalRef,common:TerminalRef):MeterResult {
        val terminals=circuit.components.flatMap { p -> (0 until p.terminalCount).map { TerminalRef(p.id,it) } }
        if(plus !in terminals || common !in terminals) return MeterResult(null,"Ω","Probe is not attached to this circuit.")
        val parent=terminals.associateWith { it }.toMutableMap()
        fun root(t:TerminalRef):TerminalRef {
            val p=parent.getValue(t)
            if(p==t) return t
            val r=root(p);parent[t]=r;return r
        }
        fun union(a:TerminalRef,b:TerminalRef) { if(a in parent && b in parent) parent[root(a)]=root(b) }
        circuit.electricalConnections().forEach { (a,b) -> union(a,b) }
        circuit.components.filter { it.kind==Kind.BATTERY || it.kind==Kind.SOURCE ||
            it.kind==Kind.FUNCTION_GENERATOR }.forEach { union(TerminalRef(it.id,0),TerminalRef(it.id,1)) }
        val grounds=circuit.components.filter { it.kind==Kind.GROUND }
        grounds.drop(1).forEach { union(TerminalRef(grounds.first().id,0),TerminalRef(it.id,0)) }
        val start=root(plus);val reference=root(common)
        if(start==reference) return MeterResult(0.0,"Ω")
        data class Edge(val a:TerminalRef,val b:TerminalRef,val conductance:Double)
        val edges=circuit.components.mapNotNull { p ->
            if(p.terminalCount!=2) return@mapNotNull null
            val g=when(p.kind) {
                Kind.RESISTOR,Kind.LAMP -> 1.0/p.value("resistance").coerceAtLeast(1e-9)
                Kind.LDR,Kind.THERMISTOR -> 1.0/sensorResistanceOhms(p)
                Kind.SWITCH -> if(p.closed) 1000.0 else 0.0
                Kind.INDUCTOR,Kind.AMMETER -> 1000.0
                Kind.VOLTMETER -> 1e-9
                else -> 0.0
            }
            if(g<=0.0) null else Edge(root(TerminalRef(p.id,0)),root(TerminalRef(p.id,1)),g)
        }
        val adjacency=mutableMapOf<TerminalRef,MutableSet<TerminalRef>>()
        edges.forEach { edge ->
            adjacency.getOrPut(edge.a) { mutableSetOf() }.add(edge.b)
            adjacency.getOrPut(edge.b) { mutableSetOf() }.add(edge.a)
        }
        val reachable=mutableSetOf(start)
        val queue=ArrayDeque<TerminalRef>();queue.add(start)
        while(queue.isNotEmpty()) adjacency[queue.removeFirst()].orEmpty().forEach {
            if(reachable.add(it)) queue.add(it)
        }
        if(reference !in reachable) return MeterResult(Double.POSITIVE_INFINITY,"Ω")
        val nodes=reachable.filter { it!=reference }.withIndex().associate { it.value to it.index }
        val matrix=Array(nodes.size) { DoubleArray(nodes.size) }
        val rhs=DoubleArray(nodes.size)
        rhs[nodes.getValue(start)]=1.0
        edges.filter { it.a in reachable && it.b in reachable }.forEach { edge ->
            val a=nodes[edge.a];val b=nodes[edge.b];val g=edge.conductance
            if(a!=null) matrix[a][a]+=g
            if(b!=null) matrix[b][b]+=g
            if(a!=null && b!=null) { matrix[a][b]-=g;matrix[b][a]-=g }
        }
        val volts=DenseLinearSolver.solve(matrix,rhs)
            ?: return MeterResult(null,"Ω","Resistance network is singular.")
        return MeterResult(volts[nodes.getValue(start)],"Ω")
    }

    fun continuity(circuit:Circuit,plus:TerminalRef,common:TerminalRef,thresholdOhms:Double=50.0):MeterResult {
        val resistance=resistance(circuit,plus,common)
        return MeterResult(resistance.value,if(resistance.value!=null && resistance.value<thresholdOhms) "Closed" else "Open",
            resistance.message)
    }
}
