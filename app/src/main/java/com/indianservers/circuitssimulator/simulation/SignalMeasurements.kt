package com.indianservers.circuitssimulator.simulation

import kotlin.math.sqrt

data class SignalStatistics(val minimum:Double,val maximum:Double,val mean:Double,
                            val rms:Double,val frequencyHz:Double?,val dutyCycle:Double?=null) {
    val peakToPeak get()=maximum-minimum
    val periodSeconds get()=frequencyHz?.let { 1.0/it }
}

/** Operates on numerical traces, independently of graph rendering and Compose. */
object SignalMeasurements {
    fun analyze(samples:List<SignalSample>):SignalStatistics? {
        if(samples.size<2) return null
        val values=samples.map { it.value }
        val minimum=values.minOrNull() ?: return null
        val maximum=values.maxOrNull() ?: return null
        val mean=values.average()
        val rms=sqrt(values.sumOf { it*it }/values.size)
        val mid=(minimum+maximum)/2.0
        val crossings=ArrayList<Double>()
        for(i in 1 until samples.size) {
            val a=samples[i-1];val b=samples[i]
            if(a.value<mid && b.value>=mid && b.value!=a.value) {
                crossings+=a.timeSeconds+(mid-a.value)*(b.timeSeconds-a.timeSeconds)/(b.value-a.value)
            }
        }
        val periods=crossings.zipWithNext { a,b -> b-a }.filter { it>0.0 }
        val frequency=if(periods.isEmpty()) null else 1.0/periods.average()
        val duty=if(crossings.size<2) null else {
            val start=crossings.first();val end=crossings.last()
            var highSeconds=0.0
            for(i in 1 until samples.size) {
                val a=samples[i-1];val b=samples[i]
                val lo=maxOf(a.timeSeconds,start);val hi=minOf(b.timeSeconds,end)
                if(hi<=lo || b.timeSeconds<=a.timeSeconds) continue
                val va=a.value+(b.value-a.value)*(lo-a.timeSeconds)/(b.timeSeconds-a.timeSeconds)
                val vb=a.value+(b.value-a.value)*(hi-a.timeSeconds)/(b.timeSeconds-a.timeSeconds)
                highSeconds+=when {
                    va>=mid && vb>=mid -> hi-lo
                    va<mid && vb<mid -> 0.0
                    else -> {
                        val crossing=lo+(mid-va)*(hi-lo)/(vb-va)
                        if(va>=mid) crossing-lo else hi-crossing
                    }
                }
            }
            (highSeconds/(end-start)).coerceIn(0.0,1.0)
        }
        return SignalStatistics(minimum,maximum,mean,rms,frequency,duty)
    }
}
