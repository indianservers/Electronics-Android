package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.simulation.SignalMeasurements
import com.indianservers.circuitssimulator.simulation.SignalSample
import org.junit.Assert.*
import org.junit.Test

class SignalMeasurementsTest {
    @Test fun actualSquareTraceYieldsFrequencyPeriodAndDuty() {
        val samples=(0..300).map { n ->
            val t=n*.001
            SignalSample(t,if((t%0.1)<.03) 5.0 else 0.0)
        }
        val stats=SignalMeasurements.analyze(samples)!!
        assertEquals(5.0,stats.maximum,1e-12)
        assertEquals(0.0,stats.minimum,1e-12)
        assertEquals(10.0,stats.frequencyHz!!,.1)
        assertEquals(.1,stats.periodSeconds!!,.001)
        assertEquals(.3,stats.dutyCycle!!,.03)
    }
}
