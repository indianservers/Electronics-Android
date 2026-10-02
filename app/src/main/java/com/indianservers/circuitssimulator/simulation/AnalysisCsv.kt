package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.TerminalRef
import java.util.Locale
import kotlin.math.log10

object AnalysisCsv {
    private fun number(value:Double)=String.format(Locale.US,"%.10g",value)
    fun scope(frames:List<TransientFrame>,ch1:TerminalRef?,ch2:TerminalRef?):String =
        scope(frames,listOf(ch1,ch2))
    fun scope(frames:List<TransientFrame>,channels:List<TerminalRef?>):String = buildString {
        append("time_seconds")
        channels.indices.forEach { append(",ch${it+1}_volts") }
        append('\n')
        frames.forEach { frame ->
            append(number(frame.timeSeconds))
            channels.forEach { channel ->
                append(',')
                channel?.let { frame.nodeVoltages[it] }?.let { append(number(it)) }
            }
            append('\n')
        }
    }
    fun bode(points:List<AcPoint>,input:TerminalRef,output:TerminalRef):String = buildString {
        append("frequency_hz,magnitude_db,phase_degrees\n")
        points.forEach { point ->
            val gain=point.gain(input,output) ?: return@forEach
            append(number(point.frequencyHz));append(',')
            append(number(20*log10(gain.magnitude.coerceAtLeast(1e-15))));append(',')
            append(number(gain.phaseDegrees));append('\n')
        }
    }
}
