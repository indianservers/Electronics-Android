package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

class SensorModelTest {
    @Test fun lightStimulusChangesSolvedVoltageAndCurrent() {
        val circuit=SampleCircuits.lightDivider()
        val sensor=circuit.components.first { it.kind==Kind.LDR }
        val normal=DcSolver().solve(circuit)
        assertNull(normal.error)
        assertEquals(2.5,normal.nodeVoltages.getValue(TerminalRef(sensor.id,0)),1e-4)
        assertEquals(0.00025,normal.readings.getValue(sensor.id).current,1e-7)
        val bright=circuit.copy(components=circuit.components.map {
            if(it.id==sensor.id) it.copy(parameters=it.parameters+("illuminanceLux" to 1000.0)) else it
        })
        val brightResult=DcSolver().solve(bright)
        assertNull(brightResult.error)
        assertTrue(brightResult.nodeVoltages.getValue(TerminalRef(sensor.id,0))<1.0)
        assertTrue(brightResult.readings.getValue(sensor.id).current>normal.readings.getValue(sensor.id).current)
    }

    @Test fun thermistorBetaEquationHasExpectedDirection() {
        val base=PlacedComponent(kind=Kind.THERMISTOR,reference="TH1",x=0f,y=0f)
        assertEquals(10000.0,sensorResistanceOhms(base),1e-6)
        val cold=base.copy(parameters=base.parameters+("temperatureC" to 0.0))
        val hot=base.copy(parameters=base.parameters+("temperatureC" to 50.0))
        assertTrue(sensorResistanceOhms(cold)>sensorResistanceOhms(base))
        assertTrue(sensorResistanceOhms(hot)<sensorResistanceOhms(base))
    }

    @Test fun lightSensorIsStampedInTransientAnalysis() {
        val circuit=SampleCircuits.lightDivider()
        val sensor=circuit.components.first { it.kind==Kind.LDR }
        val result=TransientSolver().simulate(circuit,.01,.001)
        assertNull(result.error)
        assertEquals(2.5,result.frames.last().nodeVoltages.getValue(TerminalRef(sensor.id,0)),1e-4)
    }
}
