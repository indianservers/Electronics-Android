package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.SampleCircuits
import com.indianservers.circuitssimulator.domain.SimulationSettings
import com.indianservers.circuitssimulator.simulation.DcSolver
import org.junit.Assert.*
import org.junit.Test

class SolverProgressTest {
    @Test fun iterationLimitReportsMeasuredUpdateAndTolerance() {
        val circuit=SampleCircuits.led().copy(settings=SimulationSettings(tolerance=1e-12,maxIterations=1))
        val result=DcSolver().solve(circuit)
        assertFalse(result.converged)
        assertEquals(1,result.progress?.iterations)
        assertTrue(result.progress!!.maximumUpdate.isFinite())
        assertTrue(result.error!!.contains("largest update"))
        assertTrue(result.error!!.contains("tolerance"))
    }
}
