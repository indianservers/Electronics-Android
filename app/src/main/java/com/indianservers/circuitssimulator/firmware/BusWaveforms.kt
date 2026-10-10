package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.simulation.ExternalDrive
import java.util.TreeMap

/** Shared clock, finite-drive edges. I2C HIGH releases the line to physical pull-ups. */
class BusWaveforms {
    private val history=mutableMapOf<String,TreeMap<Long,ExternalDrive?>>()
    private var count=0
    var endTime=0L;private set
    fun clear() { history.clear();endTime=0;count=0 }
    fun add(time:Long,key:String,pin:TerminalRef,voltage:Double?,resistance:Double=100.0) {
        val events=history.getOrPut(key){TreeMap()}
        // Sessions may sample the shared slice in a different order; retain recent edge history.
        while(events.size>2 && events.firstKey()<time-10_000_000) { events.pollFirstEntry();count-- }
        require(count<50000) { "Bus waveform queue is full; reduce traffic or advance the simulation." }
        if(!events.containsKey(time)) count++
        events[time]=voltage?.let { ExternalDrive(pin,it,resistance) };endTime=maxOf(endTime,time)
    }
    fun next(after:Long,limit:Long)=history.values.mapNotNull { it.higherKey(after) }.minOrNull()?.coerceAtMost(limit) ?: limit
    fun drives(time:Long):List<ExternalDrive> {
        return history.values.mapNotNull { it.floorEntry(time)?.value }
    }
}
