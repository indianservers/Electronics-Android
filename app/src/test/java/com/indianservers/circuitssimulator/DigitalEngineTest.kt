package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.simulation.digital.*
import org.junit.Assert.*
import org.junit.Test

class DigitalEngineTest {
    @Test fun clockInvertsWithPropagationDelay() {
        val gate=LogicGate(listOf("clock"),"out",GateType.NOT,PropagationDelay(.002,.003))
        val engine=DigitalEngine(listOf(gate))
        DigitalClock(100.0,.5).schedule(engine,"clock",.02)
        engine.advanceTo(.02)
        val output=engine.trace("out")
        assertEquals(LogicState.LOW,output[0].state)
        assertEquals(.003,output[0].timeSeconds,1e-9)
        assertEquals(LogicState.HIGH,output[1].state)
        assertEquals(.007,output[1].timeSeconds,1e-9)
    }
    @Test fun bridgeUsesUndefinedBandAndFiniteDrive() {
        val family=LogicFamily.TTL_5V
        assertEquals(LogicState.LOW,AnalogDigitalBridge.sense(.7,family))
        assertEquals(LogicState.UNKNOWN,AnalogDigitalBridge.sense(1.5,family))
        assertEquals(LogicState.HIGH,AnalogDigitalBridge.sense(2.1,family))
        val drive=AnalogDigitalBridge.drive(LogicState.HIGH,family)!!
        assertTrue(drive.second>0)
        assertNull(AnalogDigitalBridge.drive(LogicState.HIGH_Z,family))
    }
    @Test fun unknownGateInputPropagatesUnlessOutputDetermined() {
        val and=LogicGate(listOf("a","b"),"x",GateType.AND)
        assertEquals(LogicState.LOW,and.evaluate(listOf(LogicState.LOW,LogicState.UNKNOWN)))
        assertEquals(LogicState.UNKNOWN,and.evaluate(listOf(LogicState.HIGH,LogicState.UNKNOWN)))
    }
}
