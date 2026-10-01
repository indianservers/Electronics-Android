package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.threeTerminalCurrents
import com.indianservers.circuitssimulator.simulation.DcSolver
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SemiconductorModelTest {
    private fun part(kind:Kind)=PlacedComponent(kind=kind,reference="X1",x=0f,y=0f)

    @Test fun npnHasCutoffActiveAndSaturationFoundation() {
        val npn=part(Kind.NPN_BJT)
        val cutoff=threeTerminalCurrents(npn,doubleArrayOf(5.0,0.0,0.0))
        val active=threeTerminalCurrents(npn,doubleArrayOf(5.0,.7,0.0))
        val saturated=threeTerminalCurrents(npn,doubleArrayOf(.2,.8,0.0))
        assertTrue(abs(cutoff[0])<1e-8)
        assertTrue(active[0]>.001)
        assertTrue(active[1]>0.0)
        assertTrue(saturated[0]>0.0)
        assertTrue(abs(active.sum())<1e-12)
    }

    @Test fun pnpPolarityIsReversed() {
        val pnp=part(Kind.PNP_BJT)
        val current=threeTerminalCurrents(pnp,doubleArrayOf(0.0,4.3,5.0))
        assertTrue(current[0]<-.001)
        assertTrue(abs(current.sum())<1e-12)
    }

    @Test fun mosfetHasOffLinearSaturationAndBodyDiode() {
        val nmos=part(Kind.NMOS)
        val off=threeTerminalCurrents(nmos,doubleArrayOf(5.0,0.0,0.0))[0]
        val linear=threeTerminalCurrents(nmos,doubleArrayOf(.5,5.0,0.0))[0]
        val saturation=threeTerminalCurrents(nmos,doubleArrayOf(5.0,5.0,0.0))[0]
        val body=threeTerminalCurrents(nmos,doubleArrayOf(-.7,0.0,0.0))[0]
        assertTrue(abs(off)<1e-8)
        assertTrue(linear>0.0)
        assertTrue(saturation>linear)
        assertTrue(body<0.0)
        val pmos=part(Kind.PMOS)
        assertTrue(threeTerminalCurrents(pmos,doubleArrayOf(0.0,0.0,5.0))[0]<0.0)
    }

    @Test fun npnAndNmosSwitchesRespondToControlSource() {
        val solver=DcSolver()
        val npnOffCircuit=SampleCircuits.npnSwitch(0.0)
        val npnOnCircuit=SampleCircuits.npnSwitch(5.0)
        val npnOff=solver.solve(npnOffCircuit)
        val npnOn=solver.solve(npnOnCircuit)
        assertNull(npnOff.error)
        assertNull(npnOn.error)
        val offCollector=npnOff.readings.getValue(npnOffCircuit.components.first { it.kind==Kind.NPN_BJT }.id).current
        val onCollector=npnOn.readings.getValue(npnOnCircuit.components.first { it.kind==Kind.NPN_BJT }.id).current
        assertTrue(abs(offCollector)<1e-8)
        assertTrue(onCollector>.001)
        val mosOffCircuit=SampleCircuits.nmosSwitch(0.0)
        val mosOnCircuit=SampleCircuits.nmosSwitch(5.0)
        val mosOff=solver.solve(mosOffCircuit)
        val mosOn=solver.solve(mosOnCircuit)
        assertNull(mosOff.error)
        assertNull(mosOn.error)
        val offId=abs(mosOff.readings.getValue(mosOffCircuit.components.first { it.kind==Kind.NMOS }.id).current)
        val onId=abs(mosOn.readings.getValue(mosOnCircuit.components.first { it.kind==Kind.NMOS }.id).current)
        assertTrue(onId>offId+0.01)
    }
}
