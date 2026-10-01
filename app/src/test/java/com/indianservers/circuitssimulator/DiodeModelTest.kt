package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent
import com.indianservers.circuitssimulator.simulation.diodeAt
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class DiodeModelTest {
    private fun diode(temperature:Double=25.0)=PlacedComponent(kind=Kind.DIODE,reference="D1",x=0f,y=0f,
        parameters=mapOf("isat" to 1e-12,"ideality" to 1.8,"temperatureC" to temperature))

    @Test fun forwardCurrentIsMonotonicAndReverseCurrentSmall() {
        val part=diode()
        val reverse=diodeAt(part,-5.0)
        val low=diodeAt(part,.5)
        val high=diodeAt(part,.8)
        assertTrue(abs(reverse.current)<2e-12)
        assertTrue(low.current>0.0)
        assertTrue(high.current>low.current)
        assertTrue(high.conductance>low.conductance)
    }

    @Test fun temperatureChangesThermalVoltage() {
        val cool=diodeAt(diode(0.0),.7).current
        val hot=diodeAt(diode(100.0),.7).current
        assertTrue(cool.isFinite() && hot.isFinite())
        assertTrue(cool!=hot)
    }
}
