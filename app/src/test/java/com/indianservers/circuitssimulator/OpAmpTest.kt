package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

class OpAmpTest {
    @Test fun nonInvertingAmplifierHasGainTen() {
        val circuit=SampleCircuits.nonInvertingOpAmp()
        val op=circuit.components.first { it.kind==Kind.OPAMP }
        val result=DcSolver().solve(circuit)
        assertNull(result.error)
        assertEquals(10.0,result.nodeVoltages.getValue(TerminalRef(op.id,2)),.02)
        val source=circuit.components.first { it.kind==Kind.SOURCE }
        val ac=AcSolver().solve(circuit,100.0)!!
        assertEquals(10.0,ac.gain(TerminalRef(source.id,0),TerminalRef(op.id,2))!!.magnitude,.03)
    }
    @Test fun outputCannotExceedRails() {
        val original=SampleCircuits.nonInvertingOpAmp()
        val source=original.components.first { it.kind==Kind.SOURCE }
        val op=original.components.first { it.kind==Kind.OPAMP }
        val circuit=original.copy(components=original.components.map { p -> when(p.id) {
            source.id -> p.copy(parameters=p.parameters+("voltage" to 2.0))
            op.id -> p.copy(parameters=p.parameters+("lowerRail" to 0.0)+("upperRail" to 5.0))
            else -> p
        } })
        val result=DcSolver().solve(circuit)
        assertNull(result.error)
        assertTrue(result.nodeVoltages.getValue(TerminalRef(op.id,2))<=4.51)
    }
    @Test fun opAmpDrivesTransientAndRollsOffWithBandwidth() {
        val original=SampleCircuits.nonInvertingOpAmp()
        val source=original.components.first { it.kind==Kind.SOURCE }
        val circuit=original.copy(components=original.components.map { p -> if(p.id==source.id)
            p.copy(kind=Kind.FUNCTION_GENERATOR,parameters=mapOf("frequency" to 100.0,
                "amplitude" to .1,"offset" to 0.0)) else p })
        val op=circuit.components.first { it.kind==Kind.OPAMP }
        val trace=TransientSolver().simulate(circuit,.02,.00005)
        assertNull(trace.error)
        val quarter=trace.frameAt(.0025)!!
        assertEquals(1.0,quarter.nodeVoltages.getValue(TerminalRef(op.id,2)),.04)
        val low=AcSolver().solve(circuit,100.0)!!.gain(TerminalRef(source.id,0),TerminalRef(op.id,2))!!
        val high=AcSolver().solve(circuit,100000.0)!!.gain(TerminalRef(source.id,0),TerminalRef(op.id,2))!!
        assertEquals(10.0,low.magnitude,.03)
        assertTrue(high.magnitude<low.magnitude*.8)
    }
}
