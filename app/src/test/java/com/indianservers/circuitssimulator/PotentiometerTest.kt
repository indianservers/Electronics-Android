package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

class PotentiometerTest {
    @Test fun wiperChangesDcAndTransientNodeVoltage() {
        val sample=SampleCircuits.potentiometerDemo()
        val pot=sample.components.first { it.kind==Kind.POTENTIOMETER }
        val high=sample.copy(components=sample.components.map {
            if(it.id==pot.id) it.copy(parameters=it.parameters+("position" to 0.25)) else it
        })
        val low=sample.copy(components=sample.components.map {
            if(it.id==pot.id) it.copy(parameters=it.parameters+("position" to 0.75)) else it
        })
        val pin=TerminalRef(pot.id,1)
        val highDc=DcSolver().solve(high)
        val lowDc=DcSolver().solve(low)
        assertNull(highDc.error)
        assertNull(lowDc.error)
        assertTrue(highDc.nodeVoltages.getValue(pin)>lowDc.nodeVoltages.getValue(pin)+1.0)
        val trace=TransientSolver().simulate(high,.01,.001)
        assertNull(trace.error)
        assertEquals(highDc.nodeVoltages.getValue(pin),trace.frames.last().nodeVoltages.getValue(pin),1e-4)
    }

    @Test fun wiperIsElectricallyPresentInAc() {
        val sample=SampleCircuits.potentiometerDemo()
        val source=sample.components.first { it.kind==Kind.SOURCE }
        val pot=sample.components.first { it.kind==Kind.POTENTIOMETER }
        val point=AcSolver().solve(sample,1000.0)
        assertNotNull(point)
        val gain=point!!.gain(TerminalRef(source.id,0),TerminalRef(pot.id,1))
        assertNotNull(gain)
        assertTrue(gain!!.magnitude in 0.2..0.5)
    }
}
