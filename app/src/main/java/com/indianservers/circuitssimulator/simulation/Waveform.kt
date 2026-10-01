package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.PlacedComponent
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** All source values are SI quantities evaluated at simulation time, never UI frame time. */
interface Waveform { fun valueAt(timeSeconds: Double): Double }
interface EventAwareWaveform:Waveform { fun nextEventAfter(timeSeconds:Double):Double? }

data class DcWaveform(val level: Double) : Waveform {
    override fun valueAt(timeSeconds: Double) = level
}

data class SineWaveform(val offset: Double, val amplitude: Double, val frequencyHz: Double,
                        val phaseDegrees: Double = 0.0) : Waveform {
    override fun valueAt(timeSeconds: Double) = offset + amplitude *
        sin(2.0 * PI * frequencyHz * timeSeconds + Math.toRadians(phaseDegrees))
}

data class SquareWaveform(val offset: Double, val amplitude: Double, val frequencyHz: Double,
                          val duty: Double = .5, val phaseDegrees: Double = 0.0) : EventAwareWaveform {
    override fun valueAt(timeSeconds: Double): Double {
        if (frequencyHz <= 0.0) return offset
        val cycle = fractional(frequencyHz * timeSeconds + phaseDegrees / 360.0)
        return offset + if (cycle < duty.coerceIn(0.0, 1.0)) amplitude else -amplitude
    }
    override fun nextEventAfter(timeSeconds: Double): Double? {
        if(frequencyHz<=0.0) return null
        val cycle=frequencyHz*timeSeconds+phaseDegrees/360.0
        val base=floor(cycle)
        val events=listOf(base+duty.coerceIn(0.0,1.0),base+1.0,base+1.0+duty.coerceIn(0.0,1.0))
        return events.map { (it-phaseDegrees/360.0)/frequencyHz }.firstOrNull { it>timeSeconds+1e-12 }
    }
}

data class TriangleWaveform(val offset: Double, val amplitude: Double, val frequencyHz: Double,
                            val phaseDegrees: Double = 0.0) : Waveform {
    override fun valueAt(timeSeconds: Double): Double {
        if (frequencyHz <= 0.0) return offset
        val cycle = fractional(frequencyHz * timeSeconds + phaseDegrees / 360.0)
        return offset + amplitude * (1.0 - 4.0 * kotlin.math.abs(cycle - .5))
    }
}

data class SawtoothWaveform(val offset: Double, val amplitude: Double, val frequencyHz: Double,
                            val phaseDegrees: Double = 0.0) : Waveform {
    override fun valueAt(timeSeconds: Double): Double {
        if (frequencyHz <= 0.0) return offset
        return offset + amplitude * (2.0 * fractional(frequencyHz * timeSeconds + phaseDegrees / 360.0) - 1.0)
    }
}

data class PulseWaveform(val low: Double, val high: Double, val delaySeconds: Double,
                         val riseSeconds: Double, val fallSeconds: Double,
                         val widthSeconds: Double, val periodSeconds: Double) : EventAwareWaveform {
    override fun valueAt(timeSeconds: Double): Double {
        if (timeSeconds < delaySeconds || periodSeconds <= 0.0) return low
        val cycle = (timeSeconds - delaySeconds) % periodSeconds
        val rise = riseSeconds.coerceAtLeast(0.0)
        val fall = fallSeconds.coerceAtLeast(0.0)
        val width = widthSeconds.coerceIn(0.0, (periodSeconds - rise - fall).coerceAtLeast(0.0))
        return when {
            rise > 0.0 && cycle < rise -> low + (high - low) * cycle / rise
            cycle < rise + width -> high
            fall > 0.0 && cycle < rise + width + fall -> high + (low - high) * (cycle - rise - width) / fall
            else -> low
        }
    }
    override fun nextEventAfter(timeSeconds: Double): Double? {
        if(periodSeconds<=0.0) return null
        val index=floor((timeSeconds-delaySeconds)/periodSeconds).toInt().coerceAtLeast(0)
        val edges=listOf(0.0,riseSeconds,riseSeconds+widthSeconds,
            riseSeconds+widthSeconds+fallSeconds,periodSeconds)
        return (index..index+1).flatMap { n -> edges.map { delaySeconds+n*periodSeconds+it } }
            .filter { it>timeSeconds+1e-12 }.minOrNull()
    }
}

data class PwlWaveform(val points: List<Pair<Double, Double>>) : Waveform {
    init { require(points.isNotEmpty() && points.zipWithNext().all { it.first.first < it.second.first }) }
    override fun valueAt(timeSeconds: Double): Double {
        if (timeSeconds <= points.first().first) return points.first().second
        if (timeSeconds >= points.last().first) return points.last().second
        val upper = points.indexOfFirst { it.first >= timeSeconds }
        val a = points[upper - 1]; val b = points[upper]
        return a.second + (b.second - a.second) * (timeSeconds - a.first) / (b.first - a.first)
    }
}

private fun fractional(value: Double) = value - floor(value)

/** Waveform index is saved as a numeric parameter to remain compatible with existing circuit JSON. */
fun sourceWaveform(part: PlacedComponent): Waveform {
    if (part.kind != com.indianservers.circuitssimulator.domain.Kind.FUNCTION_GENERATOR)
        return DcWaveform(part.value("voltage"))
    val offset = part.value("offset")
    val amplitude = part.value("amplitude")
    val frequency = part.value("frequency")
    val phase = part.value("phase")
    return when (part.value("waveform").toInt()) {
        1 -> SquareWaveform(offset, amplitude, frequency, part.value("duty"), phase)
        2 -> TriangleWaveform(offset, amplitude, frequency, phase)
        3 -> SawtoothWaveform(offset, amplitude, frequency, phase)
        4 -> {
            val period = 1.0 / frequency.coerceAtLeast(1e-12)
            PulseWaveform(offset-amplitude, offset+amplitude, 0.0, part.value("rise"),
                part.value("fall"), period*part.value("duty"), period)
        }
        else -> SineWaveform(offset, amplitude, frequency, phase)
    }
}
