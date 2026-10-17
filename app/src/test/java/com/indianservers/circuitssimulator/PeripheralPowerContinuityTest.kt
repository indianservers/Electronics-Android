package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.PeripheralRuntime
import com.indianservers.circuitssimulator.simulation.DcResult
import org.junit.Assert.*
import org.junit.Test

class PeripheralPowerContinuityTest {
    @Test fun unknownSolveDoesNotResetPirWarmupButMeasuredPowerLossDoes() {
        val pir=PlacedComponent(id="P",kind=Kind.PIR_SENSOR,reference="P1",x=0f,y=0f,
            parameters=mapOf("warmupMs" to 10.0,"useEnvironment" to 0.0,"motion" to 1.0))
        val c=Circuit("PIR warmup continuity",listOf(pir),emptyList())
        fun supply(v:Double)=DcResult(nodeVoltages=mapOf(TerminalRef("P",0) to v,TerminalRef("P",2) to 0.0))
        val runtime=PeripheralRuntime()
        assertEquals(0.0,runtime.drives(c,supply(5.0),0).single().voltage,0.0)
        assertTrue(runtime.drives(c,null,15000).isEmpty())
        assertEquals(3.3,runtime.drives(c,supply(5.0),20000).single().voltage,0.0)
        assertTrue(runtime.drives(c,supply(0.0),21000).isEmpty())
        assertEquals(0.0,runtime.drives(c,supply(5.0),30000).single().voltage,0.0)
        assertEquals(3.3,runtime.drives(c,supply(5.0),40000).single().voltage,0.0)
    }
}
