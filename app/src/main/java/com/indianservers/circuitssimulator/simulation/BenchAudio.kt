package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import kotlin.math.PI
import kotlin.math.sin

/** Safe PCM derived from solved speaker voltage or an active buzzer's powered oscillator. */
object BenchAudio {
    const val SAMPLE_RATE=22050
    fun pcm(circuit:Circuit,frames:List<TransientFrame>,durationSeconds:Double=.05):ShortArray {
        if(frames.isEmpty())return ShortArray(0)
        val devices=circuit.components.filter { it.kind in setOf(Kind.SPEAKER,Kind.BUZZER) }
        if(devices.isEmpty())return ShortArray(0)
        val count=(durationSeconds.coerceIn(.001,.2)*SAMPLE_RATE).toInt()
        val start=frames.last().timeSeconds-durationSeconds
        val speakerMean=devices.filter { it.kind==Kind.SPEAKER }.associate { p -> p.id to frames.map { frame ->
            (frame.nodeVoltages[TerminalRef(p.id,0)] ?: 0.0)-(frame.nodeVoltages[TerminalRef(p.id,1)] ?: 0.0)
        }.average() }
        var at=0
        return ShortArray(count) { index ->
            val time=start+index.toDouble()/SAMPLE_RATE
            while(at<frames.lastIndex && frames[at+1].timeSeconds<=time)at++
            val frame=frames[at]
            val sample=devices.sumOf { p ->
                val voltage=(frame.nodeVoltages[TerminalRef(p.id,0)] ?: 0.0)-(frame.nodeVoltages[TerminalRef(p.id,1)] ?: 0.0)
                if(p.kind==Kind.BUZZER) {
                    if(kotlin.math.abs(voltage)>=p.value("startVoltage"))sin(2*PI*p.value("frequency")*time)*.12 else 0.0
                } else ((voltage-(speakerMean[p.id] ?: 0.0))/5.0).coerceIn(-1.0,1.0)*.12
            }.coerceIn(-.2,.2)
            (sample*Short.MAX_VALUE).toInt().toShort()
        }
    }
}
