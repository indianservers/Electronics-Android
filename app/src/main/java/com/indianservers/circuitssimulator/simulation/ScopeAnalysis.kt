package com.indianservers.circuitssimulator.simulation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class TriggerEdge { RISING, FALLING }
data class CursorMeasurement(val firstTime:Double,val secondTime:Double,
                             val firstValue:Double,val secondValue:Double) {
    val deltaTime get()=secondTime-firstTime
    val deltaValue get()=secondValue-firstValue
}
data class SpectralBin(val frequencyHz:Double,val amplitude:Double)

object ScopeAnalysis {
    fun valueAt(samples:List<SignalSample>,time:Double):Double? {
        if(samples.isEmpty() || time<samples.first().timeSeconds || time>samples.last().timeSeconds) return null
        var low=0;var high=samples.lastIndex
        while(low<high) {
            val mid=(low+high)/2
            if(samples[mid].timeSeconds<time) low=mid+1 else high=mid
        }
        if(low==0) return samples[0].value
        val a=samples[low-1];val b=samples[low]
        val span=b.timeSeconds-a.timeSeconds
        if(span<=0.0) return null
        return a.value+(b.value-a.value)*(time-a.timeSeconds)/span
    }

    fun cursors(samples:List<SignalSample>,firstTime:Double,secondTime:Double):CursorMeasurement? {
        if(firstTime>=secondTime) return null
        val a=valueAt(samples,firstTime) ?: return null
        val b=valueAt(samples,secondTime) ?: return null
        return CursorMeasurement(firstTime,secondTime,a,b)
    }

    fun trigger(samples:List<SignalSample>,threshold:Double,edge:TriggerEdge,
                afterSeconds:Double=Double.NEGATIVE_INFINITY):Double? {
        for(i in 1 until samples.size) {
            val a=samples[i-1];val b=samples[i]
            if(a.timeSeconds<afterSeconds || b.timeSeconds<=a.timeSeconds) continue
            val crossing=if(edge==TriggerEdge.RISING) a.value<threshold && b.value>=threshold
                else a.value>threshold && b.value<=threshold
            if(crossing && b.value!=a.value)
                return a.timeSeconds+(threshold-a.value)*(b.timeSeconds-a.timeSeconds)/(b.value-a.value)
        }
        return null
    }

    /** Hann-windowed, mean-removed spectrum of a trace resampled to an evenly spaced grid. */
    fun spectrum(samples:List<SignalSample>,points:Int=256):List<SpectralBin> {
        if(points !in 32..1024 || points and (points-1)!=0 || samples.size<16) return emptyList()
        val first=samples.first().timeSeconds;val last=samples.last().timeSeconds
        val duration=last-first
        if(!duration.isFinite() || duration<=0.0 || samples.any { !it.value.isFinite() } ||
            samples.zipWithNext().any { it.first.timeSeconds>=it.second.timeSeconds }) return emptyList()
        val values=DoubleArray(points) { index ->
            valueAt(samples,first+duration*index/points) ?: return emptyList()
        }
        val mean=values.average()
        val window=DoubleArray(points) { .5-.5*cos(2*PI*it/(points-1)) }
        val windowSum=window.sum()
        return (1..points/2).map { bin ->
            var real=0.0;var imaginary=0.0
            for(n in 0 until points) {
                val angle=2*PI*bin*n/points
                val value=(values[n]-mean)*window[n]
                real+=value*cos(angle);imaginary-=value*sin(angle)
            }
            SpectralBin(bin/duration,2*hypot(real,imaginary)/windowSum)
        }
    }
}
