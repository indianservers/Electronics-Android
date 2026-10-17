package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

class DcReuseRegressionTest {
    @Test fun exactDcReuseInvalidatesForStimuliWiringAndDrives() {
        val c=SampleCircuits.lightDivider()
        val sensor=c.components.first { it.kind==Kind.LDR }
        val pin=TerminalRef(sensor.id,0)
        val session=CanvasDigitalSession(c,TransientSolver(),firmwareManaged=true)
        val normal=session.settleDc(c,emptyList(),0.0)
        assertSame(normal,session.settleDc(c,emptyList(),.01))
        assertEquals(2.5,normal.nodeVoltages.getValue(pin),1e-4)
        val bright=c.copy(components=c.components.map {
            if(it.id==sensor.id)it.copy(parameters=it.parameters+("illuminanceLux" to 1000.0)) else it
        })
        assertTrue(session.settleDc(bright,emptyList(),.02).nodeVoltages.getValue(pin)<1.0)
        val forced=session.settleDc(c,listOf(ExternalDrive(pin,0.0,1.0)),.03)
        assertTrue(forced.nodeVoltages.getValue(pin)<.001)
        val rewired=c.copy(wires=c.wires+Wire(start=pin,end=TerminalRef(c.components.first { it.kind==Kind.GROUND }.id,0)))
        assertEquals(0.0,session.settleDc(rewired,emptyList(),.04).nodeVoltages.getValue(pin),1e-9)
        assertSame(normal,session.settleDc(c,emptyList(),.05))
    }

    @Test fun unchangedLinearStampsConvergeWithoutRepeatedDamping() {
        val c=SampleCircuits.lightDivider()
        val result=DcSolver().solve(c)
        assertNull(result.error)
        assertTrue(result.progress!!.iterations<=4)
    }
}
