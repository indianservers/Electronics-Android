package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10

class AcSolverTest {
    @Test fun rcLowPassCutoffAndPhase() {
        val circuit=SampleCircuits.rcLowPass()
        val source=circuit.components.first { it.kind==Kind.FUNCTION_GENERATOR }
        val resistor=circuit.components.first { it.kind==Kind.RESISTOR }
        val capacitor=circuit.components.first { it.kind==Kind.CAPACITOR }
        val input=TerminalRef(source.id,0);val output=TerminalRef(capacitor.id,0)
        val fc=1/(2*PI*1000.0*100e-9)
        val point=AcSolver().solve(circuit,fc)!!
        val gain=point.gain(input,output)!!
        assertEquals(-3.0103,20*log10(gain.magnitude),.03)
        assertEquals(-45.0,gain.phaseDegrees,.1)
        assertEquals(0.0,(point.branchCurrents.getValue(resistor.id)-point.branchCurrents.getValue(capacitor.id)).magnitude,1e-8)
        val sweep=AcSolver().sweep(circuit,10.0,100000.0,81,SweepScale.LOGARITHMIC)
        assertNull(sweep.error)
        assertEquals(81,sweep.points.size)
        assertTrue(sweep.points.first().gain(input,output)!!.magnitude > .99)
        assertTrue(sweep.points.last().gain(input,output)!!.magnitude < .02)
    }
    @Test fun complexLinearSolve() {
        val a=arrayOf(arrayOf(Complex(2.0),Complex(1.0)),arrayOf(Complex(1.0),Complex(0.0,1.0)))
        val b=arrayOf(Complex(1.0),Complex(0.0))
        val x=complexSolve(a,b)!!
        assertEquals(1.0,(Complex(2.0)*x[0]+x[1]).re,1e-10)
        assertEquals(0.0,(x[0]+Complex(0.0,1.0)*x[1]).magnitude,1e-10)
    }
    @Test fun highPassAndRlLowPassHaveExpectedCutoff() {
        val high=SampleCircuits.rcHighPass()
        val highSource=high.components.first { it.kind==Kind.FUNCTION_GENERATOR }
        val highLoad=high.components.first { it.kind==Kind.RESISTOR }
        val fc=1/(2*PI*1000.0*100e-9)
        val highGain=AcSolver().solve(high,fc)!!.gain(TerminalRef(highSource.id,0),TerminalRef(highLoad.id,0))!!
        assertEquals(-3.0103,20*log10(highGain.magnitude),.03)
        assertEquals(45.0,highGain.phaseDegrees,.1)

        val rl=SampleCircuits.rlLowPass()
        val rlSource=rl.components.first { it.kind==Kind.FUNCTION_GENERATOR }
        val rlLoad=rl.components.first { it.kind==Kind.RESISTOR }
        val rlFc=1000.0/(2*PI*.1)
        val rlGain=AcSolver().solve(rl,rlFc)!!.gain(TerminalRef(rlSource.id,0),TerminalRef(rlLoad.id,0))!!
        assertEquals(-3.0103,20*log10(rlGain.magnitude),.03)
        assertEquals(-45.0,rlGain.phaseDegrees,.1)
    }
    @Test fun transistorSweepReportsMissingSmallSignalModel() {
        val sweep=AcSolver().sweep(SampleCircuits.npnSwitch(),10.0,10000.0,20,SweepScale.LOGARITHMIC)
        assertTrue(sweep.points.isEmpty())
        assertTrue(sweep.error!!.contains("not available"))
    }
}
