package com.indianservers.circuitssimulator.intelligence

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcResult
import java.util.ArrayDeque

/** Cached wire-equivalence graph for one immutable circuit snapshot. Components are edges, not wires. */
class CircuitFacts(val circuit:Circuit,val result:DcResult?) {
    val parts=circuit.components.associateBy { it.id }
    val connectedPins=circuit.wires.flatMap { listOf(it.start,it.end) }.toSet()
    private val parent=circuit.components.flatMap { p ->
        (0 until p.terminalCount).map { TerminalRef(p.id,it) }
    }.associateWith { it }.toMutableMap()
    private fun root(ref:TerminalRef):TerminalRef {
        val prior=parent[ref] ?: return ref
        if(prior==ref) return ref
        val found=root(prior);parent[ref]=found;return found
    }
    init { circuit.electricalConnections().forEach { (a,b) ->
        if(a in parent && b in parent) parent[root(a)]=root(b)
    } }
    fun net(ref:TerminalRef):TerminalRef=root(ref)
    fun same(a:TerminalRef,b:TerminalRef)=a in parent && b in parent && net(a)==net(b)
    fun pin(part:PlacedComponent,index:Int)=TerminalRef(part.id,index)
    fun same(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=same(pin(a,ai),pin(b,bi))
    fun onNet(ref:TerminalRef):Set<TerminalRef> = parent.keys.filter { same(it,ref) }.toSet()
    fun wiresAt(ref:TerminalRef)=circuit.wires.filter { same(it.start,ref) || same(it.end,ref) }
    fun directWire(a:TerminalRef,b:TerminalRef)=circuit.wires.firstOrNull {
        (it.start==a && it.end==b) || (it.start==b && it.end==a) }
    fun voltage(ref:TerminalRef):Double?=result?.takeIf { it.error==null }?.nodeVoltages?.get(ref)
    fun voltageAcross(part:PlacedComponent):Double? {
        if(part.terminalCount<2) return null
        val a=voltage(pin(part,0));val b=voltage(pin(part,1))
        return if(a!=null && b!=null) a-b else result?.readings?.get(part.id)?.voltage
    }

    data class Edge(val from:TerminalRef,val to:TerminalRef,val part:PlacedComponent)
    private fun edges(kinds:Set<Kind>,resistorMax:Double=Double.POSITIVE_INFINITY):Map<TerminalRef,List<Edge>> {
        val graph=mutableMapOf<TerminalRef,MutableList<Edge>>()
        circuit.components.filter { it.terminalCount==2 && it.kind in kinds }.forEach { part ->
            if(part.kind==Kind.SWITCH && !part.closed) return@forEach
            if(part.kind==Kind.RESISTOR && part.value("resistance")>resistorMax) return@forEach
            val a=net(pin(part,0));val b=net(pin(part,1))
            if(a==b) return@forEach
            graph.getOrPut(a) { mutableListOf() }.add(Edge(a,b,part))
            graph.getOrPut(b) { mutableListOf() }.add(Edge(b,a,part))
        }
        return graph
    }

    /** Only low-impedance modeled paths count as a source short. */
    fun lowResistancePath(a:TerminalRef,b:TerminalRef):List<PlacedComponent>? {
        if(same(a,b)) return emptyList()
        val graph=edges(setOf(Kind.SWITCH,Kind.RESISTOR,Kind.AMMETER),1.0)
        val start=net(a);val end=net(b)
        val parentEdge=mutableMapOf<TerminalRef,Edge>()
        val seen=mutableSetOf(start);val queue=ArrayDeque<TerminalRef>();queue.add(start)
        while(queue.isNotEmpty()) {
            val at=queue.removeFirst()
            graph[at].orEmpty().forEach { edge ->
                if(seen.add(edge.to)) { parentEdge[edge.to]=edge;queue.add(edge.to) }
            }
        }
        if(end !in seen) return null
        val path=mutableListOf<PlacedComponent>();var at=end
        while(at!=start) { val edge=parentEdge[at] ?: break;path+=edge.part;at=edge.from }
        return path.reversed()
    }

    /** True when a path can reach the LED anode without passing through a resistor. */
    fun bypassesResistor(from:TerminalRef,to:TerminalRef):Boolean {
        if(same(from,to)) return true
        val graph=edges(setOf(Kind.SWITCH,Kind.AMMETER))
        val start=net(from);val target=net(to)
        val seen=mutableSetOf(start);val queue=ArrayDeque<TerminalRef>();queue.add(start)
        while(queue.isNotEmpty()) {
            val at=queue.removeFirst()
            graph[at].orEmpty().forEach { if(seen.add(it.to)) queue.add(it.to) }
        }
        return target in seen
    }

    fun sourceForLed(led:PlacedComponent):PlacedComponent?=circuit.components.firstOrNull { source ->
        source.kind in setOf(Kind.BATTERY,Kind.SOURCE,Kind.SINGLE_CELL,Kind.BATTERY_PACK,
            Kind.VARIABLE_DC_SUPPLY) && same(source,1,led,1) &&
            (same(source,0,led,0) || circuit.components.any { r ->
                r.kind==Kind.RESISTOR &&
                    ((same(source,0,r,0) && same(r,1,led,0)) ||
                        (same(source,0,r,1) && same(r,0,led,0))) })
    }

    fun seriesResistor(source:PlacedComponent,led:PlacedComponent):PlacedComponent?=
        circuit.components.firstOrNull { r -> r.kind==Kind.RESISTOR &&
            ((same(source,0,r,0) && same(r,1,led,0)) ||
                (same(source,0,r,1) && same(r,0,led,0))) &&
            !same(source,0,led,0) }

    fun isGrounded(part:PlacedComponent,pin:Int)=circuit.components.any { it.kind==Kind.GROUND &&
        same(part,pin,it,0) }
}
