package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import org.junit.Assert.*
import org.junit.Test

class LiveSimulationTest {
    @Test fun reactiveSlicesMatchUninterruptedRunAndKeepAbsoluteTime() {
        val circuit=SampleCircuits.rc()
        val capacitor=circuit.components.first { it.kind==Kind.CAPACITOR }
        val solver=TransientSolver()
        val whole=solver.simulate(circuit,.2,.001)
        val first=solver.simulate(circuit,.1,.001)
        val second=solver.simulate(circuit,.1,.001,checkpoint=first.checkpoint)
        assertNull(first.error)
        assertNull(second.error)
        assertEquals(.2,second.checkpoint!!.timeSeconds,1e-9)
        assertEquals(whole.frames.last().readings.getValue(capacitor.id).voltage,
            second.frames.last().readings.getValue(capacitor.id).voltage,1e-5)
        val joined=appendTransient(first,second)
        assertEquals(first.frames.size+second.frames.size,joined.frames.size)
        assertTrue(joined.frames.zipWithNext().all { (a,b) -> b.timeSeconds>a.timeSeconds })
    }

    @Test fun changingSourceDuringRunPreservesCapacitorChargeAndTrace() {
        val circuit=SampleCircuits.rc()
        val capacitor=circuit.components.first { it.kind==Kind.CAPACITOR }
        val battery=circuit.components.first { it.kind==Kind.BATTERY }
        val solver=TransientSolver()
        val charged=solver.simulate(circuit,.1,.001)
        val before=charged.frames.last().readings.getValue(capacitor.id).voltage
        val changed=circuit.copy(components=circuit.components.map { part ->
            if(part.id==battery.id) part.copy(parameters=part.parameters+("voltage" to 0.1)) else part
        })
        val next=solver.simulate(changed,.01,.001,checkpoint=charged.checkpoint)
        assertNull(next.error)
        val after=next.frames.last().readings.getValue(capacitor.id).voltage
        assertTrue(before>1.0)
        assertTrue(after>0.0 && after<before)
        assertEquals(.11,next.checkpoint!!.timeSeconds,1e-9)
        assertTrue(appendTransient(charged,next).frames.any { it.timeSeconds<.1 })
    }

    @Test fun closingSwitchWhileRunningStartsChargingWithoutRewind() {
        val circuit=SampleCircuits.rc()
        val capacitor=circuit.components.first { it.kind==Kind.CAPACITOR }
        val switch=circuit.components.first { it.kind==Kind.SWITCH }
        val open=circuit.copy(components=circuit.components.map { if(it.id==switch.id) it.copy(closed=false) else it })
        val solver=TransientSolver()
        val before=solver.simulate(open,.02,.001)
        val after=solver.simulate(circuit,.02,.001,checkpoint=before.checkpoint)
        assertNull(before.error)
        assertNull(after.error)
        assertEquals(0.0,before.frames.last().readings.getValue(capacitor.id).voltage,1e-6)
        assertTrue(after.frames.last().readings.getValue(capacitor.id).voltage>0.0)
        assertEquals(.04,after.checkpoint!!.timeSeconds,1e-9)
    }

    @Test fun digitalClockPhaseAndGateStateSurviveChunkBoundary() {
        val circuit=SampleCircuits.digitalInverter()
        val clock=circuit.components.first { it.kind==Kind.CLOCK }
        val session=CanvasDigitalSimulator().session(circuit)
        val first=session.advance(.04,.0005)
        val second=session.advance(.04,.0005,first.checkpoint)
        assertNull(first.error)
        assertNull(second.error)
        assertEquals(LogicState.HIGH,first.frames.last().digitalStates[TerminalRef(clock.id,0)])
        assertEquals(LogicState.LOW,second.frames.last().digitalStates[TerminalRef(clock.id,0)])
        assertTrue(second.frames.any { kotlin.math.abs(it.timeSeconds-.05)<1e-10 })
    }
}
