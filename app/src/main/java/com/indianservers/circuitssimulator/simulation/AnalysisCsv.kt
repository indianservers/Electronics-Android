package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.TerminalRef
import java.util.Locale
import kotlin.math.log10

object AnalysisCsv {
    private fun number(value:Double)=String.format(Locale.US,"%.10g",value)
    fun scope(frames:List<TransientFrame>,ch1:TerminalRef?,ch2:TerminalRef?):String = buildString {
        append("time_seconds,ch1_volts,ch2_volts\n")
        frames.forEach { frame ->
            append(number(frame.timeSeconds));append(',')
            ch1?.let { frame.nodeVoltages[it] }?.let { append(number(it)) }
            append(',')
            ch2?.let { frame.nodeVoltages[it] }?.let { append(number(it)) }
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
