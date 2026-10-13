package com.indianservers.circuitssimulator.domain

import java.util.UUID
import kotlin.math.round

/** Flat, electrically complete selection. External wires are deliberately excluded. */
object CircuitSelection {
    fun extract(circuit:Circuit,ids:Set<String>):Circuit = circuit.copy(
        components=circuit.components.filter { it.id in ids },
        wires=circuit.wires.filter { it.start.componentId in ids && it.end.componentId in ids },
        firmware=circuit.firmware.filter { it.boardId in ids },
        peripheralMemory=circuit.peripheralMemory.filterKeys { it in ids })

    fun paste(target:Circuit,block:Circuit,dx:Float=60f,dy:Float=60f):Circuit {
        require(target.components.size+block.components.size<=512) { "Component limit reached" }
        require(target.wires.size+block.wires.size<=4096) { "Wire limit reached" }
        val ids=block.components.associate { it.id to UUID.randomUUID().toString() }
        var result=target
        block.components.forEach { part ->
            result=result.copy(components=result.components+part.copy(id=ids.getValue(part.id),
                reference=result.nextReference(part.kind),x=part.x+dx,y=part.y+dy))
        }
        // A copied named net must not silently short to the original circuit.
        val used=target.wires.mapNotNull { it.label?.uppercase(java.util.Locale.ROOT) }.toMutableSet()
        val labels=block.wires.mapNotNull { it.label }.distinctBy { it.uppercase(java.util.Locale.ROOT) }
            .associate { label ->
                var candidate=label
                var n=1
                while(candidate.uppercase(java.util.Locale.ROOT) in used) {
                    val suffix="_$n";candidate=label.take(24-suffix.length)+suffix;n++
                }
                used.add(candidate.uppercase(java.util.Locale.ROOT))
                label.uppercase(java.util.Locale.ROOT) to candidate
            }
        fun remap(ref:TerminalRef)=ref.copy(componentId=ids.getValue(ref.componentId))
        return result.copy(wires=target.wires+block.wires.map { wire -> wire.copy(
            id=UUID.randomUUID().toString(),start=remap(wire.start),end=remap(wire.end),
            label=wire.label?.let { labels.getValue(it.uppercase(java.util.Locale.ROOT)) },
            waypoints=wire.waypoints.map { it.copy(x=it.x+dx,y=it.y+dy) }) },
            firmware=target.firmware+block.firmware.map { it.copy(boardId=ids.getValue(it.boardId)) },
            peripheralMemory=target.peripheralMemory+block.peripheralMemory.mapKeys { ids.getValue(it.key) })
    }

    fun delete(circuit:Circuit,ids:Set<String>)=circuit.copy(
        components=circuit.components.filterNot { it.id in ids },
        wires=circuit.wires.filterNot { it.start.componentId in ids || it.end.componentId in ids },
        firmware=circuit.firmware.filterNot { it.boardId in ids },
        peripheralMemory=circuit.peripheralMemory.filterKeys { it !in ids })

    fun snap(value:Float,enabled:Boolean,spacing:Float):Float =
        if(enabled) round(value/spacing)*spacing else value
}
