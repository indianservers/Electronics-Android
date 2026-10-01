package com.indianservers.circuitssimulator.simulation

import kotlin.math.min

/** Keeps waveform edges on sample boundaries while respecting a finite step budget. */
class TimeStepController(private val maximumSeconds:Double,private val minimumSeconds:Double=maximumSeconds/100.0) {
    init { require(maximumSeconds>0.0 && minimumSeconds>0.0 && minimumSeconds<=maximumSeconds) }
    fun next(timeSeconds:Double,stopSeconds:Double,waveforms:List<Waveform>):Double {
        val remaining=stopSeconds-timeSeconds
        var step=min(maximumSeconds,remaining)
        val event=waveforms.filterIsInstance<EventAwareWaveform>()
            .mapNotNull { it.nextEventAfter(timeSeconds) }.minOrNull()
        if(event!=null) {
            val distance=event-timeSeconds
            if(distance>=minimumSeconds && distance<step) step=distance
        }
        return step
    }
}
