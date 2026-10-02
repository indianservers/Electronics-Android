package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import kotlin.math.abs

/** Phase 1 GPIO model: a powered board exposes manually selected 0 V / logic-rail outputs. */
internal fun boardDrives(part:PlacedComponent,circuit:Circuit,
                         voltageAt:(Int)->Double):List<Triple<Int,Double,Double>> {
    val board=BoardRegistry.boards[part.kind] ?: return emptyList()
    val wired=circuit.wires.flatMap { listOf(it.start,it.end) }
        .filter { it.componentId==part.id }.map { it.index }.toSet()
    val grounds=board.pins.indices.filter { i ->
        PinCapability.GROUND in board.pins[i].capabilities && i in wired }
    val supplies=board.pins.indices.filter { i -> board.pins[i].name in board.supplyPins && i in wired }
    if(grounds.isEmpty() || supplies.isEmpty()) return emptyList()
    val ground=grounds.firstOrNull { abs(voltageAt(it))<.2 } ?: return emptyList()
    val powered=supplies.any { abs(voltageAt(it)-voltageAt(ground)-board.logicVoltage)<.45 }
    if(!powered) return emptyList()
    return board.pins.mapIndexedNotNull { index,pin ->
        if(PinCapability.DIGITAL_OUTPUT !in pin.capabilities) return@mapIndexedNotNull null
        val mode=part.value("gpio_${pin.name}").toInt()
        if(mode !in 1..2) null else Triple(index,if(mode==2) board.logicVoltage else 0.0,50.0)
    }
}
