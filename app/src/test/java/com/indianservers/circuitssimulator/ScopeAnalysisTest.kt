package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ScopeAnalysisTest {
    @Test fun interpolatedTriggerAndCursorsMeasureKnownEdges() {
        val samples=listOf(SignalSample(0.0,-1.0),SignalSample(.1,1.0),
            SignalSample(.2,1.0),SignalSample(.3,-1.0),SignalSample(.4,-1.0))
        assertEquals(.05,ScopeAnalysis.trigger(samples,0.0,TriggerEdge.RISING)!!,1e-12)
        assertEquals(.25,ScopeAnalysis.trigger(samples,0.0,TriggerEdge.FALLING)!!,1e-12)
        val cursor=ScopeAnalysis.cursors(samples,.05,.25)!!
        assertEquals(.2,cursor.deltaTime,1e-12)
        assertEquals(0.0,cursor.deltaValue,1e-12)
        assertNull(ScopeAnalysis.cursors(samples,.4,.1))
    }

    @Test fun fftFindsFundamentalAndHarmonicAtExpectedAmplitudes() {
        val samples=(0..1000).map { i ->
            val time=i/1000.0
            SignalSample(time,2*sin(2*PI*50*time)+.5*sin(2*PI*100*time))
        }
        val spectrum=ScopeAnalysis.spectrum(samples)
        val peak=spectrum.maxBy { it.amplitude }
        assertEquals(50.0,peak.frequencyHz,1.0)
        assertEquals(2.0,peak.amplitude,.12)
        val harmonic=spectrum.minBy { kotlin.math.abs(it.frequencyHz-100.0) }
        assertEquals(.5,harmonic.amplitude,.08)
    }
}
