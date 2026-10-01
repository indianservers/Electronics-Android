package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.SampleCircuits
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class WaveformTest {
    @Test fun sineSquareTriangleSawAndPulseAtKnownTimes() {
        val sine=SineWaveform(1.0,2.0,1.0)
        assertEquals(1.0,sine.valueAt(0.0),1e-12)
        assertEquals(3.0,sine.valueAt(.25),1e-12)
        assertEquals(-1.0,sine.valueAt(.75),1e-12)
        val square=SquareWaveform(0.0,5.0,1.0,.25)
        assertEquals(5.0,square.valueAt(.24),1e-12)
        assertEquals(-5.0,square.valueAt(.25),1e-12)
        val triangle=TriangleWaveform(0.0,2.0,1.0)
        assertEquals(-2.0,triangle.valueAt(0.0),1e-12)
        assertEquals(2.0,triangle.valueAt(.5),1e-12)
        val saw=SawtoothWaveform(1.0,2.0,1.0)
        assertEquals(-1.0,saw.valueAt(0.0),1e-12)
        assertEquals(0.0,saw.valueAt(.25),1e-12)
        val pulse=PulseWaveform(0.0,5.0,.1,.05,.05,.3,1.0)
        assertEquals(0.0,pulse.valueAt(.05),1e-12)
        assertEquals(2.5,pulse.valueAt(.125),1e-12)
        assertEquals(5.0,pulse.valueAt(.2),1e-12)
        assertEquals(2.5,pulse.valueAt(.475),1e-12)
    }

    @Test fun pwlInterpolatesAndHoldsEnds() {
        val wave=PwlWaveform(listOf(0.0 to 0.0,.001 to 5.0,.002 to 5.0,.0021 to 0.0))
        assertEquals(2.5,wave.valueAt(.0005),1e-12)
        assertEquals(5.0,wave.valueAt(.0015),1e-12)
        assertEquals(0.0,wave.valueAt(.003),1e-12)
    }

    @Test fun transientSourceIsStampedAtSimulationTime() {
        val circuit=SampleCircuits.generator()
        val source=circuit.components.first()
        val result=TransientSolver().simulate(circuit,.1,.001)
        assertNull(result.error)
        assertEquals(2.5,result.frameAt(.025)!!.readings.getValue(source.id).voltage,.001)
        assertEquals(-2.5,result.frameAt(.075)!!.readings.getValue(source.id).voltage,.001)
        assertEquals(2.5,result.traces.getValue(source.id).samples[25].value,.001)
    }

    @Test fun timeStepStopsAtSquareEdgeAndRespectsBounds() {
        val source=SquareWaveform(0.0,1.0,100.0,.25)
        val controller=TimeStepController(.003,.00001)
        assertEquals(.0005,controller.next(.002,.1,listOf(source)),1e-12)
        assertTrue(controller.next(.0024999999,.1,listOf(source))>0.0)
    }

    @Test fun scopeStatisticsUseSamplesIncludingSquareWaveRms() {
        val sine=SineWaveform(0.0,5.0,10.0)
        val sineSamples=(0..999).map { SignalSample(it*.0001,sine.valueAt(it*.0001)) }
        val sineStats=SignalMeasurements.analyze(sineSamples)!!
        assertEquals(10.0,sineStats.peakToPeak,.01)
        assertEquals(5.0/sqrt(2.0),sineStats.rms,.01)
        val square=SquareWaveform(0.0,5.0,10.0,.25)
        val squareSamples=(0..999).map { SignalSample(it*.0001,square.valueAt(it*.0001)) }
        val squareStats=SignalMeasurements.analyze(squareSamples)!!
        assertEquals(5.0,squareStats.rms,.01)
        assertEquals(10.0,squareStats.peakToPeak,.01)
    }
}
