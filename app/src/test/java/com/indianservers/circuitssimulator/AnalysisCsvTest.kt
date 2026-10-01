package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

class AnalysisCsvTest {
    @Test fun exportsActualSolverSamples() {
        val circuit=SampleCircuits.rcLowPass()
        val source=circuit.components.first { it.kind==Kind.FUNCTION_GENERATOR }
        val cap=circuit.components.first { it.kind==Kind.CAPACITOR }
        val input=TerminalRef(source.id,0);val output=TerminalRef(cap.id,0)
        val transient=TransientSolver().simulate(circuit,.005,.0001)
        assertNull(transient.error)
        val csv=AnalysisCsv.scope(transient.frames,input,output).lines()
        assertEquals("time_seconds,ch1_volts,ch2_volts",csv.first())
        assertEquals(transient.frames.size+2,csv.size)
        val values=csv[1].split(',')
        assertEquals(transient.frames.first().nodeVoltages.getValue(input),values[1].toDouble(),1e-8)
        val ac=AcSolver().sweep(circuit,10.0,100000.0,11,SweepScale.LOGARITHMIC)
        assertNull(ac.error)
        val bode=AnalysisCsv.bode(ac.points,input,output).lines()
        assertEquals("frequency_hz,magnitude_db,phase_degrees",bode.first())
        assertEquals(13,bode.size)
    }
}
